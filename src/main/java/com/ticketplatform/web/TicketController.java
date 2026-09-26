package com.ticketplatform.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketplatform.domain.Ticket;
import com.ticketplatform.domain.TicketEvent;
import com.ticketplatform.domain.TicketStatus;
import com.ticketplatform.domain.User;
import com.ticketplatform.pipeline.TicketPipeline;
import com.ticketplatform.repo.TicketEventRepository;
import com.ticketplatform.repo.TicketRepository;
import com.ticketplatform.repo.UserRepository;
import com.ticketplatform.security.CurrentUser;
import com.ticketplatform.service.MailSender;
import com.ticketplatform.service.TicketTransitionService;
import com.ticketplatform.web.dto.Requests;
import com.ticketplatform.web.dto.TicketView;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 工单接口 + 权限矩阵：
 *   员工   —— 创建工单、查看自己的工单、仪表盘统计（仅自己数据）
 *   管理员 —— 全量工单、详情、人工审核/驳回、失败重试、关闭工单、统计（全局）
 */
@RestController
@RequestMapping("/api/tickets")
public class TicketController {

    private final TicketRepository ticketRepository;
    private final TicketEventRepository eventRepository;
    private final TicketPipeline pipeline;
    private final TicketTransitionService transitions;
    private final MailSender mailSender;
    private final ObjectMapper objectMapper;
    private final UserRepository userRepository;

    public TicketController(TicketRepository ticketRepository,
                            TicketEventRepository eventRepository,
                            TicketPipeline pipeline,
                            TicketTransitionService transitions,
                            MailSender mailSender,
                            ObjectMapper objectMapper,
                            UserRepository userRepository) {
        this.ticketRepository = ticketRepository;
        this.eventRepository = eventRepository;
        this.pipeline = pipeline;
        this.transitions = transitions;
        this.mailSender = mailSender;
        this.objectMapper = objectMapper;
        this.userRepository = userRepository;
    }

    /** 创建工单并异步触发智能体流水线。提单人信息取自登录态，不接受前端伪造。 */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TicketView create(@Valid @RequestBody Requests.CreateTicketRequest req) {
        User user = userRepository.findByUsername(CurrentUser.username())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "登录态无效"));

        Ticket ticket = new Ticket();
        ticket.setTitle(req.title());
        ticket.setDescription(req.description());
        ticket.setRequesterName(user.getName());
        ticket.setRequesterEmail(user.getEmail());
        ticket.setCreatedBy(user.getUsername());
        ticket = ticketRepository.save(ticket);
        eventRepository.save(new TicketEvent(ticket.getId(), "NONE", "NEW", "HUMAN",
                "工单创建（创建人：" + user.getUsername() + "）：" + req.title()));
        pipeline.submitAsync(ticket.getId());
        return view(ticket);
    }

    /** 员工只看自己提的工单；管理员看全量 */
    @GetMapping
    public List<TicketView> list() {
        List<Ticket> scope = CurrentUser.isAdmin()
                ? ticketRepository.findAllByOrderByIdDesc()
                : ticketRepository.findByCreatedByOrderByIdDesc(CurrentUser.username());
        return scope.stream().map(this::view).toList();
    }

    @GetMapping("/{id}")
    public TicketView detail(@PathVariable Long id) {
        Ticket ticket = loadAndCheckPermission(id);
        return view(ticket);
    }

    /** 失败重试：从失败阶段断点续跑 —— 仅管理员 */
    @PostMapping("/{id}/retry")
    @PreAuthorize("hasRole('ADMIN')")
    public TicketView retry(@PathVariable Long id) {
        Ticket ticket = loadAndCheckPermission(id);
        if (ticket.getStatus() != TicketStatus.FAILED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "只有 FAILED 状态的工单才能重试，当前：" + ticket.getStatus());
        }
        transitions.recordNote(ticket, "HUMAN", "管理员触发重试（上次失败阶段：" + ticket.getFailedStep() + "）");
        pipeline.submitAsync(ticket.getId());
        return view(ticket);
    }

    /** 人工审核：通过 → 发送回复并关单；驳回 → 转人工处理组 —— 仅管理员 */
    @PostMapping("/{id}/review")
    @PreAuthorize("hasRole('ADMIN')")
    public TicketView review(@PathVariable Long id, @Valid @RequestBody Requests.ReviewRequest req) {
        Ticket ticket = loadAndCheckPermission(id);
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
                    "管理员审核通过，回复已发送" + comment);
        } else {
            ticket = transitions.transition(ticket, TicketStatus.ESCALATED, "HUMAN",
                    "管理员驳回回复草稿，转人工处理" + comment);
        }
        return view(ticket);
    }

    /** 转人工工单处理完毕后关闭 —— 仅管理员 */
    @PostMapping("/{id}/close")
    @PreAuthorize("hasRole('ADMIN')")
    public TicketView close(@PathVariable Long id, @Valid @RequestBody Requests.CloseRequest req) {
        Ticket ticket = loadAndCheckPermission(id);
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

    /** 统计：员工看到自己的数据，管理员看到全局 */
    @org.springframework.web.bind.annotation.GetMapping("/stats")
    public Map<String, Object> stats() {
        List<Ticket> scope = CurrentUser.isAdmin()
                ? ticketRepository.findAll()
                : ticketRepository.findByCreatedByOrderByIdDesc(CurrentUser.username());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", scope.size());
        result.put("scope", CurrentUser.isAdmin() ? "ALL" : "MINE");

        Map<String, Long> byStatus = new LinkedHashMap<>();
        for (TicketStatus s : TicketStatus.values()) {
            byStatus.put(s.name(), scope.stream().filter(t -> t.getStatus() == s).count());
        }
        result.put("byStatus", byStatus);

        Map<String, Long> byCategory = new LinkedHashMap<>();
        scope.forEach(t -> {
            if (t.getCategory() != null) byCategory.merge(t.getCategory(), 1L, Long::sum);
        });
        result.put("byCategory", byCategory);
        return result;
    }

    /** 员工只能访问自己创建的工单；种子历史工单（createdBy=null）仅管理员可见 */
    private Ticket loadAndCheckPermission(Long id) {
        Ticket ticket = ticketRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "工单不存在: " + id));
        String username = CurrentUser.username();
        boolean mine = username != null && username.equals(ticket.getCreatedBy());
        if (!CurrentUser.isAdmin() && !mine) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无权查看他人工单");
        }
        return ticket;
    }

    private TicketView view(Ticket t) {
        List<TicketEvent> events = eventRepository.findByTicketIdOrderByCreatedAtAscIdAsc(t.getId());
        return TicketView.of(t, events, objectMapper);
    }
}
