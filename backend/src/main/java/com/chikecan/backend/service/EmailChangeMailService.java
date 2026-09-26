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
 * メールアドレス変更確認用のメール送信を担当する。VerificationMailService(メール認証)・
 * PasswordResetMailService(パスワードリセット)とは責務を分け、件名・本文・URLパスを
 * メールアドレス変更専用にする。mail.enabled/mail.from/base-urlの設定キーは既存機能と
 * 共用する(用途ごとに新しい環境変数を増やす必要がないため)。
 *
 * app.mail.enabled=falseの環境(ローカル開発)では実際には送信せず、開発者が動作確認できるよう
 * 変更確認URLをログへ出力するだけにとどめる。メール送信(MailException)はここで吸収し、
 * 呼び出し元(UserService)へ伝播させない。requestEmailChangeは@Transactionalであり、
 * 例外を伝播させると申請保存自体もrollbackされてしまう。それでは「メール送信に失敗しても
 * 申請は残し、再度申請すれば新しいメールを送れる」という設計意図に反するため、
 * 意図的に例外を握りつぶす。
 */
@Service
public class EmailChangeMailService {

  private static final Logger log = LoggerFactory.getLogger(EmailChangeMailService.class);

  private final ObjectProvider<JavaMailSender> mailSenderProvider;
  private final boolean mailEnabled;
  private final String mailFrom;
  private final String appBaseUrl;

  public EmailChangeMailService(
      ObjectProvider<JavaMailSender> mailSenderProvider,
      @Value("${app.mail.enabled}") boolean mailEnabled,
      @Value("${app.mail.from}") String mailFrom,
      @Value("${app.base-url}") String appBaseUrl) {
    this.mailSenderProvider = mailSenderProvider;
    this.mailEnabled = mailEnabled;
    this.mailFrom = mailFrom;
    this.appBaseUrl = appBaseUrl;
  }

  public void sendEmailChangeEmail(String toEmail, String rawToken) {
    String changeUrl = buildChangeUrl(rawToken);

    if (!mailEnabled) {
      // ローカル開発用: 実SMTPを必須にしないため、送信の代わりにログへURLを出力する。
      // 本番(mailEnabled=true)ではこのログ出力自体を行わない。
      log.info("[開発用ログ出力] メールアドレス変更確認URL(宛先: {}): {}", toEmail, changeUrl);
      return;
    }

    SimpleMailMessage message = new SimpleMailMessage();
    message.setTo(toEmail);
    message.setFrom(mailFrom);
    message.setSubject("【chikecan】メールアドレス変更の確認をお願いします");
    message.setText(buildMailBody(changeUrl));

    try {
      mailSenderProvider.getObject().send(message);
    } catch (MailException ex) {
      // 呼び出し元のトランザクションをrollbackさせないよう、ここで例外を吸収して
      // ログにのみ記録する。ユーザーは再度メールアドレス変更を申請できる。
      log.error("メールアドレス変更確認メールの送信に失敗しました(宛先: {})", toEmail, ex);
    }
  }

  private String buildChangeUrl(String rawToken) {
    return appBaseUrl + "/verify-email-change?token=" + rawToken;
  }

  private String buildMailBody(String changeUrl) {
    return """
        chikecanでメールアドレスの変更申請を受け付けました。

        以下のURLからメールアドレスの変更を完了してください。

        %s

        このリンクの有効期限は1時間です。

        心当たりがない場合は、このメールを破棄してください。
        """.formatted(changeUrl);
  }
}
