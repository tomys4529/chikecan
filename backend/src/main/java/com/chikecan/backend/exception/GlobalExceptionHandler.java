package com.chikecan.backend.exception;

import java.time.Instant;
import java.util.Locale;
import java.util.stream.Collectors;

import org.hibernate.exception.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import jakarta.servlet.http.HttpServletRequest;

/**
 * APIのエラーレスポンス形式を統一する。
 * クライアントへスタックトレースや内部例外の詳細を返さない。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
    String message = ex.getBindingResult().getFieldErrors().stream()
        .map(FieldError::getDefaultMessage)
        .collect(Collectors.joining("、"));
    return build(HttpStatus.BAD_REQUEST, message, request);
  }

  @ExceptionHandler(DuplicateEmailException.class)
  public ResponseEntity<ErrorResponse> handleDuplicateEmail(DuplicateEmailException ex, HttpServletRequest request) {
    return build(HttpStatus.CONFLICT, ex.getMessage(), request);
  }

  @ExceptionHandler(InvalidCredentialsException.class)
  public ResponseEntity<ErrorResponse> handleInvalidCredentials(InvalidCredentialsException ex, HttpServletRequest request) {
    return build(HttpStatus.UNAUTHORIZED, ex.getMessage(), request);
  }

  @ExceptionHandler(NoResourceFoundException.class)
  public ResponseEntity<ErrorResponse> handleNotFound(NoResourceFoundException ex, HttpServletRequest request) {
    return build(HttpStatus.NOT_FOUND, "指定されたリソースが見つかりません", request);
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<ErrorResponse> handleMessageNotReadable(HttpMessageNotReadableException ex,
      HttpServletRequest request) {
    return build(HttpStatus.BAD_REQUEST, "リクエストの形式が正しくありません", request);
  }

  @ExceptionHandler(AccessDeniedException.class)
  public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
    return build(HttpStatus.FORBIDDEN, "権限がありません", request);
  }

  @ExceptionHandler(TicketNotFoundException.class)
  public ResponseEntity<ErrorResponse> handleTicketNotFound(TicketNotFoundException ex, HttpServletRequest request) {
    return build(HttpStatus.NOT_FOUND, ex.getMessage(), request);
  }

  @ExceptionHandler(InvalidAssigneeException.class)
  public ResponseEntity<ErrorResponse> handleInvalidAssignee(InvalidAssigneeException ex, HttpServletRequest request) {
    return build(HttpStatus.BAD_REQUEST, ex.getMessage(), request);
  }

  @ExceptionHandler(InvalidStatusTransitionException.class)
  public ResponseEntity<ErrorResponse> handleInvalidStatusTransition(InvalidStatusTransitionException ex,
      HttpServletRequest request) {
    return build(HttpStatus.CONFLICT, ex.getMessage(), request);
  }

  @ExceptionHandler(TicketEditNotAllowedException.class)
  public ResponseEntity<ErrorResponse> handleTicketEditNotAllowed(TicketEditNotAllowedException ex,
      HttpServletRequest request) {
    return build(HttpStatus.CONFLICT, ex.getMessage(), request);
  }

  @ExceptionHandler(DataIntegrityViolationException.class)
  public ResponseEntity<ErrorResponse> handleDataIntegrityViolation(DataIntegrityViolationException ex,
      HttpServletRequest request) {
    if (isEmailUniqueConstraintViolation(ex)) {
      return build(HttpStatus.CONFLICT, "このメールアドレスは既に登録されています", request);
    }
    log.error("予期しないデータ整合性エラーが発生しました", ex);
    return build(HttpStatus.INTERNAL_SERVER_ERROR, "サーバー内部でエラーが発生しました", request);
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex, HttpServletRequest request) {
    log.error("予期しないエラーが発生しました", ex);
    return build(HttpStatus.INTERNAL_SERVER_ERROR, "サーバー内部でエラーが発生しました", request);
  }

  private static final String EMAIL_UNIQUE_CONSTRAINT_NAME = "uk_users_email";

  private boolean isEmailUniqueConstraintViolation(DataIntegrityViolationException ex) {
    ConstraintViolationException constraintViolation = findConstraintViolationException(ex);
    if (constraintViolation != null && constraintViolation.getConstraintName() != null) {
      return EMAIL_UNIQUE_CONSTRAINT_NAME.equalsIgnoreCase(constraintViolation.getConstraintName());
    }

    // ConstraintViolationExceptionが見つからない、または制約名を取得できない場合のみ
    // 例外メッセージ文字列によるフォールバック判定を行う。
    Throwable rootCause = ex.getMostSpecificCause();
    String message = rootCause.getMessage();
    return message != null && message.toUpperCase(Locale.ROOT).contains(EMAIL_UNIQUE_CONSTRAINT_NAME.toUpperCase(Locale.ROOT));
  }

  private ConstraintViolationException findConstraintViolationException(Throwable ex) {
    Throwable current = ex;
    while (current != null) {
      if (current instanceof ConstraintViolationException constraintViolation) {
        return constraintViolation;
      }
      current = current.getCause();
    }
    return null;
  }

  private ResponseEntity<ErrorResponse> build(HttpStatus status, String message, HttpServletRequest request) {
    ErrorResponse body = new ErrorResponse(
        status.value(), status.getReasonPhrase(), message, request.getRequestURI(), Instant.now());
    return ResponseEntity.status(status).body(body);
  }
}
