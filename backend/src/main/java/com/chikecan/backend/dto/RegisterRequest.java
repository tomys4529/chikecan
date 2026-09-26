package com.chikecan.backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class RegisterRequest {

  @NotBlank(message = "氏名は必須です")
  @Size(max = 30, message = "氏名は30文字以内で入力してください")
  private String name;

  @NotBlank(message = "メールアドレスは必須です")
  @Email(message = "メールアドレスの形式が正しくありません")
  @Size(max = 100, message = "メールアドレスは100文字以内で入力してください")
  private String email;

  @NotBlank(message = "パスワードは必須です")
  @Pattern(
      regexp = "^(?=.*[A-Z])(?=.*[a-z])(?=.*\\d)(?=.*[^A-Za-z0-9])[\\x21-\\x7E]{8,72}$",
      message = "パスワードは8〜72文字で、大文字・小文字・数字・記号をそれぞれ1文字以上含めてください")
  private String password;

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

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
