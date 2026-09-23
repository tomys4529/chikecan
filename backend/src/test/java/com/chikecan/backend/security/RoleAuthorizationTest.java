package com.chikecan.backend.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.chikecan.backend.entity.Role;
import com.chikecan.backend.entity.User;
import com.chikecan.backend.repository.UserRepository;

import jakarta.servlet.http.Cookie;

/**
 * ロール別URL認可の検証。
 * /api/admin/ping, /api/agent/ping はテストコンテキストにのみ登録するダミーエンドポイントであり、
 * 本番コード(src/main/java)には一切追加しない。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Import(RoleAuthorizationTest.TestEndpoints.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RoleAuthorizationTest {

  @TestConfiguration
  static class TestEndpoints {

    @RestController
    static class DummyController {

      @GetMapping("/api/agent/ping")
      public String agentPing() {
        return "ok";
      }

      @GetMapping("/api/admin/ping")
      public String adminPing() {
        return "ok";
      }
    }
  }

  private static final String USER_EMAIL = "role-user@example.com";
  private static final String AGENT_EMAIL = "role-agent@example.com";
  private static final String ADMIN_EMAIL = "role-admin@example.com";
  private static final String PASSWORD = "Passw0rd123";

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private UserRepository userRepository;

  @Autowired
  private PasswordEncoder passwordEncoder;

  @BeforeAll
  void setUpUsers() {
    createIfAbsent(USER_EMAIL, "ロールUSER", Role.USER);
    createIfAbsent(AGENT_EMAIL, "ロールAGENT", Role.AGENT);
    createIfAbsent(ADMIN_EMAIL, "ロールADMIN", Role.ADMIN);
  }

  private void createIfAbsent(String email, String name, Role role) {
    if (userRepository.findByEmail(email).isEmpty()) {
      userRepository.save(new User(name, email, passwordEncoder.encode(PASSWORD), role, true));
    }
  }

  private record CsrfCredentials(Cookie cookie, String token) {
  }

  private CsrfCredentials obtainCsrfToken(MockHttpSession session) throws Exception {
    MvcResult result = mockMvc.perform(get("/api/auth/csrf").session(session))
        .andExpect(status().isOk())
        .andReturn();
    Cookie cookie = result.getResponse().getCookie("XSRF-TOKEN");
    return new CsrfCredentials(cookie, cookie.getValue());
  }

  /**
   * 実際のPOST /api/auth/loginでログインし、認証済みセッションを返す。
   */
  private MockHttpSession loginAs(String email) throws Exception {
    MockHttpSession session = new MockHttpSession();
    CsrfCredentials csrf = obtainCsrfToken(session);

    String body = String.format("{\"email\":\"%s\",\"password\":\"%s\"}", email, PASSWORD);

    mockMvc.perform(post("/api/auth/login")
            .session(session)
            .cookie(csrf.cookie())
            .header("X-XSRF-TOKEN", csrf.token())
            .contentType(MediaType.APPLICATION_JSON)
            .content(body))
        .andExpect(status().isOk());

    return session;
  }

  @Test
  void 未認証でagentPingへアクセスすると401になる() throws Exception {
    mockMvc.perform(get("/api/agent/ping"))
        .andExpect(status().isUnauthorized())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.status").value(401))
        .andExpect(jsonPath("$.message").value("認証が必要です"));
  }

  @Test
  void 未認証でadminPingへアクセスすると401になる() throws Exception {
    mockMvc.perform(get("/api/admin/ping"))
        .andExpect(status().isUnauthorized())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.status").value(401))
        .andExpect(jsonPath("$.message").value("認証が必要です"));
  }

  @Test
  void USERがagentPingへアクセスすると403になる() throws Exception {
    MockHttpSession session = loginAs(USER_EMAIL);

    mockMvc.perform(get("/api/agent/ping").session(session))
        .andExpect(status().isForbidden())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.status").value(403))
        .andExpect(jsonPath("$.message").value("権限がありません"));
  }

  @Test
  void USERがadminPingへアクセスすると403になる() throws Exception {
    MockHttpSession session = loginAs(USER_EMAIL);

    mockMvc.perform(get("/api/admin/ping").session(session))
        .andExpect(status().isForbidden())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.status").value(403))
        .andExpect(jsonPath("$.message").value("権限がありません"));
  }

  @Test
  void AGENTがagentPingへアクセスできる() throws Exception {
    MockHttpSession session = loginAs(AGENT_EMAIL);

    mockMvc.perform(get("/api/agent/ping").session(session))
        .andExpect(status().isOk());
  }

  @Test
  void AGENTがadminPingへアクセスすると403になる() throws Exception {
    MockHttpSession session = loginAs(AGENT_EMAIL);

    mockMvc.perform(get("/api/admin/ping").session(session))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.status").value(403))
        .andExpect(jsonPath("$.message").value("権限がありません"));
  }

  @Test
  void ADMINがagentPingへアクセスできる() throws Exception {
    MockHttpSession session = loginAs(ADMIN_EMAIL);

    mockMvc.perform(get("/api/agent/ping").session(session))
        .andExpect(status().isOk());
  }

  @Test
  void ADMINがadminPingへアクセスできる() throws Exception {
    MockHttpSession session = loginAs(ADMIN_EMAIL);

    mockMvc.perform(get("/api/admin/ping").session(session))
        .andExpect(status().isOk());
  }

  @Test
  void USERはロール制限のないauthMeへアクセスできる() throws Exception {
    MockHttpSession session = loginAs(USER_EMAIL);

    mockMvc.perform(get("/api/auth/me").session(session))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.email").value(USER_EMAIL));
  }

  // ===== 静的ファイル・SPAルートの公開(SecurityConfig回帰) =====

  @Test
  void 未認証でルートパスへアクセスできる() throws Exception {
    mockMvc.perform(get("/"))
        .andExpect(status().isOk());
  }

  @Test
  void 未認証でReactのクライアントサイドルートへアクセスできる() throws Exception {
    // /tickets/1は実ファイルではなく、SpaWebConfigによりindex.htmlへフォールバックされる。
    // Reactのルーティング自体は画面側の認可(ProtectedRoute)が担うため、
    // ここでは静的ファイル配信レベルで200になることだけを確認する。
    mockMvc.perform(get("/tickets/1"))
        .andExpect(status().isOk());
  }

  @Test
  void 保護対象のAPIは静的ファイル公開後も未認証では401のままである() throws Exception {
    mockMvc.perform(get("/api/tickets"))
        .andExpect(status().isUnauthorized())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.status").value(401));
  }
}
