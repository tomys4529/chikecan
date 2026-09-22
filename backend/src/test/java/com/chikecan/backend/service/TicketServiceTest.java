package com.chikecan.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

import com.chikecan.backend.dto.TicketAssigneeUpdateRequest;
import com.chikecan.backend.dto.TicketCreateRequest;
import com.chikecan.backend.dto.TicketResponse;
import com.chikecan.backend.dto.TicketStatusUpdateRequest;
import com.chikecan.backend.entity.Role;
import com.chikecan.backend.entity.Ticket;
import com.chikecan.backend.entity.TicketPriority;
import com.chikecan.backend.entity.TicketStatus;
import com.chikecan.backend.entity.User;
import com.chikecan.backend.exception.InvalidAssigneeException;
import com.chikecan.backend.exception.InvalidStatusTransitionException;
import com.chikecan.backend.exception.TicketNotFoundException;
import com.chikecan.backend.repository.TicketRepository;
import com.chikecan.backend.repository.UserRepository;
import com.chikecan.backend.security.AppUserDetails;

@ExtendWith(MockitoExtension.class)
class TicketServiceTest {

  @Mock
  private TicketRepository ticketRepository;

  @Mock
  private UserRepository userRepository;

  private TicketService ticketService;

  private AppUserDetails principalOf(Long id, Role role) {
    User user = new User("テストユーザー", "user" + id + "@example.com", "hashed", role, true);
    ReflectionTestUtils.setField(user, "id", id);
    return new AppUserDetails(user);
  }

  private Ticket ticketWith(Long id, TicketStatus status, Long requesterId, Long assigneeId) {
    Ticket ticket = new Ticket("タイトル", "内容", status, TicketPriority.MEDIUM, requesterId, assigneeId);
    ReflectionTestUtils.setField(ticket, "id", id);
    return ticket;
  }

  @Test
  void 登録時にrequesterIdがログインユーザーIDに固定される() {
    ticketService = new TicketService(ticketRepository, userRepository);
    AppUserDetails principal = principalOf(10L, Role.USER);

    TicketCreateRequest request = new TicketCreateRequest();
    request.setTitle("タイトル");
    request.setDescription("内容");
    request.setPriority(TicketPriority.HIGH);

    when(ticketRepository.save(any(Ticket.class))).thenAnswer(invocation -> invocation.getArgument(0));

    ticketService.create(request, principal);

    ArgumentCaptor<Ticket> captor = ArgumentCaptor.forClass(Ticket.class);
    verify(ticketRepository).save(captor.capture());
    assertThat(captor.getValue().getRequesterId()).isEqualTo(10L);
    assertThat(captor.getValue().getAssigneeId()).isNull();
    assertThat(captor.getValue().getStatus()).isEqualTo(TicketStatus.OPEN);
  }

  @Test
  void USERの一覧はrequesterIdで検索される() {
    ticketService = new TicketService(ticketRepository, userRepository);
    AppUserDetails principal = principalOf(1L, Role.USER);
    when(ticketRepository.findByRequesterIdOrderByCreatedAtDesc(1L)).thenReturn(List.of());

    ticketService.list(principal);

    verify(ticketRepository).findByRequesterIdOrderByCreatedAtDesc(1L);
    verify(ticketRepository, never()).findAllByOrderByCreatedAtDesc();
  }

  @Test
  void AGENTの一覧はassigneeIdで検索される() {
    ticketService = new TicketService(ticketRepository, userRepository);
    AppUserDetails principal = principalOf(2L, Role.AGENT);
    when(ticketRepository.findByAssigneeIdOrderByCreatedAtDesc(2L)).thenReturn(List.of());

    ticketService.list(principal);

    verify(ticketRepository).findByAssigneeIdOrderByCreatedAtDesc(2L);
  }

  @Test
  void ADMINの一覧は全件検索される() {
    ticketService = new TicketService(ticketRepository, userRepository);
    AppUserDetails principal = principalOf(3L, Role.ADMIN);
    when(ticketRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of());

    ticketService.list(principal);

    verify(ticketRepository).findAllByOrderByCreatedAtDesc();
  }

  @Test
  void 他人のチケット詳細を取得しようとするとTicketNotFoundExceptionになる() {
    ticketService = new TicketService(ticketRepository, userRepository);
    AppUserDetails principal = principalOf(1L, Role.USER);
    when(ticketRepository.findByIdAndRequesterId(100L, 1L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> ticketService.getDetail(100L, principal))
        .isInstanceOf(TicketNotFoundException.class);
  }

  @Test
  void 許可された遷移は成功する() {
    ticketService = new TicketService(ticketRepository, userRepository);
    AppUserDetails principal = principalOf(2L, Role.AGENT);
    Ticket ticket = ticketWith(50L, TicketStatus.OPEN, 1L, 2L);
    when(ticketRepository.findByIdAndAssigneeId(50L, 2L)).thenReturn(Optional.of(ticket));

    TicketStatusUpdateRequest request = new TicketStatusUpdateRequest();
    request.setStatus(TicketStatus.IN_PROGRESS);

    TicketResponse response = ticketService.updateStatus(50L, request, principal);

    assertThat(response.getStatus()).isEqualTo(TicketStatus.IN_PROGRESS);
  }

  @Test
  void 許可されない遷移はInvalidStatusTransitionExceptionになる() {
    ticketService = new TicketService(ticketRepository, userRepository);
    AppUserDetails principal = principalOf(2L, Role.AGENT);
    Ticket ticket = ticketWith(51L, TicketStatus.OPEN, 1L, 2L);
    when(ticketRepository.findByIdAndAssigneeId(51L, 2L)).thenReturn(Optional.of(ticket));

    TicketStatusUpdateRequest request = new TicketStatusUpdateRequest();
    request.setStatus(TicketStatus.CLOSED);

    assertThatThrownBy(() -> ticketService.updateStatus(51L, request, principal))
        .isInstanceOf(InvalidStatusTransitionException.class);
  }

  @Test
  void ADMINも不正なステータス遷移は拒否される() {
    ticketService = new TicketService(ticketRepository, userRepository);
    AppUserDetails principal = principalOf(9L, Role.ADMIN);
    Ticket ticket = ticketWith(52L, TicketStatus.CLOSED, 1L, 2L);
    when(ticketRepository.findById(52L)).thenReturn(Optional.of(ticket));

    TicketStatusUpdateRequest request = new TicketStatusUpdateRequest();
    request.setStatus(TicketStatus.OPEN);

    assertThatThrownBy(() -> ticketService.updateStatus(52L, request, principal))
        .isInstanceOf(InvalidStatusTransitionException.class);
  }

  @Test
  void 未担当または別担当のAGENTはTicketNotFoundExceptionになる() {
    ticketService = new TicketService(ticketRepository, userRepository);
    AppUserDetails principal = principalOf(3L, Role.AGENT);
    when(ticketRepository.findByIdAndAssigneeId(60L, 3L)).thenReturn(Optional.empty());

    TicketStatusUpdateRequest request = new TicketStatusUpdateRequest();
    request.setStatus(TicketStatus.IN_PROGRESS);

    assertThatThrownBy(() -> ticketService.updateStatus(60L, request, principal))
        .isInstanceOf(TicketNotFoundException.class);
  }

  @Test
  void USERがステータス変更しようとするとAccessDeniedExceptionになる() {
    ticketService = new TicketService(ticketRepository, userRepository);
    AppUserDetails principal = principalOf(1L, Role.USER);

    TicketStatusUpdateRequest request = new TicketStatusUpdateRequest();
    request.setStatus(TicketStatus.IN_PROGRESS);

    assertThatThrownBy(() -> ticketService.updateStatus(70L, request, principal))
        .isInstanceOf(AccessDeniedException.class);
  }

  @Test
  void ADMIN以外が担当者設定しようとするとAccessDeniedExceptionになる() {
    ticketService = new TicketService(ticketRepository, userRepository);
    AppUserDetails principal = principalOf(2L, Role.AGENT);

    TicketAssigneeUpdateRequest request = new TicketAssigneeUpdateRequest();
    request.setAssigneeId(5L);

    assertThatThrownBy(() -> ticketService.updateAssignee(80L, request, principal))
        .isInstanceOf(AccessDeniedException.class);
  }

  @Test
  void 存在しない担当者IDはInvalidAssigneeExceptionになる() {
    ticketService = new TicketService(ticketRepository, userRepository);
    AppUserDetails principal = principalOf(9L, Role.ADMIN);
    Ticket ticket = ticketWith(81L, TicketStatus.OPEN, 1L, null);
    when(ticketRepository.findById(81L)).thenReturn(Optional.of(ticket));
    when(userRepository.findById(999L)).thenReturn(Optional.empty());

    TicketAssigneeUpdateRequest request = new TicketAssigneeUpdateRequest();
    request.setAssigneeId(999L);

    assertThatThrownBy(() -> ticketService.updateAssignee(81L, request, principal))
        .isInstanceOf(InvalidAssigneeException.class);
  }

  @Test
  void USERやADMINを担当者に指定するとInvalidAssigneeExceptionになる() {
    ticketService = new TicketService(ticketRepository, userRepository);
    AppUserDetails principal = principalOf(9L, Role.ADMIN);
    Ticket ticket = ticketWith(82L, TicketStatus.OPEN, 1L, null);
    when(ticketRepository.findById(82L)).thenReturn(Optional.of(ticket));

    User notAgent = new User("USERさん", "notagent@example.com", "hashed", Role.USER, true);
    ReflectionTestUtils.setField(notAgent, "id", 20L);
    when(userRepository.findById(20L)).thenReturn(Optional.of(notAgent));

    TicketAssigneeUpdateRequest request = new TicketAssigneeUpdateRequest();
    request.setAssigneeId(20L);

    assertThatThrownBy(() -> ticketService.updateAssignee(82L, request, principal))
        .isInstanceOf(InvalidAssigneeException.class);
  }

  @Test
  void assigneeIdにnullを指定すると担当解除できる() {
    ticketService = new TicketService(ticketRepository, userRepository);
    AppUserDetails principal = principalOf(9L, Role.ADMIN);
    Ticket ticket = ticketWith(83L, TicketStatus.OPEN, 1L, 2L);
    when(ticketRepository.findById(83L)).thenReturn(Optional.of(ticket));

    TicketAssigneeUpdateRequest request = new TicketAssigneeUpdateRequest();
    request.setAssigneeId(null);

    TicketResponse response = ticketService.updateAssignee(83L, request, principal);

    assertThat(response.getAssigneeId()).isNull();
  }

  @Test
  void ADMINは有効な担当者を設定できる() {
    ticketService = new TicketService(ticketRepository, userRepository);
    AppUserDetails principal = principalOf(9L, Role.ADMIN);
    Ticket ticket = ticketWith(84L, TicketStatus.OPEN, 1L, null);
    when(ticketRepository.findById(84L)).thenReturn(Optional.of(ticket));

    User agent = new User("担当者", "agent84@example.com", "hashed", Role.AGENT, true);
    ReflectionTestUtils.setField(agent, "id", 30L);
    when(userRepository.findById(30L)).thenReturn(Optional.of(agent));

    TicketAssigneeUpdateRequest request = new TicketAssigneeUpdateRequest();
    request.setAssigneeId(30L);

    TicketResponse response = ticketService.updateAssignee(84L, request, principal);

    assertThat(response.getAssigneeId()).isEqualTo(30L);
  }
}
