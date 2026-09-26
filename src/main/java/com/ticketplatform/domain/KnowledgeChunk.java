package com.ticketplatform.domain;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * 运维手册知识块：手册导入时按固定长度+重叠切分后落库，供检索 Agent 做 RAG 检索。
 * MVP 用字符 bigram 重叠度打分；升级路径：换 pgvector/向量库，仅改 KnowledgeBaseService。
 */
@Entity
@Table(name = "knowledge_chunks",
        indexes = @Index(name = "idx_chunks_source", columnList = "sourceTitle"))
public class KnowledgeChunk {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String sourceTitle;

    @Column(nullable = false)
    private int chunkIndex;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public KnowledgeChunk() {
    }

    public KnowledgeChunk(String sourceTitle, int chunkIndex, String content) {
        this.sourceTitle = sourceTitle;
        this.chunkIndex = chunkIndex;
        this.content = content;
    }

    public Long getId() { return id; }
    public String getSourceTitle() { return sourceTitle; }
    public int getChunkIndex() { return chunkIndex; }
    public String getContent() { return content; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
