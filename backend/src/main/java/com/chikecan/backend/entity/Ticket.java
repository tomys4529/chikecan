package com.chikecan.backend.entity;

import java.time.Instant;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "tickets")
public class Ticket {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, length = 200)
  private String title;

  @Column(nullable = false, length = 5000)
  private String description;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 30)
  private TicketStatus status;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private TicketPriority priority;

  @Column(name = "requester_id", nullable = false)
  private Long requesterId;

  @Column(name = "assignee_id")
  private Long assigneeId;

  /**
   * 初回RESOLVED時のXP判定が完了したかどうか。実際にXPを付与できたか
   * (担当者が有効なAGENTだったか)ではなく、判定処理自体が一度行われたかを表す。
   * これによりRESOLVED→IN_PROGRESS→RESOLVEDの再判定・二重付与を防ぐ。
   */
  @Column(name = "xp_awarded", nullable = false)
  private boolean xpAwarded;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected Ticket() {
  }

  public Ticket(String title, String description, TicketStatus status, TicketPriority priority,
      Long requesterId, Long assigneeId) {
    this.title = title;
    this.description = description;
    this.status = status;
    this.priority = priority;
    this.requesterId = requesterId;
    this.assigneeId = assigneeId;
  }

  public Long getId() {
    return id;
  }

  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public TicketStatus getStatus() {
    return status;
  }

  public void setStatus(TicketStatus status) {
    this.status = status;
  }

  public TicketPriority getPriority() {
    return priority;
  }

  public void setPriority(TicketPriority priority) {
    this.priority = priority;
  }

  public Long getRequesterId() {
    return requesterId;
  }

  public Long getAssigneeId() {
    return assigneeId;
  }

  public void setAssigneeId(Long assigneeId) {
    this.assigneeId = assigneeId;
  }

  public boolean isXpAwarded() {
    return xpAwarded;
  }

  public void setXpAwarded(boolean xpAwarded) {
    this.xpAwarded = xpAwarded;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
