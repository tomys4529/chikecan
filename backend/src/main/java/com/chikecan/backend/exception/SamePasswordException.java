package com.chikecan.backend.exception;

/** 新しいパスワードが現在のパスワードと同じ場合に投げる。 */
public class SamePasswordException extends RuntimeException {

  public SamePasswordException(String message) {
    super(message);
  }
}
