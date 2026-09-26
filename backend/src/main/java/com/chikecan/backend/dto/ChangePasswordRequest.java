package com.chikecan.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** POST /api/account/password のリクエストボディ。 */
public class ChangePasswordRequest {

  @NotBlank(message = "現在のパスワードは必須です")
  private String currentPassword;

  @NotBlank(message = "新しいパスワードは必須です")
  @Pattern(regexp = PasswordPolicy.PATTERN, message = PasswordPolicy.MESSAGE)
  private String newPassword;

  public String getCurrentPassword() {
    return currentPassword;
  }

  public void setCurrentPassword(String currentPassword) {
    this.currentPassword = currentPassword;
  }

  public String getNewPassword() {
    return newPassword;
  }

  public void setNewPassword(String newPassword) {
    this.newPassword = newPassword;
  }
}
