package com.chikecan.backend.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
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
class AdminUserControllerTest {

  private static final String PASSWORD = "Passw0rd123";
  private static final String USER_EMAIL = "admin-agents-user@example.com";
  private static final String AGENT_EMAIL = "admin-agents-agent@example.com";
  private static final String ADMIN_EMAIL = "admin-agents-admin@example.com";

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private UserRepository userRepository;

  @Autowired
  private PasswordEncoder passwordEncoder;

  @BeforeAll
  void setUp() {
    createIfAbsent(USER_EMAIL, "候補USER", Role.USER);
    createIfAbsent(AGENT_EMAIL, "候補AGENT", Role.AGENT);
    createIfAbsent(ADMIN_EMAIL, "候補ADMIN", Role.ADMIN);
  }

  private Long createIfAbsent(String email, String name, Role role) {
    return userRepository.findByEmail(email)
        .orElseGet(() -> userRepository.save(new User(name, email, passwordEncoder.encode(PASSWORD), role, true)))
        .getId();
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
  void 未認証は401になる() throws Exception {
    mockMvc.perform(get("/api/admin/agents"))
        .andExpect(status().isUnauthorized())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.status").value(401))
        .andExpect(jsonPath("$.message").value("認証が必要です"));
  }

  @Test
  void USERは403になる() throws Exception {
    MockHttpSession session = loginAs(USER_EMAIL);

    mockMvc.perform(get("/api/admin/agents").session(session))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.status").value(403))
        .andExpect(jsonPath("$.message").value("権限がありません"));
  }

  @Test
  void AGENTは403になる() throws Exception {
    MockHttpSession session = loginAs(AGENT_EMAIL);

    mockMvc.perform(get("/api/admin/agents").session(session))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.status").value(403))
        .andExpect(jsonPath("$.message").value("権限がありません"));
  }

  @Test
  void ADMINは200でAGENT候補一覧を取得できる() throws Exception {
    MockHttpSession session = loginAs(ADMIN_EMAIL);

    MvcResult result = mockMvc.perform(get("/api/admin/agents").session(session))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andReturn();

    String bodyContent = result.getResponse().getContentAsString();
    assertThat(bodyContent).contains("候補AGENT");
    assertThat(bodyContent).doesNotContain("候補USER");
    assertThat(bodyContent).doesNotContain("候補ADMIN");
  }

  @Test
  void レスポンスにidと名前だけが含まれメールアドレス等の他の項目は含まれない() throws Exception {
    MockHttpSession session = loginAs(ADMIN_EMAIL);

    mockMvc.perform(get("/api/admin/agents").session(session))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id").exists())
        .andExpect(jsonPath("$[0].name").exists())
        .andExpect(jsonPath("$[0].email").doesNotExist())
        .andExpect(jsonPath("$[0].role").doesNotExist())
        .andExpect(jsonPath("$[0].enabled").doesNotExist())
        .andExpect(jsonPath("$[0].createdAt").doesNotExist())
        .andExpect(content().string(not(containsString("password"))))
        // メールアドレスらしき文字列が紛れ込んでいないことも念のため確認する。
        .andExpect(content().string(not(containsString("@"))));
  }

  @Test
  void 無効化されたAGENTは候補に含まれない() throws Exception {
    String disabledName = "無効AGENT";
    String disabledEmail = "admin-agents-disabled@example.com";
    userRepository.findByEmail(disabledEmail).ifPresentOrElse(
        existing -> { },
        () -> userRepository.save(new User(disabledName, disabledEmail, passwordEncoder.encode(PASSWORD), Role.AGENT, false)));

    MockHttpSession session = loginAs(ADMIN_EMAIL);

    mockMvc.perform(get("/api/admin/agents").session(session))
        .andExpect(status().isOk())
        .andExpect(content().string(not(containsString(disabledName))));
  }
}
