package com.ticketplatform.repo;

import com.ticketplatform.domain.KnowledgeChunk;
import org.springframework.data.jpa.repository.JpaRepository;

public interface KnowledgeChunkRepository extends JpaRepository<KnowledgeChunk, Long> {
}
