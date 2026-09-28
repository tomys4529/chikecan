package com.chikecan.backend.security;

import java.io.IOException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Googleログイン成功後、Reactのフロントエンドへブラウザリダイレクトで戻す。
 * JSESSIONIDはこのハンドラーが呼ばれる前(セッション認証戦略経由)に既に発行済みのため、
 * ここではリダイレクト先の決定のみを行う。
 * リダイレクト先はメール内リンクの組み立てでも使っている既存のapp.base-url
 * (ローカル: http://localhost:5173 / 本番: 実URL)を流用し、新しい環境変数は増やさない。
 */
@Component
public class OAuth2LoginSuccessHandler implements AuthenticationSuccessHandler {

  private final String redirectUrl;

  public OAuth2LoginSuccessHandler(@Value("${app.base-url}") String appBaseUrl) {
    this.redirectUrl = appBaseUrl;
  }

  @Override
  public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
      Authentication authentication) throws IOException {
    response.sendRedirect(redirectUrl);
  }
}
