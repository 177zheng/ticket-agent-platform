package com.ticketplatform.domain;

import java.util.Map;
import java.util.Set;

/**
 * 工单状态机。
 *
 * NEW → TRIAGING → RETRIEVING → DRAFTING → HUMAN_REVIEW → RESOLVED
 *                                                  └──────→ ESCALATED → RESOLVED(人工关闭)
 * 任意执行阶段失败 → FAILED，可通过重试接口从失败步骤断点续跑（FAILED → 对应工作状态）。
 */
public enum TicketStatus {
    NEW("待处理"),
    TRIAGING("分类中(规划Agent)"),
    RETRIEVING("检索中(检索Agent)"),
    DRAFTING("生成回复中(回复Agent)"),
    HUMAN_REVIEW("待人工审核"),
    RESOLVED("已解决"),
    ESCALATED("已转人工"),
    FAILED("处理失败");

    private static final Map<TicketStatus, Set<TicketStatus>> TRANSITIONS = Map.of(
            NEW, Set.of(TRIAGING),
            TRIAGING, Set.of(RETRIEVING, FAILED),
            RETRIEVING, Set.of(DRAFTING, FAILED),
            DRAFTING, Set.of(HUMAN_REVIEW, RESOLVED, ESCALATED, FAILED),
            HUMAN_REVIEW, Set.of(RESOLVED, ESCALATED),
            ESCALATED, Set.of(RESOLVED),
            FAILED, Set.of(TRIAGING, RETRIEVING, DRAFTING),
            RESOLVED, Set.of()
    );

    private final String label;

    TicketStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public boolean canTransitionTo(TicketStatus target) {
        return TRANSITIONS.getOrDefault(this, Set.of()).contains(target);
    }

    /** 是否终态（不可再流转）。ESCALATED 允许人工处理后关闭，因此不算终态。 */
    public boolean isTerminal() {
        return this == RESOLVED;
    }
}
