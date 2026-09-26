package com.chikecan.backend.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * メールアドレス確認用のメール送信を担当する。
 * app.mail.enabled=falseの環境(ローカル開発)では実際には送信せず、
 * 開発者が動作確認できるよう認証URLをログへ出力するだけにとどめる。
 * 実際の送信(Resend REST API呼び出し)はResendMailClientへ委譲する。
 * ResendMailClient.sendは内部で例外を吸収し呼び出し元へ伝播させないため、
 * ここでは追加のtry/catchを行わない。
 */
@Service
public class VerificationMailService {

  private static final Logger log = LoggerFactory.getLogger(VerificationMailService.class);

  private final ResendMailClient resendMailClient;
  private final boolean mailEnabled;
  private final String mailFrom;
  private final String appBaseUrl;

  public VerificationMailService(
      ResendMailClient resendMailClient,
      @Value("${app.mail.enabled}") boolean mailEnabled,
      @Value("${app.mail.from}") String mailFrom,
      @Value("${app.base-url}") String appBaseUrl) {
    this.resendMailClient = resendMailClient;
    this.mailEnabled = mailEnabled;
    this.mailFrom = mailFrom;
    this.appBaseUrl = appBaseUrl;
  }

  public void sendVerificationEmail(String toEmail, String rawToken) {
    String verificationUrl = buildVerificationUrl(rawToken);

    if (!mailEnabled) {
      // ローカル開発用: 実送信を必須にしないため、送信の代わりにログへURLを出力する。
      // 生パスワードや生トークン以外の機密情報は出力しない。本番(mailEnabled=true)では
      // このログ出力自体を行わない。
      log.info("[開発用ログ出力] メール認証URL(宛先: {}): {}", toEmail, verificationUrl);
      return;
    }

    resendMailClient.send(toEmail, mailFrom, "【chikecan】メールアドレスの確認をお願いします", buildMailBody(verificationUrl));
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
