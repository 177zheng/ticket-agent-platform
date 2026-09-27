package com.ticketplatform.pipeline;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 全链路集成测试（Mock Agent 模式 + 内存 H2 + 真实 Spring Security 过滤链）：
 * 注册/登录 → 提单 → 三 Agent 流水线 → 数据隔离 → 权限矩阵 → 不满意重开闭环。
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PipelineIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper om;

    private static final String ADMIN = "admin";           // DataSeeder 预置
    private static final String ADMIN_PWD = "admin123";
    private static final AtomicLong SEQ = new AtomicLong(System.currentTimeMillis() % 100000);

    // ---------- 基础动作 ----------

    /** MockMvc 的 content(String) 走 ISO-8859-1 会打碎中文，统一走 UTF-8 字节 */
    private byte[] json(Object body) throws Exception {
        return om.writeValueAsString(body).getBytes(StandardCharsets.UTF_8);
    }

    private String login(String username, String password) throws Exception {
        MvcResult r = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("username", username, "password", password))))
                .andExpect(status().isOk()).andReturn();
        return om.readTree(r.getResponse().getContentAsString(StandardCharsets.UTF_8)).path("token").asText();
    }

    /** 注册一个新员工（请求里带 role=ADMIN 也不该生效），返回其 token */
    private String registerEmployee() throws Exception {
        String username = "emp" + SEQ.incrementAndGet();
        Map<String, String> body = Map.of(
                "username", username, "name", "测试员工",
                "email", username + "@corp.com", "password", "pass123", "role", "ADMIN");
        MvcResult r = mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isCreated()).andReturn();
        JsonNode json = om.readTree(r.getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertEquals("EMPLOYEE", json.path("role").asText(), "注册接口必须强制员工角色");
        return json.path("token").asText();
    }

    private long createTicket(String token, String title, String description) throws Exception {
        MvcResult r = mvc.perform(post("/api/tickets")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("title", title, "description", description))))
                .andExpect(status().isCreated()).andReturn();
        return om.readTree(r.getResponse().getContentAsString(StandardCharsets.UTF_8)).path("id").asLong();
    }

    private JsonNode getTicket(String token, long id) throws Exception {
        MvcResult r = mvc.perform(get("/api/tickets/" + id)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn();
        return om.readTree(r.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    /** 轮询等待流水线离开工作状态（Mock 模式毫秒级完成，5 秒足够宽裕） */
    private JsonNode awaitPipelineDone(String token, long id) throws Exception {
        long deadline = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < deadline) {
            JsonNode t = getTicket(token, id);
            String s = t.path("status").asText();
            if (!s.equals("NEW") && !s.equals("TRIAGING") && !s.equals("RETRIEVING") && !s.equals("DRAFTING")) {
                return t;
            }
            Thread.sleep(100);
        }
        fail("流水线 5 秒内未完成，工单 #" + id);
        return null;
    }

    // ---------- 测试用例 ----------

    @Test
    @org.junit.jupiter.api.Order(1)
    void 匿名访问_一律401() throws Exception {
        mvc.perform(get("/api/tickets")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/knowledge/manuals")).andExpect(status().isUnauthorized());
    }

    @Test
    @org.junit.jupiter.api.Order(2)
    void VPN工单_Mock流水线自动回复并解决() throws Exception {
        String token = registerEmployee();
        long id = createTicket(token, "VPN 连接后打不开内网系统",
                "在家办公，VPN 显示已连接成功，但是 OA 和 ERP 网页都打不开，重启电脑也没用");

        JsonNode t = awaitPipelineDone(token, id);
        assertEquals("RESOLVED", t.path("status").asText(), "Mock 规则命中知识库应自动回复关单");
        assertEquals("网络与接入", t.path("category").asText());
        assertEquals("AUTO_REPLY", t.path("suggestedAction").asText());
        assertFalse(t.path("replyDraft").asText().isBlank(), "回复草稿不应为空");
        assertFalse(t.path("resolution").isNull(), "自动解决应写入最终处理方案");
        assertFalse(t.path("manuals").isEmpty(), "应命中 VPN 运维手册");
        assertTrue(t.path("events").size() >= 6, "审计时间线应记录完整流转");
    }

    @Test
    @org.junit.jupiter.api.Order(3)
    void 员工只看自己的工单_管理员看全量() throws Exception {
        String empToken = registerEmployee();
        createTicket(empToken, "打印机打印乱码", "打出来全是乱码符号，别人打也这样");
        createTicket(empToken, "邮箱满了收不到邮件", "系统提示容量已满，客户邮件被退回");

        String adminToken = login(ADMIN, ADMIN_PWD);

        MvcResult empView = mvc.perform(get("/api/tickets").header("Authorization", "Bearer " + empToken))
                .andExpect(status().isOk()).andReturn();
        assertEquals(2, om.readTree(empView.getResponse().getContentAsString(StandardCharsets.UTF_8)).size(),
                "员工列表只应包含自己的工单");

        MvcResult statsView = mvc.perform(get("/api/tickets/stats").header("Authorization", "Bearer " + empToken))
                .andExpect(status().isOk()).andReturn();
        JsonNode stats = om.readTree(statsView.getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertEquals("MINE", stats.path("scope").asText());

        MvcResult adminStats = mvc.perform(get("/api/tickets/stats").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk()).andReturn();
        assertEquals("ALL", om.readTree(adminStats.getResponse().getContentAsString(StandardCharsets.UTF_8)).path("scope").asText());
        assertTrue(om.readTree(adminStats.getResponse().getContentAsString(StandardCharsets.UTF_8)).path("total").asLong() >= 2 + 6,
                "管理员统计应含种子历史工单");
    }

    @Test
    @org.junit.jupiter.api.Order(4)
    void 权限矩阵_员工越权操作全部403() throws Exception {
        String empToken = registerEmployee();
        long id = createTicket(empToken, "咨询会议室预订规则", "请问下周三的会议室怎么预订");
        awaitPipelineDone(empToken, id);

        mvc.perform(post("/api/tickets/" + id + "/review").header("Authorization", "Bearer " + empToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("approve", true))))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/tickets/" + id + "/retry").header("Authorization", "Bearer " + empToken))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/tickets/" + id + "/close").header("Authorization", "Bearer " + empToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("comment", "x"))))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/knowledge/manuals").header("Authorization", "Bearer " + empToken))
                .andExpect(status().isForbidden());

        // 管理员访问知识库正常
        String adminToken = login(ADMIN, ADMIN_PWD);
        mvc.perform(get("/api/knowledge/manuals").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
    }

    @Test
    @org.junit.jupiter.api.Order(5)
    void 员工不能查看他人工单_403() throws Exception {
        String empA = registerEmployee();
        long id = createTicket(empA, "我的工单", "内容");

        String empB = registerEmployee();
        mvc.perform(get("/api/tickets/" + id).header("Authorization", "Bearer " + empB))
                .andExpect(status().isForbidden());

        // 管理员可以看
        String adminToken = login(ADMIN, ADMIN_PWD);
        mvc.perform(get("/api/tickets/" + id).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
    }

    @Test
    @org.junit.jupiter.api.Order(6)
    void 不满意重开闭环_纠正方案覆盖回流() throws Exception {
        String empToken = registerEmployee();
        long id = createTicket(empToken, "VPN 连接后打不开内网系统",
                "在家办公 VPN 已连接但 OA 打不开");
        awaitPipelineDone(empToken, id);
        JsonNode resolved = getTicket(empToken, id);
        assertEquals("RESOLVED", resolved.path("status").asText());

        // 不填原因 → 400
        mvc.perform(post("/api/tickets/" + id + "/feedback").header("Authorization", "Bearer " + empToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("satisfied", false, "reason", ""))))
                .andExpect(status().isBadRequest());

        // 填原因 → 重开转人工
        mvc.perform(post("/api/tickets/" + id + "/feedback").header("Authorization", "Bearer " + empToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("satisfied", false, "reason", "按步骤做了还是连不上"))))
                .andExpect(status().isOk());
        assertEquals("ESCALATED", getTicket(empToken, id).path("status").asText());

        // 管理员纠正后关闭 → 回到已解决且方案被覆盖
        String adminToken = login(ADMIN, ADMIN_PWD);
        mvc.perform(post("/api/tickets/" + id + "/close").header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("comment", "远程排查为运营商拦截UDP，切换TLS443后恢复"))))
                .andExpect(status().isOk());

        JsonNode finalTicket = getTicket(empToken, id);
        assertEquals("RESOLVED", finalTicket.path("status").asText());
        assertTrue(finalTicket.path("resolution").asText().contains("TLS443"),
                "人工纠正方案应覆盖为最终处理方案");
    }

    @Test
    @org.junit.jupiter.api.Order(7)
    void 紧急工单_Mock规则直接升级转人工() throws Exception {
        String empToken = registerEmployee();
        long id = createTicket(empToken, "ERP 系统全面宕机，生产事故，全员无法下单",
                "从14点开始全部打不开，全公司无法下单，紧急！");
        JsonNode t = awaitPipelineDone(empToken, id);
        assertEquals("ESCALATED", t.path("status").asText());
        assertEquals("URGENT", t.path("priority").asText());
    }
}
