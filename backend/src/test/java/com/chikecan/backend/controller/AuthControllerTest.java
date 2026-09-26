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

import com.chikecan.backend.entity.PasswordResetToken;
import com.chikecan.backend.entity.PendingRegistration;
import com.chikecan.backend.entity.Role;
import com.chikecan.backend.entity.User;
import com.chikecan.backend.repository.PasswordResetTokenRepository;
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
  private static final String INVALID_PASSWORD_RESET_TOKEN_MESSAGE = "再設定リンクが無効または期限切れです";
  private static final String PASSWORD_RESET_REQUEST_MESSAGE = "対象のアカウントが確認できた場合、パスワード再設定メールを送信します。";

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private UserRepository userRepository;

  @Autowired
  private PendingRegistrationRepository pendingRegistrationRepository;

  @Autowired
  private PasswordResetTokenRepository passwordResetTokenRepository;

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

  private PasswordResetToken seedPasswordResetToken(Long userId, String rawToken, Instant expiresAt) {
    PasswordResetToken token = new PasswordResetToken(userId, VerificationTokenGenerator.hash(rawToken), expiresAt);
    return passwordResetTokenRepository.saveAndFlush(token);
  }

  private CsrfCredentials obtainCsrfToken() throws Exception {
    MvcResult result = mockMvc.perform(get("/api/auth/csrf"))
        .andExpect(status().isOk())
        .andReturn();
    Cookie cookie = result.getResponse().getCookie("XSRF-TOKEN");
    assertThat(cookie).isNotNull();
    return new CsrfCredentials(cookie, cookie.getValue());
  }

  /** 日本向け(familyName/givenNameのみ)の登録リクエストJSONを組み立てる。 */
  private String japaneseRegisterBody(String familyName, String givenName, String email, String password) {
    return String.format(
        "{\"nameFormat\":\"JAPANESE\",\"familyName\":\"%s\",\"givenName\":\"%s\",\"email\":\"%s\",\"password\":\"%s\"}",
        familyName, givenName, email, password);
  }

  /** 海外向け(familyName/givenName/middleName)の登録リクエストJSONを組み立てる。 */
  private String internationalRegisterBody(String familyName, String givenName, String middleName, String email,
      String password) {
    return String.format(
        "{\"nameFormat\":\"INTERNATIONAL\",\"familyName\":\"%s\",\"givenName\":\"%s\",\"middleName\":%s,"
            + "\"email\":\"%s\",\"password\":\"%s\"}",
        familyName, givenName, middleName == null ? "null" : "\"" + middleName + "\"", email, password);
  }

  @Test
  void 正常な入力で登録するとusersへは保存されずpendingへ保存されメッセージが返る() throws Exception {
    CsrfCredentials csrf = obtainCsrfToken();
    String body = japaneseRegisterBody("山田", "太郎", "YAMADA@EXAMPLE.COM", "Passw0rd123!");

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
    assertThat(pending.getName()).isEqualTo("山田 太郎");
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
    String body = japaneseRegisterBody("田中", "花子", "tanaka@example.com", "short1");

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
    String body = japaneseRegisterBody("田中", "花子", "invalid-email", "Passw0rd123!");

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
    String body = japaneseRegisterBody("佐藤", "二郎", "Already-Registered@Example.com", "Passw0rd123!");

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
    String firstBody = japaneseRegisterBody("佐藤", "一郎", "duplicate-pending@example.com", "Passw0rd123!");

    mockMvc.perform(post("/api/auth/register")
            .cookie(firstCsrf.cookie())
            .header("X-XSRF-TOKEN", firstCsrf.token())
            .contentType(MediaType.APPLICATION_JSON)
            .content(firstBody))
        .andExpect(status().isOk());

    var firstPending = pendingRegistrationRepository.findByEmail("duplicate-pending@example.com").orElseThrow();
    String firstTokenHash = firstPending.getTokenHash();

    CsrfCredentials secondCsrf = obtainCsrfToken();
    String secondBody = japaneseRegisterBody("佐藤", "二郎", "Duplicate-Pending@Example.com", "NewPassw0rd1!");

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
    assertThat(updatedPending.getName()).isEqualTo("佐藤 二郎");
    // 古いtokenは新しいtokenへ置き換わり、既に無効になっている。
    assertThat(updatedPending.getTokenHash()).isNotEqualTo(firstTokenHash);
  }

  @Test
  void CSRFトークンなしでPOSTすると403になる() throws Exception {
    String body = japaneseRegisterBody("無効", "ユーザー", "nocsrf@example.com", "Passw0rd123!");

    mockMvc.perform(post("/api/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .content(body))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.status").value(403));
  }

  // ===== 氏名の入力形式・クロスフィールド検証 =====

  @Test
  void JAPANESEでmiddleNameを指定しなくても登録できる() throws Exception {
    CsrfCredentials csrf = obtainCsrfToken();
    String body = japaneseRegisterBody("高橋", "健一", "japanese-ok@example.com", "Passw0rd123!");

    mockMvc.perform(post("/api/auth/register")
            .cookie(csrf.cookie())
            .header("X-XSRF-TOKEN", csrf.token())
            .contentType(MediaType.APPLICATION_JSON)
            .content(body))
        .andExpect(status().isOk());

    var pending = pendingRegistrationRepository.findByEmail("japanese-ok@example.com").orElseThrow();
    assertThat(pending.getFamilyName()).isEqualTo("高橋");
    assertThat(pending.getGivenName()).isEqualTo("健一");
  }

  @Test
  void JAPANESEでfamilyNameがないと400になる() throws Exception {
    CsrfCredentials csrf = obtainCsrfToken();
    String body = "{\"nameFormat\":\"JAPANESE\",\"givenName\":\"健一\","
        + "\"email\":\"japanese-no-family@example.com\",\"password\":\"Passw0rd123!\"}";

    mockMvc.perform(post("/api/auth/register")
            .cookie(csrf.cookie())
            .header("X-XSRF-TOKEN", csrf.token())
            .contentType(MediaType.APPLICATION_JSON)
            .content(body))
        .andExpect(status().isBadRequest());
  }

  @Test
  void JAPANESEでgivenNameがないと400になる() throws Exception {
    CsrfCredentials csrf = obtainCsrfToken();
    String body = "{\"nameFormat\":\"JAPANESE\",\"familyName\":\"高橋\","
        + "\"email\":\"japanese-no-given@example.com\",\"password\":\"Passw0rd123!\"}";

    mockMvc.perform(post("/api/auth/register")
            .cookie(csrf.cookie())
            .header("X-XSRF-TOKEN", csrf.token())
            .contentType(MediaType.APPLICATION_JSON)
            .content(body))
        .andExpect(status().isBadRequest());
  }

  @Test
  void familyNameが31文字だと400になる() throws Exception {
    CsrfCredentials csrf = obtainCsrfToken();
    String tooLongFamilyName = "あ".repeat(31);
    String body = japaneseRegisterBody(tooLongFamilyName, "太郎", "family-31@example.com", "Passw0rd123!");

    mockMvc.perform(post("/api/auth/register")
            .cookie(csrf.cookie())
            .header("X-XSRF-TOKEN", csrf.token())
            .contentType(MediaType.APPLICATION_JSON)
            .content(body))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("姓は30文字以内で入力してください"));
  }

  @Test
  void givenNameが31文字だと400になる() throws Exception {
    CsrfCredentials csrf = obtainCsrfToken();
    String tooLongGivenName = "あ".repeat(31);
    String body = japaneseRegisterBody("山田", tooLongGivenName, "given-31@example.com", "Passw0rd123!");

    mockMvc.perform(post("/api/auth/register")
            .cookie(csrf.cookie())
            .header("X-XSRF-TOKEN", csrf.token())
            .contentType(MediaType.APPLICATION_JSON)
            .content(body))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("名は30文字以内で入力してください"));
  }

  @Test
  void INTERNATIONALでmiddleNameなしでも登録できる() throws Exception {
    CsrfCredentials csrf = obtainCsrfToken();
    String body = internationalRegisterBody("Smith", "John", null, "international-no-middle@example.com",
        "Passw0rd123!");

    mockMvc.perform(post("/api/auth/register")
            .cookie(csrf.cookie())
            .header("X-XSRF-TOKEN", csrf.token())
            .contentType(MediaType.APPLICATION_JSON)
            .content(body))
        .andExpect(status().isOk());

    var pending = pendingRegistrationRepository.findByEmail("international-no-middle@example.com").orElseThrow();
    assertThat(pending.getFamilyName()).isEqualTo("Smith");
    assertThat(pending.getGivenName()).isEqualTo("John");
    assertThat(pending.getMiddleName()).isNull();
    assertThat(pending.getName()).isEqualTo("John Smith");
  }

  @Test
  void INTERNATIONALでmiddleNameありで登録できる() throws Exception {
    CsrfCredentials csrf = obtainCsrfToken();
    String body = internationalRegisterBody("Smith", "John", "Michael", "international-with-middle@example.com",
        "Passw0rd123!");

    mockMvc.perform(post("/api/auth/register")
            .cookie(csrf.cookie())
            .header("X-XSRF-TOKEN", csrf.token())
            .contentType(MediaType.APPLICATION_JSON)
            .content(body))
        .andExpect(status().isOk());

    var pending = pendingRegistrationRepository.findByEmail("international-with-middle@example.com").orElseThrow();
    assertThat(pending.getMiddleName()).isEqualTo("Michael");
    assertThat(pending.getName()).isEqualTo("John Michael Smith");
  }

  @Test
  void INTERNATIONALでgivenNameがないと400になる() throws Exception {
    CsrfCredentials csrf = obtainCsrfToken();
    String body = "{\"nameFormat\":\"INTERNATIONAL\",\"familyName\":\"Smith\","
        + "\"email\":\"international-no-given@example.com\",\"password\":\"Passw0rd123!\"}";

    mockMvc.perform(post("/api/auth/register")
            .cookie(csrf.cookie())
            .header("X-XSRF-TOKEN", csrf.token())
            .contentType(MediaType.APPLICATION_JSON)
            .content(body))
        .andExpect(status().isBadRequest());
  }

  @Test
  void INTERNATIONALでfamilyNameがないと400になる() throws Exception {
    CsrfCredentials csrf = obtainCsrfToken();
    String body = "{\"nameFormat\":\"INTERNATIONAL\",\"givenName\":\"John\","
        + "\"email\":\"international-no-family@example.com\",\"password\":\"Passw0rd123!\"}";

    mockMvc.perform(post("/api/auth/register")
            .cookie(csrf.cookie())
            .header("X-XSRF-TOKEN", csrf.token())
            .contentType(MediaType.APPLICATION_JSON)
            .content(body))
        .andExpect(status().isBadRequest());
  }

  @Test
  void middleNameが31文字だと400になる() throws Exception {
    CsrfCredentials csrf = obtainCsrfToken();
    String tooLongMiddleName = "a".repeat(31);
    String body = internationalRegisterBody("Smith", "John", tooLongMiddleName, "middle-31@example.com",
        "Passw0rd123!");

    mockMvc.perform(post("/api/auth/register")
            .cookie(csrf.cookie())
            .header("X-XSRF-TOKEN", csrf.token())
            .contentType(MediaType.APPLICATION_JSON)
            .content(body))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("ミドルネームは30文字以内で入力してください"));
  }

  @Test
  void nameFormatにLEGACYを指定すると400になる() throws Exception {
    CsrfCredentials csrf = obtainCsrfToken();
    String body = "{\"nameFormat\":\"LEGACY\",\"familyName\":\"山田\",\"givenName\":\"太郎\","
        + "\"email\":\"legacy-not-allowed@example.com\",\"password\":\"Passw0rd123!\"}";

    mockMvc.perform(post("/api/auth/register")
            .cookie(csrf.cookie())
            .header("X-XSRF-TOKEN", csrf.token())
            .contentType(MediaType.APPLICATION_JSON)
            .content(body))
        .andExpect(status().isBadRequest());
  }

  @Test
  void nameFormatがないと400になる() throws Exception {
    CsrfCredentials csrf = obtainCsrfToken();
    String body = "{\"familyName\":\"山田\",\"givenName\":\"太郎\","
        + "\"email\":\"no-name-format@example.com\",\"password\":\"Passw0rd123!\"}";

    mockMvc.perform(post("/api/auth/register")
            .cookie(csrf.cookie())
            .header("X-XSRF-TOKEN", csrf.token())
            .contentType(MediaType.APPLICATION_JSON)
            .content(body))
        .andExpect(status().isBadRequest());
  }

  // ===== メールアドレスの文字数制限 =====

  @Test
  void メールアドレスが100文字以内なら登録できる() throws Exception {
    CsrfCredentials csrf = obtainCsrfToken();
    // ローカル部はRFCの実務上の上限(64文字)以内、ドメインの各ラベルも63文字以内に収めつつ
    // 合計100文字ちょうどにする。ローカル部20 + "@"(1) + (60+1+14+".com"(4))=79 = 100文字。
    String email = "a".repeat(20) + "@" + "b".repeat(60) + "." + "b".repeat(14) + ".com";
    String body = japaneseRegisterBody("メール", "百文字", email, "Passw0rd123!");

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
    String body = japaneseRegisterBody("メール", "百一文字", email, "Passw0rd123!");

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
    String body = japaneseRegisterBody("強度", "太郎", "strong-password@example.com", "Password1!");

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
    String body = japaneseRegisterBody("バリデーション", "太郎", email, password);

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

  // ===== パスワードリセット要求(password-reset/request) =====

  @Test
  void 登録済みメールアドレスへのリセット要求は200になりtokenが保存される() throws Exception {
    seedRegisteredUser("リセット太郎", "reset-request@example.com", "OldPassw0rd1!");

    CsrfCredentials csrf = obtainCsrfToken();
    mockMvc.perform(post("/api/auth/password-reset/request")
            .cookie(csrf.cookie())
            .header("X-XSRF-TOKEN", csrf.token())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"email\":\"Reset-Request@Example.com\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.message").value(PASSWORD_RESET_REQUEST_MESSAGE));

    var user = userRepository.findByEmail("reset-request@example.com").orElseThrow();
    var token = passwordResetTokenRepository.findByUserId(user.getId()).orElseThrow();
    // 生tokenがそのまま保存されていないこと(SHA-256の16進64文字であること)。
    assertThat(token.getTokenHash()).hasSize(64).matches("^[0-9a-f]{64}$");
    assertThat(token.getExpiresAt()).isAfter(Instant.now().plusSeconds(3500));
    assertThat(token.getExpiresAt()).isBefore(Instant.now().plusSeconds(3700));
  }

  @Test
  void 未登録メールアドレスへのリセット要求も同じ一般化されたレスポンスになる() throws Exception {
    CsrfCredentials csrf = obtainCsrfToken();
    mockMvc.perform(post("/api/auth/password-reset/request")
            .cookie(csrf.cookie())
            .header("X-XSRF-TOKEN", csrf.token())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"email\":\"unknown-for-reset@example.com\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.message").value(PASSWORD_RESET_REQUEST_MESSAGE));
  }

  @Test
  void 同一ユーザーが再度リセット要求すると古いtokenが無効化され新しいtokenが発行される() throws Exception {
    var user = seedRegisteredUser("再要求花子", "reissue-request@example.com", "OldPassw0rd1!");
    var firstToken = seedPasswordResetToken(user.getId(), "first-reset-token", Instant.now().plusSeconds(1800));

    CsrfCredentials csrf = obtainCsrfToken();
    mockMvc.perform(post("/api/auth/password-reset/request")
            .cookie(csrf.cookie())
            .header("X-XSRF-TOKEN", csrf.token())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"email\":\"reissue-request@example.com\"}"))
        .andExpect(status().isOk());

    // 同一ユーザーに対してtokenは1件だけであること。
    long tokenCountForUser = passwordResetTokenRepository.findAll().stream()
        .filter(t -> t.getUserId().equals(user.getId()))
        .count();
    assertThat(tokenCountForUser).isEqualTo(1);

    var updatedToken = passwordResetTokenRepository.findByUserId(user.getId()).orElseThrow();
    assertThat(updatedToken.getId()).isEqualTo(firstToken.getId());
    assertThat(updatedToken.getTokenHash()).isNotEqualTo(firstToken.getTokenHash());

    // 古いtokenは既に無効(confirmすると失敗する)。
    CsrfCredentials confirmCsrf = obtainCsrfToken();
    mockMvc.perform(post("/api/auth/password-reset/confirm")
            .cookie(confirmCsrf.cookie())
            .header("X-XSRF-TOKEN", confirmCsrf.token())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"token\":\"first-reset-token\",\"password\":\"NewPassw0rd1!\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value(INVALID_PASSWORD_RESET_TOKEN_MESSAGE));
  }

  @Test
  void CSRFトークンなしでpassword_reset_requestを呼ぶと403になる() throws Exception {
    mockMvc.perform(post("/api/auth/password-reset/request")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"email\":\"someone@example.com\"}"))
        .andExpect(status().isForbidden());
  }

  // ===== パスワードリセット確認(password-reset/confirm) =====

  @Test
  void 正常なtokenでパスワードを変更するとusersのpassword_hashが更新されtokenが削除される() throws Exception {
    var user = seedRegisteredUser("確認花子", "confirm-success@example.com", "OldPassw0rd1!");
    seedPasswordResetToken(user.getId(), "confirm-success-token", Instant.now().plusSeconds(1800));

    CsrfCredentials csrf = obtainCsrfToken();
    mockMvc.perform(post("/api/auth/password-reset/confirm")
            .cookie(csrf.cookie())
            .header("X-XSRF-TOKEN", csrf.token())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"token\":\"confirm-success-token\",\"password\":\"NewPassw0rd1!\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.message").exists());

    var updatedUser = userRepository.findByEmail("confirm-success@example.com").orElseThrow();
    assertThat(passwordEncoder.matches("NewPassw0rd1!", updatedUser.getPasswordHash())).isTrue();
    assertThat(passwordEncoder.matches("OldPassw0rd1!", updatedUser.getPasswordHash())).isFalse();
    assertThat(passwordResetTokenRepository.findByUserId(user.getId())).isEmpty();
  }

  @Test
  void 存在しないtokenで確認すると400になる() throws Exception {
    CsrfCredentials csrf = obtainCsrfToken();
    mockMvc.perform(post("/api/auth/password-reset/confirm")
            .cookie(csrf.cookie())
            .header("X-XSRF-TOKEN", csrf.token())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"token\":\"no-such-reset-token\",\"password\":\"NewPassw0rd1!\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value(INVALID_PASSWORD_RESET_TOKEN_MESSAGE));
  }

  @Test
  void 期限切れtokenで確認すると400になりパスワードが更新されない() throws Exception {
    var user = seedRegisteredUser("期限切れ花子", "expired-reset@example.com", "OldPassw0rd1!");
    seedPasswordResetToken(user.getId(), "expired-reset-token", Instant.now().minusSeconds(1));

    CsrfCredentials csrf = obtainCsrfToken();
    mockMvc.perform(post("/api/auth/password-reset/confirm")
            .cookie(csrf.cookie())
            .header("X-XSRF-TOKEN", csrf.token())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"token\":\"expired-reset-token\",\"password\":\"NewPassw0rd1!\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value(INVALID_PASSWORD_RESET_TOKEN_MESSAGE));

    var unchangedUser = userRepository.findByEmail("expired-reset@example.com").orElseThrow();
    assertThat(passwordEncoder.matches("OldPassw0rd1!", unchangedUser.getPasswordHash())).isTrue();
  }

  @Test
  void 確認成功後に同じtokenを再使用すると400になる() throws Exception {
    var user = seedRegisteredUser("再利用太郎", "reset-reuse@example.com", "OldPassw0rd1!");
    seedPasswordResetToken(user.getId(), "reset-reuse-token", Instant.now().plusSeconds(1800));

    CsrfCredentials firstCsrf = obtainCsrfToken();
    mockMvc.perform(post("/api/auth/password-reset/confirm")
            .cookie(firstCsrf.cookie())
            .header("X-XSRF-TOKEN", firstCsrf.token())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"token\":\"reset-reuse-token\",\"password\":\"NewPassw0rd1!\"}"))
        .andExpect(status().isOk());

    CsrfCredentials secondCsrf = obtainCsrfToken();
    mockMvc.perform(post("/api/auth/password-reset/confirm")
            .cookie(secondCsrf.cookie())
            .header("X-XSRF-TOKEN", secondCsrf.token())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"token\":\"reset-reuse-token\",\"password\":\"AnotherPassw0rd1!\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value(INVALID_PASSWORD_RESET_TOKEN_MESSAGE));
  }

  @Test
  void 弱いパスワードで確認すると400になりパスワードが更新されない() throws Exception {
    var user = seedRegisteredUser("弱い太郎", "weak-reset@example.com", "OldPassw0rd1!");
    seedPasswordResetToken(user.getId(), "weak-reset-token", Instant.now().plusSeconds(1800));

    CsrfCredentials csrf = obtainCsrfToken();
    mockMvc.perform(post("/api/auth/password-reset/confirm")
            .cookie(csrf.cookie())
            .header("X-XSRF-TOKEN", csrf.token())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"token\":\"weak-reset-token\",\"password\":\"weakpass\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value(
            "パスワードは8〜72文字で、大文字・小文字・数字・記号をそれぞれ1文字以上含めてください"));

    var unchangedUser = userRepository.findByEmail("weak-reset@example.com").orElseThrow();
    assertThat(passwordEncoder.matches("OldPassw0rd1!", unchangedUser.getPasswordHash())).isTrue();
    // バリデーションで弾かれ、tokenはまだ使用されていない。
    assertThat(passwordResetTokenRepository.findByUserId(user.getId())).isPresent();
  }

  @Test
  void パスワードが72文字を超える場合は400になる() throws Exception {
    var user = seedRegisteredUser("超過太郎", "too-long-reset@example.com", "OldPassw0rd1!");
    seedPasswordResetToken(user.getId(), "too-long-reset-token", Instant.now().plusSeconds(1800));
    String tooLong = "Aa1!" + "a".repeat(69);

    CsrfCredentials csrf = obtainCsrfToken();
    mockMvc.perform(post("/api/auth/password-reset/confirm")
            .cookie(csrf.cookie())
            .header("X-XSRF-TOKEN", csrf.token())
            .contentType(MediaType.APPLICATION_JSON)
            .content(String.format("{\"token\":\"too-long-reset-token\",\"password\":\"%s\"}", tooLong)))
        .andExpect(status().isBadRequest());
  }

  @Test
  void CSRFトークンなしでpassword_reset_confirmを呼ぶと403になる() throws Exception {
    mockMvc.perform(post("/api/auth/password-reset/confirm")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"token\":\"any-token\",\"password\":\"NewPassw0rd1!\"}"))
        .andExpect(status().isForbidden());
  }

  // ===== パスワード変更後のログイン可否 =====

  @Test
  void パスワード変更後は旧パスワードでログインできず新パスワードでログインできる() throws Exception {
    seedRegisteredUser("ログイン確認太郎", "login-after-reset@example.com", "OldPassw0rd1!");

    CsrfCredentials requestCsrf = obtainCsrfToken();
    mockMvc.perform(post("/api/auth/password-reset/request")
            .cookie(requestCsrf.cookie())
            .header("X-XSRF-TOKEN", requestCsrf.token())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"email\":\"login-after-reset@example.com\"}"))
        .andExpect(status().isOk());

    var user = userRepository.findByEmail("login-after-reset@example.com").orElseThrow();
    // 実際に発行された生tokenはメール送信(ローカルはログ出力)経由でしか得られないため、
    // テストでは同じ仕組み(token_hash上書き)を使い、既知の生tokenへ差し替えて検証する。
    var resetToken = passwordResetTokenRepository.findByUserId(user.getId()).orElseThrow();
    resetToken.setTokenHash(VerificationTokenGenerator.hash("known-reset-token-for-login-test"));
    passwordResetTokenRepository.saveAndFlush(resetToken);

    CsrfCredentials confirmCsrf = obtainCsrfToken();
    mockMvc.perform(post("/api/auth/password-reset/confirm")
            .cookie(confirmCsrf.cookie())
            .header("X-XSRF-TOKEN", confirmCsrf.token())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"token\":\"known-reset-token-for-login-test\",\"password\":\"NewPassw0rd1!\"}"))
        .andExpect(status().isOk());

    CsrfCredentials oldLoginCsrf = obtainCsrfToken();
    mockMvc.perform(post("/api/auth/login")
            .cookie(oldLoginCsrf.cookie())
            .header("X-XSRF-TOKEN", oldLoginCsrf.token())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"email\":\"login-after-reset@example.com\",\"password\":\"OldPassw0rd1!\"}"))
        .andExpect(status().isUnauthorized());

    CsrfCredentials newLoginCsrf = obtainCsrfToken();
    mockMvc.perform(post("/api/auth/login")
            .cookie(newLoginCsrf.cookie())
            .header("X-XSRF-TOKEN", newLoginCsrf.token())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"email\":\"login-after-reset@example.com\",\"password\":\"NewPassw0rd1!\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.email").value("login-after-reset@example.com"));
  }
}
