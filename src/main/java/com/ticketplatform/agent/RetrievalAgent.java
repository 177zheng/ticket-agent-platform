package com.ticketplatform.agent;

import com.ticketplatform.config.AppProperties;
import com.ticketplatform.domain.Ticket;
import com.ticketplatform.domain.TicketStatus;
import com.ticketplatform.repo.TicketRepository;
import com.ticketplatform.service.KnowledgeBaseService;
import com.ticketplatform.util.Tokenizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * 检索 Agent：以工单标题+描述为查询，检索
 * 1) 运维手册知识库（RAG）；2) 历史已解决工单（相似案例）。
 * 为回复 Agent 准备上下文，检索结果同步落库供前端展示。
 */
@Component
public class RetrievalAgent {

    private static final Logger log = LoggerFactory.getLogger(RetrievalAgent.class);

    private final AppProperties props;
    private final KnowledgeBaseService knowledgeBase;
    private final TicketRepository ticketRepository;

    public RetrievalAgent(AppProperties props, KnowledgeBaseService knowledgeBase, TicketRepository ticketRepository) {
        this.props = props;
        this.knowledgeBase = knowledgeBase;
        this.ticketRepository = ticketRepository;
    }

    public RetrievalResult retrieve(Ticket ticket) {
        String query = ticket.getTitle() + " " + ticket.getDescription();
        int topK = props.getKnowledge().getTopK();

        List<RetrievalResult.ManualHit> manuals = knowledgeBase.search(query, topK);

        List<RetrievalResult.SimilarTicketHit> similar = searchSimilarTickets(ticket, query, topK);

        log.info("[RetrievalAgent] 工单#{} 检索完成：手册命中 {} 条，相似工单 {} 条",
                ticket.getId(), manuals.size(), similar.size());
        return new RetrievalResult(manuals, similar);
    }

    private List<RetrievalResult.SimilarTicketHit> searchSimilarTickets(Ticket current, String query, int topK) {
        Set<String> queryTokens = Tokenizer.tokenize(query);
        if (queryTokens.isEmpty()) {
            return List.of();
        }
        record Scored(double score, Ticket t) {
        }
        List<Scored> scored = new ArrayList<>();
        for (Ticket t : ticketRepository.findByStatusOrderByIdDesc(TicketStatus.RESOLVED)) {
            if (t.getId().equals(current.getId())) continue;
            Set<String> docTokens = Tokenizer.tokenize(t.getTitle() + " " + t.getDescription());
            if (docTokens.isEmpty()) continue;
            long overlap = queryTokens.stream().filter(docTokens::contains).count();
            double score = (double) overlap / queryTokens.size();
            // 类别相同额外加权（规划 Agent 已给出类别时）
            if (current.getCategory() != null && current.getCategory().equals(t.getCategory())) {
                score += 0.15;
            }
            if (score > 0.05) {
                scored.add(new Scored(score, t));
            }
        }
        return scored.stream()
                .sorted(Comparator.comparingDouble(Scored::score).reversed())
                .limit(topK)
                .map(s -> new RetrievalResult.SimilarTicketHit(
                        s.t().getId(), s.t().getTitle(),
                        s.t().getReplyDraft() != null ? firstLine(s.t().getReplyDraft()) : "（历史工单无解决方案记录）"))
                .toList();
    }

    private static String firstLine(String text) {
        String s = text.strip();
        int idx = s.indexOf('\n');
        return idx > 0 ? s.substring(0, idx) : (s.length() > 120 ? s.substring(0, 120) + "…" : s);
    }
}
