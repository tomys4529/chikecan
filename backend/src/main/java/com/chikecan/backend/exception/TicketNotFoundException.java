package com.chikecan.backend.exception;

public class TicketNotFoundException extends RuntimeException {

  public TicketNotFoundException(String message) {
    super(message);
  }
}
