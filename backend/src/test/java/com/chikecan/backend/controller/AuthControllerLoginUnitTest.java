package com.chikecan.backend.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;

import com.chikecan.backend.dto.LoginRequest;
import com.chikecan.backend.exception.InvalidCredentialsException;
import com.chikecan.backend.service.UserService;

class AuthControllerLoginUnitTest {

  @AfterEach
  void clearSecurityContext() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void 認証失敗後にSecurityContextへ認証情報が残らない() {
    UserService userService = mock(UserService.class);
    AuthenticationManager authenticationManager = mock(AuthenticationManager.class);
    SessionAuthenticationStrategy sessionAuthenticationStrategy = mock(SessionAuthenticationStrategy.class);
    SecurityContextRepository securityContextRepository = mock(SecurityContextRepository.class);

    when(authenticationManager.authenticate(any())).thenThrow(new BadCredentialsException("bad credentials"));

    AuthController controller = new AuthController(
        userService, authenticationManager, sessionAuthenticationStrategy, securityContextRepository);

    LoginRequest request = new LoginRequest();
    request.setEmail("test@example.com");
    request.setPassword("Passw0rd123");

    MockHttpServletRequest httpRequest = new MockHttpServletRequest();
    MockHttpServletResponse httpResponse = new MockHttpServletResponse();

    assertThatThrownBy(() -> controller.login(request, httpRequest, httpResponse))
        .isInstanceOf(InvalidCredentialsException.class);

    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    verifyNoInteractions(sessionAuthenticationStrategy);
    verifyNoInteractions(securityContextRepository);
  }
}
