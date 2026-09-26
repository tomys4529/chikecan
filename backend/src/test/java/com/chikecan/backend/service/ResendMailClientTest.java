package com.chikecan.backend.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.io.IOException;
import java.net.SocketTimeoutException;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * ResendMailClient(共通のResend REST API送信処理)の単体テスト。
 * 実際にネットワークへは接続せず、MockRestServiceServerでHTTPリクエストの内容を検証する。
 */
class ResendMailClientTest {

  private ResendMailClient newClient(MockRestServiceServer[] serverHolder, String apiKey) {
    RestClient.Builder builder = RestClient.builder();
    serverHolder[0] = MockRestServiceServer.bindTo(builder).build();
    return new ResendMailClient(builder, apiKey);
  }

  @Test
  void 正常な2xxレスポンスの場合は例外を投げない() {
    MockRestServiceServer[] serverHolder = new MockRestServiceServer[1];
    ResendMailClient client = newClient(serverHolder, "test-api-key");

    serverHolder[0].expect(requestTo("https://api.resend.com/emails"))
        .andExpect(method(HttpMethod.POST))
        .andExpect(header("Authorization", "Bearer test-api-key"))
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.from").value("no-reply@chikecan.local"))
        .andExpect(jsonPath("$.to[0]").value("user@example.com"))
        .andExpect(jsonPath("$.subject").value("テスト件名"))
        .andExpect(jsonPath("$.text").value("テスト本文"))
        .andRespond(withSuccess("{\"id\":\"msg-123\"}", MediaType.APPLICATION_JSON));

    assertThatCode(() -> client.send("user@example.com", "no-reply@chikecan.local", "テスト件名", "テスト本文"))
        .doesNotThrowAnyException();

    serverHolder[0].verify();
  }

  @Test
  void クライアントエラー4xxレスポンスでも例外を伝播させない() {
    MockRestServiceServer[] serverHolder = new MockRestServiceServer[1];
    ResendMailClient client = newClient(serverHolder, "test-api-key");

    serverHolder[0].expect(requestTo("https://api.resend.com/emails"))
        .andRespond(withStatus(HttpStatus.BAD_REQUEST).body("{\"message\":\"invalid from address\"}")
            .contentType(MediaType.APPLICATION_JSON));

    assertThatCode(() -> client.send("user@example.com", "no-reply@chikecan.local", "件名", "本文"))
        .doesNotThrowAnyException();

    serverHolder[0].verify();
  }

  @Test
  void サーバーエラー5xxレスポンスでも例外を伝播させない() {
    MockRestServiceServer[] serverHolder = new MockRestServiceServer[1];
    ResendMailClient client = newClient(serverHolder, "test-api-key");

    serverHolder[0].expect(requestTo("https://api.resend.com/emails"))
        .andRespond(withServerError());

    assertThatCode(() -> client.send("user@example.com", "no-reply@chikecan.local", "件名", "本文"))
        .doesNotThrowAnyException();

    serverHolder[0].verify();
  }

  @Test
  void 接続エラー相当の例外でも伝播させない() {
    RestClient.Builder builder = RestClient.builder();
    // 常にIOException(接続エラー相当)を投げるClientHttpRequestFactoryへ差し替える。
    ClientHttpRequestFactory failingFactory = (uri, httpMethod) -> {
      throw new IOException("connection refused");
    };
    ResendMailClient client = new ResendMailClient(builder.requestFactory(failingFactory), "test-api-key");

    assertThatCode(() -> client.send("user@example.com", "no-reply@chikecan.local", "件名", "本文"))
        .doesNotThrowAnyException();
  }

  @Test
  void タイムアウト相当の例外でも伝播させない() {
    RestClient.Builder builder = RestClient.builder();
    ClientHttpRequestFactory timeoutFactory = (uri, httpMethod) -> {
      throw new IOException(new SocketTimeoutException("read timed out"));
    };
    ResendMailClient client = new ResendMailClient(builder.requestFactory(timeoutFactory), "test-api-key");

    assertThatCode(() -> client.send("user@example.com", "no-reply@chikecan.local", "件名", "本文"))
        .doesNotThrowAnyException();
  }

  @Test
  void レスポンスのJSONが不正でも例外を伝播させない() {
    MockRestServiceServer[] serverHolder = new MockRestServiceServer[1];
    ResendMailClient client = newClient(serverHolder, "test-api-key");

    serverHolder[0].expect(requestTo("https://api.resend.com/emails"))
        .andRespond(withSuccess("not-a-valid-json", MediaType.APPLICATION_JSON));

    assertThatCode(() -> client.send("user@example.com", "no-reply@chikecan.local", "件名", "本文"))
        .doesNotThrowAnyException();

    serverHolder[0].verify();
  }
}
