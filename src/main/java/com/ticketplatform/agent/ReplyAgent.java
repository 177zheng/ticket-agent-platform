package com.ticketplatform.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketplatform.config.AppProperties;
import com.ticketplatform.domain.SuggestedAction;
import com.ticketplatform.domain.Ticket;
import com.ticketplatform.llm.JsonExtractor;
import com.ticketplatform.llm.LlmClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * 回复 Agent：综合分诊结果 + 检索上下文，生成回复草稿并给出处置建议
 * （自动回复 / 人工审核 / 转人工）。
 */
@Component
public class ReplyAgent {

    private static final Logger log = LoggerFactory.getLogger(ReplyAgent.class);

    private static final String SYSTEM_PROMPT = """
            你是企业 IT 服务台的资深工程师，负责起草工单回复。请根据分诊结果、命中的运维手册片段和历史相似工单，为用户起草一封礼貌、可执行的中文回复邮件正文。
            只输出一个 JSON 对象，不要输出任何其他文字或代码块标记，格式如下：
            {"replyDraft":"回复邮件正文","action":"AUTO_REPLY|NEED_HUMAN|ESCALATE","confidence":0.0}
            action 判断标准：手册中有明确可操作步骤且与问题高度相关 → AUTO_REPLY；
            有思路但不完全确定 → NEED_HUMAN；问题紧急、疑似系统缺陷或涉及数据变更 → ESCALATE。
            replyDraft 中引用手册步骤时注明出处（如「参见《VPN 接入故障排查手册》」）。""";

    private final AppProperties props;
    private final LlmClient llmClient;
    private final ObjectMapper objectMapper;

    public ReplyAgent(AppProperties props, ObjectProvider<LlmClient> llmClientProvider, ObjectMapper objectMapper) {
        this.props = props;
        this.llmClient = llmClientProvider.getIfAvailable();
        this.objectMapper = objectMapper;
    }

    public ReplyResult draftReply(Ticket ticket, RetrievalResult retrieval) {
        if ("mock".equalsIgnoreCase(props.getLlm().getMode())) {
            return mockReply(ticket, retrieval);
        }
        return llmReply(ticket, retrieval);
    }

    // ---- mock：模板生成 ----

    private ReplyResult mockReply(Ticket ticket, RetrievalResult retrieval) {
        boolean urgent = ticket.getPriority() == com.ticketplatform.domain.Priority.URGENT;

        // 1) 紧急工单：直接转人工
        if (urgent) {
            String draft = "【升级通知】工单#" + ticket.getId() + "「" + ticket.getTitle() + "」被识别为紧急问题"
                    + "（类别：" + ticket.getCategory() + "），已自动升级转派 " + ticket.getAssignedTeam()
                    + " 人工处置。智能体建议处理步骤：\n"
                    + bulletList(ticket) + "\n请值班工程师在 30 分钟内响应。";
            return new ReplyResult(draft, SuggestedAction.ESCALATE, 0.9);
        }

        // 2) 手册命中 + 分诊置信度足够 → 自动回复
        if (!retrieval.manuals().isEmpty() && ticket.getTriageConfidence() != null
                && ticket.getTriageConfidence() >= 0.6) {
            RetrievalResult.ManualHit manual = retrieval.manuals().get(0);
            StringBuilder sb = new StringBuilder();
            sb.append(ticket.getRequesterName()).append("，您好：\n\n");
            sb.append("您提交的工单「").append(ticket.getTitle()).append("」已由智能体自动处理，判断为「")
              .append(ticket.getCategory()).append("」类问题。请参考以下步骤自助处理：\n\n");
            sb.append(bulletList(ticket)).append("\n\n");
            sb.append("详细操作可参见《").append(manual.sourceTitle()).append("》：\n")
              .append("> ").append(manual.snippet()).append("\n");
            if (!retrieval.similarTickets().isEmpty()) {
                RetrievalResult.SimilarTicketHit hit = retrieval.similarTickets().get(0);
                sb.append("\n此前类似工单 #").append(hit.ticketId()).append("「").append(hit.title())
                  .append("」的解决方式：").append(hit.resolution()).append("\n");
            }
            sb.append("\n如按上述步骤仍无法解决，请直接回复本邮件，我们将转人工工程师跟进。\n\n")
              .append("—— IT 服务台 · 工单智能体");
            return new ReplyResult(sb.toString(), SuggestedAction.AUTO_REPLY, 0.82);
        }

        // 3) 没有可靠依据 → 生成半成品草稿，交人工审核
        String draft = "（草稿-待人工完善）" + ticket.getRequesterName() + "，您好：\n\n"
                + "关于您提交的「" + ticket.getTitle() + "」，初步判断为「" + ticket.getCategory()
                + "」类问题，建议步骤：\n" + bulletList(ticket) + "\n\n"
                + "知识库中未命中高置信度手册条目，本草稿由智能体生成、需人工审核后发送。";
        return new ReplyResult(draft, SuggestedAction.NEED_HUMAN, 0.5);
    }

    private String bulletList(Ticket ticket) {
        StringBuilder sb = new StringBuilder();
        String[] subtasks = ticket.getSubtasksJson() == null ? new String[0]
                : ticket.getSubtasksJson().replace("[", "").replace("]", "").replace("\"", "").split(",");
        for (int i = 0; i < subtasks.length; i++) {
            if (!subtasks[i].isBlank()) {
                sb.append(i + 1).append(". ").append(subtasks[i].strip()).append("\n");
            }
        }
        return sb.toString();
    }

    // ---- openai：LLM 生成 ----

    private ReplyResult llmReply(Ticket ticket, RetrievalResult retrieval) {
        if (llmClient == null) {
            throw new com.ticketplatform.llm.LlmException("app.llm.mode=openai 但 LLM 客户端未初始化");
        }
        StringBuilder user = new StringBuilder();
        user.append("工单信息：\n标题：").append(ticket.getTitle())
            .append("\n描述：").append(ticket.getDescription())
            .append("\n提单人：").append(ticket.getRequesterName())
            .append("\n\n分诊结果：类别=").append(ticket.getCategory())
            .append("，优先级=").append(ticket.getPriority())
            .append("，处理组=").append(ticket.getAssignedTeam())
            .append("，建议步骤=").append(ticket.getSubtasksJson())
            .append("\n\n命中的运维手册片段：\n");
        if (retrieval.manuals().isEmpty()) {
            user.append("（无命中）\n");
        } else {
            for (RetrievalResult.ManualHit m : retrieval.manuals()) {
                user.append("- 《").append(m.sourceTitle()).append("》").append(m.snippet()).append("\n");
            }
        }
        user.append("\n历史相似工单：\n");
        if (retrieval.similarTickets().isEmpty()) {
            user.append("（无相似工单）\n");
        } else {
            for (RetrievalResult.SimilarTicketHit s : retrieval.similarTickets()) {
                user.append("- #").append(s.ticketId()).append(" ").append(s.title())
                   .append(" → ").append(s.resolution()).append("\n");
            }
        }

        String raw = llmClient.complete(SYSTEM_PROMPT, user.toString());
        try {
            JsonNode node = objectMapper.readTree(JsonExtractor.extractJsonObject(raw));
            String draft = node.path("replyDraft").asText("");
            if (draft.isBlank()) {
                throw new com.ticketplatform.llm.LlmException("LLM 未返回回复草稿");
            }
            double confidence = node.path("confidence").asDouble(0.5);
            SuggestedAction action = SuggestedAction.parse(
                    node.path("action").asText(null), SuggestedAction.NEED_HUMAN);
            return new ReplyResult(draft, action, Math.max(0, Math.min(1, confidence)));
        } catch (com.ticketplatform.llm.LlmException e) {
            throw e;
        } catch (Exception e) {
            throw new com.ticketplatform.llm.LlmException("解析回复结果失败: " + e.getMessage(), e);
        }
    }
}
