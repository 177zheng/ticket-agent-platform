package com.ticketplatform.service;

import com.ticketplatform.domain.Ticket;
import com.ticketplatform.domain.TicketEvent;
import com.ticketplatform.domain.TicketStatus;
import com.ticketplatform.repo.TicketEventRepository;
import com.ticketplatform.repo.TicketRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 状态流转统一入口：校验状态机合法性 + 落审计事件，流水线与 REST 接口都必须经过这里改状态。
 */
@Service
public class TicketTransitionService {

    private final TicketRepository ticketRepository;
    private final TicketEventRepository eventRepository;

    public TicketTransitionService(TicketRepository ticketRepository, TicketEventRepository eventRepository) {
        this.ticketRepository = ticketRepository;
        this.eventRepository = eventRepository;
    }

    /**
     * REQUIRES_NEW：即使外层事务因 Agent 异常回滚，状态流转与事件审计也已独立提交，
     * 保证 FAILED 状态和失败原因一定可见。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Ticket transition(Ticket ticket, TicketStatus to, String actor, String note) {
        TicketStatus from = ticket.getStatus();
        if (!from.canTransitionTo(to)) {
            throw new IllegalStateException("非法状态流转: " + from + " → " + to + "（工单#" + ticket.getId() + "）");
        }
        ticket.setStatus(to);
        ticket = ticketRepository.saveAndFlush(ticket);
        eventRepository.save(new TicketEvent(ticket.getId(), from.name(), to.name(), actor, note));
        return ticket;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordNote(Ticket ticket, String actor, String note) {
        eventRepository.save(new TicketEvent(ticket.getId(), ticket.getStatus().name(),
                ticket.getStatus().name(), actor, note));
    }
}
