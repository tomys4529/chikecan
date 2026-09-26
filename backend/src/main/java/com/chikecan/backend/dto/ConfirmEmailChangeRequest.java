package com.chikecan.backend.dto;

import jakarta.validation.constraints.NotBlank;

/** POST /api/account/email-change/confirm のリクエストボディ。 */
public class ConfirmEmailChangeRequest {

  @NotBlank(message = "トークンは必須です")
  private String token;

  public String getToken() {
    return token;
  }

  public void setToken(String token) {
    this.token = token;
  }
}
