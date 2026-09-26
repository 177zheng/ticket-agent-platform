package com.ticketplatform.agent;

import com.ticketplatform.domain.Priority;

import java.util.List;

/**
 * 规划 Agent 的产出：工单分类 + 处理步骤拆解。
 */
public record TriageResult(
        String category,
        Priority priority,
        String assignedTeam,
        List<String> subtasks,
        double confidence,
        String summary) {
}
