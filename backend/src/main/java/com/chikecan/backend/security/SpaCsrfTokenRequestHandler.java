package com.chikecan.backend.security;

import java.util.function.Supplier;

import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.csrf.CsrfTokenRequestHandler;
import org.springframework.security.web.csrf.XorCsrfTokenRequestAttributeHandler;
import org.springframework.util.StringUtils;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Cookie経由でトークンをそのまま(非マスク)返しつつ、
 * ヘッダー送信時は生値、パラメータ送信時はBREACH対策済みのXORマスク値で照合するハンドラー。
 * Spring公式のSPA向けCSRF構成パターンに準拠する。
 */
public final class SpaCsrfTokenRequestHandler extends CsrfTokenRequestAttributeHandler {

  private final CsrfTokenRequestHandler xorHandler = new XorCsrfTokenRequestAttributeHandler();

  @Override
  public void handle(HttpServletRequest request, HttpServletResponse response, Supplier<CsrfToken> csrfToken) {
    this.xorHandler.handle(request, response, csrfToken);
  }

  @Override
  public String resolveCsrfTokenValue(HttpServletRequest request, CsrfToken csrfToken) {
    String headerValue = request.getHeader(csrfToken.getHeaderName());
    return StringUtils.hasText(headerValue)
        ? super.resolveCsrfTokenValue(request, csrfToken)
        : this.xorHandler.resolveCsrfTokenValue(request, csrfToken);
  }
}
