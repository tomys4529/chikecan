package com.chikecan.backend.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.test.util.ReflectionTestUtils;

import jakarta.servlet.http.Cookie;
import tools.jackson.databind.ObjectMapper;

class CookieConfigurationTest {

  private SecurityConfig newSecurityConfig() {
    return new SecurityConfig(
        new RestAuthenticationEntryPoint(new ObjectMapper()),
        new RestAccessDeniedHandler(new ObjectMapper()),
        new RestLogoutSuccessHandler(new ObjectMapper()));
  }

  // MockHttpServletResponse#getHeader("Set-Cookie")は内部でCookieオブジェクトへ
  // 変換して再構築するためSameSite属性が文字列へ反映されない(Mock特有の制限)。
  // そのためCookieオブジェクトのgetAttribute("SameSite")で直接検証する。
  private Cookie csrfCookieFor(boolean cookieSecure) {
    SecurityConfig config = newSecurityConfig();
    ReflectionTestUtils.setField(config, "cookieSecure", cookieSecure);

    var repository = config.csrfTokenRepository();
    MockHttpServletRequest request = new MockHttpServletRequest();
    MockHttpServletResponse response = new MockHttpServletResponse();
    CsrfToken token = repository.generateToken(request);
    repository.saveToken(token, request, response);
    return response.getCookie("XSRF-TOKEN");
  }

  @Test
  void cookieSecureがtrueならXSRFトークンCookieにSecureが付与される() {
    Cookie cookie = csrfCookieFor(true);

    assertThat(cookie).isNotNull();
    assertThat(cookie.getSecure()).isTrue();
    assertThat(cookie.getAttribute("SameSite")).isEqualTo("Lax");
    assertThat(cookie.getPath()).isEqualTo("/");
    assertThat(cookie.isHttpOnly()).isFalse();
  }

  @Test
  void cookieSecureがfalseならXSRFトークンCookieにSecureが付与されない() {
    Cookie cookie = csrfCookieFor(false);

    assertThat(cookie).isNotNull();
    assertThat(cookie.getSecure()).isFalse();
    assertThat(cookie.getAttribute("SameSite")).isEqualTo("Lax");
    assertThat(cookie.getPath()).isEqualTo("/");
  }

  @Test
  void local設定ファイルはCookieSecureを無効化している() throws IOException {
    String content = readResource("application-local.properties");
    assertThat(content).contains("server.servlet.session.cookie.secure=false");
    assertThat(content).contains("app.security.cookie-secure=false");
  }

  @Test
  void prod設定ファイルはCookieSecureを有効化している() throws IOException {
    String content = readResource("application-prod.properties");
    assertThat(content).contains("server.servlet.session.cookie.secure=true");
    assertThat(content).contains("app.security.cookie-secure=true");
  }

  private String readResource(String name) throws IOException {
    try (InputStream in = new ClassPathResource(name).getInputStream()) {
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }
  }
}
