package com.chikecan.backend.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Locale;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.chikecan.backend.entity.Role;
import com.chikecan.backend.entity.User;
import com.chikecan.backend.repository.UserRepository;

import jakarta.servlet.http.Cookie;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AuthSessionTest {

  private static final String EMAIL = "session-user@example.com";
  private static final String PASSWORD = "Passw0rd123";
  private static final String DISABLED_EMAIL = "disabled-session-user@example.com";

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private UserRepository userRepository;

  @Autowired
  private PasswordEncoder passwordEncoder;

  @BeforeAll
  void setUpUsers() {
    if (userRepository.findByEmail(EMAIL).isEmpty()) {
      userRepository.save(new User("セッション太郎", EMAIL, passwordEncoder.encode(PASSWORD), Role.USER, true));
    }
    if (userRepository.findByEmail(DISABLED_EMAIL).isEmpty()) {
      userRepository.save(new User("無効太郎", DISABLED_EMAIL, passwordEncoder.encode(PASSWORD), Role.USER, false));
    }
  }

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

  private String loginBody(String email, String password) {
    return String.format("{\"email\":\"%s\",\"password\":\"%s\"}", email, password);
  }

  @Test
  void 正しいメールとパスワードでログイン成功する() throws Exception {
    MockHttpSession session = new MockHttpSession();
    CsrfCredentials csrf = obtainCsrfToken(session);

    mockMvc.perform(post("/api/auth/login")
            .session(session)
            .cookie(csrf.cookie())
            .header("X-XSRF-TOKEN", csrf.token())
            .contentType(MediaType.APPLICATION_JSON)
            .content(loginBody(EMAIL.toUpperCase(Locale.ROOT), PASSWORD)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.email").value(EMAIL))
        .andExpect(jsonPath("$.role").value("USER"))
        .andExpect(content().string(not(containsString("password"))));
  }

  @Test
  void メール不存在で401になる() throws Exception {
    MockHttpSession session = new MockHttpSession();
    CsrfCredentials csrf = obtainCsrfToken(session);

    mockMvc.perform(post("/api/auth/login")
            .session(session)
            .cookie(csrf.cookie())
            .header("X-XSRF-TOKEN", csrf.token())
            .contentType(MediaType.APPLICATION_JSON)
            .content(loginBody("notfound@example.com", PASSWORD)))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.message").value("メールアドレスまたはパスワードが正しくありません"));
  }

  @Test
  void パスワード不一致で401になる() throws Exception {
    MockHttpSession session = new MockHttpSession();
    CsrfCredentials csrf = obtainCsrfToken(session);

    mockMvc.perform(post("/api/auth/login")
            .session(session)
            .cookie(csrf.cookie())
            .header("X-XSRF-TOKEN", csrf.token())
            .contentType(MediaType.APPLICATION_JSON)
            .content(loginBody(EMAIL, "WrongPassword1")))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.message").value("メールアドレスまたはパスワードが正しくありません"));
  }

  @Test
  void disabledユーザーで401になる() throws Exception {
    MockHttpSession session = new MockHttpSession();
    CsrfCredentials csrf = obtainCsrfToken(session);

    mockMvc.perform(post("/api/auth/login")
            .session(session)
            .cookie(csrf.cookie())
            .header("X-XSRF-TOKEN", csrf.token())
            .contentType(MediaType.APPLICATION_JSON)
            .content(loginBody(DISABLED_EMAIL, PASSWORD)))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.message").value("メールアドレスまたはパスワードが正しくありません"));
  }

  @Test
  void CSRFなしのログインは403になる() throws Exception {
    mockMvc.perform(post("/api/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content(loginBody(EMAIL, PASSWORD)))
        .andExpect(status().isForbidden());
  }

  @Test
  void 空または上限超過のログイン入力は400になる() throws Exception {
    MockHttpSession session = new MockHttpSession();
    CsrfCredentials csrf = obtainCsrfToken(session);

    mockMvc.perform(post("/api/auth/login")
            .session(session)
            .cookie(csrf.cookie())
            .header("X-XSRF-TOKEN", csrf.token())
            .contentType(MediaType.APPLICATION_JSON)
            .content(loginBody("", "")))
        .andExpect(status().isBadRequest());

    String tooLongEmail = "a".repeat(250) + "@example.com";
    mockMvc.perform(post("/api/auth/login")
            .session(session)
            .cookie(csrf.cookie())
            .header("X-XSRF-TOKEN", csrf.token())
            .contentType(MediaType.APPLICATION_JSON)
            .content(loginBody(tooLongEmail, PASSWORD)))
        .andExpect(status().isBadRequest());
  }

  @Test
  void ログイン成功後にセッションIDが変更される() throws Exception {
    MockHttpSession session = new MockHttpSession();
    String originalId = session.getId();
    CsrfCredentials csrf = obtainCsrfToken(session);

    mockMvc.perform(post("/api/auth/login")
            .session(session)
            .cookie(csrf.cookie())
            .header("X-XSRF-TOKEN", csrf.token())
            .contentType(MediaType.APPLICATION_JSON)
            .content(loginBody(EMAIL, PASSWORD)))
        .andExpect(status().isOk());

    assertThat(session.getId()).isNotEqualTo(originalId);
  }

  @Test
  void ログイン後同じセッションでmeが200になりpasswordHashを含まない() throws Exception {
    MockHttpSession session = new MockHttpSession();
    CsrfCredentials csrf = obtainCsrfToken(session);

    mockMvc.perform(post("/api/auth/login")
            .session(session)
            .cookie(csrf.cookie())
            .header("X-XSRF-TOKEN", csrf.token())
            .contentType(MediaType.APPLICATION_JSON)
            .content(loginBody(EMAIL, PASSWORD)))
        .andExpect(status().isOk());

    mockMvc.perform(get("/api/auth/me").session(session))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.email").value(EMAIL))
        .andExpect(content().string(not(containsString("password"))));
  }

  @Test
  void 未認証のmeは401になる() throws Exception {
    mockMvc.perform(get("/api/auth/me"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.message").value("認証が必要です"));
  }

  @Test
  void CSRFなしのログアウトは403になる() throws Exception {
    MockHttpSession session = new MockHttpSession();
    CsrfCredentials csrf = obtainCsrfToken(session);
    mockMvc.perform(post("/api/auth/login")
            .session(session)
            .cookie(csrf.cookie())
            .header("X-XSRF-TOKEN", csrf.token())
            .contentType(MediaType.APPLICATION_JSON)
            .content(loginBody(EMAIL, PASSWORD)))
        .andExpect(status().isOk());

    mockMvc.perform(post("/api/auth/logout").session(session))
        .andExpect(status().isForbidden());
  }

  @Test
  void ログアウト後にmeが401になりセッションが無効化される() throws Exception {
    MockHttpSession session = new MockHttpSession();
    CsrfCredentials csrf = obtainCsrfToken(session);
    mockMvc.perform(post("/api/auth/login")
            .session(session)
            .cookie(csrf.cookie())
            .header("X-XSRF-TOKEN", csrf.token())
            .contentType(MediaType.APPLICATION_JSON)
            .content(loginBody(EMAIL, PASSWORD)))
        .andExpect(status().isOk());

    CsrfCredentials logoutCsrf = obtainCsrfToken(session);

    mockMvc.perform(post("/api/auth/logout")
            .session(session)
            .cookie(logoutCsrf.cookie())
            .header("X-XSRF-TOKEN", logoutCsrf.token()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.message").value("ログアウトしました"));

    assertThat(session.isInvalid()).isTrue();

    mockMvc.perform(get("/api/auth/me").session(session))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void GETによるログアウトは無効でセッションは維持される() throws Exception {
    MockHttpSession session = new MockHttpSession();
    CsrfCredentials csrf = obtainCsrfToken(session);
    mockMvc.perform(post("/api/auth/login")
            .session(session)
            .cookie(csrf.cookie())
            .header("X-XSRF-TOKEN", csrf.token())
            .contentType(MediaType.APPLICATION_JSON)
            .content(loginBody(EMAIL, PASSWORD)))
        .andExpect(status().isOk());

    mockMvc.perform(get("/api/auth/logout").session(session))
        .andExpect(status().is4xxClientError());

    mockMvc.perform(get("/api/auth/me").session(session))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.email").value(EMAIL));
  }

  @Test
  void ログインとログアウト後に新しいCSRFトークンを取得できる() throws Exception {
    MockHttpSession session = new MockHttpSession();
    CsrfCredentials beforeLogin = obtainCsrfToken(session);

    mockMvc.perform(post("/api/auth/login")
            .session(session)
            .cookie(beforeLogin.cookie())
            .header("X-XSRF-TOKEN", beforeLogin.token())
            .contentType(MediaType.APPLICATION_JSON)
            .content(loginBody(EMAIL, PASSWORD)))
        .andExpect(status().isOk());

    CsrfCredentials afterLogin = obtainCsrfToken(session);
    assertThat(afterLogin.token()).isNotEqualTo(beforeLogin.token());

    mockMvc.perform(post("/api/auth/logout")
            .session(session)
            .cookie(afterLogin.cookie())
            .header("X-XSRF-TOKEN", afterLogin.token()))
        .andExpect(status().isOk());

    MockHttpSession newSession = new MockHttpSession();
    CsrfCredentials afterLogout = obtainCsrfToken(newSession);
    assertThat(afterLogout.token()).isNotBlank();
  }

  // JSESSIONIDのSet-Cookieヘッダーは、MockMvc(TestDispatcherServlet)が
  // サーブレットコンテナのセッションCookie発行処理を再現しないため、
  // ここでは検証できない。application.propertiesの値そのものは
  // security.CookieConfigurationTestでServerProperties経由で検証し、
  // 実際のヘッダー出力は本番相当のHTTPサーバへの手動リクエストで確認済み(報告参照)。
}
