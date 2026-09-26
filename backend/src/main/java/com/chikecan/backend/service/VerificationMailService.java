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
 * メールアドレス確認用のメール送信を担当する。
 * app.mail.enabled=falseの環境(ローカル開発)では実際には送信せず、
 * 開発者が動作確認できるよう認証URLをログへ出力するだけにとどめる。
 * JavaMailSenderはapp.mail.enabled=trueの場合のみ実際に利用するため、
 * ObjectProvider経由で取得し、無効時にBean未設定でも起動時エラーにならないようにする。
 *
 * メール送信(MailException)はここで吸収し、呼び出し元(UserService)へ伝播させない。
 * register/resendVerificationは@Transactionalであり、例外を伝播させるとpending_registrations
 * への保存自体もrollbackされてしまう。それでは「メール送信に失敗してもpendingは残し、
 * resend-verificationで回復できる」という設計意図に反するため、意図的に例外を握りつぶす。
 */
@Service
public class VerificationMailService {

  private static final Logger log = LoggerFactory.getLogger(VerificationMailService.class);

  private final ObjectProvider<JavaMailSender> mailSenderProvider;
  private final boolean mailEnabled;
  private final String mailFrom;
  private final String appBaseUrl;

  public VerificationMailService(
      ObjectProvider<JavaMailSender> mailSenderProvider,
      @Value("${app.mail.enabled}") boolean mailEnabled,
      @Value("${app.mail.from}") String mailFrom,
      @Value("${app.base-url}") String appBaseUrl) {
    this.mailSenderProvider = mailSenderProvider;
    this.mailEnabled = mailEnabled;
    this.mailFrom = mailFrom;
    this.appBaseUrl = appBaseUrl;
  }

  public void sendVerificationEmail(String toEmail, String rawToken) {
    String verificationUrl = buildVerificationUrl(rawToken);

    if (!mailEnabled) {
      // ローカル開発用: 実SMTPを必須にしないため、送信の代わりにログへURLを出力する。
      // 生パスワードや生トークン以外の機密情報は出力しない。本番(mailEnabled=true)では
      // このログ出力自体を行わない。
      log.info("[開発用ログ出力] メール認証URL(宛先: {}): {}", toEmail, verificationUrl);
      return;
    }

    SimpleMailMessage message = new SimpleMailMessage();
    message.setTo(toEmail);
    message.setFrom(mailFrom);
    message.setSubject("【chikecan】メールアドレスの確認をお願いします");
    message.setText(buildMailBody(verificationUrl));

    try {
      mailSenderProvider.getObject().send(message);
    } catch (MailException ex) {
      // 呼び出し元のトランザクションをrollbackさせないよう、ここで例外を吸収して
      // ログにのみ記録する。ユーザーは/api/auth/resend-verificationで再送できる。
      log.error("認証メールの送信に失敗しました(宛先: {})", toEmail, ex);
    }
  }

  private String buildVerificationUrl(String rawToken) {
    return appBaseUrl + "/verify-email?token=" + rawToken;
  }

  private String buildMailBody(String verificationUrl) {
    return """
        chikecanへご登録ありがとうございます。

        以下のURLからメールアドレスの確認を完了してください。

        %s

        このリンクの有効期限は24時間です。

        心当たりがない場合は、このメールを破棄してください。
        """.formatted(verificationUrl);
  }
}
