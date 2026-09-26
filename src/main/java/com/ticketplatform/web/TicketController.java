package com.ticketplatform.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketplatform.domain.Ticket;
import com.ticketplatform.domain.TicketEvent;
import com.ticketplatform.domain.TicketStatus;
import com.ticketplatform.pipeline.TicketPipeline;
import com.ticketplatform.repo.TicketEventRepository;
import com.ticketplatform.repo.TicketRepository;
import com.ticketplatform.service.MailSender;
import com.ticketplatform.service.TicketTransitionService;
import com.ticketplatform.web.dto.Requests;
import com.ticketplatform.web.dto.TicketView;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/tickets")
public class TicketController {

    private final TicketRepository ticketRepository;
    private final TicketEventRepository eventRepository;
    private final TicketPipeline pipeline;
    private final TicketTransitionService transitions;
    private final MailSender mailSender;
    private final ObjectMapper objectMapper;

    public TicketController(TicketRepository ticketRepository,
                            TicketEventRepository eventRepository,
                            TicketPipeline pipeline,
                            TicketTransitionService transitions,
                            MailSender mailSender,
                            ObjectMapper objectMapper) {
        this.ticketRepository = ticketRepository;
        this.eventRepository = eventRepository;
        this.pipeline = pipeline;
        this.transitions = transitions;
        this.mailSender = mailSender;
        this.objectMapper = objectMapper;
    }

    /** 创建工单并异步触发智能体流水线 */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TicketView create(@Valid @RequestBody Requests.CreateTicketRequest req) {
        Ticket ticket = new Ticket();
        ticket.setTitle(req.title());
        ticket.setDescription(req.description());
        ticket.setRequesterName(req.requesterName());
        ticket.setRequesterEmail(req.requesterEmail());
        ticket = ticketRepository.save(ticket);
        eventRepository.save(new TicketEvent(ticket.getId(), "NONE", "NEW", "HUMAN",
                "工单创建：" + req.title()));
        pipeline.submitAsync(ticket.getId());
        return view(ticket);
    }

    @GetMapping
    public List<TicketView> list() {
        return ticketRepository.findAllByOrderByIdDesc().stream()
                .map(t -> view(t))
                .toList();
    }

    @GetMapping("/{id}")
    public TicketView detail(@PathVariable Long id) {
        Ticket ticket = load(id);
        return view(ticket);
    }

    /** 失败重试：从失败阶段断点续跑（之前阶段的结果已持久化，不会重算） */
    @PostMapping("/{id}/retry")
    public TicketView retry(@PathVariable Long id) {
        Ticket ticket = load(id);
        if (ticket.getStatus() != TicketStatus.FAILED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "只有 FAILED 状态的工单才能重试，当前：" + ticket.getStatus());
        }
        transitions.recordNote(ticket, "HUMAN", "人工触发重试（上次失败阶段：" + ticket.getFailedStep() + "）");
        pipeline.submitAsync(ticket.getId());
        return view(ticket);
    }

    /** 人工审核：通过 → 发送回复并关单；驳回 → 转人工处理组 */
    @PostMapping("/{id}/review")
    public TicketView review(@PathVariable Long id, @Valid @RequestBody Requests.ReviewRequest req) {
        Ticket ticket = load(id);
        if (ticket.getStatus() != TicketStatus.HUMAN_REVIEW) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "只有 HUMAN_REVIEW 状态的工单才能审核，当前：" + ticket.getStatus());
        }
        String comment = req.comment() == null ? "" : "；审核意见：" + req.comment();
        if (Boolean.TRUE.equals(req.approve())) {
            mailSender.send(ticket.getRequesterEmail(),
                    "[已解决] 工单#" + ticket.getId() + "：" + ticket.getTitle(),
                    ticket.getReplyDraft());
            ticket.setResolvedAt(LocalDateTime.now());
            ticket = transitions.transition(ticket, TicketStatus.RESOLVED, "HUMAN",
                    "人工审核通过，回复已发送" + comment);
        } else {
            ticket = transitions.transition(ticket, TicketStatus.ESCALATED, "HUMAN",
                    "人工驳回回复草稿，转人工处理" + comment);
        }
        return view(ticket);
    }

    /** 转人工工单处理完毕后关闭 */
    @PostMapping("/{id}/close")
    public TicketView close(@PathVariable Long id, @Valid @RequestBody Requests.CloseRequest req) {
        Ticket ticket = load(id);
        if (ticket.getStatus() != TicketStatus.ESCALATED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "只有 ESCALATED 状态的工单才能关闭，当前：" + ticket.getStatus());
        }
        ticket.setResolvedAt(LocalDateTime.now());
        String comment = (req.comment() == null || req.comment().isBlank()) ? "" : "：" + req.comment();
        ticket = transitions.transition(ticket, TicketStatus.RESOLVED, "HUMAN",
                "人工处理完毕，关闭工单" + comment);
        return view(ticket);
    }

    @org.springframework.web.bind.annotation.GetMapping("/stats")
    public Map<String, Object> stats() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", ticketRepository.count());
        Map<String, Long> byStatus = new LinkedHashMap<>();
        for (TicketStatus s : TicketStatus.values()) {
            byStatus.put(s.name(), ticketRepository.countByStatus(s));
        }
        result.put("byStatus", byStatus);
        // Agent 分诊的类别分布（仪表盘图表用）
        Map<String, Long> byCategory = new LinkedHashMap<>();
        ticketRepository.findAll().forEach(t -> {
            if (t.getCategory() != null) byCategory.merge(t.getCategory(), 1L, Long::sum);
        });
        result.put("byCategory", byCategory);
        return result;
    }

    private Ticket load(Long id) {
        return ticketRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "工单不存在: " + id));
    }

    private TicketView view(Ticket t) {
        List<TicketEvent> events = eventRepository.findByTicketIdOrderByCreatedAtAscIdAsc(t.getId());
        return TicketView.of(t, events, objectMapper);
    }
}
