package com.chikecan.backend.dto;

import org.springframework.security.web.csrf.CsrfToken;

public class CsrfTokenResponse {

  private final String token;
  private final String headerName;
  private final String parameterName;

  public CsrfTokenResponse(CsrfToken csrfToken) {
    this.token = csrfToken.getToken();
    this.headerName = csrfToken.getHeaderName();
    this.parameterName = csrfToken.getParameterName();
  }

  public String getToken() {
    return token;
  }

  public String getHeaderName() {
    return headerName;
  }

  public String getParameterName() {
    return parameterName;
  }
}
