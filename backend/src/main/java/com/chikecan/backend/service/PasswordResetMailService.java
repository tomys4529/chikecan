package com.chikecan.backend.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * パスワード再設定用のメール送信を担当する。VerificationMailService(メール認証)とは
 * 責務を分け、件名・本文・URLパスをパスワードリセット専用にする。
 * mail.enabled/mail.from/base-urlの設定キーはメール認証機能と共用する
 * (用途ごとに新しい環境変数を増やす必要がないため)。
 *
 * app.mail.enabled=falseの環境(ローカル開発)では実際には送信せず、
 * 開発者が動作確認できるようリセットURLをログへ出力するだけにとどめる。
 * 実際の送信(Resend REST API呼び出し)はResendMailClientへ委譲する。
 * ResendMailClient.sendは内部で例外を吸収し呼び出し元へ伝播させないため、
 * ここでは追加のtry/catchを行わない。
 */
@Service
public class PasswordResetMailService {

  private static final Logger log = LoggerFactory.getLogger(PasswordResetMailService.class);

  private final ResendMailClient resendMailClient;
  private final boolean mailEnabled;
  private final String mailFrom;
  private final String appBaseUrl;

  public PasswordResetMailService(
      ResendMailClient resendMailClient,
      @Value("${app.mail.enabled}") boolean mailEnabled,
      @Value("${app.mail.from}") String mailFrom,
      @Value("${app.base-url}") String appBaseUrl) {
    this.resendMailClient = resendMailClient;
    this.mailEnabled = mailEnabled;
    this.mailFrom = mailFrom;
    this.appBaseUrl = appBaseUrl;
  }

  public void sendPasswordResetEmail(String toEmail, String rawToken) {
    String resetUrl = buildResetUrl(rawToken);

    if (!mailEnabled) {
      // ローカル開発用: 実送信を必須にしないため、送信の代わりにログへURLを出力する。
      // 本番(mailEnabled=true)ではこのログ出力自体を行わない。
      log.info("[開発用ログ出力] パスワード再設定URL(宛先: {}): {}", toEmail, resetUrl);
      return;
    }

    resendMailClient.send(toEmail, mailFrom, "【chikecan】パスワード再設定のご案内", buildMailBody(resetUrl));
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
