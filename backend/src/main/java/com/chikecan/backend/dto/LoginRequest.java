package com.chikecan.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class LoginRequest {

  @NotBlank(message = "メールアドレスは必須です")
  @Size(max = 255, message = "メールアドレスは255文字以内で入力してください")
  private String email;

  @NotBlank(message = "パスワードは必須です")
  @Size(max = 72, message = "パスワードは72文字以内で入力してください")
  private String password;

  public String getEmail() {
    return email;
  }

  public void setEmail(String email) {
    this.email = email;
  }

  public String getPassword() {
    return password;
  }

  public void setPassword(String password) {
    this.password = password;
  }
}
