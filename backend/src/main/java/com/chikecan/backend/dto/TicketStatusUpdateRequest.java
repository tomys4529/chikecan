package com.chikecan.backend.dto;

import com.chikecan.backend.entity.TicketStatus;

import jakarta.validation.constraints.NotNull;

public class TicketStatusUpdateRequest {

  @NotNull(message = "ステータスは必須です")
  private TicketStatus status;

  public TicketStatus getStatus() {
    return status;
  }

  public void setStatus(TicketStatus status) {
    this.status = status;
  }
}
