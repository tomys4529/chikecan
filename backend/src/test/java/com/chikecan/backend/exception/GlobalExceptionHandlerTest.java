package com.chikecan.backend.exception;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.SQLException;

import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

class GlobalExceptionHandlerTest {

  private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

  private MockHttpServletRequest request() {
    return new MockHttpServletRequest("POST", "/api/auth/register");
  }

  @Test
  void ConstraintViolationExceptionでuk_users_emailを取得した場合は409になる() {
    ConstraintViolationException cause = new ConstraintViolationException(
        "could not execute statement", new SQLException("duplicate key"), "uk_users_email");
    DataIntegrityViolationException ex = new DataIntegrityViolationException("insert failed", cause);

    ResponseEntity<ErrorResponse> response = handler.handleDataIntegrityViolation(ex, request());

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(response.getBody().getMessage()).isEqualTo("このメールアドレスは既に登録されています");
  }

  @Test
  void 制約名の大文字小文字が異なっても409になる() {
    ConstraintViolationException cause = new ConstraintViolationException(
        "could not execute statement", new SQLException("duplicate key"), "UK_USERS_EMAIL");
    DataIntegrityViolationException ex = new DataIntegrityViolationException("insert failed", cause);

    ResponseEntity<ErrorResponse> response = handler.handleDataIntegrityViolation(ex, request());

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
  }

  @Test
  void 別の制約名の場合はメール重複として扱われず500になる() {
    ConstraintViolationException cause = new ConstraintViolationException(
        "could not execute statement", new SQLException("fk violation"), "fk_tickets_requester");
    DataIntegrityViolationException ex = new DataIntegrityViolationException("insert failed", cause);

    ResponseEntity<ErrorResponse> response = handler.handleDataIntegrityViolation(ex, request());

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    assertThat(response.getBody().getMessage()).isEqualTo("サーバー内部でエラーが発生しました");
  }

  @Test
  void ConstraintViolationExceptionが存在しない場合はメッセージ文字列で判定される() {
    SQLException sqlException = new SQLException(
        "Unique index or primary key violation: \"PUBLIC.UK_USERS_EMAIL INDEX ...\"");
    DataIntegrityViolationException ex = new DataIntegrityViolationException("insert failed", sqlException);

    ResponseEntity<ErrorResponse> response = handler.handleDataIntegrityViolation(ex, request());

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
  }

  @Test
  void ConstraintViolationExceptionの制約名がnullの場合はメッセージ文字列にフォールバックする() {
    ConstraintViolationException cause = new ConstraintViolationException(
        "could not execute statement",
        new SQLException("Unique index or primary key violation: \"PUBLIC.UK_USERS_EMAIL INDEX ...\""),
        null);
    DataIntegrityViolationException ex = new DataIntegrityViolationException("insert failed", cause);

    ResponseEntity<ErrorResponse> response = handler.handleDataIntegrityViolation(ex, request());

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
  }
}
