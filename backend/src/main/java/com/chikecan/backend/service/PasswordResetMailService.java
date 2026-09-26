package com.chikecan.backend.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * パスワード再設定用のメール送信を担当する。VerificationMailService(メール認証)とは
 * 責務を分け、件名・本文・URLパスをパスワードリセット専用にする。
 * mail.enabled/mail.from/base-urlの設定キーはメール認証機能と共用する
 * (用途ごとに新しい環境変数を増やす必要がないため)。
 *
 * app.mail.enabled=falseの環境(ローカル開発)では実際には送信せず、
 * 開発者が動作確認できるようリセットURLをログへ出力するだけにとどめる。
 * メール送信(MailException)はここで吸収し、呼び出し元(UserService)へ伝播させない。
 * requestPasswordResetは@Transactionalであり、例外を伝播させるとtoken保存自体も
 * rollbackされてしまう。それでは「メール送信に失敗してもtokenは残し、再度要求すれば
 * 新しいメールを送れる」という設計意図に反するため、意図的に例外を握りつぶす。
 */
@Service
public class PasswordResetMailService {

  private static final Logger log = LoggerFactory.getLogger(PasswordResetMailService.class);

  private final ObjectProvider<JavaMailSender> mailSenderProvider;
  private final boolean mailEnabled;
  private final String mailFrom;
  private final String appBaseUrl;

  public PasswordResetMailService(
      ObjectProvider<JavaMailSender> mailSenderProvider,
      @Value("${app.mail.enabled}") boolean mailEnabled,
      @Value("${app.mail.from}") String mailFrom,
      @Value("${app.base-url}") String appBaseUrl) {
    this.mailSenderProvider = mailSenderProvider;
    this.mailEnabled = mailEnabled;
    this.mailFrom = mailFrom;
    this.appBaseUrl = appBaseUrl;
  }

  public void sendPasswordResetEmail(String toEmail, String rawToken) {
    String resetUrl = buildResetUrl(rawToken);

    if (!mailEnabled) {
      // ローカル開発用: 実SMTPを必須にしないため、送信の代わりにログへURLを出力する。
      // 本番(mailEnabled=true)ではこのログ出力自体を行わない。
      log.info("[開発用ログ出力] パスワード再設定URL(宛先: {}): {}", toEmail, resetUrl);
      return;
    }

    SimpleMailMessage message = new SimpleMailMessage();
    message.setTo(toEmail);
    message.setFrom(mailFrom);
    message.setSubject("【chikecan】パスワード再設定のご案内");
    message.setText(buildMailBody(resetUrl));

    try {
      mailSenderProvider.getObject().send(message);
    } catch (MailException ex) {
      // 呼び出し元のトランザクションをrollbackさせないよう、ここで例外を吸収して
      // ログにのみ記録する。ユーザーは再度パスワードリセットを要求できる。
      log.error("パスワード再設定メールの送信に失敗しました(宛先: {})", toEmail, ex);
    }
  }

  private String buildResetUrl(String rawToken) {
    return appBaseUrl + "/reset-password?token=" + rawToken;
  }

  private String buildMailBody(String resetUrl) {
    return """
        chikecanのパスワード再設定リクエストを受け付けました。

        以下のURLから新しいパスワードを設定してください。

        %s

        このリンクの有効期限は1時間です。

        心当たりがない場合は、このメールを破棄してください。
        """.formatted(resetUrl);
  }
}
