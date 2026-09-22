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
        {"name":"山田太郎","email":"YAMADA@EXAMPLE.COM","password":"Passw0rd123"}
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
    assertThat(saved.getPasswordHash()).isNotEqualTo("Passw0rd123");
    assertThat(passwordEncoder.matches("Passw0rd123", saved.getPasswordHash())).isTrue();
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
        {"name":"田中花子","email":"invalid-email","password":"Passw0rd123"}
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
        {"name":"佐藤一郎","email":"duplicate-user@example.com","password":"Passw0rd123"}
        """;

    mockMvc.perform(post("/api/auth/register")
            .cookie(firstCsrf.cookie())
            .header("X-XSRF-TOKEN", firstCsrf.token())
            .contentType(MediaType.APPLICATION_JSON)
            .content(body))
        .andExpect(status().isCreated());

    CsrfCredentials secondCsrf = obtainCsrfToken();
    String secondBody = """
        {"name":"佐藤二郎","email":"Duplicate-User@Example.com","password":"Passw0rd123"}
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
        {"name":"無効ユーザー","email":"nocsrf@example.com","password":"Passw0rd123"}
        """;

    mockMvc.perform(post("/api/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .content(body))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.status").value(403));
  }
}
