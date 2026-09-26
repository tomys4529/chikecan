package com.chikecan.backend.controller;

import java.util.Map;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.chikecan.backend.dto.ChangeEmailRequest;
import com.chikecan.backend.dto.ChangePasswordRequest;
import com.chikecan.backend.dto.ConfirmEmailChangeRequest;
import com.chikecan.backend.security.AppUserDetails;
import com.chikecan.backend.service.UserService;

import jakarta.validation.Valid;

/**
 * ログイン済みユーザー自身のアカウント設定(パスワード変更・メールアドレス変更)を担当する。
 * email-change/confirmを除き、/api/**の既定ルール(認証必須)にそのまま従う。
 */
@RestController
@RequestMapping("/api/account")
public class AccountController {

  private final UserService userService;

  public AccountController(UserService userService) {
    this.userService = userService;
  }

  @PostMapping("/password")
  public Map<String, String> changePassword(@AuthenticationPrincipal AppUserDetails principal,
      @Valid @RequestBody ChangePasswordRequest request) {
    userService.changePassword(principal.getId(), request.getCurrentPassword(), request.getNewPassword());
    return Map.of("message", "パスワードを変更しました。");
  }

  @PostMapping("/email-change/request")
  public Map<String, String> requestEmailChange(@AuthenticationPrincipal AppUserDetails principal,
      @Valid @RequestBody ChangeEmailRequest request) {
    userService.requestEmailChange(principal.getId(), request.getNewEmail(), request.getCurrentPassword());
    return Map.of("message", "新しいメールアドレス宛に確認メールを送信しました。メール内のリンクから変更を完了してください。");
  }

  @PostMapping("/email-change/confirm")
  public Map<String, String> confirmEmailChange(@Valid @RequestBody ConfirmEmailChangeRequest request) {
    userService.confirmEmailChange(request.getToken());
    return Map.of("message", "メールアドレスを変更しました。");
  }
}
