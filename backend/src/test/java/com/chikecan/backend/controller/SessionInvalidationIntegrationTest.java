package com.chikecan.backend.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.chikecan.backend.entity.PasswordResetToken;
import com.chikecan.backend.entity.Role;
import com.chikecan.backend.entity.User;
import com.chikecan.backend.repository.PasswordResetTokenRepository;
import com.chikecan.backend.repository.UserRepository;
import com.chikecan.backend.security.VerificationTokenGenerator;

import jakarta.servlet.http.Cookie;

/**
 * パスワード変更・パスワードリセット完了後に、対象ユーザーの既存セッション(複数端末を含む)が
 * SessionRegistry経由で実際に認証不可になり、他ユーザーのセッションには影響しないことを
 * MockMvcで(実際のHTTPリクエストの往復として)エンドツーエンドで検証する。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class SessionInvalidationIntegrationTest {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private UserRepository userRepository;

  @Autowired
  private PasswordResetTokenRepository passwordResetTokenRepository;

  @Autowired
  private PasswordEncoder passwordEncoder;

  private record CsrfCredentials(Cookie cookie, String token) {
  }

  private CsrfCredentials obtainCsrfToken(MockHttpSession session) throws Exception {
    MvcResult result = mockMvc.perform(get("/api/auth/csrf").session(session))
        .andExpect(status().isOk())
        .andReturn();
    Cookie cookie = result.getResponse().getCookie("XSRF-TOKEN");
    assertThat(cookie).isNotNull();
    return new CsrfCredentials(cookie, cookie.getValue());
  }

  private User seedUser(String name, String email, String rawPassword) {
    return userRepository.saveAndFlush(new User(name, email, passwordEncoder.encode(rawPassword), Role.USER, true));
  }

  private MockHttpSession loginAs(String email, String rawPassword) throws Exception {
    MockHttpSession session = new MockHttpSession();
    CsrfCredentials csrf = obtainCsrfToken(session);
    mockMvc.perform(post("/api/auth/login")
            .session(session)
            .cookie(csrf.cookie())
            .header("X-XSRF-TOKEN", csrf.token())
            .contentType(MediaType.APPLICATION_JSON)
            .content(String.format("{\"email\":\"%s\",\"password\":\"%s\"}", email, rawPassword)))
        .andExpect(status().isOk());
    return session;
  }

  private PasswordResetToken seedPasswordResetToken(Long userId, String rawToken, Instant expiresAt) {
    PasswordResetToken token = new PasswordResetToken(userId, VerificationTokenGenerator.hash(rawToken), expiresAt);
    return passwordResetTokenRepository.saveAndFlush(token);
  }

  @Test
  void パスワード変更後は同一ユーザーの全セッションが認証不可になり他ユーザーのセッションは維持される() throws Exception {
    seedUser("対象太郎", "session-invalidation-target@example.com", "OldPassw0rd1!");
    seedUser("他人花子", "session-invalidation-other@example.com", "OtherPassw0rd1!");

    // 同一ユーザーが2端末でログイン(操作中のセッションを含む複数セッションを模擬)。
    MockHttpSession sessionDeviceA = loginAs("session-invalidation-target@example.com", "OldPassw0rd1!");
    MockHttpSession sessionDeviceB = loginAs("session-invalidation-target@example.com", "OldPassw0rd1!");
    MockHttpSession otherUserSession = loginAs("session-invalidation-other@example.com", "OtherPassw0rd1!");

    // 変更前はどちらの端末でも認証済みとして扱われる。
    mockMvc.perform(get("/api/auth/me").session(sessionDeviceA)).andExpect(status().isOk());
    mockMvc.perform(get("/api/auth/me").session(sessionDeviceB)).andExpect(status().isOk());

    // 端末A(操作中のセッション)からパスワードを変更する。
    CsrfCredentials changeCsrf = obtainCsrfToken(sessionDeviceA);
    mockMvc.perform(post("/api/account/password")
            .session(sessionDeviceA)
            .cookie(changeCsrf.cookie())
            .header("X-XSRF-TOKEN", changeCsrf.token())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"currentPassword\":\"OldPassw0rd1!\",\"newPassword\":\"NewPassw0rd1!\"}"))
        .andExpect(status().isOk());

    // 変更を実行した端末Aも、別端末Bも、次回アクセス時には401(要再ログイン)になる。
    mockMvc.perform(get("/api/auth/me").session(sessionDeviceA)).andExpect(status().isUnauthorized());
    mockMvc.perform(get("/api/auth/me").session(sessionDeviceB)).andExpect(status().isUnauthorized());

    // 他ユーザーのセッションには一切影響しない。
    mockMvc.perform(get("/api/auth/me").session(otherUserSession)).andExpect(status().isOk());
  }

  @Test
  void パスワードリセット完了後は対象ユーザーの既存セッションが認証不可になる() throws Exception {
    var targetUser = seedUser("リセット対象太郎", "session-invalidation-reset@example.com", "OldPassw0rd1!");
    seedUser("無関係次郎", "session-invalidation-unrelated@example.com", "UnrelatedPassw0rd1!");

    MockHttpSession existingSession = loginAs("session-invalidation-reset@example.com", "OldPassw0rd1!");
    MockHttpSession unrelatedSession = loginAs("session-invalidation-unrelated@example.com", "UnrelatedPassw0rd1!");
    mockMvc.perform(get("/api/auth/me").session(existingSession)).andExpect(status().isOk());

    seedPasswordResetToken(targetUser.getId(), "session-invalidation-reset-token", Instant.now().plusSeconds(1800));

    // パスワードリセット自体は未ログインの別セッションから行う(侵害された端末とは別の想定)。
    CsrfCredentials resetCsrf = obtainCsrfToken(new MockHttpSession());
    mockMvc.perform(post("/api/auth/password-reset/confirm")
            .cookie(resetCsrf.cookie())
            .header("X-XSRF-TOKEN", resetCsrf.token())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"token\":\"session-invalidation-reset-token\",\"password\":\"NewPassw0rd1!\"}"))
        .andExpect(status().isOk());

    // 侵害された可能性のある既存セッションは無効化される。
    mockMvc.perform(get("/api/auth/me").session(existingSession)).andExpect(status().isUnauthorized());
    // 無関係な他ユーザーのセッションには影響しない。
    mockMvc.perform(get("/api/auth/me").session(unrelatedSession)).andExpect(status().isOk());
  }
}
