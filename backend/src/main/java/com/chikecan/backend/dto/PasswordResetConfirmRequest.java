package com.chikecan.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** POST /api/auth/password-reset/confirm のリクエストボディ。 */
public class PasswordResetConfirmRequest {

  @NotBlank(message = "トークンは必須です")
  private String token;

  @NotBlank(message = "パスワードは必須です")
  @Pattern(regexp = PasswordPolicy.PATTERN, message = PasswordPolicy.MESSAGE)
  private String password;

  public String getToken() {
    return token;
  }

  public void setToken(String token) {
    this.token = token;
  }

  public String getPassword() {
    return password;
  }

  public void setPassword(String password) {
    this.password = password;
  }
}
