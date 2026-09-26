package com.chikecan.backend.service;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Resend(https://resend.com)のREST APIを使ってメールを送信する共通クライアント。
 * VerificationMailService/PasswordResetMailService/EmailChangeMailServiceの3つが
 * 共通で使う「Resend APIへのHTTP POST」部分だけをここへ集約する。
 * 件名・本文・URLの組み立てや、app.mail.enabledによるローカル/本番の分岐は
 * 引き続き各XxxMailService側の責務とする。
 *
 * Render Freeプラン等、SMTPポート(25/465/587)への外向き通信がブロックされる環境でも
 * HTTPS(443)は通るため、SMTP(JavaMailSender)からこのHTTP API方式へ移行した。
 *
 * このプロジェクトの構成ではRestClient.BuilderがSpringのBeanとして自動構成されないため、
 * (別途starter追加はせず)RestClient.builder()を直接呼び出して自前で構築する。
 * 接続・読み取りタイムアウトもここで明示的に設定し、本番で登録処理等が無限に
 * 待たされることを防ぐ。
 *
 * 4xx/5xx/タイムアウト/接続エラー/レスポンスのJSONパースエラーは、いずれもRestClientの
 * RestClientException(またはそのサブクラス)としてここに到達するため、まとめてcatchし
 * ログにのみ記録して例外を伝播させない。呼び出し元(各XxxMailService経由でUserServiceの
 * @Transactionalメソッド)をrollbackさせないためであり、ユーザーはメール再送の仕組みで
 * 回復できる。
 */
@Service
public class ResendMailClient {

  private static final Logger log = LoggerFactory.getLogger(ResendMailClient.class);
  private static final String RESEND_API_URL = "https://api.resend.com/emails";

  // 本番で「登録中…」等の画面が無限に待たされることを避けるための明示的なタイムアウト。
  private static final int CONNECT_TIMEOUT_MS = 5_000;
  private static final int READ_TIMEOUT_MS = 10_000;

  private final RestClient restClient;
  private final String apiKey;

  /**
   * ローカル開発ではapp.mail.enabled=falseのためこのクラスのsendは呼ばれない。
   * RESEND_API_KEYが未設定でも起動時エラーにならないよう、デフォルト値を空文字にする。
   */
  @Autowired
  public ResendMailClient(@Value("${resend.api-key:}") String apiKey) {
    this(newTimeoutConfiguredBuilder(), apiKey);
  }

  /**
   * テスト用: MockRestServiceServerをbindしたRestClient.Builder等、外部から用意した
   * builderへ差し替えられるようにする(本番経路の1引数コンストラクタはタイムアウト設定済みの
   * requestFactoryを内部で構築するため、ここではbaseUrlの設定のみ行いrequestFactoryには触れない)。
   */
  ResendMailClient(RestClient.Builder restClientBuilder, String apiKey) {
    this.apiKey = apiKey;
    this.restClient = restClientBuilder.baseUrl(RESEND_API_URL).build();
  }

  private static RestClient.Builder newTimeoutConfiguredBuilder() {
    SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
    requestFactory.setConnectTimeout(CONNECT_TIMEOUT_MS);
    requestFactory.setReadTimeout(READ_TIMEOUT_MS);
    return RestClient.builder().requestFactory(requestFactory);
  }

  public void send(String to, String from, String subject, String text) {
    try {
      Map<String, Object> response = restClient.post()
          .header("Authorization", "Bearer " + apiKey)
          .contentType(MediaType.APPLICATION_JSON)
          .body(Map.of(
              "from", from,
              "to", List.of(to),
              "subject", subject,
              "text", text))
          .retrieve()
          .body(new ParameterizedTypeReference<Map<String, Object>>() {
          });

      Object messageId = response != null ? response.get("id") : null;
      log.info("Resend APIへのメール送信に成功しました(宛先: {}, messageId: {})", to, messageId);
    } catch (RestClientException ex) {
      // 例外メッセージ・スタックトレースにAuthorizationヘッダ(APIキー)の内容は含まれない。
      log.error("Resend APIへのメール送信に失敗しました(宛先: {})", to, ex);
    }
  }
}
