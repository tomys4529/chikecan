package com.chikecan.backend.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

/**
 * SpaWebConfigによる静的ファイル配信・SPAフォールバックの検証。
 * src/test/resources/static/ にテスト専用のダミーindex.html・assets/app.jsを配置している
 * (本番のfrontend/distはDockerビルド時にのみ生成され、リポジトリには含めない)。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SpaWebConfigTest {

  private static final String PASSWORD = "Passw0rd123";
  private static final String USER_EMAIL = "spa-config-user@example.com";

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private UserRepository userRepository;

  @Autowired
  private PasswordEncoder passwordEncoder;

  @BeforeAll
  void setUp() {
    if (userRepository.findByEmail(USER_EMAIL).isEmpty()) {
      userRepository.save(new User("SPA確認用USER", USER_EMAIL, passwordEncoder.encode(PASSWORD), Role.USER, true));
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

  private MockHttpSession loginAsUser() throws Exception {
    MockHttpSession session = new MockHttpSession();
    CsrfCredentials csrf = obtainCsrfToken(session);
    String body = String.format("{\"email\":\"%s\",\"password\":\"%s\"}", USER_EMAIL, PASSWORD);
    mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/auth/login")
            .session(session)
            .cookie(csrf.cookie())
            .header("X-XSRF-TOKEN", csrf.token())
            .contentType(MediaType.APPLICATION_JSON)
            .content(body))
        .andExpect(status().isOk());
    return session;
  }

  @Test
  void ルートパスはindex_htmlへフォワードされる() throws Exception {
    // Spring Bootの「ウェルカムページ」機構は forward:index.html というViewを解決する。
    // MockMvcはforwardを実際には追跡・実行しない(実サーバーでは自動的に実行される)ため、
    // ここではforward先が正しくindex.htmlであることを確認する。
    MvcResult result = mockMvc.perform(get("/"))
        .andExpect(status().isOk())
        .andReturn();

    assertThat(result.getResponse().getForwardedUrl()).isEqualTo("index.html");
  }

  @Test
  void loginパスの直接アクセスはindex_htmlへフォールバックする() throws Exception {
    MvcResult result = mockMvc.perform(get("/login"))
        .andExpect(status().isOk())
        .andReturn();

    String bodyContent = result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
    assertThat(bodyContent).contains("SpaWebConfigTest用のダミーindex.html");
  }

  @Test
  void tickets番号パスの直接アクセスはindex_htmlへフォールバックする() throws Exception {
    MvcResult result = mockMvc.perform(get("/tickets/1"))
        .andExpect(status().isOk())
        .andReturn();

    String bodyContent = result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
    assertThat(bodyContent).contains("SpaWebConfigTest用のダミーindex.html");
  }

  @Test
  void 実在するassetsファイルはそのまま返す() throws Exception {
    MvcResult result = mockMvc.perform(get("/assets/app.js"))
        .andExpect(status().isOk())
        .andReturn();

    String bodyContent = result.getResponse().getContentAsString();
    assertThat(bodyContent).contains("dummy asset for SpaWebConfigTest");
    assertThat(bodyContent).doesNotContain("SpaWebConfigTest用のダミーindex.html");
  }

  @Test
  void 未認証で存在しないapiパスはindex_htmlではなく401のJSONになる() throws Exception {
    mockMvc.perform(get("/api/no-such-endpoint"))
        .andExpect(status().isUnauthorized())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.status").value(401));
  }

  @Test
  void 認証済みでも存在しないapiパスはindex_htmlではなく404のJSONになる() throws Exception {
    MockHttpSession session = loginAsUser();

    mockMvc.perform(get("/api/no-such-endpoint").session(session))
        .andExpect(status().isNotFound())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.status").value(404));
  }
}
