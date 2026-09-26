package com.chikecan.backend.exception;

/**
 * メールアドレス変更tokenが不正・期限切れ・使用済みの場合に投げる。
 * メール認証用のInvalidVerificationTokenException、パスワードリセット用の
 * InvalidPasswordResetTokenExceptionとは用途を分け、例外の型からも
 * どの機能で発生したエラーかを区別できるようにする。
 */
public class InvalidEmailChangeTokenException extends RuntimeException {

  public InvalidEmailChangeTokenException(String message) {
    super(message);
  }
}
