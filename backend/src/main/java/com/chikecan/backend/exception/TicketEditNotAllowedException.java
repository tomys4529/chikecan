package com.chikecan.backend.exception;

public class TicketEditNotAllowedException extends RuntimeException {

  public TicketEditNotAllowedException(String message) {
    super(message);
  }
}
