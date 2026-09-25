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
  private final String requesterName;
  private final Long assigneeId;
  private final String assigneeName;
  private final Instant createdAt;
  private final Instant updatedAt;

  /**
   * requesterName・assigneeNameは呼び出し側(Service)で解決済みの値を渡す。
   * Entity側にUserへの関連は持たせず、IDのみの設計を維持したまま表示名だけを付加する。
   */
  public TicketResponse(Ticket ticket, String requesterName, String assigneeName) {
    this.id = ticket.getId();
    this.title = ticket.getTitle();
    this.description = ticket.getDescription();
    this.status = ticket.getStatus();
    this.priority = ticket.getPriority();
    this.requesterId = ticket.getRequesterId();
    this.requesterName = requesterName;
    this.assigneeId = ticket.getAssigneeId();
    this.assigneeName = assigneeName;
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

  public String getRequesterName() {
    return requesterName;
  }

  public Long getAssigneeId() {
    return assigneeId;
  }

  public String getAssigneeName() {
    return assigneeName;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
