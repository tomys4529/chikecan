package com.chikecan.backend.controller;

import java.util.Locale;
import java.util.Map;

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
import com.chikecan.backend.dto.ResendVerificationRequest;
import com.chikecan.backend.dto.UserResponse;
import com.chikecan.backend.dto.VerifyEmailRequest;
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
  public Map<String, String> register(@Valid @RequestBody RegisterRequest request) {
    userService.register(request);
    return Map.of("message", "確認メールの送信を受け付けました。メール内のリンクから本登録を完了してください。");
  }

  @PostMapping("/verify-email")
  public Map<String, String> verifyEmail(@Valid @RequestBody VerifyEmailRequest request) {
    userService.verifyEmail(request.getToken());
    return Map.of("message", "メールアドレスの確認が完了しました。");
  }

  @PostMapping("/resend-verification")
  public Map<String, String> resendVerification(@Valid @RequestBody ResendVerificationRequest request) {
    userService.resendVerification(request.getEmail());
    return Map.of("message", "対象のアカウントが確認できた場合、認証メールを送信します。");
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
    // セッションのAppUserDetailsはログイン時点のスナップショットのため、
    // XP等の最新値を返すためDBから再取得する。
    return userService.getCurrentUser(principal.getId());
  }
}
