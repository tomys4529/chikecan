package com.chikecan.backend.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.web.server.Cookie.SameSite;
import org.springframework.boot.web.server.autoconfigure.ServerProperties;
import org.springframework.test.context.ActiveProfiles;

/**
 * localプロファイルで実際にバインドされたJSESSIONID関連のCookie設定値を検証する。
 * MockMvcはサーブレットコンテナのセッションCookie発行処理を再現しないため、
 * ここではSpring Bootの設定バインディング結果(ServerProperties)を直接検証する。
 * 実際のSet-Cookieヘッダーは本番相当のHTTPサーバへの手動リクエストで確認済み(報告参照)。
 */
@SpringBootTest(webEnvironment = WebEnvironment.MOCK)
@ActiveProfiles("local")
class SessionCookiePropertiesTest {

  @Autowired
  private ServerProperties serverProperties;

  @Test
  void localではJSESSIONIDのCookie設定がHttpOnlyかつSameSiteLaxでSecureが無効になっている() {
    var cookie = serverProperties.getServlet().getSession().getCookie();

    assertThat(cookie.getHttpOnly()).isTrue();
    assertThat(cookie.getSameSite()).isEqualTo(SameSite.LAX);
    assertThat(cookie.getSecure()).isFalse();
    assertThat(cookie.getPath()).isEqualTo("/");
  }
}
