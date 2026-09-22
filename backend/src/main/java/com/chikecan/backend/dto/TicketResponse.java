package com.chikecan.backend.dto;

import java.time.Instant;

import com.chikecan.backend.entity.Ticket;
import com.chikecan.backend.entity.TicketPriority;
import com.chikecan.backend.entity.TicketStatus;

public class TicketResponse {

  private final Long id;
  private final String title;
  private final String description;
  private final TicketStatus status;
  private final TicketPriority priority;
  private final Long requesterId;
  private final Long assigneeId;
  private final Instant createdAt;
  private final Instant updatedAt;

  public TicketResponse(Ticket ticket) {
    this.id = ticket.getId();
    this.title = ticket.getTitle();
    this.description = ticket.getDescription();
    this.status = ticket.getStatus();
    this.priority = ticket.getPriority();
    this.requesterId = ticket.getRequesterId();
    this.assigneeId = ticket.getAssigneeId();
    this.createdAt = ticket.getCreatedAt();
    this.updatedAt = ticket.getUpdatedAt();
  }

  public Long getId() {
    return id;
  }

  public String getTitle() {
    return title;
  }

  public String getDescription() {
    return description;
  }

  public TicketStatus getStatus() {
    return status;
  }

  public TicketPriority getPriority() {
    return priority;
  }

  public Long getRequesterId() {
    return requesterId;
  }

  public Long getAssigneeId() {
    return assigneeId;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
