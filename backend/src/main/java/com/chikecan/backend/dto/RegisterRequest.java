package com.chikecan.backend.dto;

import com.chikecan.backend.entity.NameFormat;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class RegisterRequest {

  @NotNull(message = "氏名の入力形式を選択してください")
  private NameFormat nameFormat;

  @Size(max = 30, message = "姓は30文字以内で入力してください")
  private String familyName;

  @Size(max = 30, message = "名は30文字以内で入力してください")
  private String givenName;

  @Size(max = 30, message = "ミドルネームは30文字以内で入力してください")
  private String middleName;

  @NotBlank(message = "メールアドレスは必須です")
  @Email(message = "メールアドレスの形式が正しくありません")
  @Size(max = 100, message = "メールアドレスは100文字以内で入力してください")
  private String email;

  @NotBlank(message = "パスワードは必須です")
  @Pattern(regexp = PasswordPolicy.PATTERN, message = PasswordPolicy.MESSAGE)
  private String password;

  /**
   * 新規登録画面ではLEGACYを選択できない(この機能導入前の既存データ専用のマーカーのため)。
   * nameFormatがnullの場合は@NotNullが別途報告するので、ここでは素通りさせる。
   * Bean Validationのプロパティアクセスに認識されるよう、getter規約に沿ったpublicメソッドにする。
   */
  @AssertTrue(message = "氏名の入力形式が不正です")
  public boolean isNameFormatSelectable() {
    return nameFormat == null || nameFormat != NameFormat.LEGACY;
  }

  @AssertTrue(message = "姓は必須です")
  public boolean isFamilyNamePresent() {
    return nameFormat == null || nameFormat == NameFormat.LEGACY || isNotBlank(familyName);
  }

  @AssertTrue(message = "名は必須です")
  public boolean isGivenNamePresent() {
    return nameFormat == null || nameFormat == NameFormat.LEGACY || isNotBlank(givenName);
  }

  private static boolean isNotBlank(String value) {
    return value != null && !value.isBlank();
  }

  public NameFormat getNameFormat() {
    return nameFormat;
  }

  public void setNameFormat(NameFormat nameFormat) {
    this.nameFormat = nameFormat;
  }

  public String getFamilyName() {
    return familyName;
  }

  public void setFamilyName(String familyName) {
    this.familyName = familyName;
  }

  public String getGivenName() {
    return givenName;
  }

  public void setGivenName(String givenName) {
    this.givenName = givenName;
  }

  public String getMiddleName() {
    return middleName;
  }

  public void setMiddleName(String middleName) {
    this.middleName = middleName;
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
