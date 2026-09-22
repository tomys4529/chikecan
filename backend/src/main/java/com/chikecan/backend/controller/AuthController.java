package com.chikecan.backend.controller;

import java.util.Locale;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.chikecan.backend.dto.CsrfTokenResponse;
import com.chikecan.backend.dto.LoginRequest;
import com.chikecan.backend.dto.RegisterRequest;
import com.chikecan.backend.dto.UserResponse;
import com.chikecan.backend.exception.InvalidCredentialsException;
import com.chikecan.backend.security.AppUserDetails;
import com.chikecan.backend.service.UserService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

  private final UserService userService;
  private final AuthenticationManager authenticationManager;
  private final SessionAuthenticationStrategy sessionAuthenticationStrategy;
  private final SecurityContextRepository securityContextRepository;

  public AuthController(UserService userService,
      AuthenticationManager authenticationManager,
      SessionAuthenticationStrategy sessionAuthenticationStrategy,
      SecurityContextRepository securityContextRepository) {
    this.userService = userService;
    this.authenticationManager = authenticationManager;
    this.sessionAuthenticationStrategy = sessionAuthenticationStrategy;
    this.securityContextRepository = securityContextRepository;
  }

  @GetMapping("/csrf")
  public CsrfTokenResponse csrf(CsrfToken csrfToken) {
    return new CsrfTokenResponse(csrfToken);
  }

  @PostMapping("/register")
  public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterRequest request) {
    UserResponse response = userService.register(request);
    return ResponseEntity.status(HttpStatus.CREATED).body(response);
  }

  @PostMapping("/login")
  public UserResponse login(@Valid @RequestBody LoginRequest request,
      HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
    String normalizedEmail = request.getEmail().trim().toLowerCase(Locale.ROOT);
    UsernamePasswordAuthenticationToken authRequest =
        UsernamePasswordAuthenticationToken.unauthenticated(normalizedEmail, request.getPassword());

    Authentication authentication;
    try {
      authentication = authenticationManager.authenticate(authRequest);
    } catch (AuthenticationException ex) {
      throw new InvalidCredentialsException("メールアドレスまたはパスワードが正しくありません");
    }

    sessionAuthenticationStrategy.onAuthentication(authentication, httpRequest, httpResponse);

    SecurityContext context = SecurityContextHolder.createEmptyContext();
    context.setAuthentication(authentication);
    SecurityContextHolder.setContext(context);

    securityContextRepository.saveContext(context, httpRequest, httpResponse);

    AppUserDetails principal = (AppUserDetails) authentication.getPrincipal();
    return new UserResponse(principal);
  }

  @GetMapping("/me")
  public UserResponse me(@AuthenticationPrincipal AppUserDetails principal) {
    return new UserResponse(principal);
  }
}
