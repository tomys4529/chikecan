package com.chikecan.backend.security;

import java.io.IOException;
import java.time.Instant;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.web.session.SessionInformationExpiredEvent;
import org.springframework.security.web.session.SessionInformationExpiredStrategy;
import org.springframework.stereotype.Component;

import com.chikecan.backend.exception.ErrorResponse;

import tools.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * SessionRegistry上でexpireNow()された(=パスワード変更等により無効化された)セッションで
 * リクエストが来た場合に、標準のリダイレクトではなく統一フォーマットのJSONで401を返す。
 * RestAuthenticationEntryPointと同じ形式にすることで、フロント側のエラー処理を統一する。
 */
@Component
public class RestSessionInformationExpiredStrategy implements SessionInformationExpiredStrategy {

  private final ObjectMapper objectMapper;

  public RestSessionInformationExpiredStrategy(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  @Override
  public void onExpiredSessionDetected(SessionInformationExpiredEvent event) throws IOException {
    HttpServletRequest request = event.getRequest();
    HttpServletResponse response = event.getResponse();

    ErrorResponse body = new ErrorResponse(
        HttpStatus.UNAUTHORIZED.value(),
        HttpStatus.UNAUTHORIZED.getReasonPhrase(),
        "セッションが無効になりました。再度ログインしてください",
        request.getRequestURI(),
        Instant.now());
    response.setStatus(HttpStatus.UNAUTHORIZED.value());
    response.setCharacterEncoding("UTF-8");
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response.getWriter().write(objectMapper.writeValueAsString(body));
  }
}
