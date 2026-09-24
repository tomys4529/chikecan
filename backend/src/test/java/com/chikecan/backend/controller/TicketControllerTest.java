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
    Ticket ticket = new Ticket("直接作成", "内容", status, TicketPriority.MEDIUM, requesterId, assigneeId);
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

  // ===== 一覧・詳細 =====

  @Test
  void USERは自分のチケットだけ一覧に含まれる() throws Exception {
    createTicket(userId, null, TicketStatus.OPEN);
    createTicket(otherUserId, null, TicketStatus.OPEN);
    MockHttpSession session = loginAs(USER_EMAIL);

    mockMvc.perform(get("/api/tickets").session(session))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[*].requesterId", everyItemEquals(userId)));
  }

  @Test
  void 一覧レスポンスに依頼者名と担当者名が含まれる() throws Exception {
    Ticket assigned = createTicket(userId, agentId, TicketStatus.OPEN);
    Ticket unassigned = createTicket(userId, null, TicketStatus.OPEN);
    MockHttpSession session = loginAs(ADMIN_EMAIL);

    MvcResult result = mockMvc.perform(get("/api/tickets").session(session))
        .andExpect(status().isOk())
        .andReturn();

    tools.jackson.databind.JsonNode array = new tools.jackson.databind.ObjectMapper()
        .readTree(result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
    tools.jackson.databind.JsonNode assignedNode = findById(array, assigned.getId());
    tools.jackson.databind.JsonNode unassignedNode = findById(array, unassigned.getId());

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
        .andExpect(jsonPath("$[*].assigneeId", everyItemEquals(agentId)));
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
        .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
        .andExpect(jsonPath("$.requesterName").value("チケットUSER"))
        .andExpect(jsonPath("$.assigneeName").value("チケットAGENT"));

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
}
