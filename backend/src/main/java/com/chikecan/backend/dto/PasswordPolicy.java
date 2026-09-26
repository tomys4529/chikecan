package com.chikecan.backend.dto;

/**
 * ユーザー登録・パスワードリセット双方で共通のパスワード強度ルール。
 * 条件を変更する場合はここを直せば両方のDTOへ反映される。
 * フロントエンド(frontend/src/utils/passwordValidation.ts)の条件とも必ず一致させること。
 */
public final class PasswordPolicy {

  public static final String PATTERN =
      "^(?=.*[A-Z])(?=.*[a-z])(?=.*\\d)(?=.*[^A-Za-z0-9])[\\x21-\\x7E]{8,72}$";

  public static final String MESSAGE =
      "パスワードは8〜72文字で、大文字・小文字・数字・記号をそれぞれ1文字以上含めてください";

  private PasswordPolicy() {
  }
}
