package com.ticketplatform.pipeline;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketplatform.agent.ReplyAgent;
import com.ticketplatform.agent.ReplyResult;
import com.ticketplatform.agent.RetrievalAgent;
import com.ticketplatform.agent.RetrievalResult;
import com.ticketplatform.agent.TriageAgent;
import com.ticketplatform.agent.TriageResult;
import com.ticketplatform.domain.SuggestedAction;
import com.ticketplatform.domain.Ticket;
import com.ticketplatform.domain.TicketStatus;
import com.ticketplatform.repo.TicketRepository;
import com.ticketplatform.service.MailSender;
import com.ticketplatform.service.TicketTransitionService;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * 工单处理流水线（编排器）：
 *
 *   NEW → TRIAGING(规划Agent) → RETRIEVING(检索Agent) → DRAFTING(回复Agent)
 *       → 按 Agent 建议流转：HUMAN_REVIEW / RESOLVED(自动发邮件) / ESCALATED
 *
 * 任一阶段失败 → FAILED（记录失败阶段与原因），已完成的阶段结果不丢，
 * 调用重试接口后从失败阶段断点续跑。
 */
@Component
public class TicketPipeline {

    private static final Logger log = LoggerFactory.getLogger(TicketPipeline.class);

    /** 单线程顺序执行：MVP 足够，且避免并发改单条工单；扩容时可换线程池按工单ID分片 */
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "ticket-pipeline");
        t.setDaemon(true);
        return t;
    });

    private final TicketRepository ticketRepository;
    private final TicketTransitionService transitions;
    private final TriageAgent triageAgent;
    private final RetrievalAgent retrievalAgent;
    private final ReplyAgent replyAgent;
    private final MailSender mailSender;
    private final ObjectMapper objectMapper;

    public TicketPipeline(TicketRepository ticketRepository,
                          TicketTransitionService transitions,
                          TriageAgent triageAgent,
                          RetrievalAgent retrievalAgent,
                          ReplyAgent replyAgent,
                          MailSender mailSender,
                          ObjectMapper objectMapper) {
        this.ticketRepository = ticketRepository;
        this.transitions = transitions;
        this.triageAgent = triageAgent;
        this.retrievalAgent = retrievalAgent;
        this.replyAgent = replyAgent;
        this.mailSender = mailSender;
        this.objectMapper = objectMapper;
    }

    /** 异步触发处理（REST 创建工单后调用，立即返回） */
    public void submitAsync(Long ticketId) {
        executor.submit(() -> {
            try {
                process(ticketId);
            } catch (Exception e) {
                log.error("工单#{} 流水线执行异常", ticketId, e);
            }
        });
    }

    /** 同步执行全流程（供测试与重试） */
    public void process(Long ticketId) {
        Ticket ticket = ticketRepository.findById(ticketId).orElse(null);
        if (ticket == null) return;
        if (ticket.getStatus().isTerminal()) {
            log.info("工单#{} 已是终态 {}，跳过", ticketId, ticket.getStatus());
            return;
        }

        // ---- 阶段 1：规划 Agent（已有分类结果且非该阶段失败 → 断点续跑时跳过）----
        if (ticket.getCategory() == null || "TRIAGE".equals(ticket.getFailedStep())) {
            ticket = stage(ticket, TicketStatus.TRIAGING, "TRIAGE_AGENT", "TRIAGE", this::triageStage);
            if (ticket == null) return;
        }

        // ---- 阶段 2：检索 Agent ----
        if (ticket.getRetrievalContextJson() == null || "RETRIEVAL".equals(ticket.getFailedStep())) {
            ticket = stage(ticket, TicketStatus.RETRIEVING, "RETRIEVAL_AGENT", "RETRIEVAL", this::retrievalStage);
            if (ticket == null) return;
        }

        // ---- 阶段 3：回复 Agent + 终态流转 ----
        ticket = stage(ticket, TicketStatus.DRAFTING, "REPLY_AGENT", "REPLY", this::replyStage);
        if (ticket == null) return;

        finalizeTicket(ticket);
    }

    @FunctionalInterface
    private interface StageLogic {
        Ticket apply(Ticket ticket) throws Exception;
    }

    /**
     * 阶段执行包装：先把工单推进到工作状态，再执行业务逻辑；
     * 任何异常都收敛为 FAILED（含失败阶段与原因），返回 null 表示流水线终止。
     */
    private Ticket stage(Ticket ticket, TicketStatus working, String actor, String failedStep, StageLogic logic) {
        Ticket current = transitions.transition(ticket, working, "SYSTEM", "进入" + working.getLabel());
        try {
            current = logic.apply(current);
            current = ticketRepository.saveAndFlush(current);
            return current;
        } catch (Exception e) {
            log.error("工单#{} 阶段 {} 失败：{}", current.getId(), failedStep, e.getMessage(), e);
            current.setFailedStep(failedStep);
            current.setErrorMessage(abbreviate(e.getMessage(), 900));
            transitions.transition(current, TicketStatus.FAILED, actor,
                    "阶段失败(" + failedStep + ")：" + abbreviate(e.getMessage(), 300));
            return null;
        }
    }

    // ---- 各阶段业务 ----

    private Ticket triageStage(Ticket ticket) throws Exception {
        TriageResult result = triageAgent.triage(ticket);
        ticket.setCategory(result.category());
        ticket.setPriority(result.priority());
        ticket.setAssignedTeam(result.assignedTeam());
        ticket.setSubtasksJson(objectMapper.writeValueAsString(result.subtasks()));
        ticket.setTriageSummary(result.summary());
        ticket.setTriageConfidence(result.confidence());
        ticket.setFailedStep(null);
        ticket.setErrorMessage(null);
        transitions.recordNote(ticket, "TRIAGE_AGENT", result.summary()
                + "（类别=" + result.category() + "，优先级=" + result.priority().getLabel()
                + "，处理组=" + result.assignedTeam() + "，置信度=" + result.confidence() + "）");
        return ticket;
    }

    private Ticket retrievalStage(Ticket ticket) throws Exception {
        RetrievalResult result = retrievalAgent.retrieve(ticket);
        String json = objectMapper.writeValueAsString(result);
        ticket.setRetrievalContextJson(json);
        ticket.setFailedStep(null);
        ticket.setErrorMessage(null);
        transitions.recordNote(ticket, "RETRIEVAL_AGENT",
                "检索完成：手册命中 " + result.manuals().size() + " 条，相似工单 " + result.similarTickets().size() + " 条");
        return ticket;
    }

    private Ticket replyStage(Ticket ticket) throws Exception {
        RetrievalResult retrieval = parseRetrievalContext(ticket);
        ReplyResult result = replyAgent.draftReply(ticket, retrieval);
        ticket.setReplyDraft(result.replyDraft());
        ticket.setSuggestedAction(result.action());
        ticket.setReplyConfidence(result.confidence());
        ticket.setFailedStep(null);
        ticket.setErrorMessage(null);
        transitions.recordNote(ticket, "REPLY_AGENT",
                "回复草稿已生成，处置建议=" + result.action().getLabel() + "，置信度=" + result.confidence());
        return ticket;
    }

    private void finalizeTicket(Ticket ticket) {
        SuggestedAction action = ticket.getSuggestedAction();
        if (action == null) {
            action = SuggestedAction.NEED_HUMAN;
        }
        switch (action) {
            case AUTO_REPLY -> {
                sendMailSafely(ticket);
                ticket.setResolvedAt(java.time.LocalDateTime.now());
                ticket.setResolution(ticket.getReplyDraft());
                transitions.transition(ticket, TicketStatus.RESOLVED, "SYSTEM",
                        "智能体自动回复邮件并关单（置信度=" + ticket.getReplyConfidence() + "）");
            }
            case ESCALATE -> transitions.transition(ticket, TicketStatus.ESCALATED, "SYSTEM",
                    "按 Agent 建议转人工处理组：" + ticket.getAssignedTeam());
            case NEED_HUMAN -> transitions.transition(ticket, TicketStatus.HUMAN_REVIEW, "SYSTEM",
                    "回复草稿待人工审核（置信度=" + ticket.getReplyConfidence() + "）");
        }
    }

    private void sendMailSafely(Ticket ticket) {
        try {
            mailSender.send(ticket.getRequesterEmail(),
                    "[已解决] 工单#" + ticket.getId() + "：" + ticket.getTitle(),
                    ticket.getReplyDraft());
        } catch (Exception e) {
            log.error("工单#{} 自动回复邮件发送失败：{}", ticket.getId(), e.getMessage());
            transitions.recordNote(ticket, "SYSTEM", "警告：回复邮件发送失败(" + abbreviate(e.getMessage(), 120)
                    + ")，请人工补发。正文见 replyDraft 字段");
        }
    }

    private RetrievalResult parseRetrievalContext(Ticket ticket) throws Exception {
        String json = ticket.getRetrievalContextJson();
        if (json == null || json.isBlank()) {
            return new RetrievalResult(java.util.List.of(), java.util.List.of());
        }
        return objectMapper.readValue(json, RetrievalResult.class);
    }

    private static String abbreviate(String s, int max) {
        if (s == null) return "未知错误";
        return s.length() > max ? s.substring(0, max) + "..." : s;
    }

    @PreDestroy
    void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
