package com.ticketplatform.domain;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * 工单流转事件（审计流水线）：每一次状态变化都记录谁（SYSTEM/各Agent/HUMAN）在何时因为什么改了状态。
 */
@Entity
@Table(name = "ticket_events",
        indexes = @Index(name = "idx_ticket_events_ticket", columnList = "ticketId"))
public class TicketEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long ticketId;

    @Column(nullable = false, length = 30)
    private String fromStatus;

    @Column(nullable = false, length = 30)
    private String toStatus;

    /** SYSTEM / TRIAGE_AGENT / RETRIEVAL_AGENT / REPLY_AGENT / HUMAN */
    @Column(nullable = false, length = 30)
    private String actor;

    @Column(length = 1000)
    private String note;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public TicketEvent() {
    }

    public TicketEvent(Long ticketId, String fromStatus, String toStatus, String actor, String note) {
        this.ticketId = ticketId;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.actor = actor;
        this.note = note;
    }

    public Long getId() { return id; }
    public Long getTicketId() { return ticketId; }
    public String getFromStatus() { return fromStatus; }
    public String getToStatus() { return toStatus; }
    public String getActor() { return actor; }
    public String getNote() { return note; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
