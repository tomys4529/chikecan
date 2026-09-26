package com.chikecan.backend.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;

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
import com.chikecan.backend.entity.Ticket;
import com.chikecan.backend.entity.TicketPriority;
import com.chikecan.backend.entity.TicketStatus;
import com.chikecan.backend.entity.User;
import com.chikecan.backend.repository.TicketRepository;
import com.chikecan.backend.repository.UserRepository;

import jakarta.servlet.http.Cookie;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TicketControllerTest {

  private static final String PASSWORD = "Passw0rd123";
  private static final String USER_EMAIL = "ticket-user@example.com";
  private static final String OTHER_USER_EMAIL = "ticket-other-user@example.com";
  private static final String AGENT_EMAIL = "ticket-agent@example.com";
  private static final String OTHER_AGENT_EMAIL = "ticket-other-agent@example.com";
  private static final String ADMIN_EMAIL = "ticket-admin@example.com";

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private UserRepository userRepository;

  @Autowired
  private TicketRepository ticketRepository;

  @Autowired
  private PasswordEncoder passwordEncoder;

  private Long userId;
  private Long otherUserId;
  private Long agentId;
  private Long otherAgentId;

  @BeforeAll
  void setUp() {
    userId = createIfAbsent(USER_EMAIL, "チケットUSER", Role.USER);
    otherUserId = createIfAbsent(OTHER_USER_EMAIL, "他のUSER", Role.USER);
    agentId = createIfAbsent(AGENT_EMAIL, "チケットAGENT", Role.AGENT);
    otherAgentId = createIfAbsent(OTHER_AGENT_EMAIL, "他のAGENT", Role.AGENT);
    createIfAbsent(ADMIN_EMAIL, "チケットADMIN", Role.ADMIN);
  }

  private Long createIfAbsent(String email, String name, Role role) {
    return userRepository.findByEmail(email)
        .orElseGet(() -> userRepository.save(new User(name, email, passwordEncoder.encode(PASSWORD), role, true)))
        .getId();
  }

  private Ticket createTicket(Long requesterId, Long assigneeId, TicketStatus status) {
    return createTicket(requesterId, assigneeId, status, TicketPriority.MEDIUM);
  }

  private Ticket createTicket(Long requesterId, Long assigneeId, TicketStatus status, TicketPriority priority) {
    Ticket ticket = new Ticket("直接作成", "内容", status, priority, requesterId, assigneeId);
    return ticketRepository.saveAndFlush(ticket);
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

  // ===== 登録 =====

  @Test
  void USERはチケットを登録でき201でrequesterIdが自分になる() throws Exception {
    MockHttpSession session = loginAs(USER_EMAIL);
    CsrfCredentials csrf = obtainCsrfToken(session);
    String body = "{\"title\":\"困っています\",\"description\":\"詳細内容\",\"priority\":\"HIGH\"}";

    mockMvc.perform(post("/api/tickets")
            .session(session)
            .cookie(csrf.cookie())
            .header("X-XSRF-TOKEN", csrf.token())
            .contentType(MediaType.APPLICATION_JSON)
            .content(body))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.requesterId").value(userId))
        .andExpect(jsonPath("$.requesterName").value("チケットUSER"))
        .andExpect(jsonPath("$.assigneeId").value(nullValue()))
        .andExpect(jsonPath("$.assigneeName").value(nullValue()))
        .andExpect(jsonPath("$.status").value("OPEN"));
  }

  @Test
  void requesterIdとassigneeIdをリクエストに含めても採用されない() throws Exception {
    MockHttpSession session = loginAs(USER_EMAIL);
    CsrfCredentials csrf = obtainCsrfToken(session);
    String body = String.format(
        "{\"title\":\"なりすまし試行\",\"description\":\"詳細\",\"priority\":\"LOW\",\"requesterId\":%d,\"assigneeId\":%d}",
        otherUserId, agentId);

    mockMvc.perform(post("/api/tickets")
            .session(session)
            .cookie(csrf.cookie())
            .header("X-XSRF-TOKEN", csrf.token())
            .contentType(MediaType.APPLICATION_JSON)
            .content(body))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.requesterId").value(userId))
        .andExpect(jsonPath("$.assigneeId").value(nullValue()));
  }

  @Test
  void AGENTはチケットを登録できず403になる() throws Exception {
    MockHttpSession session = loginAs(AGENT_EMAIL);
    CsrfCredentials csrf = obtainCsrfToken(session);
    String body = "{\"title\":\"タイトル\",\"description\":\"内容\",\"priority\":\"LOW\"}";

    mockMvc.perform(post("/api/tickets")
            .session(session)
            .cookie(csrf.cookie())
            .header("X-XSRF-TOKEN", csrf.token())
            .contentType(MediaType.APPLICATION_JSON)
            .content(body))
        .andExpect(status().isForbidden())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.status").value(403))
        .andExpect(jsonPath("$.message").value("権限がありません"));
  }

  @Test
  void ADMINはチケットを登録できず403になる() throws Exception {
    MockHttpSession session = loginAs(ADMIN_EMAIL);
    CsrfCredentials csrf = obtainCsrfToken(session);
    String body = "{\"title\":\"タイトル\",\"description\":\"内容\",\"priority\":\"LOW\"}";

    mockMvc.perform(post("/api/tickets")
            .session(session)
            .cookie(csrf.cookie())
            .header("X-XSRF-TOKEN", csrf.token())
            .contentType(MediaType.APPLICATION_JSON)
            .content(body))
        .andExpect(status().isForbidden());
  }

  @Test
  void 不正な入力は400になる() throws Exception {
    MockHttpSession session = loginAs(USER_EMAIL);
    CsrfCredentials csrf = obtainCsrfToken(session);
    String body = "{\"title\":\"\",\"description\":\"\"}";

    mockMvc.perform(post("/api/tickets")
            .session(session)
            .cookie(csrf.cookie())
            .header("X-XSRF-TOKEN", csrf.token())
            .contentType(MediaType.APPLICATION_JSON)
            .content(body))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value(400));
  }

  @Test
  void 不正なpriority文字列は400になる() throws Exception {
    MockHttpSession session = loginAs(USER_EMAIL);
    CsrfCredentials csrf = obtainCsrfToken(session);
    String body = "{\"title\":\"タイトル\",\"description\":\"内容\",\"priority\":\"URGENT\"}";

    mockMvc.perform(post("/api/tickets")
            .session(session)
            .cookie(csrf.cookie())
            .header("X-XSRF-TOKEN", csrf.token())
            .contentType(MediaType.APPLICATION_JSON)
            .content(body))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value(400));
  }

  @Test
  void 壊れたJSONは400になる() throws Exception {
    MockHttpSession session = loginAs(USER_EMAIL);
    CsrfCredentials csrf = obtainCsrfToken(session);

    mockMvc.perform(post("/api/tickets")
            .session(session)
            .cookie(csrf.cookie())
            .header("X-XSRF-TOKEN", csrf.token())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"title\":\"タイトル\", broken"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value(400));
  }

  @Test
  void CSRFなしの登録は403になる() throws Exception {
    String body = "{\"title\":\"タイトル\",\"description\":\"内容\",\"priority\":\"LOW\"}";
    mockMvc.perform(post("/api/tickets").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isForbidden());
  }

  // ===== 登録時の文字数・空白バリデーション =====

  @Test
  void タイトルが50文字なら登録できる() throws Exception {
    MockHttpSession session = loginAs(USER_EMAIL);
    CsrfCredentials csrf = obtainCsrfToken(session);
    String title = "あ".repeat(50);
    String body = String.format("{\"title\":\"%s\",\"description\":\"内容\",\"priority\":\"LOW\"}", title);

    mockMvc.perform(post("/api/tickets")
            .session(session)
            .cookie(csrf.cookie())
            .header("X-XSRF-TOKEN", csrf.token())
            .contentType(MediaType.APPLICATION_JSON)
            .content(body))
        .andExpect(status().isCreated());
  }

  @Test
  void タイトルが51文字だと400になる() throws Exception {
    MockHttpSession session = loginAs(USER_EMAIL);
    CsrfCredentials csrf = obtainCsrfToken(session);
    String title = "あ".repeat(51);
    String body = String.format("{\"title\":\"%s\",\"description\":\"内容\",\"priority\":\"LOW\"}", title);

    mockMvc.perform(post("/api/tickets")
            .session(session)
            .cookie(csrf.cookie())
            .header("X-XSRF-TOKEN", csrf.token())
            .contentType(MediaType.APPLICATION_JSON)
            .content(body))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("タイトルは50文字以内で入力してください"));
  }

  @Test
  void 内容が500文字なら登録できる() throws Exception {
    MockHttpSession session = loginAs(USER_EMAIL);
    CsrfCredentials csrf = obtainCsrfToken(session);
    String description = "あ".repeat(500);
    String body = String.format("{\"title\":\"タイトル\",\"description\":\"%s\",\"priority\":\"LOW\"}", description);

    mockMvc.perform(post("/api/tickets")
            .session(session)
            .cookie(csrf.cookie())
            .header("X-XSRF-TOKEN", csrf.token())
            .contentType(MediaType.APPLICATION_JSON)
            .content(body))
        .andExpect(status().isCreated());
  }

  @Test
  void 内容が501文字だと400になる() throws Exception {
    MockHttpSession session = loginAs(USER_EMAIL);
    CsrfCredentials csrf = obtainCsrfToken(session);
    String description = "あ".repeat(501);
    String body = String.format("{\"title\":\"タイトル\",\"description\":\"%s\",\"priority\":\"LOW\"}", description);

    mockMvc.perform(post("/api/tickets")
            .session(session)
            .cookie(csrf.cookie())
            .header("X-XSRF-TOKEN", csrf.token())
            .contentType(MediaType.APPLICATION_JSON)
            .content(body))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("内容は500文字以内で入力してください"));
  }

  @Test
  void タイトルが空文字だと400になる() throws Exception {
    MockHttpSession session = loginAs(USER_EMAIL);
    CsrfCredentials csrf = obtainCsrfToken(session);
    String body = "{\"title\":\"\",\"description\":\"内容\",\"priority\":\"LOW\"}";

    mockMvc.perform(post("/api/tickets")
            .session(session)
            .cookie(csrf.cookie())
            .header("X-XSRF-TOKEN", csrf.token())
            .contentType(MediaType.APPLICATION_JSON)
            .content(body))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("タイトルは必須です"));
  }

  @Test
  void タイトルが空白のみだと400になる() throws Exception {
    MockHttpSession session = loginAs(USER_EMAIL);
    CsrfCredentials csrf = obtainCsrfToken(session);
    String body = "{\"title\":\"   \",\"description\":\"内容\",\"priority\":\"LOW\"}";

    mockMvc.perform(post("/api/tickets")
            .session(session)
            .cookie(csrf.cookie())
            .header("X-XSRF-TOKEN", csrf.token())
            .contentType(MediaType.APPLICATION_JSON)
            .content(body))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("タイトルは必須です"));
  }

  @Test
  void 内容が空文字だと400になる() throws Exception {
    MockHttpSession session = loginAs(USER_EMAIL);
    CsrfCredentials csrf = obtainCsrfToken(session);
    String body = "{\"title\":\"タイトル\",\"description\":\"\",\"priority\":\"LOW\"}";

    mockMvc.perform(post("/api/tickets")
            .session(session)
            .cookie(csrf.cookie())
            .header("X-XSRF-TOKEN", csrf.token())
            .contentType(MediaType.APPLICATION_JSON)
            .content(body))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("内容は必須です"));
  }

  @Test
  void 内容が空白のみだと400になる() throws Exception {
    MockHttpSession session = loginAs(USER_EMAIL);
    CsrfCredentials csrf = obtainCsrfToken(session);
    String body = "{\"title\":\"タイトル\",\"description\":\"   \",\"priority\":\"LOW\"}";

    mockMvc.perform(post("/api/tickets")
            .session(session)
            .cookie(csrf.cookie())
            .header("X-XSRF-TOKEN", csrf.token())
            .contentType(MediaType.APPLICATION_JSON)
            .content(body))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("内容は必須です"));
  }

  // ===== 一覧・詳細 =====

  @Test
  void USERは自分のチケットだけ一覧に含まれる() throws Exception {
    createTicket(userId, null, TicketStatus.OPEN);
    createTicket(otherUserId, null, TicketStatus.OPEN);
    MockHttpSession session = loginAs(USER_EMAIL);

    mockMvc.perform(get("/api/tickets").session(session))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[*].requesterId", everyItemEquals(userId)));
  }

  @Test
  void 一覧レスポンスに依頼者名と担当者名が含まれる() throws Exception {
    Ticket assigned = createTicket(userId, agentId, TicketStatus.OPEN);
    Ticket unassigned = createTicket(userId, null, TicketStatus.OPEN);
    MockHttpSession session = loginAs(ADMIN_EMAIL);

    MvcResult result = mockMvc.perform(get("/api/tickets").session(session))
        .andExpect(status().isOk())
        .andReturn();

    tools.jackson.databind.JsonNode root = new tools.jackson.databind.ObjectMapper()
        .readTree(result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
    tools.jackson.databind.JsonNode content = root.get("content");
    tools.jackson.databind.JsonNode assignedNode = findById(content, assigned.getId());
    tools.jackson.databind.JsonNode unassignedNode = findById(content, unassigned.getId());

    assertThat(assignedNode.get("requesterName").asText()).isEqualTo("チケットUSER");
    assertThat(assignedNode.get("assigneeName").asText()).isEqualTo("チケットAGENT");
    assertThat(unassignedNode.get("requesterName").asText()).isEqualTo("チケットUSER");
    assertThat(unassignedNode.get("assigneeName").isNull()).isTrue();
  }

  private tools.jackson.databind.JsonNode findById(tools.jackson.databind.JsonNode array, Long id) {
    for (tools.jackson.databind.JsonNode node : array) {
      if (node.get("id").asLong() == id) {
        return node;
      }
    }
    throw new AssertionError("id=" + id + "の要素が見つかりません");
  }

  @Test
  void 一覧はcreatedAt降順で返る() throws Exception {
    Ticket first = createTicket(userId, null, TicketStatus.OPEN);
    Thread.sleep(10);
    Ticket second = createTicket(userId, null, TicketStatus.OPEN);
    MockHttpSession session = loginAs(USER_EMAIL);

    MvcResult result = mockMvc.perform(get("/api/tickets").session(session))
        .andExpect(status().isOk())
        .andReturn();

    String bodyContent = result.getResponse().getContentAsString();
    int secondIndex = bodyContent.indexOf("\"id\":" + second.getId());
    int firstIndex = bodyContent.indexOf("\"id\":" + first.getId());
    assertThat(secondIndex).isGreaterThanOrEqualTo(0);
    assertThat(firstIndex).isGreaterThan(secondIndex);
  }

  @Test
  void AGENTは自分が担当のチケットだけ一覧に含まれる() throws Exception {
    createTicket(userId, agentId, TicketStatus.OPEN);
    createTicket(userId, otherAgentId, TicketStatus.OPEN);
    MockHttpSession session = loginAs(AGENT_EMAIL);

    mockMvc.perform(get("/api/tickets").session(session))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[*].assigneeId", everyItemEquals(agentId)));
  }

  @Test
  void ADMINは全件取得できる() throws Exception {
    createTicket(userId, null, TicketStatus.OPEN);
    MockHttpSession session = loginAs(ADMIN_EMAIL);

    MvcResult result = mockMvc.perform(get("/api/tickets").session(session))
        .andExpect(status().isOk())
        .andReturn();

    assertThat(result.getResponse().getContentAsString()).isNotEmpty();
  }

  @Test
  void USERは他人のチケットIDを指定しても404になる() throws Exception {
    Ticket ticket = createTicket(otherUserId, null, TicketStatus.OPEN);
    MockHttpSession session = loginAs(USER_EMAIL);

    mockMvc.perform(get("/api/tickets/" + ticket.getId()).session(session))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.status").value(404))
        .andExpect(jsonPath("$.message").value("チケットが見つかりません"));
  }

  @Test
  void AGENTは他担当者のチケットIDを指定しても404になる() throws Exception {
    Ticket ticket = createTicket(userId, otherAgentId, TicketStatus.OPEN);
    MockHttpSession session = loginAs(AGENT_EMAIL);

    mockMvc.perform(get("/api/tickets/" + ticket.getId()).session(session))
        .andExpect(status().isNotFound());
  }

  @Test
  void チケット詳細レスポンスに依頼者名と担当者名が含まれる() throws Exception {
    Ticket ticket = createTicket(userId, agentId, TicketStatus.OPEN);
    MockHttpSession session = loginAs(ADMIN_EMAIL);

    mockMvc.perform(get("/api/tickets/" + ticket.getId()).session(session))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.requesterName").value("チケットUSER"))
        .andExpect(jsonPath("$.assigneeName").value("チケットAGENT"));
  }

  @Test
  void 存在しないIDも同じ404になる() throws Exception {
    MockHttpSession session = loginAs(USER_EMAIL);

    mockMvc.perform(get("/api/tickets/999999").session(session))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.message").value("チケットが見つかりません"));
  }

  @Test
  void 未認証の一覧取得は401になる() throws Exception {
    mockMvc.perform(get("/api/tickets"))
        .andExpect(status().isUnauthorized())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.status").value(401))
        .andExpect(jsonPath("$.message").value("認証が必要です"));
  }

  // ===== ステータス変更 =====

  @Test
  void 担当AGENTはステータスを変更できる() throws Exception {
    Ticket ticket = createTicket(userId, agentId, TicketStatus.OPEN);
    MockHttpSession session = loginAs(AGENT_EMAIL);
    CsrfCredentials csrf = obtainCsrfToken(session);

    mockMvc.perform(patch("/api/tickets/" + ticket.getId() + "/status")
            .session(session)
            .cookie(csrf.cookie())
            .header("X-XSRF-TOKEN", csrf.token())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"status\":\"IN_PROGRESS\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.ticket.status").value("IN_PROGRESS"))
        .andExpect(jsonPath("$.ticket.requesterName").value("チケットUSER"))
        .andExpect(jsonPath("$.ticket.assigneeName").value("チケットAGENT"))
        // IN_PROGRESSへの変更ではXPは付与されない。
        .andExpect(jsonPath("$.xpResult.awarded").value(false));

    Ticket reloaded = ticketRepository.findById(ticket.getId()).orElseThrow();
    assertThat(reloaded.getStatus()).isEqualTo(TicketStatus.IN_PROGRESS);
  }

  @Test
  void 未担当または別担当AGENTはステータス変更で404になる() throws Exception {
    Ticket ticket = createTicket(userId, otherAgentId, TicketStatus.OPEN);
    MockHttpSession session = loginAs(AGENT_EMAIL);
    CsrfCredentials csrf = obtainCsrfToken(session);

    mockMvc.perform(patch("/api/tickets/" + ticket.getId() + "/status")
            .session(session)
            .cookie(csrf.cookie())
            .header("X-XSRF-TOKEN", csrf.token())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"status\":\"IN_PROGRESS\"}"))
        .andExpect(status().isNotFound());
  }

  @Test
  void USERはステータス変更できず403になりHTTP500にならない() throws Exception {
    Ticket ticket = createTicket(userId, agentId, TicketStatus.OPEN);
    MockHttpSession session = loginAs(USER_EMAIL);
    CsrfCredentials csrf = obtainCsrfToken(session);

    mockMvc.perform(patch("/api/tickets/" + ticket.getId() + "/status")
            .session(session)
            .cookie(csrf.cookie())
            .header("X-XSRF-TOKEN", csrf.token())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"status\":\"IN_PROGRESS\"}"))
        .andExpect(status().isForbidden())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.status").value(403))
        .andExpect(jsonPath("$.message").value("権限がありません"));
  }

  @Test
  void ADMINはステータス変更できる() throws Exception {
    Ticket ticket = createTicket(userId, agentId, TicketStatus.OPEN);
    MockHttpSession session = loginAs(ADMIN_EMAIL);
    CsrfCredentials csrf = obtainCsrfToken(session);

    mockMvc.perform(patch("/api/tickets/" + ticket.getId() + "/status")
            .session(session)
            .cookie(csrf.cookie())
            .header("X-XSRF-TOKEN", csrf.token())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"status\":\"IN_PROGRESS\"}"))
        .andExpect(status().isOk());

    Ticket reloaded = ticketRepository.findById(ticket.getId()).orElseThrow();
    assertThat(reloaded.getStatus()).isEqualTo(TicketStatus.IN_PROGRESS);
  }

  @Test
  void ADMINも不正なステータス遷移は409になる() throws Exception {
    Ticket ticket = createTicket(userId, agentId, TicketStatus.OPEN);
    MockHttpSession session = loginAs(ADMIN_EMAIL);
    CsrfCredentials csrf = obtainCsrfToken(session);

    mockMvc.perform(patch("/api/tickets/" + ticket.getId() + "/status")
            .session(session)
            .cookie(csrf.cookie())
            .header("X-XSRF-TOKEN", csrf.token())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"status\":\"CLOSED\"}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.status").value(409));
  }

  @Test
  void 不正なstatus文字列は400になる() throws Exception {
    Ticket ticket = createTicket(userId, agentId, TicketStatus.OPEN);
    MockHttpSession session = loginAs(AGENT_EMAIL);
    CsrfCredentials csrf = obtainCsrfToken(session);

    mockMvc.perform(patch("/api/tickets/" + ticket.getId() + "/status")
            .session(session)
            .cookie(csrf.cookie())
            .header("X-XSRF-TOKEN", csrf.token())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"status\":\"UNKNOWN\"}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void CSRFなしのステータス変更は403になる() throws Exception {
    Ticket ticket = createTicket(userId, agentId, TicketStatus.OPEN);
    MockHttpSession session = loginAs(AGENT_EMAIL);

    mockMvc.perform(patch("/api/tickets/" + ticket.getId() + "/status")
            .session(session)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"status\":\"IN_PROGRESS\"}"))
        .andExpect(status().isForbidden());
  }

  // ===== 担当者設定 =====

  @Test
  void ADMINは担当者を設定できる() throws Exception {
    Ticket ticket = createTicket(userId, null, TicketStatus.OPEN);
    MockHttpSession session = loginAs(ADMIN_EMAIL);
    CsrfCredentials csrf = obtainCsrfToken(session);

    mockMvc.perform(patch("/api/tickets/" + ticket.getId() + "/assignee")
            .session(session)
            .cookie(csrf.cookie())
            .header("X-XSRF-TOKEN", csrf.token())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"assigneeId\":" + agentId + "}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.assigneeId").value(agentId))
        .andExpect(jsonPath("$.assigneeName").value("チケットAGENT"))
        .andExpect(jsonPath("$.requesterName").value("チケットUSER"));

    Ticket reloaded = ticketRepository.findById(ticket.getId()).orElseThrow();
    assertThat(reloaded.getAssigneeId()).isEqualTo(agentId);
  }

  @Test
  void ADMIN以外の担当者変更は403になる() throws Exception {
    Ticket ticket = createTicket(userId, null, TicketStatus.OPEN);
    MockHttpSession session = loginAs(AGENT_EMAIL);
    CsrfCredentials csrf = obtainCsrfToken(session);

    mockMvc.perform(patch("/api/tickets/" + ticket.getId() + "/assignee")
            .session(session)
            .cookie(csrf.cookie())
            .header("X-XSRF-TOKEN", csrf.token())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"assigneeId\":" + agentId + "}"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.status").value(403))
        .andExpect(jsonPath("$.message").value("権限がありません"));
  }

  @Test
  void USERの担当者変更は403になる() throws Exception {
    Ticket ticket = createTicket(userId, null, TicketStatus.OPEN);
    MockHttpSession session = loginAs(USER_EMAIL);
    CsrfCredentials csrf = obtainCsrfToken(session);

    mockMvc.perform(patch("/api/tickets/" + ticket.getId() + "/assignee")
            .session(session)
            .cookie(csrf.cookie())
            .header("X-XSRF-TOKEN", csrf.token())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"assigneeId\":" + agentId + "}"))
        .andExpect(status().isForbidden());
  }

  @Test
  void USERやADMINを担当者に指定すると400になる() throws Exception {
    Ticket ticket = createTicket(userId, null, TicketStatus.OPEN);
    MockHttpSession session = loginAs(ADMIN_EMAIL);
    CsrfCredentials csrf = obtainCsrfToken(session);

    mockMvc.perform(patch("/api/tickets/" + ticket.getId() + "/assignee")
            .session(session)
            .cookie(csrf.cookie())
            .header("X-XSRF-TOKEN", csrf.token())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"assigneeId\":" + userId + "}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value(400));
  }

  @Test
  void assigneeIdにnullを指定すると担当解除できる() throws Exception {
    Ticket ticket = createTicket(userId, agentId, TicketStatus.OPEN);
    MockHttpSession session = loginAs(ADMIN_EMAIL);
    CsrfCredentials csrf = obtainCsrfToken(session);

    mockMvc.perform(patch("/api/tickets/" + ticket.getId() + "/assignee")
            .session(session)
            .cookie(csrf.cookie())
            .header("X-XSRF-TOKEN", csrf.token())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"assigneeId\":null}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.assigneeId").value(nullValue()))
        .andExpect(jsonPath("$.assigneeName").value(nullValue()));

    Ticket reloaded = ticketRepository.findById(ticket.getId()).orElseThrow();
    assertThat(reloaded.getAssigneeId()).isNull();
  }

  @Test
  void CSRFなしの担当者変更は403になる() throws Exception {
    Ticket ticket = createTicket(userId, null, TicketStatus.OPEN);
    MockHttpSession session = loginAs(ADMIN_EMAIL);

    mockMvc.perform(patch("/api/tickets/" + ticket.getId() + "/assignee")
            .session(session)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"assigneeId\":" + agentId + "}"))
        .andExpect(status().isForbidden());
  }

  // ===== チケット内容編集(USER本人・OPENのみ) =====

  @Test
  void USER本人は自分のOPENチケットのタイトル内容優先度を更新できる() throws Exception {
    Ticket ticket = createTicket(userId, agentId, TicketStatus.OPEN);
    Instant beforeUpdatedAt = ticket.getUpdatedAt();
    MockHttpSession session = loginAs(USER_EMAIL);
    CsrfCredentials csrf = obtainCsrfToken(session);
    Thread.sleep(10);

    mockMvc.perform(patch("/api/tickets/" + ticket.getId())
            .session(session)
            .cookie(csrf.cookie())
            .header("X-XSRF-TOKEN", csrf.token())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"title\":\"修正後タイトル\",\"description\":\"修正後内容\",\"priority\":\"HIGH\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.title").value("修正後タイトル"))
        .andExpect(jsonPath("$.description").value("修正後内容"))
        .andExpect(jsonPath("$.priority").value("HIGH"))
        // 変更されない項目
        .andExpect(jsonPath("$.requesterId").value(userId))
        .andExpect(jsonPath("$.assigneeId").value(agentId))
        .andExpect(jsonPath("$.status").value("OPEN"))
        // 名前表示は維持される
        .andExpect(jsonPath("$.requesterName").value("チケットUSER"))
        .andExpect(jsonPath("$.assigneeName").value("チケットAGENT"));

    Ticket reloaded = ticketRepository.findById(ticket.getId()).orElseThrow();
    assertThat(reloaded.getTitle()).isEqualTo("修正後タイトル");
    assertThat(reloaded.getDescription()).isEqualTo("修正後内容");
    assertThat(reloaded.getPriority()).isEqualTo(TicketPriority.HIGH);
    assertThat(reloaded.getRequesterId()).isEqualTo(userId);
    assertThat(reloaded.getAssigneeId()).isEqualTo(agentId);
    assertThat(reloaded.getStatus()).isEqualTo(TicketStatus.OPEN);
    assertThat(reloaded.getCreatedAt()).isEqualTo(ticket.getCreatedAt());
    assertThat(reloaded.getUpdatedAt()).isAfter(beforeUpdatedAt);
  }

  @Test
  void 他のUSERのチケットは更新できず404になる() throws Exception {
    Ticket ticket = createTicket(otherUserId, null, TicketStatus.OPEN);
    MockHttpSession session = loginAs(USER_EMAIL);
    CsrfCredentials csrf = obtainCsrfToken(session);

    mockMvc.perform(patch("/api/tickets/" + ticket.getId())
            .session(session)
            .cookie(csrf.cookie())
            .header("X-XSRF-TOKEN", csrf.token())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"title\":\"タイトル\",\"description\":\"内容\",\"priority\":\"LOW\"}"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.status").value(404))
        .andExpect(jsonPath("$.message").value("チケットが見つかりません"));
  }

  @Test
  void AGENTはチケット編集で403になる() throws Exception {
    Ticket ticket = createTicket(userId, agentId, TicketStatus.OPEN);
    MockHttpSession session = loginAs(AGENT_EMAIL);
    CsrfCredentials csrf = obtainCsrfToken(session);

    mockMvc.perform(patch("/api/tickets/" + ticket.getId())
            .session(session)
            .cookie(csrf.cookie())
            .header("X-XSRF-TOKEN", csrf.token())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"title\":\"タイトル\",\"description\":\"内容\",\"priority\":\"LOW\"}"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.status").value(403))
        .andExpect(jsonPath("$.message").value("権限がありません"));
  }

  @Test
  void ADMINはチケット編集で403になる() throws Exception {
    Ticket ticket = createTicket(userId, null, TicketStatus.OPEN);
    MockHttpSession session = loginAs(ADMIN_EMAIL);
    CsrfCredentials csrf = obtainCsrfToken(session);

    mockMvc.perform(patch("/api/tickets/" + ticket.getId())
            .session(session)
            .cookie(csrf.cookie())
            .header("X-XSRF-TOKEN", csrf.token())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"title\":\"タイトル\",\"description\":\"内容\",\"priority\":\"LOW\"}"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.status").value(403));
  }

  @Test
  void 未認証のチケット編集は401になる() throws Exception {
    Ticket ticket = createTicket(userId, null, TicketStatus.OPEN);
    // CSRF検証は認証チェックより先に行われるため、CSRFトークン自体は正しく付与したうえで
    // ログインしていない状態を検証する(そうしないとCSRF不足による403と区別できない)。
    MockHttpSession session = new MockHttpSession();
    CsrfCredentials csrf = obtainCsrfToken(session);

    mockMvc.perform(patch("/api/tickets/" + ticket.getId())
            .session(session)
            .cookie(csrf.cookie())
            .header("X-XSRF-TOKEN", csrf.token())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"title\":\"タイトル\",\"description\":\"内容\",\"priority\":\"LOW\"}"))
        .andExpect(status().isUnauthorized())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.status").value(401));
  }

  @Test
  void 存在しないチケットの編集は404になる() throws Exception {
    MockHttpSession session = loginAs(USER_EMAIL);
    CsrfCredentials csrf = obtainCsrfToken(session);

    mockMvc.perform(patch("/api/tickets/999999")
            .session(session)
            .cookie(csrf.cookie())
            .header("X-XSRF-TOKEN", csrf.token())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"title\":\"タイトル\",\"description\":\"内容\",\"priority\":\"LOW\"}"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.message").value("チケットが見つかりません"));
  }

  @Test
  void IN_PROGRESSのチケットは編集できず409になる() throws Exception {
    Ticket ticket = createTicket(userId, agentId, TicketStatus.IN_PROGRESS);
    MockHttpSession session = loginAs(USER_EMAIL);
    CsrfCredentials csrf = obtainCsrfToken(session);

    mockMvc.perform(patch("/api/tickets/" + ticket.getId())
            .session(session)
            .cookie(csrf.cookie())
            .header("X-XSRF-TOKEN", csrf.token())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"title\":\"タイトル\",\"description\":\"内容\",\"priority\":\"LOW\"}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.status").value(409))
        .andExpect(jsonPath("$.message").value("OPEN以外のチケットは編集できません"));
  }

  @Test
  void RESOLVEDのチケットは編集できず409になる() throws Exception {
    Ticket ticket = createTicket(userId, agentId, TicketStatus.RESOLVED);
    MockHttpSession session = loginAs(USER_EMAIL);
    CsrfCredentials csrf = obtainCsrfToken(session);

    mockMvc.perform(patch("/api/tickets/" + ticket.getId())
            .session(session)
            .cookie(csrf.cookie())
            .header("X-XSRF-TOKEN", csrf.token())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"title\":\"タイトル\",\"description\":\"内容\",\"priority\":\"LOW\"}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.status").value(409));
  }

  @Test
  void CLOSEDのチケットは編集できず409になる() throws Exception {
    Ticket ticket = createTicket(userId, agentId, TicketStatus.CLOSED);
    MockHttpSession session = loginAs(USER_EMAIL);
    CsrfCredentials csrf = obtainCsrfToken(session);

    mockMvc.perform(patch("/api/tickets/" + ticket.getId())
            .session(session)
            .cookie(csrf.cookie())
            .header("X-XSRF-TOKEN", csrf.token())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"title\":\"タイトル\",\"description\":\"内容\",\"priority\":\"LOW\"}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.status").value(409));
  }

  @Test
  void チケット編集の不正入力は400になる() throws Exception {
    Ticket ticket = createTicket(userId, null, TicketStatus.OPEN);
    MockHttpSession session = loginAs(USER_EMAIL);
    CsrfCredentials csrf = obtainCsrfToken(session);

    mockMvc.perform(patch("/api/tickets/" + ticket.getId())
            .session(session)
            .cookie(csrf.cookie())
            .header("X-XSRF-TOKEN", csrf.token())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"title\":\"\",\"description\":\"\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value(400));
  }

  // ===== 編集時の文字数・空白バリデーション =====

  @Test
  void チケット編集でタイトルが50文字なら更新できる() throws Exception {
    Ticket ticket = createTicket(userId, null, TicketStatus.OPEN);
    MockHttpSession session = loginAs(USER_EMAIL);
    CsrfCredentials csrf = obtainCsrfToken(session);
    String title = "あ".repeat(50);
    String body = String.format("{\"title\":\"%s\",\"description\":\"内容\",\"priority\":\"LOW\"}", title);

    mockMvc.perform(patch("/api/tickets/" + ticket.getId())
            .session(session)
            .cookie(csrf.cookie())
            .header("X-XSRF-TOKEN", csrf.token())
            .contentType(MediaType.APPLICATION_JSON)
            .content(body))
        .andExpect(status().isOk());
  }

  @Test
  void チケット編集でタイトルが51文字だと400になる() throws Exception {
    Ticket ticket = createTicket(userId, null, TicketStatus.OPEN);
    MockHttpSession session = loginAs(USER_EMAIL);
    CsrfCredentials csrf = obtainCsrfToken(session);
    String title = "あ".repeat(51);
    String body = String.format("{\"title\":\"%s\",\"description\":\"内容\",\"priority\":\"LOW\"}", title);

    mockMvc.perform(patch("/api/tickets/" + ticket.getId())
            .session(session)
            .cookie(csrf.cookie())
            .header("X-XSRF-TOKEN", csrf.token())
            .contentType(MediaType.APPLICATION_JSON)
            .content(body))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("タイトルは50文字以内で入力してください"));
  }

  @Test
  void チケット編集で内容が500文字なら更新できる() throws Exception {
    Ticket ticket = createTicket(userId, null, TicketStatus.OPEN);
    MockHttpSession session = loginAs(USER_EMAIL);
    CsrfCredentials csrf = obtainCsrfToken(session);
    String description = "あ".repeat(500);
    String body = String.format("{\"title\":\"タイトル\",\"description\":\"%s\",\"priority\":\"LOW\"}", description);

    mockMvc.perform(patch("/api/tickets/" + ticket.getId())
            .session(session)
            .cookie(csrf.cookie())
            .header("X-XSRF-TOKEN", csrf.token())
            .contentType(MediaType.APPLICATION_JSON)
            .content(body))
        .andExpect(status().isOk());
  }

  @Test
  void チケット編集で内容が501文字だと400になる() throws Exception {
    Ticket ticket = createTicket(userId, null, TicketStatus.OPEN);
    MockHttpSession session = loginAs(USER_EMAIL);
    CsrfCredentials csrf = obtainCsrfToken(session);
    String description = "あ".repeat(501);
    String body = String.format("{\"title\":\"タイトル\",\"description\":\"%s\",\"priority\":\"LOW\"}", description);

    mockMvc.perform(patch("/api/tickets/" + ticket.getId())
            .session(session)
            .cookie(csrf.cookie())
            .header("X-XSRF-TOKEN", csrf.token())
            .contentType(MediaType.APPLICATION_JSON)
            .content(body))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("内容は500文字以内で入力してください"));
  }

  @Test
  void チケット編集でタイトルが空白のみだと400になる() throws Exception {
    Ticket ticket = createTicket(userId, null, TicketStatus.OPEN);
    MockHttpSession session = loginAs(USER_EMAIL);
    CsrfCredentials csrf = obtainCsrfToken(session);

    mockMvc.perform(patch("/api/tickets/" + ticket.getId())
            .session(session)
            .cookie(csrf.cookie())
            .header("X-XSRF-TOKEN", csrf.token())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"title\":\"   \",\"description\":\"内容\",\"priority\":\"LOW\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("タイトルは必須です"));
  }

  @Test
  void チケット編集で内容が空白のみだと400になる() throws Exception {
    Ticket ticket = createTicket(userId, null, TicketStatus.OPEN);
    MockHttpSession session = loginAs(USER_EMAIL);
    CsrfCredentials csrf = obtainCsrfToken(session);

    mockMvc.perform(patch("/api/tickets/" + ticket.getId())
            .session(session)
            .cookie(csrf.cookie())
            .header("X-XSRF-TOKEN", csrf.token())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"title\":\"タイトル\",\"description\":\"   \",\"priority\":\"LOW\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("内容は必須です"));
  }

  @Test
  void CSRFなしのチケット編集は403になる() throws Exception {
    Ticket ticket = createTicket(userId, null, TicketStatus.OPEN);
    MockHttpSession session = loginAs(USER_EMAIL);

    mockMvc.perform(patch("/api/tickets/" + ticket.getId())
            .session(session)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"title\":\"タイトル\",\"description\":\"内容\",\"priority\":\"LOW\"}"))
        .andExpect(status().isForbidden());
  }

  // ===== レスポンスにpasswordHashなどが含まれないこと =====

  @Test
  void チケットレスポンスにパスワード関連の文字列が含まれない() throws Exception {
    MockHttpSession session = loginAs(USER_EMAIL);
    CsrfCredentials csrf = obtainCsrfToken(session);
    String body = "{\"title\":\"タイトル\",\"description\":\"内容\",\"priority\":\"LOW\"}";

    mockMvc.perform(post("/api/tickets")
            .session(session)
            .cookie(csrf.cookie())
            .header("X-XSRF-TOKEN", csrf.token())
            .contentType(MediaType.APPLICATION_JSON)
            .content(body))
        .andExpect(status().isCreated())
        .andExpect(content().string(not(containsString("password"))));
  }

  private static org.hamcrest.Matcher<Iterable<? extends Number>> everyItemEquals(Long expected) {
    return org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.is(expected.intValue()));
  }

  // ===== AGENTのXP・レベル =====

  @Test
  void MEDIUMチケットの初回RESOLVEDでステータス更新レスポンスにXP結果が含まれる() throws Exception {
    Ticket ticket = createTicket(userId, agentId, TicketStatus.IN_PROGRESS, TicketPriority.MEDIUM);
    MockHttpSession session = loginAs(AGENT_EMAIL);
    CsrfCredentials csrf = obtainCsrfToken(session);

    mockMvc.perform(patch("/api/tickets/" + ticket.getId() + "/status")
            .session(session)
            .cookie(csrf.cookie())
            .header("X-XSRF-TOKEN", csrf.token())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"status\":\"RESOLVED\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.ticket.status").value("RESOLVED"))
        .andExpect(jsonPath("$.xpResult.awarded").value(true))
        .andExpect(jsonPath("$.xpResult.gainedExperience").value(20));
  }

  @Test
  void 担当者未設定のチケットをADMINがRESOLVEDにするとXP非付与のレスポンスになる() throws Exception {
    Ticket ticket = createTicket(userId, null, TicketStatus.IN_PROGRESS, TicketPriority.HIGH);
    MockHttpSession session = loginAs(ADMIN_EMAIL);
    CsrfCredentials csrf = obtainCsrfToken(session);

    mockMvc.perform(patch("/api/tickets/" + ticket.getId() + "/status")
            .session(session)
            .cookie(csrf.cookie())
            .header("X-XSRF-TOKEN", csrf.token())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"status\":\"RESOLVED\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.xpResult.awarded").value(false))
        .andExpect(jsonPath("$.xpResult.gainedExperience").value(0))
        .andExpect(jsonPath("$.xpResult.levelUp").value(false));
  }

  @Test
  void 再度RESOLVEDにしてもステータス更新APIレベルで二重付与されない() throws Exception {
    String dedicatedAgentEmail = "ticket-xp-double-resolve-agent@example.com";
    Long dedicatedAgentId = createIfAbsent(dedicatedAgentEmail, "二重付与確認AGENT", Role.AGENT);
    Ticket ticket = createTicket(userId, dedicatedAgentId, TicketStatus.IN_PROGRESS, TicketPriority.LOW);
    MockHttpSession session = loginAs(dedicatedAgentEmail);
    CsrfCredentials csrf = obtainCsrfToken(session);

    mockMvc.perform(patch("/api/tickets/" + ticket.getId() + "/status")
            .session(session).cookie(csrf.cookie()).header("X-XSRF-TOKEN", csrf.token())
            .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"RESOLVED\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.xpResult.awarded").value(true));

    CsrfCredentials csrf2 = obtainCsrfToken(session);
    mockMvc.perform(patch("/api/tickets/" + ticket.getId() + "/status")
            .session(session).cookie(csrf2.cookie()).header("X-XSRF-TOKEN", csrf2.token())
            .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"IN_PROGRESS\"}"))
        .andExpect(status().isOk());

    CsrfCredentials csrf3 = obtainCsrfToken(session);
    mockMvc.perform(patch("/api/tickets/" + ticket.getId() + "/status")
            .session(session).cookie(csrf3.cookie()).header("X-XSRF-TOKEN", csrf3.token())
            .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"RESOLVED\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.xpResult.awarded").value(false));

    User reloadedAgent = userRepository.findById(dedicatedAgentId).orElseThrow();
    assertThat(reloadedAgent.getExperience()).isEqualTo(10);
  }

  @Test
  void AGENTでログインしauthMeからXPとレベル情報を取得できる() throws Exception {
    String dedicatedAgentEmail = "ticket-xp-auth-me-agent@example.com";
    Long dedicatedAgentId = createIfAbsent(dedicatedAgentEmail, "auth-me確認AGENT", Role.AGENT);
    Ticket ticket = createTicket(userId, dedicatedAgentId, TicketStatus.IN_PROGRESS, TicketPriority.HIGH);
    MockHttpSession resolveSession = loginAs(dedicatedAgentEmail);
    CsrfCredentials csrf = obtainCsrfToken(resolveSession);
    mockMvc.perform(patch("/api/tickets/" + ticket.getId() + "/status")
            .session(resolveSession).cookie(csrf.cookie()).header("X-XSRF-TOKEN", csrf.token())
            .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"RESOLVED\"}"))
        .andExpect(status().isOk());

    User reloadedAgent = userRepository.findById(dedicatedAgentId).orElseThrow();
    int expectedExperience = reloadedAgent.getExperience();
    assertThat(expectedExperience).isEqualTo(30);

    MockHttpSession meSession = loginAs(dedicatedAgentEmail);
    mockMvc.perform(get("/api/auth/me").session(meSession))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.experience").value(expectedExperience))
        .andExpect(jsonPath("$.level").value(reloadedAgent.getLevel()))
        .andExpect(jsonPath("$.currentLevelExperience").value(reloadedAgent.getCurrentLevelExperience()))
        .andExpect(jsonPath("$.experienceToNextLevel").value(reloadedAgent.getExperienceToNextLevel()))
        .andExpect(jsonPath("$.experienceProgressPercentage").value(reloadedAgent.getExperienceProgressPercentage()));
  }

  @Test
  void 同時に2件のRESOLVED更新リクエストが来てもXPは1回しか付与されない() throws Exception {
    String concurrencyAgentEmail = "ticket-xp-concurrency-agent@example.com";
    Long concurrencyAgentId = createIfAbsent(concurrencyAgentEmail, "同時実行AGENT", Role.AGENT);
    Ticket ticket = createTicket(userId, concurrencyAgentId, TicketStatus.IN_PROGRESS, TicketPriority.HIGH);

    MockHttpSession sessionA = loginAs(concurrencyAgentEmail);
    CsrfCredentials csrfA = obtainCsrfToken(sessionA);
    MockHttpSession sessionB = loginAs(concurrencyAgentEmail);
    CsrfCredentials csrfB = obtainCsrfToken(sessionB);

    java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newFixedThreadPool(2);
    java.util.concurrent.Callable<Integer> requestA = () -> mockMvc.perform(patch("/api/tickets/" + ticket.getId() + "/status")
            .session(sessionA).cookie(csrfA.cookie()).header("X-XSRF-TOKEN", csrfA.token())
            .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"RESOLVED\"}"))
        .andReturn().getResponse().getStatus();
    java.util.concurrent.Callable<Integer> requestB = () -> mockMvc.perform(patch("/api/tickets/" + ticket.getId() + "/status")
            .session(sessionB).cookie(csrfB.cookie()).header("X-XSRF-TOKEN", csrfB.token())
            .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"RESOLVED\"}"))
        .andReturn().getResponse().getStatus();

    java.util.List<java.util.concurrent.Future<Integer>> futures = executor.invokeAll(java.util.List.of(requestA, requestB));
    java.util.List<Integer> statuses = new java.util.ArrayList<>();
    for (java.util.concurrent.Future<Integer> future : futures) {
      statuses.add(future.get());
    }
    executor.shutdown();

    // 悲観ロックにより2件のリクエストは直列化される。先にロックを獲得した側は200、
    // 後からロックを獲得した側はチケットが既にRESOLVEDになっているため
    // 「RESOLVED→RESOLVED」という許可されない遷移として409になる。
    // どちらの順序で実行されても、成功は必ずどちらか1件だけになる。
    assertThat(statuses).containsExactlyInAnyOrder(200, 409);

    User reloadedAgent = userRepository.findById(concurrencyAgentId).orElseThrow();
    // 同時に2回RESOLVEDへの更新が送られても、悲観ロックにより直列化され、
    // XPはHIGHの30のみ1回だけ加算される(60にはならない)。
    assertThat(reloadedAgent.getExperience()).isEqualTo(30);
  }

  // ===== 一覧のページネーション =====
  // このテストクラスは@BeforeAllで1度だけデータを準備し、以降のテストメソッド間で
  // DBの内容をロールバックしない(PER_CLASS・非トランザクション)ため、件数を厳密に
  // 検証するテストは、他のテストの影響を受けないよう専用の一意なユーザーを使う。

  @Test
  void デフォルトでは20件ずつ取得される() throws Exception {
    Long requesterId = createIfAbsent("ticket-paging-default@example.com", "ページングUSER1", Role.USER);
    for (int i = 0; i < 21; i++) {
      createTicket(requesterId, null, TicketStatus.OPEN);
    }
    MockHttpSession session = loginAs("ticket-paging-default@example.com");

    mockMvc.perform(get("/api/tickets").session(session))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(20))
        .andExpect(jsonPath("$.size").value(20))
        .andExpect(jsonPath("$.page").value(0))
        .andExpect(jsonPath("$.totalElements").value(21))
        .andExpect(jsonPath("$.totalPages").value(2))
        .andExpect(jsonPath("$.first").value(true))
        .andExpect(jsonPath("$.last").value(false));
  }

  @Test
  void 件数が21件の場合2ページ目に1件だけ含まれる() throws Exception {
    Long requesterId = createIfAbsent("ticket-paging-21@example.com", "ページングUSER2", Role.USER);
    for (int i = 0; i < 21; i++) {
      createTicket(requesterId, null, TicketStatus.OPEN);
    }
    MockHttpSession session = loginAs("ticket-paging-21@example.com");

    mockMvc.perform(get("/api/tickets").session(session).param("page", "1").param("size", "20"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(1))
        .andExpect(jsonPath("$.page").value(1))
        .andExpect(jsonPath("$.totalElements").value(21))
        .andExpect(jsonPath("$.totalPages").value(2))
        .andExpect(jsonPath("$.first").value(false))
        .andExpect(jsonPath("$.last").value(true));
  }

  @Test
  void 件数が40件は2ページになり41件は3ページになる() throws Exception {
    Long requesterId = createIfAbsent("ticket-paging-40-41@example.com", "ページングUSER3", Role.USER);
    for (int i = 0; i < 40; i++) {
      createTicket(requesterId, null, TicketStatus.OPEN);
    }
    MockHttpSession session = loginAs("ticket-paging-40-41@example.com");

    mockMvc.perform(get("/api/tickets").session(session))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(40))
        .andExpect(jsonPath("$.totalPages").value(2));

    createTicket(requesterId, null, TicketStatus.OPEN);

    mockMvc.perform(get("/api/tickets").session(session))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(41))
        .andExpect(jsonPath("$.totalPages").value(3));
  }

  @Test
  void sizeに極端に大きい値を指定しても上限の100件にクランプされる() throws Exception {
    Long requesterId = createIfAbsent("ticket-paging-cap@example.com", "ページングUSER4", Role.USER);
    createTicket(requesterId, null, TicketStatus.OPEN);
    MockHttpSession session = loginAs("ticket-paging-cap@example.com");

    mockMvc.perform(get("/api/tickets").session(session).param("size", "100000"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.size").value(100));
  }

  @Test
  void 一覧が0件の場合はtotalPagesが0でfirstもlastもtrueになる() throws Exception {
    createIfAbsent("ticket-paging-empty@example.com", "ページングUSER5", Role.USER);
    // このユーザーにはチケットを1件も作成しない。
    MockHttpSession session = loginAs("ticket-paging-empty@example.com");

    mockMvc.perform(get("/api/tickets").session(session))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(0))
        .andExpect(jsonPath("$.totalElements").value(0))
        .andExpect(jsonPath("$.totalPages").value(0))
        .andExpect(jsonPath("$.first").value(true))
        .andExpect(jsonPath("$.last").value(true));
  }

  @Test
  void ページネーション後もロールごとの取得範囲外のチケットは含まれない() throws Exception {
    Long requesterId = createIfAbsent("ticket-paging-scope@example.com", "ページングUSER6", Role.USER);
    Long otherRequesterId = createIfAbsent("ticket-paging-scope-other@example.com", "ページングUSER7", Role.USER);
    for (int i = 0; i < 5; i++) {
      createTicket(requesterId, null, TicketStatus.OPEN);
    }
    for (int i = 0; i < 5; i++) {
      createTicket(otherRequesterId, null, TicketStatus.OPEN);
    }
    MockHttpSession session = loginAs("ticket-paging-scope@example.com");

    mockMvc.perform(get("/api/tickets").session(session))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(5))
        .andExpect(jsonPath("$.content[*].requesterId", everyItemEquals(requesterId)));
  }

  @Test
  void ページネーション後も依頼者名担当者名がcontentへ含まれる() throws Exception {
    Long requesterId = createIfAbsent("ticket-paging-names@example.com", "ページングUSER8", Role.USER);
    createTicket(requesterId, agentId, TicketStatus.OPEN);
    MockHttpSession session = loginAs("ticket-paging-names@example.com");

    mockMvc.perform(get("/api/tickets").session(session))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].requesterName").value("ページングUSER8"))
        .andExpect(jsonPath("$.content[0].assigneeName").value("チケットAGENT"));
  }
}
