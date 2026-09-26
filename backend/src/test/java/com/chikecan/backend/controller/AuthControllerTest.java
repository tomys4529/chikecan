package com.chikecan.backend.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.chikecan.backend.repository.UserRepository;

import jakarta.servlet.http.Cookie;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class AuthControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private UserRepository userRepository;

  @Autowired
  private PasswordEncoder passwordEncoder;

  private record CsrfCredentials(Cookie cookie, String token) {
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
  void 正常な入力で登録すると201でパスワードハッシュを含まないレスポンスが返る() throws Exception {
    CsrfCredentials csrf = obtainCsrfToken();
    String body = """
        {"name":"山田太郎","email":"YAMADA@EXAMPLE.COM","password":"Passw0rd123!"}
        """;

    mockMvc.perform(post("/api/auth/register")
            .cookie(csrf.cookie())
            .header("X-XSRF-TOKEN", csrf.token())
            .contentType(MediaType.APPLICATION_JSON)
            .content(body))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.email").value("yamada@example.com"))
        .andExpect(jsonPath("$.role").value("USER"))
        .andExpect(jsonPath("$.enabled").value(true))
        .andExpect(content().string(not(org.hamcrest.Matchers.containsString("password"))));

    var saved = userRepository.findByEmail("yamada@example.com").orElseThrow();
    assertThat(saved.getPasswordHash()).startsWith("$2");
    assertThat(saved.getPasswordHash()).isNotEqualTo("Passw0rd123!");
    assertThat(passwordEncoder.matches("Passw0rd123!", saved.getPasswordHash())).isTrue();
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
  void 同じメールアドレスを重複登録すると409になる() throws Exception {
    CsrfCredentials firstCsrf = obtainCsrfToken();
    String body = """
        {"name":"佐藤一郎","email":"duplicate-user@example.com","password":"Passw0rd123!"}
        """;

    mockMvc.perform(post("/api/auth/register")
            .cookie(firstCsrf.cookie())
            .header("X-XSRF-TOKEN", firstCsrf.token())
            .contentType(MediaType.APPLICATION_JSON)
            .content(body))
        .andExpect(status().isCreated());

    CsrfCredentials secondCsrf = obtainCsrfToken();
    String secondBody = """
        {"name":"佐藤二郎","email":"Duplicate-User@Example.com","password":"Passw0rd123!"}
        """;

    mockMvc.perform(post("/api/auth/register")
            .cookie(secondCsrf.cookie())
            .header("X-XSRF-TOKEN", secondCsrf.token())
            .contentType(MediaType.APPLICATION_JSON)
            .content(secondBody))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.status").value(409));
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
        .andExpect(status().isCreated());
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
        .andExpect(status().isCreated());
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
        .andExpect(status().isCreated());
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
}
