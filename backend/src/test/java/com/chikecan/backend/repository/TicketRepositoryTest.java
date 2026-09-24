package com.chikecan.backend.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.chikecan.backend.entity.Role;
import com.chikecan.backend.entity.Ticket;
import com.chikecan.backend.entity.TicketPriority;
import com.chikecan.backend.entity.TicketStatus;
import com.chikecan.backend.entity.User;

@DataJpaTest
@ActiveProfiles("local")
class TicketRepositoryTest {

  @Autowired
  private TicketRepository ticketRepository;

  @Autowired
  private UserRepository userRepository;

  @Autowired
  private DataSource dataSource;

  private User saveUser(String email, Role role) {
    return userRepository.saveAndFlush(new User("テストユーザー", email, "hashed", role, true));
  }

  @Test
  void チケットを保存し取得できる() {
    User requester = saveUser("requester1@example.com", Role.USER);

    Ticket ticket = new Ticket("タイトル", "内容", TicketStatus.OPEN, TicketPriority.HIGH, requester.getId(), null);
    Ticket saved = ticketRepository.saveAndFlush(ticket);

    Ticket found = ticketRepository.findById(saved.getId()).orElseThrow();
    assertThat(found.getTitle()).isEqualTo("タイトル");
    assertThat(found.getRequesterId()).isEqualTo(requester.getId());
    assertThat(found.getAssigneeId()).isNull();
  }

  @Test
  void assigneeがnullでも保存できステータスと優先度が正しく復元される() {
    User requester = saveUser("requester2@example.com", Role.USER);

    Ticket ticket = new Ticket("タイトル2", "内容2", TicketStatus.IN_PROGRESS, TicketPriority.LOW, requester.getId(), null);
    Ticket saved = ticketRepository.saveAndFlush(ticket);

    Ticket found = ticketRepository.findById(saved.getId()).orElseThrow();
    assertThat(found.getAssigneeId()).isNull();
    assertThat(found.getStatus()).isEqualTo(TicketStatus.IN_PROGRESS);
    assertThat(found.getPriority()).isEqualTo(TicketPriority.LOW);
    assertThat(found.getCreatedAt()).isNotNull();
    assertThat(found.getUpdatedAt()).isNotNull();
  }

  @Test
  void requesterIdでの検索がcreatedAt降順になる() throws InterruptedException {
    User requester = saveUser("requester3@example.com", Role.USER);

    Ticket first = ticketRepository.saveAndFlush(
        new Ticket("1件目", "内容", TicketStatus.OPEN, TicketPriority.LOW, requester.getId(), null));
    Thread.sleep(10);
    Ticket second = ticketRepository.saveAndFlush(
        new Ticket("2件目", "内容", TicketStatus.OPEN, TicketPriority.LOW, requester.getId(), null));

    var results = ticketRepository.findByRequesterIdOrderByCreatedAtDesc(requester.getId());

    assertThat(results).hasSize(2);
    assertThat(results.get(0).getId()).isEqualTo(second.getId());
    assertThat(results.get(1).getId()).isEqualTo(first.getId());
  }

  @Test
  void assigneeIdでの検索とID組み合わせ検索ができる() {
    User requester = saveUser("requester4@example.com", Role.USER);
    User agent = saveUser("agent4@example.com", Role.AGENT);
    User otherAgent = saveUser("other-agent4@example.com", Role.AGENT);

    Ticket assigned = ticketRepository.saveAndFlush(
        new Ticket("担当あり", "内容", TicketStatus.IN_PROGRESS, TicketPriority.MEDIUM, requester.getId(), agent.getId()));

    var byAssignee = ticketRepository.findByAssigneeIdOrderByCreatedAtDesc(agent.getId());
    assertThat(byAssignee).hasSize(1);
    assertThat(byAssignee.get(0).getId()).isEqualTo(assigned.getId());

    assertThat(ticketRepository.findByIdAndAssigneeId(assigned.getId(), agent.getId())).isPresent();
    assertThat(ticketRepository.findByIdAndAssigneeId(assigned.getId(), otherAgent.getId())).isEmpty();
    assertThat(ticketRepository.findByIdAndRequesterId(assigned.getId(), requester.getId())).isPresent();
  }

  @Test
  void findAllByOrderByCreatedAtDescで全件がcreatedAt降順に取得できる() throws InterruptedException {
    User requester = saveUser("requester5@example.com", Role.USER);

    Ticket first = ticketRepository.saveAndFlush(
        new Ticket("A", "内容", TicketStatus.OPEN, TicketPriority.LOW, requester.getId(), null));
    Thread.sleep(10);
    Ticket second = ticketRepository.saveAndFlush(
        new Ticket("B", "内容", TicketStatus.OPEN, TicketPriority.LOW, requester.getId(), null));

    var results = ticketRepository.findAllByOrderByCreatedAtDesc();

    int firstIndex = results.indexOf(results.stream().filter(t -> t.getId().equals(first.getId())).findFirst().orElseThrow());
    int secondIndex = results.indexOf(results.stream().filter(t -> t.getId().equals(second.getId())).findFirst().orElseThrow());
    assertThat(secondIndex).isLessThan(firstIndex);
  }

  @Test
  void 不正なstatus文字列を直接INSERTするとCHECK制約違反になる() {
    User requester = saveUser("check-status@example.com", Role.USER);
    JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

    assertThatThrownBy(() -> jdbcTemplate.update(
        "INSERT INTO tickets (title, description, status, priority, requester_id, created_at, updated_at) "
            + "VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
        "タイトル", "内容", "INVALID_STATUS", "LOW", requester.getId()))
        .isInstanceOf(DataAccessException.class);
  }

  @Test
  void 不正なpriority文字列を直接INSERTするとCHECK制約違反になる() {
    User requester = saveUser("check-priority@example.com", Role.USER);
    JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

    assertThatThrownBy(() -> jdbcTemplate.update(
        "INSERT INTO tickets (title, description, status, priority, requester_id, created_at, updated_at) "
            + "VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
        "タイトル", "内容", "OPEN", "INVALID_PRIORITY", requester.getId()))
        .isInstanceOf(DataAccessException.class);
  }
}
