package com.ticketplatform.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketplatform.config.AppProperties;
import com.ticketplatform.domain.Priority;
import com.ticketplatform.domain.Ticket;
import com.ticketplatform.llm.JsonExtractor;
import com.ticketplatform.llm.LlmClient;
import com.ticketplatform.llm.LlmException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 规划 Agent：对工单做分类（类别/优先级/处理组）并拆解处理步骤。
 * mock 模式走内置规则引擎；openai 模式走 LLM 结构化输出。
 */
@Component
public class TriageAgent {

    private static final Logger log = LoggerFactory.getLogger(TriageAgent.class);

    private static final String SYSTEM_PROMPT = """
            你是企业 IT 服务台的工单分诊专家。根据用户工单的标题和描述，输出工单分诊结果。
            只输出一个 JSON 对象，不要输出任何其他文字或代码块标记，格式如下：
            {"category":"问题类别","priority":"LOW|MEDIUM|HIGH|URGENT","assignedTeam":"处理组名称","subtasks":["处理步骤1","处理步骤2","处理步骤3"],"confidence":0.0,"summary":"一句话分诊结论"}
            类别从以下选择：账号与权限 / 网络与接入 / 办公设备 / 企业邮箱 / 终端设备 / 业务系统 / 综合支持。
            confidence 为 0.0~1.0 的小数，表示你对本次分诊的把握。
            subtasks 是 2~5 条具体可执行的处理步骤。summary 不超过 50 字。""";

    private record Rule(String[] keywords, String category, String team, Priority priority, String[] subtasks) {
    }

    private static final List<Rule> RULES = List.of(
            new Rule(new String[]{"密码", "登录", "账号", "锁定", "lock", "password", "登录不上", "无法登陆"},
                    "账号与权限", "身份认证组", Priority.HIGH,
                    new String[]{"核实用户身份信息", "检查 AD 域账号锁定状态", "重置密码或解锁账号", "电话回访确认恢复"}),
            new Rule(new String[]{"vpn", "网络", "断网", "连不上", "wifi", "无线", "上不了网", "内网"},
                    "网络与接入", "网络运维组", Priority.HIGH,
                    new String[]{"确认影响范围（单人/部门）", "检查 VPN 客户端版本与认证状态", "核查交换机/无线网关状态", "必要时上门排查网线与端口"}),
            new Rule(new String[]{"打印机", "复印机", "扫描仪", "卡纸", "打印不了"},
                    "办公设备", "桌面运维组", Priority.MEDIUM,
                    new String[]{"确认设备型号与报错现象", "检查卡纸/耗材/队列", "重装或更新打印驱动", "打印测试页验证"}),
            new Rule(new String[]{"邮箱", "邮件", "收不到", "发不出", "垃圾箱", "outlook"},
                    "企业邮箱", "邮件系统组", Priority.MEDIUM,
                    new String[]{"检查邮箱容量与配额", "核查收发件规则与垃圾箱", "核对客户端 IMAP/SMTP 配置", "跟进确认收发恢复"}),
            new Rule(new String[]{"蓝屏", "死机", "电脑", "卡顿", "笔记本", "显示器", "黑屏", "鼠标", "键盘"},
                    "终端设备", "桌面运维组", Priority.MEDIUM,
                    new String[]{"收集报错截图/蓝屏代码", "检查硬件连接与外设", "清理磁盘与开机项", "无法远程解决则安排上门"}),
            new Rule(new String[]{"系统", "报错", "页面", "erp", "oa", "crm", "报表", "下单", "500", "bug"},
                    "业务系统", "应用支持组", Priority.HIGH,
                    new String[]{"收集报错截图与发生时间", "确认影响用户范围", "检查浏览器兼容性与缓存", "疑似系统缺陷转研发二线"}));

    private final AppProperties props;
    private final LlmClient llmClient;
    private final ObjectMapper objectMapper;

    public TriageAgent(AppProperties props, ObjectProvider<LlmClient> llmClientProvider, ObjectMapper objectMapper) {
        this.props = props;
        this.llmClient = llmClientProvider.getIfAvailable();
        this.objectMapper = objectMapper;
    }

    public TriageResult triage(Ticket ticket) {
        if ("mock".equalsIgnoreCase(props.getLlm().getMode())) {
            return mockTriage(ticket);
        }
        return llmTriage(ticket);
    }

    // ---- mock：规则引擎 ----

    private TriageResult mockTriage(Ticket ticket) {
        String text = (ticket.getTitle() + " " + ticket.getDescription()).toLowerCase();

        Rule best = null;
        for (Rule rule : RULES) {
            for (String kw : rule.keywords()) {
                if (text.contains(kw.toLowerCase())) {
                    best = rule;
                    break;
                }
            }
            if (best != null) break;
        }

        // 紧急程度提升：描述里出现生产事故类关键词 → URGENT
        boolean urgent = text.contains("紧急") || text.contains("生产") && text.contains("事故")
                || text.contains("宕机") || text.contains("全员") || text.contains("大面积");

        if (best == null) {
            List<String> subtasks = List.of("补充收集问题现象与截图", "电话联系 requester 澄清问题", "按知识库手册尝试匹配方案");
            return new TriageResult("综合支持", Priority.MEDIUM, "IT服务台", subtasks, 0.45,
                    "未匹配明确类别，转综合支持人工跟进");
        }

        Priority priority = urgent ? Priority.URGENT : best.priority();
        double confidence = urgent ? 0.9 : 0.85;
        String summary = "识别为「" + best.category() + "」问题，派单至" + best.team()
                + (urgent ? "（含紧急关键词，已提升为 URGENT）" : "");
        log.info("[TriageAgent-mock] 工单#{} 分诊：{} / {} / {}", ticket.getId(), best.category(), priority, best.team());
        return new TriageResult(best.category(), priority, best.team(), List.of(best.subtasks()), confidence, summary);
    }

    // ---- openai：LLM 结构化输出 ----

    private TriageResult llmTriage(Ticket ticket) {
        if (llmClient == null) {
            throw new com.ticketplatform.llm.LlmException("app.llm.mode=openai 但 LLM 客户端未初始化");
        }
        String userPrompt = "工单标题：" + ticket.getTitle() + "\n工单描述：" + ticket.getDescription()
                + "\n提单人：" + ticket.getRequesterName();
        String raw = llmClient.complete(SYSTEM_PROMPT, userPrompt);
        try {
            JsonNode node = objectMapper.readTree(JsonExtractor.extractJsonObject(raw));
            List<String> subtasks = new ArrayList<>();
            node.path("subtasks").forEach(n -> {
                if (n.isTextual() && !n.asText().isBlank()) subtasks.add(n.asText());
            });
            if (subtasks.isEmpty()) subtasks.add("按知识库手册处理");
            double confidence = node.path("confidence").asDouble(0.5);
            return new TriageResult(
                    textOrDefault(node, "category", "综合支持"),
                    Priority.parse(node.path("priority").asText(null), Priority.MEDIUM),
                    textOrDefault(node, "assignedTeam", "IT服务台"),
                    subtasks,
                    Math.max(0, Math.min(1, confidence)),
                    textOrDefault(node, "summary", "LLM 分诊完成"));
        } catch (LlmException e) {
            throw e;
        } catch (Exception e) {
            throw new com.ticketplatform.llm.LlmException("解析分诊结果失败: " + e.getMessage(), e);
        }
    }

    private static String textOrDefault(JsonNode node, String field, String def) {
        String v = node.path(field).asText(null);
        return (v == null || v.isBlank()) ? def : v;
    }
}
