package com.chikecan.backend.security;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Googleログイン失敗時、内部例外の詳細(スタックトレース・Googleのtoken・認可コード等)を
 * ブラウザへ一切返さず、ログイン画面へのリダイレクトのみを行う。詳細な理由はサーバーログにのみ記録する。
 * LoginPageは?oauthError=trueを見て、固定の汎用メッセージを表示する。
 */
@Component
public class OAuth2LoginFailureHandler implements AuthenticationFailureHandler {

  private static final Logger log = LoggerFactory.getLogger(OAuth2LoginFailureHandler.class);

  private final String redirectBaseUrl;

  public OAuth2LoginFailureHandler(@Value("${app.base-url}") String appBaseUrl) {
    this.redirectBaseUrl = appBaseUrl;
  }

  @Override
  public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
      AuthenticationException exception) throws IOException {
    log.warn("Googleログインに失敗しました: {}", exception.getMessage());
    response.sendRedirect(redirectBaseUrl + "/login?oauthError=true");
  }
}
