package com.ticketplatform.domain;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * 工单实体。Agent 各阶段的产出（分类结果、检索上下文、回复草稿）
 * 都落在本表，一方面作为前端展示数据源，另一方面支撑 FAILED 后断点续跑。
 */
@Entity
@Table(name = "tickets")
public class Ticket {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false, length = 100)
    private String requesterName;

    @Column(nullable = false, length = 200)
    private String requesterEmail;

    // ---- 规划 Agent 产出 ----
    private String category;
    @Enumerated(EnumType.STRING)
    private Priority priority;
    private String assignedTeam;
    /** JSON 数组：["步骤1","步骤2"] */
    @Column(columnDefinition = "TEXT")
    private String subtasksJson;
    @Column(length = 500)
    private String triageSummary;
    private Double triageConfidence;

    // ---- 检索 Agent 产出 ----
    /** JSON：{"manuals":[...],"similarTickets":[...]} */
    @Column(columnDefinition = "TEXT")
    private String retrievalContextJson;

    // ---- 回复 Agent 产出 ----
    @Column(columnDefinition = "TEXT")
    private String replyDraft;
    @Enumerated(EnumType.STRING)
    private SuggestedAction suggestedAction;
    private Double replyConfidence;

    // ---- 状态与失败信息 ----
    @Enumerated(EnumType.STRING)
    private TicketStatus status = TicketStatus.NEW;
    /** 失败时所处阶段：TRIAGE / RETRIEVAL / REPLY */
    @Column(length = 20)
    private String failedStep;
    @Column(length = 1000)
    private String errorMessage;

    @Column(nullable = false)
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime resolvedAt;

    @PrePersist
    void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getRequesterName() { return requesterName; }
    public void setRequesterName(String requesterName) { this.requesterName = requesterName; }

    public String getRequesterEmail() { return requesterEmail; }
    public void setRequesterEmail(String requesterEmail) { this.requesterEmail = requesterEmail; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public Priority getPriority() { return priority; }
    public void setPriority(Priority priority) { this.priority = priority; }

    public String getAssignedTeam() { return assignedTeam; }
    public void setAssignedTeam(String assignedTeam) { this.assignedTeam = assignedTeam; }

    public String getSubtasksJson() { return subtasksJson; }
    public void setSubtasksJson(String subtasksJson) { this.subtasksJson = subtasksJson; }

    public String getTriageSummary() { return triageSummary; }
    public void setTriageSummary(String triageSummary) { this.triageSummary = triageSummary; }

    public Double getTriageConfidence() { return triageConfidence; }
    public void setTriageConfidence(Double triageConfidence) { this.triageConfidence = triageConfidence; }

    public String getRetrievalContextJson() { return retrievalContextJson; }
    public void setRetrievalContextJson(String retrievalContextJson) { this.retrievalContextJson = retrievalContextJson; }

    public String getReplyDraft() { return replyDraft; }
    public void setReplyDraft(String replyDraft) { this.replyDraft = replyDraft; }

    public SuggestedAction getSuggestedAction() { return suggestedAction; }
    public void setSuggestedAction(SuggestedAction suggestedAction) { this.suggestedAction = suggestedAction; }

    public Double getReplyConfidence() { return replyConfidence; }
    public void setReplyConfidence(Double replyConfidence) { this.replyConfidence = replyConfidence; }

    public TicketStatus getStatus() { return status; }
    public void setStatus(TicketStatus status) { this.status = status; }

    public String getFailedStep() { return failedStep; }
    public void setFailedStep(String failedStep) { this.failedStep = failedStep; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public LocalDateTime getResolvedAt() { return resolvedAt; }
    public void setResolvedAt(LocalDateTime resolvedAt) { this.resolvedAt = resolvedAt; }
}
