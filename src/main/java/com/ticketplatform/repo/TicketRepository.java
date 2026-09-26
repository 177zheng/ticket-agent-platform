package com.ticketplatform.repo;

import com.ticketplatform.domain.Ticket;
import com.ticketplatform.domain.TicketStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TicketRepository extends JpaRepository<Ticket, Long> {

    List<Ticket> findAllByOrderByIdDesc();

    List<Ticket> findByStatusOrderByIdDesc(TicketStatus status);

    long countByStatus(TicketStatus status);
}
