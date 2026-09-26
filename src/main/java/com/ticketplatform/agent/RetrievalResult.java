package com.ticketplatform.agent;

import java.util.List;

/**
 * 检索 Agent 的产出：运维手册命中片段 + 历史相似工单。
 */
public record RetrievalResult(List<ManualHit> manuals, List<SimilarTicketHit> similarTickets) {

    public record ManualHit(String sourceTitle, String snippet) {
    }

    public record SimilarTicketHit(Long ticketId, String title, String resolution) {
    }
}
