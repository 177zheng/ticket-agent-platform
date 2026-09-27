package com.ticketplatform.web.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketplatform.agent.RetrievalResult;
import com.ticketplatform.domain.Ticket;
import com.ticketplatform.domain.TicketEvent;
import com.ticketplatform.domain.TicketStatus;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 工单详情视图：把落库的 JSON 字段解析成前端友好的结构。
 */
public record TicketView(
        Long id,
        String title,
        String description,
        String requesterName,
        String requesterEmail,
        String category,
        String priority,
        String priorityLabel,
        String assignedTeam,
        List<String> subtasks,
        String triageSummary,
        Double triageConfidence,
        List<ManualItem> manuals,
        List<SimilarItem> similarTickets,
        String replyDraft,
        String suggestedAction,
        String suggestedActionLabel,
        Double replyConfidence,
        String resolution,
        String status,
        String statusLabel,
        String failedStep,
        String errorMessage,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LocalDateTime resolvedAt,
        List<EventItem> events) {

    public record ManualItem(String sourceTitle, String snippet) {
    }

    public record SimilarItem(Long ticketId, String title, String resolution) {
    }

    public record EventItem(String fromStatus, String toStatus, String actor, String note, LocalDateTime createdAt) {
    }

    public static TicketView of(Ticket t, List<TicketEvent> events, ObjectMapper om) {
        List<String> subtasks = new ArrayList<>();
        try {
            if (t.getSubtasksJson() != null) {
                om.readTree(t.getSubtasksJson()).forEach(n -> subtasks.add(n.asText()));
            }
        } catch (Exception ignored) {
        }

        List<ManualItem> manuals = new ArrayList<>();
        List<SimilarItem> similar = new ArrayList<>();
        try {
            if (t.getRetrievalContextJson() != null) {
                JsonNode node = om.readTree(t.getRetrievalContextJson());
                node.path("manuals").forEach(m ->
                        manuals.add(new ManualItem(m.path("sourceTitle").asText(), m.path("snippet").asText())));
                node.path("similarTickets").forEach(s -> similar.add(new SimilarItem(
                        s.path("ticketId").asLong(), s.path("title").asText(), s.path("resolution").asText())));
            }
        } catch (Exception ignored) {
        }

        List<EventItem> eventItems = events.stream()
                .map(e -> new EventItem(e.getFromStatus(), e.getToStatus(), e.getActor(),
                        e.getNote(), e.getCreatedAt()))
                .toList();

        TicketStatus status = t.getStatus();
        return new TicketView(
                t.getId(), t.getTitle(), t.getDescription(), t.getRequesterName(), t.getRequesterEmail(),
                t.getCategory(),
                t.getPriority() != null ? t.getPriority().name() : null,
                t.getPriority() != null ? t.getPriority().getLabel() : null,
                t.getAssignedTeam(),
                subtasks, t.getTriageSummary(), t.getTriageConfidence(),
                manuals, similar,
                t.getReplyDraft(),
                t.getSuggestedAction() != null ? t.getSuggestedAction().name() : null,
                t.getSuggestedAction() != null ? t.getSuggestedAction().getLabel() : null,
                t.getReplyConfidence(),
                t.getResolution(),
                status.name(), status.getLabel(),
                t.getFailedStep(), t.getErrorMessage(),
                t.getCreatedAt(), t.getUpdatedAt(), t.getResolvedAt(),
                eventItems);
    }
}
