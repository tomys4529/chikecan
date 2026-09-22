package com.chikecan.backend.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class SecurityConfigTest {

  @Autowired
  private MockMvc mockMvc;

  @Test
  void 未認証で保護されたURLにアクセスすると401になる() throws Exception {
    mockMvc.perform(get("/api/tickets"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.status").value(401))
        .andExpect(jsonPath("$.message").value("認証が必要です"));
  }

  @Test
  void healthはpermitAllのため未認証でも200になる() throws Exception {
    mockMvc.perform(get("/api/health"))
        .andExpect(status().isOk());
  }

  @Test
  void 許可されたOriginからのリクエストにはAllowOriginヘッダーが付与される() throws Exception {
    mockMvc.perform(get("/api/health").header("Origin", "http://localhost:5173"))
        .andExpect(status().isOk())
        .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
  }

  @Test
  void 許可されていないOriginからのプリフライトは拒否される() throws Exception {
    mockMvc.perform(options("/api/auth/register")
            .header("Origin", "http://evil.example.com")
            .header("Access-Control-Request-Method", "POST"))
        .andExpect(status().isForbidden());
  }
}
