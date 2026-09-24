package com.chikecan.backend.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.chikecan.backend.entity.Ticket;

public interface TicketRepository extends JpaRepository<Ticket, Long> {

  List<Ticket> findByRequesterIdOrderByCreatedAtDesc(Long requesterId);

  List<Ticket> findByAssigneeIdOrderByCreatedAtDesc(Long assigneeId);

  List<Ticket> findAllByOrderByCreatedAtDesc();

  Optional<Ticket> findByIdAndRequesterId(Long id, Long requesterId);

  Optional<Ticket> findByIdAndAssigneeId(Long id, Long assigneeId);
}
