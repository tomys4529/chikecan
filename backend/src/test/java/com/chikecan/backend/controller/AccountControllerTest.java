package com.chikecan.backend.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.chikecan.backend.entity.EmailChangeRequest;
import com.chikecan.backend.entity.PasswordResetToken;
import com.chikecan.backend.entity.PendingRegistration;
import com.chikecan.backend.entity.Role;
import com.chikecan.backend.entity.User;
import com.chikecan.backend.repository.EmailChangeRequestRepository;
import com.chikecan.backend.repository.PasswordResetTokenRepository;
import com.chikecan.backend.repository.PendingRegistrationRepository;
import com.chikecan.backend.repository.UserRepository;
import com.chikecan.backend.security.VerificationTokenGenerator;

import jakarta.servlet.http.Cookie;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class AccountControllerTest {

  private static final String INVALID_EMAIL_CHANGE_TOKEN_MESSAGE = "変更リンクが無効または期限切れです";

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private UserRepository userRepository;

  @Autowired
  private PendingRegistrationRepository pendingRegistrationRepository;

  @Autowired
  private EmailChangeRequestRepository emailChangeRequestRepository;

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

  private EmailChangeRequest seedEmailChangeRequest(Long userId, String newEmail, String rawToken, Instant expiresAt) {
    EmailChangeRequest request =
        new EmailChangeRequest(userId, newEmail, VerificationTokenGenerator.hash(rawToken), expiresAt);
    return emailChangeRequestRepository.saveAndFlush(request);
  }

  private PasswordResetToken seedPasswordResetToken(Long userId, String rawToken, Instant expiresAt) {
    PasswordResetToken token = new PasswordResetToken(userId, VerificationTokenGenerator.hash(rawToken), expiresAt);
    return passwordResetTokenRepository.saveAndFlush(token);
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

  /**
   * email-change/confirmはpermitAll(認可不要)だが、CSRF保護はpermitAllの対象外の
   * ため、未ログインでもCSRFトークンは必要になる。/api/auth/csrfは未ログインでも
   * 呼び出せるため、ここで取得したトークンを使ってconfirmを呼び出す。
   */
  private org.springframework.test.web.servlet.ResultActions performConfirmEmailChange(String token) throws Exception {
    MockHttpSession session = new MockHttpSession();
    CsrfCredentials csrf = obtainCsrfToken(session);
    return mockMvc.perform(post("/api/account/email-change/confirm")
        .session(session)
        .cookie(csrf.cookie())
        .header("X-XSRF-TOKEN", csrf.token())
        .contentType(MediaType.APPLICATION_JSON)
        .content(String.format("{\"token\":\"%s\"}", token)));
  }

  private void loginFails(String email, String rawPassword) throws Exception {
    MockHttpSession session = new MockHttpSession();
    CsrfCredentials csrf = obtainCsrfToken(session);
    mockMvc.perform(post("/api/auth/login")
            .session(session)
            .cookie(csrf.cookie())
            .header("X-XSRF-TOKEN", csrf.token())
            .contentType(MediaType.APPLICATION_JSON)
            .content(String.format("{\"email\":\"%s\",\"password\":\"%s\"}", email, rawPassword)))
        .andExpect(status().isUnauthorized());
  }

  // ===== パスワード変更(/api/account/password) =====

  @Test
  void 現在のパスワードが正しい場合はパスワード変更に成功する() throws Exception {
    seedUser("変更太郎", "change-ok@example.com", "OldPassw0rd1!");
    MockHttpSession session = loginAs("change-ok@example.com", "OldPassw0rd1!");
    CsrfCredentials csrf = obtainCsrfToken(session);

    mockMvc.perform(post("/api/account/password")
            .session(session)
            .cookie(csrf.cookie())
            .header("X-XSRF-TOKEN", csrf.token())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"currentPassword\":\"OldPassw0rd1!\",\"newPassword\":\"NewPassw0rd1!\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.message").exists());

    var user = userRepository.findByEmail("change-ok@example.com").orElseThrow();
    assertThat(user.getPasswordHash()).startsWith("$2");
    assertThat(passwordEncoder.matches("NewPassw0rd1!", user.getPasswordHash())).isTrue();
    assertThat(passwordEncoder.matches("OldPassw0rd1!", user.getPasswordHash())).isFalse();
  }

  @Test
  void 現在のパスワードが誤っている場合は400または401系のエラーになりパスワードが変更されない() throws Exception {
    seedUser("誤り太郎", "change-wrong@example.com", "OldPassw0rd1!");
    MockHttpSession session = loginAs("change-wrong@example.com", "OldPassw0rd1!");
    CsrfCredentials csrf = obtainCsrfToken(session);

    mockMvc.perform(post("/api/account/password")
            .session(session)
            .cookie(csrf.cookie())
            .header("X-XSRF-TOKEN", csrf.token())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"currentPassword\":\"WrongPassw0rd1!\",\"newPassword\":\"NewPassw0rd1!\"}"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.message").value("現在のパスワードが正しくありません"));

    var user = userRepository.findByEmail("change-wrong@example.com").orElseThrow();
    assertThat(passwordEncoder.matches("OldPassw0rd1!", user.getPasswordHash())).isTrue();
  }

  @Test
  void 新しいパスワードが弱い場合は400になる() throws Exception {
    seedUser("弱い太郎", "change-weak@example.com", "OldPassw0rd1!");
    MockHttpSession session = loginAs("change-weak@example.com", "OldPassw0rd1!");
    CsrfCredentials csrf = obtainCsrfToken(session);

    mockMvc.perform(post("/api/account/password")
            .session(session)
            .cookie(csrf.cookie())
            .header("X-XSRF-TOKEN", csrf.token())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"currentPassword\":\"OldPassw0rd1!\",\"newPassword\":\"weakpass\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value(
            "パスワードは8〜72文字で、大文字・小文字・数字・記号をそれぞれ1文字以上含めてください"));
  }

  @Test
  void 新しいパスワードが現在のパスワードと同じ場合は400になる() throws Exception {
    seedUser("同一太郎", "change-same@example.com", "SamePassw0rd1!");
    MockHttpSession session = loginAs("change-same@example.com", "SamePassw0rd1!");
    CsrfCredentials csrf = obtainCsrfToken(session);

    mockMvc.perform(post("/api/account/password")
            .session(session)
            .cookie(csrf.cookie())
            .header("X-XSRF-TOKEN", csrf.token())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"currentPassword\":\"SamePassw0rd1!\",\"newPassword\":\"SamePassw0rd1!\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("新しいパスワードは現在のパスワードと異なるものにしてください"));
  }

  @Test
  void パスワード変更後は旧パスワードでログインできず新パスワードでログインできる() throws Exception {
    seedUser("ログイン確認太郎", "change-login@example.com", "OldPassw0rd1!");
    MockHttpSession session = loginAs("change-login@example.com", "OldPassw0rd1!");
    CsrfCredentials csrf = obtainCsrfToken(session);

    mockMvc.perform(post("/api/account/password")
            .session(session)
            .cookie(csrf.cookie())
            .header("X-XSRF-TOKEN", csrf.token())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"currentPassword\":\"OldPassw0rd1!\",\"newPassword\":\"NewPassw0rd1!\"}"))
        .andExpect(status().isOk());

    loginFails("change-login@example.com", "OldPassw0rd1!");

    MockHttpSession newSession = new MockHttpSession();
    CsrfCredentials newCsrf = obtainCsrfToken(newSession);
    mockMvc.perform(post("/api/auth/login")
            .session(newSession)
            .cookie(newCsrf.cookie())
            .header("X-XSRF-TOKEN", newCsrf.token())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"email\":\"change-login@example.com\",\"password\":\"NewPassw0rd1!\"}"))
        .andExpect(status().isOk());
  }

  @Test
  void 他ユーザーのパスワードには影響しない() throws Exception {
    seedUser("自分太郎", "change-self@example.com", "OldPassw0rd1!");
    seedUser("他人花子", "change-other@example.com", "OtherPassw0rd1!");
    MockHttpSession session = loginAs("change-self@example.com", "OldPassw0rd1!");
    CsrfCredentials csrf = obtainCsrfToken(session);

    mockMvc.perform(post("/api/account/password")
            .session(session)
            .cookie(csrf.cookie())
            .header("X-XSRF-TOKEN", csrf.token())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"currentPassword\":\"OldPassw0rd1!\",\"newPassword\":\"NewPassw0rd1!\"}"))
        .andExpect(status().isOk());

    var other = userRepository.findByEmail("change-other@example.com").orElseThrow();
    assertThat(passwordEncoder.matches("OtherPassw0rd1!", other.getPasswordHash())).isTrue();
  }

  @Test
  void CSRFトークンなしでpassword変更を呼ぶと403になる() throws Exception {
    seedUser("CSRF太郎", "change-csrf@example.com", "OldPassw0rd1!");
    MockHttpSession session = loginAs("change-csrf@example.com", "OldPassw0rd1!");

    mockMvc.perform(post("/api/account/password")
            .session(session)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"currentPassword\":\"OldPassw0rd1!\",\"newPassword\":\"NewPassw0rd1!\"}"))
        .andExpect(status().isForbidden());
  }

  @Test
  void 未ログインでpassword変更を呼ぶと401になる() throws Exception {
    MockHttpSession session = new MockHttpSession();
    CsrfCredentials csrf = obtainCsrfToken(session);

    mockMvc.perform(post("/api/account/password")
            .session(session)
            .cookie(csrf.cookie())
            .header("X-XSRF-TOKEN", csrf.token())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"currentPassword\":\"OldPassw0rd1!\",\"newPassword\":\"NewPassw0rd1!\"}"))
        .andExpect(status().isUnauthorized());
  }

  // ===== メールアドレス変更申請(/api/account/email-change/request) =====

  @Test
  void 正常な申請でrequestが保存され生tokenは保存されずtoken_hashとexpiresAtが正しい() throws Exception {
    var user = seedUser("申請太郎", "request-ok@example.com", "Passw0rd123!");
    MockHttpSession session = loginAs("request-ok@example.com", "Passw0rd123!");
    CsrfCredentials csrf = obtainCsrfToken(session);

    mockMvc.perform(post("/api/account/email-change/request")
            .session(session)
            .cookie(csrf.cookie())
            .header("X-XSRF-TOKEN", csrf.token())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"newEmail\":\"New-Address@Example.com\",\"currentPassword\":\"Passw0rd123!\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.message").exists());

    var request = emailChangeRequestRepository.findByUserId(user.getId()).orElseThrow();
    assertThat(request.getNewEmail()).isEqualTo("new-address@example.com");
    assertThat(request.getTokenHash()).hasSize(64).matches("^[0-9a-f]{64}$");
    assertThat(request.getExpiresAt()).isAfter(Instant.now().plusSeconds(3500));
    assertThat(request.getExpiresAt()).isBefore(Instant.now().plusSeconds(3700));

    // users.emailはまだ変更されていない(確認前は現在のメールアドレスのままログイン可能)。
    assertThat(userRepository.findByEmail("request-ok@example.com")).isPresent();
  }

  @Test
  void 現在のパスワードが誤っている場合は申請が失敗する() throws Exception {
    seedUser("誤り花子", "request-wrong@example.com", "Passw0rd123!");
    MockHttpSession session = loginAs("request-wrong@example.com", "Passw0rd123!");
    CsrfCredentials csrf = obtainCsrfToken(session);

    mockMvc.perform(post("/api/account/email-change/request")
            .session(session)
            .cookie(csrf.cookie())
            .header("X-XSRF-TOKEN", csrf.token())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"newEmail\":\"new-target@example.com\",\"currentPassword\":\"WrongPassw0rd1!\"}"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.message").value("現在のパスワードが正しくありません"));
  }

  @Test
  void 現在のメールアドレスと同じ場合は申請が失敗する() throws Exception {
    seedUser("同一太郎", "request-same@example.com", "Passw0rd123!");
    MockHttpSession session = loginAs("request-same@example.com", "Passw0rd123!");
    CsrfCredentials csrf = obtainCsrfToken(session);

    mockMvc.perform(post("/api/account/email-change/request")
            .session(session)
            .cookie(csrf.cookie())
            .header("X-XSRF-TOKEN", csrf.token())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"newEmail\":\"Request-Same@Example.com\",\"currentPassword\":\"Passw0rd123!\"}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.message").value("新しいメールアドレスが現在のメールアドレスと同じです"));
  }

  @Test
  void usersで既に使われているメールアドレスへの申請は失敗する() throws Exception {
    seedUser("重複太郎", "request-dup@example.com", "Passw0rd123!");
    seedUser("既存花子", "already-taken@example.com", "AnotherPassw0rd1!");
    MockHttpSession session = loginAs("request-dup@example.com", "Passw0rd123!");
    CsrfCredentials csrf = obtainCsrfToken(session);

    mockMvc.perform(post("/api/account/email-change/request")
            .session(session)
            .cookie(csrf.cookie())
            .header("X-XSRF-TOKEN", csrf.token())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"newEmail\":\"already-taken@example.com\",\"currentPassword\":\"Passw0rd123!\"}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.message").value("このメールアドレスは既に使用されています"));
  }

  @Test
  void pendingで既に使われているメールアドレスへの申請は失敗する() throws Exception {
    seedUser("保留太郎", "request-pending@example.com", "Passw0rd123!");
    pendingRegistrationRepository.saveAndFlush(new PendingRegistration(
        "保留中ユーザー", "pending-target@example.com", "hash",
        VerificationTokenGenerator.hash("some-pending-token"), Instant.now().plusSeconds(3600)));
    MockHttpSession session = loginAs("request-pending@example.com", "Passw0rd123!");
    CsrfCredentials csrf = obtainCsrfToken(session);

    mockMvc.perform(post("/api/account/email-change/request")
            .session(session)
            .cookie(csrf.cookie())
            .header("X-XSRF-TOKEN", csrf.token())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"newEmail\":\"pending-target@example.com\",\"currentPassword\":\"Passw0rd123!\"}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.message").value("このメールアドレスは既に使用されています"));
  }

  @Test
  void 同一ユーザーが再申請すると古いtokenが無効化され新しいtokenが発行される() throws Exception {
    var user = seedUser("再申請花子", "reissue@example.com", "Passw0rd123!");
    var firstRequest = seedEmailChangeRequest(user.getId(), "first-target@example.com", "first-email-change-token",
        Instant.now().plusSeconds(1800));
    MockHttpSession session = loginAs("reissue@example.com", "Passw0rd123!");
    CsrfCredentials csrf = obtainCsrfToken(session);

    mockMvc.perform(post("/api/account/email-change/request")
            .session(session)
            .cookie(csrf.cookie())
            .header("X-XSRF-TOKEN", csrf.token())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"newEmail\":\"second-target@example.com\",\"currentPassword\":\"Passw0rd123!\"}"))
        .andExpect(status().isOk());

    long countForUser = emailChangeRequestRepository.findAll().stream()
        .filter(r -> r.getUserId().equals(user.getId()))
        .count();
    assertThat(countForUser).isEqualTo(1);

    var updated = emailChangeRequestRepository.findByUserId(user.getId()).orElseThrow();
    assertThat(updated.getId()).isEqualTo(firstRequest.getId());
    assertThat(updated.getNewEmail()).isEqualTo("second-target@example.com");
    assertThat(updated.getTokenHash()).isNotEqualTo(firstRequest.getTokenHash());

    // 古いtokenは既に無効。
    performConfirmEmailChange("first-email-change-token")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value(INVALID_EMAIL_CHANGE_TOKEN_MESSAGE));
  }

  @Test
  void CSRFトークンなしでemail_change_requestを呼ぶと403になる() throws Exception {
    seedUser("CSRF花子", "request-csrf@example.com", "Passw0rd123!");
    MockHttpSession session = loginAs("request-csrf@example.com", "Passw0rd123!");

    mockMvc.perform(post("/api/account/email-change/request")
            .session(session)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"newEmail\":\"whatever@example.com\",\"currentPassword\":\"Passw0rd123!\"}"))
        .andExpect(status().isForbidden());
  }

  @Test
  void 未ログインでemail_change_requestを呼ぶと401になる() throws Exception {
    MockHttpSession session = new MockHttpSession();
    CsrfCredentials csrf = obtainCsrfToken(session);

    mockMvc.perform(post("/api/account/email-change/request")
            .session(session)
            .cookie(csrf.cookie())
            .header("X-XSRF-TOKEN", csrf.token())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"newEmail\":\"whatever@example.com\",\"currentPassword\":\"Passw0rd123!\"}"))
        .andExpect(status().isUnauthorized());
  }

  // ===== メールアドレス変更確認(/api/account/email-change/confirm) =====

  @Test
  void 正常なtokenでusersのemailが更新され申請が削除される() throws Exception {
    var user = seedUser("確認太郎", "confirm-old@example.com", "Passw0rd123!");
    seedEmailChangeRequest(user.getId(), "confirm-new@example.com", "confirm-success-token",
        Instant.now().plusSeconds(1800));

    performConfirmEmailChange("confirm-success-token")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.message").exists());

    assertThat(userRepository.findByEmail("confirm-old@example.com")).isEmpty();
    var updatedUser = userRepository.findByEmail("confirm-new@example.com").orElseThrow();
    assertThat(updatedUser.getId()).isEqualTo(user.getId());
    assertThat(emailChangeRequestRepository.findByUserId(user.getId())).isEmpty();
  }

  @Test
  void メール変更成功時に対象ユーザーの既存パスワードリセットtokenが無効化される() throws Exception {
    var user = seedUser("旧メール太郎", "reset-invalidate-old@example.com", "Passw0rd123!");
    seedEmailChangeRequest(user.getId(), "reset-invalidate-new@example.com", "reset-invalidate-email-token",
        Instant.now().plusSeconds(1800));
    // 旧メールアドレス宛に発行済みのパスワードリセットtoken。
    seedPasswordResetToken(user.getId(), "reset-invalidate-reset-token", Instant.now().plusSeconds(1800));

    performConfirmEmailChange("reset-invalidate-email-token")
        .andExpect(status().isOk());

    assertThat(passwordResetTokenRepository.findByUserId(user.getId())).isEmpty();

    // 旧メール宛のリセットリンク(token)はもう使えない。
    CsrfCredentials csrf = obtainCsrfToken(new MockHttpSession());
    mockMvc.perform(post("/api/auth/password-reset/confirm")
            .cookie(csrf.cookie())
            .header("X-XSRF-TOKEN", csrf.token())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"token\":\"reset-invalidate-reset-token\",\"password\":\"AnotherPassw0rd1!\"}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void メール変更に失敗した場合はパスワードリセットtokenが削除されない() throws Exception {
    var user = seedUser("失敗太郎", "reset-keep-old@example.com", "Passw0rd123!");
    // 期限切れのemail変更tokenを用意し、confirmを失敗させる。
    seedEmailChangeRequest(user.getId(), "reset-keep-new@example.com", "reset-keep-expired-email-token",
        Instant.now().minusSeconds(1));
    seedPasswordResetToken(user.getId(), "reset-keep-reset-token", Instant.now().plusSeconds(1800));

    performConfirmEmailChange("reset-keep-expired-email-token")
        .andExpect(status().isBadRequest());

    assertThat(passwordResetTokenRepository.findByUserId(user.getId())).isPresent();
  }

  @Test
  void メール変更成功が他ユーザーのパスワードリセットtokenに影響しない() throws Exception {
    var targetUser = seedUser("対象太郎", "reset-target-old@example.com", "Passw0rd123!");
    var otherUser = seedUser("他人花子", "reset-other@example.com", "Passw0rd123!");
    seedEmailChangeRequest(targetUser.getId(), "reset-target-new@example.com", "reset-target-email-token",
        Instant.now().plusSeconds(1800));
    seedPasswordResetToken(otherUser.getId(), "reset-other-untouched-token", Instant.now().plusSeconds(1800));

    performConfirmEmailChange("reset-target-email-token")
        .andExpect(status().isOk());

    assertThat(passwordResetTokenRepository.findByUserId(otherUser.getId())).isPresent();
  }

  @Test
  void 確認は未ログインでも利用できる() throws Exception {
    var user = seedUser("未ログイン太郎", "no-login-old@example.com", "Passw0rd123!");
    seedEmailChangeRequest(user.getId(), "no-login-new@example.com", "no-login-confirm-token",
        Instant.now().plusSeconds(1800));

    // ログインしていない(セッションが未認証の)状態のまま、CSRFトークンだけ取得して呼び出す。
    performConfirmEmailChange("no-login-confirm-token")
        .andExpect(status().isOk());
  }

  @Test
  void CSRFトークンなしでemail_change_confirmを呼ぶと403になる() throws Exception {
    mockMvc.perform(post("/api/account/email-change/confirm")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"token\":\"any-token\"}"))
        .andExpect(status().isForbidden());
  }

  @Test
  void 変更後は古いemailで新規ログインできず新しいemailでログインできる() throws Exception {
    var user = seedUser("ログイン検証太郎", "login-change-old@example.com", "Passw0rd123!");
    seedEmailChangeRequest(user.getId(), "login-change-new@example.com", "login-change-token",
        Instant.now().plusSeconds(1800));

    performConfirmEmailChange("login-change-token")
        .andExpect(status().isOk());

    loginFails("login-change-old@example.com", "Passw0rd123!");

    MockHttpSession session = new MockHttpSession();
    CsrfCredentials csrf = obtainCsrfToken(session);
    mockMvc.perform(post("/api/auth/login")
            .session(session)
            .cookie(csrf.cookie())
            .header("X-XSRF-TOKEN", csrf.token())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"email\":\"login-change-new@example.com\",\"password\":\"Passw0rd123!\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.email").value("login-change-new@example.com"));
  }

  @Test
  void 存在しないtokenで確認すると400になる() throws Exception {
    performConfirmEmailChange("no-such-email-change-token")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value(INVALID_EMAIL_CHANGE_TOKEN_MESSAGE));
  }

  @Test
  void 期限切れtokenで確認すると400になりemailが更新されない() throws Exception {
    var user = seedUser("期限切れ太郎", "expired-old@example.com", "Passw0rd123!");
    seedEmailChangeRequest(user.getId(), "expired-new@example.com", "expired-email-change-token",
        Instant.now().minusSeconds(1));

    performConfirmEmailChange("expired-email-change-token")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value(INVALID_EMAIL_CHANGE_TOKEN_MESSAGE));

    assertThat(userRepository.findByEmail("expired-old@example.com")).isPresent();
  }

  @Test
  void 確認成功後に同じtokenを再使用すると400になる() throws Exception {
    var user = seedUser("再利用太郎", "reuse-old@example.com", "Passw0rd123!");
    seedEmailChangeRequest(user.getId(), "reuse-new@example.com", "reuse-email-change-token",
        Instant.now().plusSeconds(1800));

    performConfirmEmailChange("reuse-email-change-token")
        .andExpect(status().isOk());

    performConfirmEmailChange("reuse-email-change-token")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value(INVALID_EMAIL_CHANGE_TOKEN_MESSAGE));
  }

  @Test
  void 確認直前に別ユーザーが同じメールアドレスを取得していた場合は400になる() throws Exception {
    var user = seedUser("競合太郎", "race-old@example.com", "Passw0rd123!");
    seedUser("先に取得した人", "race-target@example.com", "AnotherPassw0rd1!");
    seedEmailChangeRequest(user.getId(), "race-target@example.com", "race-email-change-token",
        Instant.now().plusSeconds(1800));

    performConfirmEmailChange("race-email-change-token")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value(INVALID_EMAIL_CHANGE_TOKEN_MESSAGE));

    assertThat(userRepository.findByEmail("race-old@example.com")).isPresent();
  }

  @Test
  void tokenなしで確認すると400になる() throws Exception {
    performConfirmEmailChange("")
        .andExpect(status().isBadRequest());
  }
}
