package com.chikecan.backend.security;

import java.io.IOException;
import java.time.Instant;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import com.chikecan.backend.exception.ErrorResponse;

import tools.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 未認証アクセス時に、Basic認証ダイアログではなく統一フォーマットのJSONで401を返す。
 */
@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

  private final ObjectMapper objectMapper;

  public RestAuthenticationEntryPoint(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  @Override
  public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException)
      throws IOException {
    ErrorResponse body = new ErrorResponse(
        HttpStatus.UNAUTHORIZED.value(),
        HttpStatus.UNAUTHORIZED.getReasonPhrase(),
        "認証が必要です",
        request.getRequestURI(),
        Instant.now());
    response.setStatus(HttpStatus.UNAUTHORIZED.value());
    response.setCharacterEncoding("UTF-8");
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response.getWriter().write(objectMapper.writeValueAsString(body));
  }
}
