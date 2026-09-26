package com.chikecan.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

@ExtendWith(MockitoExtension.class)
class PasswordResetMailServiceTest {

  @Mock
  private ObjectProvider<JavaMailSender> mailSenderProvider;

  @Mock
  private JavaMailSender mailSender;

  @Test
  void mailEnabledがfalseの場合はJavaMailSenderを一切使わない() {
    PasswordResetMailService service = new PasswordResetMailService(
        mailSenderProvider, false, "no-reply@chikecan.local", "http://localhost:5173");

    service.sendPasswordResetEmail("user@example.com", "raw-token");

    verify(mailSenderProvider, never()).getObject();
  }

  @Test
  void mailEnabledがtrueの場合は宛先送信元本文URLを含むメールを送信する() {
    when(mailSenderProvider.getObject()).thenReturn(mailSender);
    PasswordResetMailService service = new PasswordResetMailService(
        mailSenderProvider, true, "no-reply@chikecan.local", "http://localhost:5173");

    service.sendPasswordResetEmail("user@example.com", "raw-token-value");

    ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
    verify(mailSender).send(captor.capture());
    SimpleMailMessage sent = captor.getValue();

    assertThat(sent.getTo()).containsExactly("user@example.com");
    assertThat(sent.getFrom()).isEqualTo("no-reply@chikecan.local");
    assertThat(sent.getText()).contains("http://localhost:5173/reset-password?token=raw-token-value");
  }

  /**
   * メール送信失敗時にrequestPasswordResetの@Transactionalがrollbackされないようにするための
   * 要件。ここで例外を伝播させないことを検証する
   * (呼び出し元のUserServiceはこのメソッドが例外を投げない前提で実装されている)。
   */
  @Test
  void メール送信が失敗しても例外を伝播させない() {
    when(mailSenderProvider.getObject()).thenReturn(mailSender);
    doThrow(new MailSendException("smtp接続に失敗しました")).when(mailSender).send(any(SimpleMailMessage.class));
    PasswordResetMailService service = new PasswordResetMailService(
        mailSenderProvider, true, "no-reply@chikecan.local", "http://localhost:5173");

    assertThatCode(() -> service.sendPasswordResetEmail("user@example.com", "raw-token"))
        .doesNotThrowAnyException();
  }
}
