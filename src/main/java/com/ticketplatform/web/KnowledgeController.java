package com.ticketplatform.web;

import com.ticketplatform.agent.RetrievalResult;
import com.ticketplatform.service.KnowledgeBaseService;
import com.ticketplatform.web.dto.Requests;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 运维手册知识库：导入（自动切块）与检索调试。
 */
@RestController
@RequestMapping("/api/knowledge")
public class KnowledgeController {

    private final KnowledgeBaseService knowledgeBase;

    public KnowledgeController(KnowledgeBaseService knowledgeBase) {
        this.knowledgeBase = knowledgeBase;
    }

    @PostMapping("/manual")
    public Map<String, Object> ingest(@Valid @RequestBody Requests.ManualRequest req) {
        int chunks = knowledgeBase.ingest(req.title(), req.content());
        return Map.of("title", req.title(), "chunks", chunks);
    }

    @GetMapping("/manuals")
    public List<Map<String, Object>> manuals() {
        return knowledgeBase.listManuals();
    }

    @GetMapping("/search")
    public List<RetrievalResult.ManualHit> search(@RequestParam String q,
                                                  @RequestParam(defaultValue = "3") int k) {
        return knowledgeBase.search(q, Math.min(Math.max(k, 1), 10));
    }
}
