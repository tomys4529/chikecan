package com.chikecan.backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** POST /api/account/email-change/request のリクエストボディ。 */
public class ChangeEmailRequest {

  @NotBlank(message = "メールアドレスは必須です")
  @Email(message = "メールアドレスの形式が正しくありません")
  @Size(max = 100, message = "メールアドレスは100文字以内で入力してください")
  private String newEmail;

  @NotBlank(message = "現在のパスワードは必須です")
  private String currentPassword;

  public String getNewEmail() {
    return newEmail;
  }

  public void setNewEmail(String newEmail) {
    this.newEmail = newEmail;
  }

  public String getCurrentPassword() {
    return currentPassword;
  }

  public void setCurrentPassword(String currentPassword) {
    this.currentPassword = currentPassword;
  }
}
