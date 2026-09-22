package com.chikecan.backend.exception;

import java.time.Instant;

public class ErrorResponse {

  private final int status;
  private final String error;
  private final String message;
  private final String path;
  private final Instant timestamp;

  public ErrorResponse(int status, String error, String message, String path, Instant timestamp) {
    this.status = status;
    this.error = error;
    this.message = message;
    this.path = path;
    this.timestamp = timestamp;
  }

  public int getStatus() {
    return status;
  }

  public String getError() {
    return error;
  }

  public String getMessage() {
    return message;
  }

  public String getPath() {
    return path;
  }

  public Instant getTimestamp() {
    return timestamp;
  }
}
