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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.chikecan.backend.entity.PendingRegistration;
import com.chikecan.backend.entity.Role;
import com.chikecan.backend.entity.User;
import com.chikecan.backend.repository.PendingRegistrationRepository;
import com.chikecan.backend.repository.UserRepository;
import com.chikecan.backend.security.VerificationTokenGenerator;

import jakarta.servlet.http.Cookie;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class AuthControllerTest {

  private static final String INVALID_TOKEN_MESSAGE = "認証リンクが無効または期限切れです";
  private static final String RESEND_MESSAGE = "対象のアカウントが確認できた場合、認証メールを送信します。";

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private UserRepository userRepository;

  @Autowired
  private PendingRegistrationRepository pendingRegistrationRepository;

  @Autowired
  private PasswordEncoder passwordEncoder;

  private record CsrfCredentials(Cookie cookie, String token) {
  }

  private PendingRegistration seedPending(String name, String email, String rawPassword, String rawToken,
      Instant expiresAt) {
    PendingRegistration pending = new PendingRegistration(
        name, email, passwordEncoder.encode(rawPassword), VerificationTokenGenerator.hash(rawToken), expiresAt);
    return pendingRegistrationRepository.saveAndFlush(pending);
  }

  private User seedRegisteredUser(String name, String email, String rawPassword) {
    return userRepository.saveAndFlush(new User(name, email, passwordEncoder.encode(rawPassword), Role.USER, true));
  }

  private CsrfCredentials obtainCsrfToken() throws Exception {
    MvcResult result = mockMvc.perform(get("/api/auth/csrf"))
        .andExpect(status().isOk())
        .andReturn();
    Cookie cookie = result.getResponse().getCookie("XSRF-TOKEN");
    assertThat(cookie).isNotNull();
    return new CsrfCredentials(cookie, cookie.getValue());
  }

  @Test
  void 正常な入力で登録するとusersへは保存されずpendingへ保存されメッセージが返る() throws Exception {
    CsrfCredentials csrf = obtainCsrfToken();
    String body = """
        {"name":"山田太郎","email":"YAMADA@EXAMPLE.COM","password":"Passw0rd123!"}
        """;

    mockMvc.perform(post("/api/auth/register")
            .cookie(csrf.cookie())
            .header("X-XSRF-TOKEN", csrf.token())
            .contentType(MediaType.APPLICATION_JSON)
            .content(body))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.message").exists());

    // usersへは即時登録されない。
    assertThat(userRepository.findByEmail("yamada@example.com")).isEmpty();

    // pending_registrationsへ、ハッシュ化済みパスワードで保存される。
    var pending = pendingRegistrationRepository.findByEmail("yamada@example.com").orElseThrow();
    assertThat(pending.getName()).isEqualTo("山田太郎");
    assertThat(pending.getPasswordHash()).startsWith("$2");
    assertThat(pending.getPasswordHash()).isNotEqualTo("Passw0rd123!");
    assertThat(passwordEncoder.matches("Passw0rd123!", pending.getPasswordHash())).isTrue();
    // 生tokenがそのまま保存されていないこと(SHA-256の16進64文字であること)。
    assertThat(pending.getTokenHash()).hasSize(64).matches("^[0-9a-f]{64}$");
    assertThat(pending.getExpiresAt()).isAfter(Instant.now().plusSeconds(23 * 3600));
  }

  @Test
  void パスワードが条件を満たさない場合は400になる() throws Exception {
    CsrfCredentials csrf = obtainCsrfToken();
    String body = """
        {"name":"田中花子","email":"tanaka@example.com","password":"short1"}
        """;

    mockMvc.perform(post("/api/auth/register")
            .cookie(csrf.cookie())
            .header("X-XSRF-TOKEN", csrf.token())
            .contentType(MediaType.APPLICATION_JSON)
            .content(body))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value(400));
  }

  @Test
  void メールアドレス形式が不正な場合は400になる() throws Exception {
    CsrfCredentials csrf = obtainCsrfToken();
    String body = """
        {"name":"田中花子","email":"invalid-email","password":"Passw0rd123!"}
        """;

    mockMvc.perform(post("/api/auth/register")
            .cookie(csrf.cookie())
            .header("X-XSRF-TOKEN", csrf.token())
            .contentType(MediaType.APPLICATION_JSON)
            .content(body))
        .andExpect(status().isBadRequest());
  }

  @Test
  void 正式登録済みの同じメールアドレスで登録すると409になる() throws Exception {
    seedRegisteredUser("既存ユーザー", "already-registered@example.com", "Passw0rd123!");

    CsrfCredentials csrf = obtainCsrfToken();
    String body = """
        {"name":"佐藤二郎","email":"Already-Registered@Example.com","password":"Passw0rd123!"}
        """;

    mockMvc.perform(post("/api/auth/register")
            .cookie(csrf.cookie())
            .header("X-XSRF-TOKEN", csrf.token())
            .contentType(MediaType.APPLICATION_JSON)
            .content(body))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.status").value(409))
        .andExpect(jsonPath("$.message").value("このメールアドレスは既に登録されています"));
  }

  @Test
  void pending中の同じメールアドレスで再登録すると200になり既存pendingが更新される() throws Exception {
    CsrfCredentials firstCsrf = obtainCsrfToken();
    String firstBody = """
        {"name":"佐藤一郎","email":"duplicate-pending@example.com","password":"Passw0rd123!"}
        """;

    mockMvc.perform(post("/api/auth/register")
            .cookie(firstCsrf.cookie())
            .header("X-XSRF-TOKEN", firstCsrf.token())
            .contentType(MediaType.APPLICATION_JSON)
            .content(firstBody))
        .andExpect(status().isOk());

    var firstPending = pendingRegistrationRepository.findByEmail("duplicate-pending@example.com").orElseThrow();
    String firstTokenHash = firstPending.getTokenHash();

    CsrfCredentials secondCsrf = obtainCsrfToken();
    String secondBody = """
        {"name":"佐藤二郎","email":"Duplicate-Pending@Example.com","password":"NewPassw0rd1!"}
        """;

    // 重複エラーにはならず、既存pendingが新しい入力内容・新しいtokenへ置き換わる。
    mockMvc.perform(post("/api/auth/register")
            .cookie(secondCsrf.cookie())
            .header("X-XSRF-TOKEN", secondCsrf.token())
            .contentType(MediaType.APPLICATION_JSON)
            .content(secondBody))
        .andExpect(status().isOk());

    // 同じメールに対してpendingは1件だけであること。
    long pendingCountForEmail = pendingRegistrationRepository.findAll().stream()
        .filter(p -> p.getEmail().equals("duplicate-pending@example.com"))
        .count();
    assertThat(pendingCountForEmail).isEqualTo(1);

    var updatedPending = pendingRegistrationRepository.findByEmail("duplicate-pending@example.com").orElseThrow();
    assertThat(updatedPending.getId()).isEqualTo(firstPending.getId());
    assertThat(updatedPending.getName()).isEqualTo("佐藤二郎");
    // 古いtokenは新しいtokenへ置き換わり、既に無効になっている。
    assertThat(updatedPending.getTokenHash()).isNotEqualTo(firstTokenHash);
  }

  @Test
  void CSRFトークンなしでPOSTすると403になる() throws Exception {
    String body = """
        {"name":"無効ユーザー","email":"nocsrf@example.com","password":"Passw0rd123!"}
        """;

    mockMvc.perform(post("/api/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .content(body))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.status").value(403));
  }

  // ===== 氏名・メールアドレスの文字数制限 =====

  @Test
  void 氏名が30文字なら登録できる() throws Exception {
    CsrfCredentials csrf = obtainCsrfToken();
    String name = "あ".repeat(30);
    String body = String.format(
        "{\"name\":\"%s\",\"email\":\"name30@example.com\",\"password\":\"Passw0rd123!\"}", name);

    mockMvc.perform(post("/api/auth/register")
            .cookie(csrf.cookie())
            .header("X-XSRF-TOKEN", csrf.token())
            .contentType(MediaType.APPLICATION_JSON)
            .content(body))
        .andExpect(status().isOk());
  }

  @Test
  void 氏名が31文字だと400になる() throws Exception {
    CsrfCredentials csrf = obtainCsrfToken();
    String name = "あ".repeat(31);
    String body = String.format(
        "{\"name\":\"%s\",\"email\":\"name31@example.com\",\"password\":\"Passw0rd123!\"}", name);

    mockMvc.perform(post("/api/auth/register")
            .cookie(csrf.cookie())
            .header("X-XSRF-TOKEN", csrf.token())
            .contentType(MediaType.APPLICATION_JSON)
            .content(body))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("氏名は30文字以内で入力してください"));
  }

  @Test
  void メールアドレスが100文字以内なら登録できる() throws Exception {
    CsrfCredentials csrf = obtainCsrfToken();
    // ローカル部はRFCの実務上の上限(64文字)以内、ドメインの各ラベルも63文字以内に収めつつ
    // 合計100文字ちょうどにする。ローカル部20 + "@"(1) + (60+1+14+".com"(4))=79 = 100文字。
    String email = "a".repeat(20) + "@" + "b".repeat(60) + "." + "b".repeat(14) + ".com";
    String body = String.format(
        "{\"name\":\"メール百文字\",\"email\":\"%s\",\"password\":\"Passw0rd123!\"}", email);

    mockMvc.perform(post("/api/auth/register")
            .cookie(csrf.cookie())
            .header("X-XSRF-TOKEN", csrf.token())
            .contentType(MediaType.APPLICATION_JSON)
            .content(body))
        .andExpect(status().isOk());
  }

  @Test
  void メールアドレスが101文字だと400になる() throws Exception {
    CsrfCredentials csrf = obtainCsrfToken();
    // ローカル部20 + "@"(1) + (60+1+15+".com"(4))=80 = 101文字。
    String email = "a".repeat(20) + "@" + "b".repeat(60) + "." + "b".repeat(15) + ".com";
    String body = String.format(
        "{\"name\":\"メール百一文字\",\"email\":\"%s\",\"password\":\"Passw0rd123!\"}", email);

    mockMvc.perform(post("/api/auth/register")
            .cookie(csrf.cookie())
            .header("X-XSRF-TOKEN", csrf.token())
            .contentType(MediaType.APPLICATION_JSON)
            .content(body))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("メールアドレスは100文字以内で入力してください"));
  }

  // ===== パスワード強度 =====

  @Test
  void 大文字小文字数字記号を含むパスワードで登録できる() throws Exception {
    CsrfCredentials csrf = obtainCsrfToken();
    String body = """
        {"name":"強度太郎","email":"strong-password@example.com","password":"Password1!"}
        """;

    mockMvc.perform(post("/api/auth/register")
            .cookie(csrf.cookie())
            .header("X-XSRF-TOKEN", csrf.token())
            .contentType(MediaType.APPLICATION_JSON)
            .content(body))
        .andExpect(status().isOk());
  }

  @Test
  void パスワードに大文字がないと400になる() throws Exception {
    assertPasswordRejected("no-uppercase@example.com", "password1!");
  }

  @Test
  void パスワードに小文字がないと400になる() throws Exception {
    assertPasswordRejected("no-lowercase@example.com", "PASSWORD1!");
  }

  @Test
  void パスワードに数字がないと400になる() throws Exception {
    assertPasswordRejected("no-digit@example.com", "Password!!");
  }

  @Test
  void パスワードに記号がないと400になる() throws Exception {
    assertPasswordRejected("no-symbol@example.com", "Password1");
  }

  @Test
  void パスワードが72文字を超えると400になる() throws Exception {
    // 先頭4文字で大文字・小文字・数字・記号をすべて満たしたうえで、73文字まで埋める。
    String tooLong = "Aa1!" + "a".repeat(69);
    assertPasswordRejected("too-long-password@example.com", tooLong);
  }

  private void assertPasswordRejected(String email, String password) throws Exception {
    CsrfCredentials csrf = obtainCsrfToken();
    String body = String.format(
        "{\"name\":\"バリデーション太郎\",\"email\":\"%s\",\"password\":\"%s\"}", email, password);

    mockMvc.perform(post("/api/auth/register")
            .cookie(csrf.cookie())
            .header("X-XSRF-TOKEN", csrf.token())
            .contentType(MediaType.APPLICATION_JSON)
            .content(body))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("パスワードは8〜72文字で、大文字・小文字・数字・記号をそれぞれ1文字以上含めてください"));
  }

  // ===== メール認証(verify-email) =====

  @Test
  void 正常なtokenで認証するとusersへ登録されpendingが削除される() throws Exception {
    seedPending("認証太郎", "verify-success@example.com", "Passw0rd123!", "verify-success-token",
        Instant.now().plusSeconds(3600));

    CsrfCredentials csrf = obtainCsrfToken();
    mockMvc.perform(post("/api/auth/verify-email")
            .cookie(csrf.cookie())
            .header("X-XSRF-TOKEN", csrf.token())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"token\":\"verify-success-token\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.message").exists());

    var user = userRepository.findByEmail("verify-success@example.com").orElseThrow();
    assertThat(user.getName()).isEqualTo("認証太郎");
    assertThat(user.getRole()).isEqualTo(Role.USER);
    assertThat(user.isEnabled()).isTrue();
    assertThat(passwordEncoder.matches("Passw0rd123!", user.getPasswordHash())).isTrue();

    assertThat(pendingRegistrationRepository.findByEmail("verify-success@example.com")).isEmpty();
  }

  @Test
  void 存在しないtokenで認証すると400になる() throws Exception {
    CsrfCredentials csrf = obtainCsrfToken();
    mockMvc.perform(post("/api/auth/verify-email")
            .cookie(csrf.cookie())
            .header("X-XSRF-TOKEN", csrf.token())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"token\":\"no-such-token\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value(INVALID_TOKEN_MESSAGE));
  }

  @Test
  void 期限切れtokenで認証すると400になりusersへ登録されない() throws Exception {
    seedPending("期限切れ太郎", "expired@example.com", "Passw0rd123!", "expired-token",
        Instant.now().minusSeconds(1));

    CsrfCredentials csrf = obtainCsrfToken();
    mockMvc.perform(post("/api/auth/verify-email")
            .cookie(csrf.cookie())
            .header("X-XSRF-TOKEN", csrf.token())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"token\":\"expired-token\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value(INVALID_TOKEN_MESSAGE));

    assertThat(userRepository.findByEmail("expired@example.com")).isEmpty();
  }

  @Test
  void 認証成功後に同じtokenを再使用すると400になる() throws Exception {
    seedPending("再利用太郎", "reuse@example.com", "Passw0rd123!", "reuse-token", Instant.now().plusSeconds(3600));

    CsrfCredentials firstCsrf = obtainCsrfToken();
    mockMvc.perform(post("/api/auth/verify-email")
            .cookie(firstCsrf.cookie())
            .header("X-XSRF-TOKEN", firstCsrf.token())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"token\":\"reuse-token\"}"))
        .andExpect(status().isOk());

    CsrfCredentials secondCsrf = obtainCsrfToken();
    mockMvc.perform(post("/api/auth/verify-email")
            .cookie(secondCsrf.cookie())
            .header("X-XSRF-TOKEN", secondCsrf.token())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"token\":\"reuse-token\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value(INVALID_TOKEN_MESSAGE));
  }

  @Test
  void tokenなしでverify_emailを呼ぶと400になる() throws Exception {
    CsrfCredentials csrf = obtainCsrfToken();
    mockMvc.perform(post("/api/auth/verify-email")
            .cookie(csrf.cookie())
            .header("X-XSRF-TOKEN", csrf.token())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"token\":\"\"}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void CSRFトークンなしでverify_emailを呼ぶと403になる() throws Exception {
    mockMvc.perform(post("/api/auth/verify-email")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"token\":\"any-token\"}"))
        .andExpect(status().isForbidden());
  }

  // ===== 認証メール再送(resend-verification) =====

  @Test
  void pendingが存在する場合の再送は200になり新しいtokenが発行される() throws Exception {
    var pending = seedPending("再送太郎", "resend-pending@example.com", "Passw0rd123!", "old-resend-token",
        Instant.now().plusSeconds(60));
    String oldTokenHash = pending.getTokenHash();

    CsrfCredentials csrf = obtainCsrfToken();
    mockMvc.perform(post("/api/auth/resend-verification")
            .cookie(csrf.cookie())
            .header("X-XSRF-TOKEN", csrf.token())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"email\":\"resend-pending@example.com\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.message").value(RESEND_MESSAGE));

    var updated = pendingRegistrationRepository.findByEmail("resend-pending@example.com").orElseThrow();
    assertThat(updated.getTokenHash()).isNotEqualTo(oldTokenHash);
    assertThat(updated.getExpiresAt()).isAfter(Instant.now().plusSeconds(23 * 3600));

    // 古いtokenは既に無効(見つからない)。
    mockMvc.perform(post("/api/auth/verify-email")
            .cookie(csrf.cookie())
            .header("X-XSRF-TOKEN", csrf.token())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"token\":\"old-resend-token\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value(INVALID_TOKEN_MESSAGE));
  }

  @Test
  void 正式登録済みユーザーへの再送要求も未登録と同じ一般化されたレスポンスになる() throws Exception {
    seedRegisteredUser("登録済み太郎", "resend-registered@example.com", "Passw0rd123!");

    CsrfCredentials csrf = obtainCsrfToken();
    mockMvc.perform(post("/api/auth/resend-verification")
            .cookie(csrf.cookie())
            .header("X-XSRF-TOKEN", csrf.token())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"email\":\"resend-registered@example.com\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.message").value(RESEND_MESSAGE));
  }

  @Test
  void 存在しないメールアドレスへの再送要求も同じ一般化されたレスポンスになる() throws Exception {
    CsrfCredentials csrf = obtainCsrfToken();
    mockMvc.perform(post("/api/auth/resend-verification")
            .cookie(csrf.cookie())
            .header("X-XSRF-TOKEN", csrf.token())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"email\":\"nobody-here@example.com\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.message").value(RESEND_MESSAGE));
  }

  @Test
  void CSRFトークンなしでresend_verificationを呼ぶと403になる() throws Exception {
    mockMvc.perform(post("/api/auth/resend-verification")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"email\":\"someone@example.com\"}"))
        .andExpect(status().isForbidden());
  }

  // ===== 未認証(pending)ユーザーのログイン可否 =====

  @Test
  void pending中の未認証ユーザーはログインできない() throws Exception {
    seedPending("未認証太郎", "not-yet-verified@example.com", "Passw0rd123!", "login-block-token",
        Instant.now().plusSeconds(3600));

    CsrfCredentials csrf = obtainCsrfToken();
    mockMvc.perform(post("/api/auth/login")
            .cookie(csrf.cookie())
            .header("X-XSRF-TOKEN", csrf.token())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"email\":\"not-yet-verified@example.com\",\"password\":\"Passw0rd123!\"}"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.message").value("メールアドレスまたはパスワードが正しくありません"));
  }

  @Test
  void 認証成功後はログイン可能になる() throws Exception {
    seedPending("ログイン太郎", "login-after-verify@example.com", "Passw0rd123!", "login-after-verify-token",
        Instant.now().plusSeconds(3600));

    CsrfCredentials verifyCsrf = obtainCsrfToken();
    mockMvc.perform(post("/api/auth/verify-email")
            .cookie(verifyCsrf.cookie())
            .header("X-XSRF-TOKEN", verifyCsrf.token())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"token\":\"login-after-verify-token\"}"))
        .andExpect(status().isOk());

    CsrfCredentials loginCsrf = obtainCsrfToken();
    mockMvc.perform(post("/api/auth/login")
            .cookie(loginCsrf.cookie())
            .header("X-XSRF-TOKEN", loginCsrf.token())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"email\":\"login-after-verify@example.com\",\"password\":\"Passw0rd123!\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.email").value("login-after-verify@example.com"))
        .andExpect(jsonPath("$.enabled").value(true));
  }
}
