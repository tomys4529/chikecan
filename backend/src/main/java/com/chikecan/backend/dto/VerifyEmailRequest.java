package com.chikecan.backend.dto;

import jakarta.validation.constraints.NotBlank;

public class VerifyEmailRequest {

  @NotBlank(message = "トークンは必須です")
  private String token;

  public String getToken() {
    return token;
  }

  public void setToken(String token) {
    this.token = token;
  }
}
