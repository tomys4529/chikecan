package com.chikecan.backend.exception;

/**
 * パスワードリセットtokenが不正・期限切れ・使用済みの場合に投げる。
 * メール認証用のInvalidVerificationTokenExceptionとは用途を分け、
 * 例外の型からもどちらの機能で発生したエラーかを区別できるようにする。
 */
public class InvalidPasswordResetTokenException extends RuntimeException {

  public InvalidPasswordResetTokenException(String message) {
    super(message);
  }
}
