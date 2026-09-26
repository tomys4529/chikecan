package com.chikecan.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

@ExtendWith(MockitoExtension.class)
class EmailChangeMailServiceTest {

  @Mock
  private ResendMailClient resendMailClient;

  @Test
  void mailEnabledがfalseの場合はResendMailClientを一切使わない() {
    EmailChangeMailService service = new EmailChangeMailService(
        resendMailClient, false, "no-reply@chikecan.local", "http://localhost:5173");

    service.sendEmailChangeEmail("new-address@example.com", "raw-token");

    verify(resendMailClient, never()).send(anyString(), anyString(), anyString(), anyString());
  }

  @Test
  void mailEnabledがtrueの場合は宛先送信元本文URLを含むメールを送信する() {
    EmailChangeMailService service = new EmailChangeMailService(
        resendMailClient, true, "no-reply@chikecan.local", "http://localhost:5173");

    service.sendEmailChangeEmail("new-address@example.com", "raw-token-value");

    ArgumentCaptor<String> textCaptor = ArgumentCaptor.forClass(String.class);
    verify(resendMailClient).send(
        eq("new-address@example.com"), eq("no-reply@chikecan.local"),
        eq("【chikecan】メールアドレス変更の確認をお願いします"), textCaptor.capture());

    assertThat(textCaptor.getValue()).contains("http://localhost:5173/verify-email-change?token=raw-token-value");
  }

  /**
   * ResendMailClientをモックへ差し替えず、実際のHTTP呼び出し失敗(5xx)を
   * MockRestServiceServerで再現し、その結果がEmailChangeMailServiceの呼び出し元まで
   * 伝播しないことをエンドツーエンドで確認する
   * (呼び出し元のUserService.requestEmailChangeは@Transactionalであり、
   * ここで例外が伝播すると意図せず申請保存がrollbackされてしまう)。
   */
  @Test
  void メール送信が失敗しても例外を伝播させない() {
    RestClient.Builder builder = RestClient.builder();
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    ResendMailClient realResendMailClient = new ResendMailClient(builder, "test-api-key");
    server.expect(requestTo("https://api.resend.com/emails")).andRespond(withServerError());

    EmailChangeMailService service = new EmailChangeMailService(
        realResendMailClient, true, "no-reply@chikecan.local", "http://localhost:5173");

    assertThatCode(() -> service.sendEmailChangeEmail("new-address@example.com", "raw-token"))
        .doesNotThrowAnyException();

    server.verify();
  }
}
