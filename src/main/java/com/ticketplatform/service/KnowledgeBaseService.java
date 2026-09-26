package com.ticketplatform.service;

import com.ticketplatform.agent.RetrievalResult;
import com.ticketplatform.config.AppProperties;
import com.ticketplatform.domain.KnowledgeChunk;
import com.ticketplatform.repo.KnowledgeChunkRepository;
import com.ticketplatform.util.Tokenizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * 运维手册知识库：导入时按 chunk-size/overlap 字符切块；检索时按字符 bigram 重叠度打分。
 * MVP 不引向量库依赖；升级路径：把 search() 换成 pgvector/Elasticsearch，接口不变。
 */
@Service
public class KnowledgeBaseService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeBaseService.class);

    private final AppProperties props;
    private final KnowledgeChunkRepository chunkRepository;

    public KnowledgeBaseService(AppProperties props, KnowledgeChunkRepository chunkRepository) {
        this.props = props;
        this.chunkRepository = chunkRepository;
    }

    @Transactional
    public int ingest(String sourceTitle, String content) {
        int size = props.getKnowledge().getChunkSize();
        int overlap = Math.min(props.getKnowledge().getChunkOverlap(), size / 2);
        String normalized = content.strip().replaceAll("\\r\\n", "\n");
        int idx = 0;
        int total = 0;
        while (idx < normalized.length()) {
            int end = Math.min(idx + size, normalized.length());
            String piece = normalized.substring(idx, end).strip();
            if (!piece.isEmpty()) {
                chunkRepository.save(new KnowledgeChunk(sourceTitle, total, piece));
                total++;
            }
            if (end >= normalized.length()) break;
            idx = end - overlap;
        }
        log.info("知识库导入《{}》：切成 {} 块", sourceTitle, total);
        return total;
    }

    @Transactional(readOnly = true)
    public List<RetrievalResult.ManualHit> search(String query, int topK) {
        Set<String> queryTokens = Tokenizer.tokenize(query);
        if (queryTokens.isEmpty()) {
            return List.of();
        }
        record Scored(double score, KnowledgeChunk c) {
        }
        List<Scored> scored = new ArrayList<>();
        for (KnowledgeChunk chunk : chunkRepository.findAll()) {
            Set<String> chunkTokens = Tokenizer.tokenize(chunk.getContent() + " " + chunk.getSourceTitle());
            if (chunkTokens.isEmpty()) continue;
            long overlap = queryTokens.stream().filter(chunkTokens::contains).count();
            double score = (double) overlap / queryTokens.size();
            if (score > 0.08) {
                scored.add(new Scored(score, chunk));
            }
        }
        return scored.stream()
                .sorted(Comparator.comparingDouble(Scored::score).reversed())
                .limit(topK)
                .map(s -> new RetrievalResult.ManualHit(s.c().getSourceTitle(), snippet(s.c().getContent())))
                .toList();
    }

    private static String snippet(String content) {
        String s = content.replaceAll("\\s+", " ").strip();
        return s.length() > 160 ? s.substring(0, 160) + "…" : s;
    }
}
