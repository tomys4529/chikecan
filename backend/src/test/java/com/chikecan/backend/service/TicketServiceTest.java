package com.chikecan.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

import com.chikecan.backend.dto.PageResponse;
import com.chikecan.backend.dto.TicketAssigneeUpdateRequest;
import com.chikecan.backend.dto.TicketCreateRequest;
import com.chikecan.backend.dto.TicketResponse;
import com.chikecan.backend.dto.TicketStatusUpdateRequest;
import com.chikecan.backend.dto.TicketStatusUpdateResponse;
import com.chikecan.backend.dto.TicketUpdateRequest;
import com.chikecan.backend.entity.Role;
import com.chikecan.backend.entity.Ticket;
import com.chikecan.backend.entity.TicketPriority;
import com.chikecan.backend.entity.TicketStatus;
import com.chikecan.backend.entity.User;
import com.chikecan.backend.exception.InvalidAssigneeException;
import com.chikecan.backend.exception.InvalidStatusTransitionException;
import com.chikecan.backend.exception.TicketEditNotAllowedException;
import com.chikecan.backend.exception.TicketNotFoundException;
import com.chikecan.backend.repository.TicketRepository;
import com.chikecan.backend.repository.UserRepository;
import com.chikecan.backend.security.AppUserDetails;

@ExtendWith(MockitoExtension.class)
class TicketServiceTest {

  // TicketService内のTICKET_LIST_SORTと同じ並び順(createdAt降順・id降順)。
  // privateフィールドのため参照できず、テスト側でも同じ値を再構築して比較する。
  private static final Sort TICKET_LIST_SORT = Sort.by(Sort.Direction.DESC, "createdAt", "id");

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

  private User userWith(Long id, String name) {
    User user = new User(name, "user" + id + "@example.com", "hashed", Role.USER, true);
    ReflectionTestUtils.setField(user, "id", id);
    return user;
  }

  private User userWith(Long id, String name, Role role, int experience) {
    User user = new User(name, "user" + id + "@example.com", "hashed", role, true);
    ReflectionTestUtils.setField(user, "id", id);
    user.addExperience(experience);
    return user;
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
  void 登録時のrequesterNameはprincipalの名前がそのまま使われユーザー検索は行われない() {
    ticketService = new TicketService(ticketRepository, userRepository);
    AppUserDetails principal = principalOf(10L, Role.USER);

    TicketCreateRequest request = new TicketCreateRequest();
    request.setTitle("タイトル");
    request.setDescription("内容");
    request.setPriority(TicketPriority.HIGH);

    when(ticketRepository.save(any(Ticket.class))).thenAnswer(invocation -> invocation.getArgument(0));

    TicketResponse response = ticketService.create(request, principal);

    assertThat(response.getRequesterName()).isEqualTo("テストユーザー");
    assertThat(response.getAssigneeName()).isNull();
    verify(userRepository, never()).findAllById(anyIterable());
    verify(userRepository, never()).findById(any());
  }

  @Test
  void USERの一覧はrequesterIdとPageableで検索される() {
    ticketService = new TicketService(ticketRepository, userRepository);
    AppUserDetails principal = principalOf(1L, Role.USER);
    when(ticketRepository.findByRequesterId(eq(1L), any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of()));

    ticketService.list(principal, 0, 20);

    verify(ticketRepository).findByRequesterId(eq(1L), any(Pageable.class));
    verify(ticketRepository, never()).findAll(any(Pageable.class));
    verify(ticketRepository, never()).findByAssigneeId(any(), any());
  }

  @Test
  void AGENTの一覧はassigneeIdとPageableで検索される() {
    ticketService = new TicketService(ticketRepository, userRepository);
    AppUserDetails principal = principalOf(2L, Role.AGENT);
    when(ticketRepository.findByAssigneeId(eq(2L), any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of()));

    ticketService.list(principal, 0, 20);

    verify(ticketRepository).findByAssigneeId(eq(2L), any(Pageable.class));
  }

  @Test
  void ADMINの一覧はPageableで全件検索される() {
    ticketService = new TicketService(ticketRepository, userRepository);
    AppUserDetails principal = principalOf(3L, Role.ADMIN);
    when(ticketRepository.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

    ticketService.list(principal, 0, 20);

    verify(ticketRepository).findAll(any(Pageable.class));
  }

  @Test
  void 並び順はcreatedAt降順id降順で指定される() {
    ticketService = new TicketService(ticketRepository, userRepository);
    AppUserDetails principal = principalOf(3L, Role.ADMIN);
    when(ticketRepository.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

    ticketService.list(principal, 0, 20);

    ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
    verify(ticketRepository).findAll(captor.capture());
    Sort sort = captor.getValue().getSort();
    assertThat(sort.getOrderFor("createdAt").getDirection()).isEqualTo(Sort.Direction.DESC);
    assertThat(sort.getOrderFor("id").getDirection()).isEqualTo(Sort.Direction.DESC);
  }

  @Test
  void sizeが1未満の場合はデフォルトの20件にクランプされる() {
    ticketService = new TicketService(ticketRepository, userRepository);
    AppUserDetails principal = principalOf(3L, Role.ADMIN);
    when(ticketRepository.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

    ticketService.list(principal, 0, 0);

    ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
    verify(ticketRepository).findAll(captor.capture());
    assertThat(captor.getValue().getPageSize()).isEqualTo(20);
  }

  @Test
  void sizeが上限を超える場合は100件にクランプされる() {
    ticketService = new TicketService(ticketRepository, userRepository);
    AppUserDetails principal = principalOf(3L, Role.ADMIN);
    when(ticketRepository.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

    ticketService.list(principal, 0, 100_000);

    ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
    verify(ticketRepository).findAll(captor.capture());
    assertThat(captor.getValue().getPageSize()).isEqualTo(100);
  }

  @Test
  void pageが負数の場合は0ページ目にクランプされる() {
    ticketService = new TicketService(ticketRepository, userRepository);
    AppUserDetails principal = principalOf(3L, Role.ADMIN);
    when(ticketRepository.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

    ticketService.list(principal, -5, 20);

    ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
    verify(ticketRepository).findAll(captor.capture());
    assertThat(captor.getValue().getPageNumber()).isEqualTo(0);
  }

  @Test
  void 一覧取得は依頼者名担当者名を含みユーザー取得はページ内チケット件数によらず1回だけ発行される() {
    ticketService = new TicketService(ticketRepository, userRepository);
    AppUserDetails principal = principalOf(9L, Role.ADMIN);

    Ticket ticketA = ticketWith(100L, TicketStatus.OPEN, 1L, 2L);
    Ticket ticketB = ticketWith(101L, TicketStatus.OPEN, 1L, null);
    Ticket ticketC = ticketWith(102L, TicketStatus.OPEN, 3L, 2L);
    when(ticketRepository.findAll(any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(ticketA, ticketB, ticketC)));

    User requester1 = userWith(1L, "依頼者イチ");
    User requester3 = userWith(3L, "依頼者サン");
    User assignee2 = userWith(2L, "担当者ニ");
    // 3件のチケット・重複を含む3種類のユーザーIDに対し、findAllByIdは1回だけ呼ばれる想定。
    when(userRepository.findAllById(any())).thenReturn(List.of(requester1, requester3, assignee2));

    PageResponse<TicketResponse> pageResponse = ticketService.list(principal, 0, 20);
    List<TicketResponse> responses = pageResponse.getContent();

    assertThat(responses).hasSize(3);
    assertThat(responses.get(0).getRequesterName()).isEqualTo("依頼者イチ");
    assertThat(responses.get(0).getAssigneeName()).isEqualTo("担当者ニ");
    assertThat(responses.get(1).getRequesterName()).isEqualTo("依頼者イチ");
    assertThat(responses.get(1).getAssigneeName()).isNull();
    assertThat(responses.get(2).getRequesterName()).isEqualTo("依頼者サン");
    assertThat(responses.get(2).getAssigneeName()).isEqualTo("担当者ニ");

    verify(userRepository, times(1)).findAllById(any());
    verify(userRepository, never()).findById(any());
  }

  @Test
  void 一覧が0件の場合はユーザー取得を発行しない() {
    ticketService = new TicketService(ticketRepository, userRepository);
    AppUserDetails principal = principalOf(9L, Role.ADMIN);
    // totalElements=0であってもPageableは20件ページのまま(unpaged扱いにしない)ことで
    // 本番のfindAll(Pageable)の挙動を正しく再現する。
    when(ticketRepository.findAll(any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

    PageResponse<TicketResponse> pageResponse = ticketService.list(principal, 0, 20);

    assertThat(pageResponse.getContent()).isEmpty();
    assertThat(pageResponse.getTotalElements()).isZero();
    assertThat(pageResponse.getTotalPages()).isZero();
    assertThat(pageResponse.isFirst()).isTrue();
    assertThat(pageResponse.isLast()).isTrue();
    verify(userRepository, never()).findAllById(any());
  }

  @Test
  void 件数が21件を20件ずつに分けると1ページ目は20件で2ページ目は1件になりtotalElementsとtotalPagesが正しい() {
    ticketService = new TicketService(ticketRepository, userRepository);
    AppUserDetails principal = principalOf(9L, Role.ADMIN);

    List<Ticket> firstPageTickets = java.util.stream.IntStream.range(0, 20)
        .mapToObj(i -> ticketWith((long) (200 + i), TicketStatus.OPEN, 1L, null))
        .toList();
    when(ticketRepository.findAll(eq(PageRequest.of(0, 20, TICKET_LIST_SORT))))
        .thenReturn(new PageImpl<>(firstPageTickets, PageRequest.of(0, 20, TICKET_LIST_SORT), 21));
    when(userRepository.findAllById(any())).thenReturn(List.of(userWith(1L, "依頼者太郎")));

    PageResponse<TicketResponse> firstPage = ticketService.list(principal, 0, 20);

    assertThat(firstPage.getContent()).hasSize(20);
    assertThat(firstPage.getTotalElements()).isEqualTo(21);
    assertThat(firstPage.getTotalPages()).isEqualTo(2);
    assertThat(firstPage.isFirst()).isTrue();
    assertThat(firstPage.isLast()).isFalse();

    Ticket lastTicket = ticketWith(220L, TicketStatus.OPEN, 1L, null);
    when(ticketRepository.findAll(eq(PageRequest.of(1, 20, TICKET_LIST_SORT))))
        .thenReturn(new PageImpl<>(List.of(lastTicket), PageRequest.of(1, 20, TICKET_LIST_SORT), 21));

    PageResponse<TicketResponse> secondPage = ticketService.list(principal, 1, 20);

    assertThat(secondPage.getContent()).hasSize(1);
    assertThat(secondPage.getTotalElements()).isEqualTo(21);
    assertThat(secondPage.getTotalPages()).isEqualTo(2);
    assertThat(secondPage.isFirst()).isFalse();
    assertThat(secondPage.isLast()).isTrue();
  }

  @Test
  void 件数が40件の場合は2ページになる() {
    ticketService = new TicketService(ticketRepository, userRepository);
    AppUserDetails principal = principalOf(9L, Role.ADMIN);
    when(ticketRepository.findAll(any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 40));

    PageResponse<TicketResponse> pageResponse = ticketService.list(principal, 0, 20);

    assertThat(pageResponse.getTotalPages()).isEqualTo(2);
  }

  @Test
  void 件数が41件の場合は3ページになる() {
    ticketService = new TicketService(ticketRepository, userRepository);
    AppUserDetails principal = principalOf(9L, Role.ADMIN);
    when(ticketRepository.findAll(any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 41));

    PageResponse<TicketResponse> pageResponse = ticketService.list(principal, 0, 20);

    assertThat(pageResponse.getTotalPages()).isEqualTo(3);
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
  void チケット詳細に依頼者名と担当者名が含まれる() {
    ticketService = new TicketService(ticketRepository, userRepository);
    AppUserDetails principal = principalOf(9L, Role.ADMIN);
    Ticket ticket = ticketWith(40L, TicketStatus.OPEN, 1L, 2L);
    when(ticketRepository.findById(40L)).thenReturn(Optional.of(ticket));
    when(userRepository.findAllById(any()))
        .thenReturn(List.of(userWith(1L, "依頼者太郎"), userWith(2L, "担当花子")));

    TicketResponse response = ticketService.getDetail(40L, principal);

    assertThat(response.getRequesterName()).isEqualTo("依頼者太郎");
    assertThat(response.getAssigneeName()).isEqualTo("担当花子");
  }

  @Test
  void 担当者未設定のチケット詳細はassigneeNameがnullになる() {
    ticketService = new TicketService(ticketRepository, userRepository);
    AppUserDetails principal = principalOf(9L, Role.ADMIN);
    Ticket ticket = ticketWith(41L, TicketStatus.OPEN, 1L, null);
    when(ticketRepository.findById(41L)).thenReturn(Optional.of(ticket));
    when(userRepository.findAllById(any())).thenReturn(List.of(userWith(1L, "依頼者太郎")));

    TicketResponse response = ticketService.getDetail(41L, principal);

    assertThat(response.getRequesterName()).isEqualTo("依頼者太郎");
    assertThat(response.getAssigneeName()).isNull();
  }

  @Test
  void 許可された遷移は成功する() {
    ticketService = new TicketService(ticketRepository, userRepository);
    AppUserDetails principal = principalOf(2L, Role.AGENT);
    Ticket ticket = ticketWith(50L, TicketStatus.OPEN, 1L, 2L);
    when(ticketRepository.findByIdAndAssigneeIdForUpdate(50L, 2L)).thenReturn(Optional.of(ticket));
    when(userRepository.findAllById(any()))
        .thenReturn(List.of(userWith(1L, "依頼者太郎"), userWith(2L, "担当花子")));

    TicketStatusUpdateRequest request = new TicketStatusUpdateRequest();
    request.setStatus(TicketStatus.IN_PROGRESS);

    TicketStatusUpdateResponse response = ticketService.updateStatus(50L, request, principal);

    assertThat(response.getTicket().getStatus()).isEqualTo(TicketStatus.IN_PROGRESS);
    assertThat(response.getTicket().getRequesterName()).isEqualTo("依頼者太郎");
    assertThat(response.getTicket().getAssigneeName()).isEqualTo("担当花子");
    // IN_PROGRESSへの変更ではXP判定を行わない。
    assertThat(response.getXpResult().isAwarded()).isFalse();
  }

  @Test
  void 許可されない遷移はInvalidStatusTransitionExceptionになる() {
    ticketService = new TicketService(ticketRepository, userRepository);
    AppUserDetails principal = principalOf(2L, Role.AGENT);
    Ticket ticket = ticketWith(51L, TicketStatus.OPEN, 1L, 2L);
    when(ticketRepository.findByIdAndAssigneeIdForUpdate(51L, 2L)).thenReturn(Optional.of(ticket));

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
    when(ticketRepository.findByIdForUpdate(52L)).thenReturn(Optional.of(ticket));

    TicketStatusUpdateRequest request = new TicketStatusUpdateRequest();
    request.setStatus(TicketStatus.OPEN);

    assertThatThrownBy(() -> ticketService.updateStatus(52L, request, principal))
        .isInstanceOf(InvalidStatusTransitionException.class);
  }

  @Test
  void 未担当または別担当のAGENTはTicketNotFoundExceptionになる() {
    ticketService = new TicketService(ticketRepository, userRepository);
    AppUserDetails principal = principalOf(3L, Role.AGENT);
    when(ticketRepository.findByIdAndAssigneeIdForUpdate(60L, 3L)).thenReturn(Optional.empty());

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
  void assigneeIdにnullを指定すると担当解除できアサイニー名もnullになる() {
    ticketService = new TicketService(ticketRepository, userRepository);
    AppUserDetails principal = principalOf(9L, Role.ADMIN);
    Ticket ticket = ticketWith(83L, TicketStatus.OPEN, 1L, 2L);
    when(ticketRepository.findById(83L)).thenReturn(Optional.of(ticket));
    when(userRepository.findById(1L)).thenReturn(Optional.of(userWith(1L, "依頼者太郎")));

    TicketAssigneeUpdateRequest request = new TicketAssigneeUpdateRequest();
    request.setAssigneeId(null);

    TicketResponse response = ticketService.updateAssignee(83L, request, principal);

    assertThat(response.getAssigneeId()).isNull();
    assertThat(response.getAssigneeName()).isNull();
    assertThat(response.getRequesterName()).isEqualTo("依頼者太郎");
  }

  @Test
  void ADMINは有効な担当者を設定でき担当者名がレスポンスに反映される() {
    ticketService = new TicketService(ticketRepository, userRepository);
    AppUserDetails principal = principalOf(9L, Role.ADMIN);
    Ticket ticket = ticketWith(84L, TicketStatus.OPEN, 1L, null);
    when(ticketRepository.findById(84L)).thenReturn(Optional.of(ticket));
    when(userRepository.findById(1L)).thenReturn(Optional.of(userWith(1L, "依頼者太郎")));

    User agent = new User("担当者", "agent84@example.com", "hashed", Role.AGENT, true);
    ReflectionTestUtils.setField(agent, "id", 30L);
    when(userRepository.findById(30L)).thenReturn(Optional.of(agent));

    TicketAssigneeUpdateRequest request = new TicketAssigneeUpdateRequest();
    request.setAssigneeId(30L);

    TicketResponse response = ticketService.updateAssignee(84L, request, principal);

    assertThat(response.getAssigneeId()).isEqualTo(30L);
    assertThat(response.getAssigneeName()).isEqualTo("担当者");
    assertThat(response.getRequesterName()).isEqualTo("依頼者太郎");
    // 担当者検証で既に取得済みのUserを再利用するため、findByIdは(30L用の1回のみ)。
    // findAllByIdはこのメソッド内では使用しない(1件ずつの直接取得で十分なため)。
    verify(userRepository, never()).findAllById(any());
  }

  // ===== チケット内容編集(USER本人・OPENのみ) =====

  private TicketUpdateRequest updateRequest(String title, String description, TicketPriority priority) {
    TicketUpdateRequest request = new TicketUpdateRequest();
    request.setTitle(title);
    request.setDescription(description);
    request.setPriority(priority);
    return request;
  }

  @Test
  void USER本人は自分のOPENチケットのタイトル内容優先度を更新できる() {
    ticketService = new TicketService(ticketRepository, userRepository);
    AppUserDetails principal = principalOf(1L, Role.USER);
    Ticket ticket = ticketWith(90L, TicketStatus.OPEN, 1L, 2L);
    when(ticketRepository.findByIdAndRequesterId(90L, 1L)).thenReturn(Optional.of(ticket));
    when(userRepository.findAllById(any()))
        .thenReturn(List.of(userWith(1L, "依頼者太郎"), userWith(2L, "担当花子")));

    TicketResponse response = ticketService.updateContent(90L, updateRequest("新タイトル", "新内容", TicketPriority.HIGH), principal);

    assertThat(response.getTitle()).isEqualTo("新タイトル");
    assertThat(response.getDescription()).isEqualTo("新内容");
    assertThat(response.getPriority()).isEqualTo(TicketPriority.HIGH);
    // requesterId・assigneeId・status・createdAtは変更されない。
    assertThat(response.getRequesterId()).isEqualTo(1L);
    assertThat(response.getAssigneeId()).isEqualTo(2L);
    assertThat(response.getStatus()).isEqualTo(TicketStatus.OPEN);
    assertThat(response.getRequesterName()).isEqualTo("依頼者太郎");
    assertThat(response.getAssigneeName()).isEqualTo("担当花子");
  }

  @Test
  void 他人のチケットは更新できずTicketNotFoundExceptionになる() {
    ticketService = new TicketService(ticketRepository, userRepository);
    AppUserDetails principal = principalOf(1L, Role.USER);
    when(ticketRepository.findByIdAndRequesterId(91L, 1L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> ticketService.updateContent(91L, updateRequest("t", "d", TicketPriority.LOW), principal))
        .isInstanceOf(TicketNotFoundException.class);
  }

  @Test
  void AGENTが直接呼び出してもAccessDeniedExceptionになる() {
    ticketService = new TicketService(ticketRepository, userRepository);
    AppUserDetails principal = principalOf(2L, Role.AGENT);

    assertThatThrownBy(() -> ticketService.updateContent(92L, updateRequest("t", "d", TicketPriority.LOW), principal))
        .isInstanceOf(AccessDeniedException.class);
    verify(ticketRepository, never()).findByIdAndRequesterId(any(), any());
  }

  @Test
  void ADMINが直接呼び出してもAccessDeniedExceptionになる() {
    ticketService = new TicketService(ticketRepository, userRepository);
    AppUserDetails principal = principalOf(9L, Role.ADMIN);

    assertThatThrownBy(() -> ticketService.updateContent(93L, updateRequest("t", "d", TicketPriority.LOW), principal))
        .isInstanceOf(AccessDeniedException.class);
  }

  @Test
  void OPEN以外のチケットはTicketEditNotAllowedExceptionになる() {
    ticketService = new TicketService(ticketRepository, userRepository);
    AppUserDetails principal = principalOf(1L, Role.USER);
    Ticket ticket = ticketWith(94L, TicketStatus.IN_PROGRESS, 1L, null);
    when(ticketRepository.findByIdAndRequesterId(94L, 1L)).thenReturn(Optional.of(ticket));

    assertThatThrownBy(() -> ticketService.updateContent(94L, updateRequest("t", "d", TicketPriority.LOW), principal))
        .isInstanceOf(TicketEditNotAllowedException.class);
  }

  // ===== AGENTのXP・レベル =====

  private TicketStatusUpdateRequest resolveRequest() {
    TicketStatusUpdateRequest request = new TicketStatusUpdateRequest();
    request.setStatus(TicketStatus.RESOLVED);
    return request;
  }

  @Test
  void LOWチケットの初回RESOLVEDで10XP付与される() {
    ticketService = new TicketService(ticketRepository, userRepository);
    AppUserDetails principal = principalOf(2L, Role.AGENT);
    Ticket ticket = ticketWith(200L, TicketStatus.IN_PROGRESS, 1L, 2L);
    ReflectionTestUtils.setField(ticket, "priority", TicketPriority.LOW);
    when(ticketRepository.findByIdAndAssigneeIdForUpdate(200L, 2L)).thenReturn(Optional.of(ticket));
    User agent = userWith(2L, "担当AGENT", Role.AGENT, 0);
    when(userRepository.findById(2L)).thenReturn(Optional.of(agent));
    when(userRepository.findAllById(any())).thenReturn(List.of(agent));

    TicketStatusUpdateResponse response = ticketService.updateStatus(200L, resolveRequest(), principal);

    assertThat(response.getXpResult().isAwarded()).isTrue();
    assertThat(response.getXpResult().getGainedExperience()).isEqualTo(10);
    assertThat(agent.getExperience()).isEqualTo(10);
    assertThat(ticket.isXpAwarded()).isTrue();
  }

  @Test
  void MEDIUMチケットの初回RESOLVEDで20XP付与される() {
    ticketService = new TicketService(ticketRepository, userRepository);
    AppUserDetails principal = principalOf(2L, Role.AGENT);
    Ticket ticket = ticketWith(201L, TicketStatus.IN_PROGRESS, 1L, 2L);
    ReflectionTestUtils.setField(ticket, "priority", TicketPriority.MEDIUM);
    when(ticketRepository.findByIdAndAssigneeIdForUpdate(201L, 2L)).thenReturn(Optional.of(ticket));
    User agent = userWith(2L, "担当AGENT", Role.AGENT, 0);
    when(userRepository.findById(2L)).thenReturn(Optional.of(agent));
    when(userRepository.findAllById(any())).thenReturn(List.of(agent));

    TicketStatusUpdateResponse response = ticketService.updateStatus(201L, resolveRequest(), principal);

    assertThat(response.getXpResult().getGainedExperience()).isEqualTo(20);
    assertThat(agent.getExperience()).isEqualTo(20);
  }

  @Test
  void HIGHチケットの初回RESOLVEDで30XP付与される() {
    ticketService = new TicketService(ticketRepository, userRepository);
    AppUserDetails principal = principalOf(2L, Role.AGENT);
    Ticket ticket = ticketWith(202L, TicketStatus.IN_PROGRESS, 1L, 2L);
    ReflectionTestUtils.setField(ticket, "priority", TicketPriority.HIGH);
    when(ticketRepository.findByIdAndAssigneeIdForUpdate(202L, 2L)).thenReturn(Optional.of(ticket));
    User agent = userWith(2L, "担当AGENT", Role.AGENT, 0);
    when(userRepository.findById(2L)).thenReturn(Optional.of(agent));
    when(userRepository.findAllById(any())).thenReturn(List.of(agent));

    TicketStatusUpdateResponse response = ticketService.updateStatus(202L, resolveRequest(), principal);

    assertThat(response.getXpResult().getGainedExperience()).isEqualTo(30);
    assertThat(agent.getExperience()).isEqualTo(30);
  }

  @Test
  void 既存の累計XPへ加算される() {
    ticketService = new TicketService(ticketRepository, userRepository);
    AppUserDetails principal = principalOf(2L, Role.AGENT);
    Ticket ticket = ticketWith(203L, TicketStatus.IN_PROGRESS, 1L, 2L);
    ReflectionTestUtils.setField(ticket, "priority", TicketPriority.LOW);
    when(ticketRepository.findByIdAndAssigneeIdForUpdate(203L, 2L)).thenReturn(Optional.of(ticket));
    User agent = userWith(2L, "担当AGENT", Role.AGENT, 45);
    when(userRepository.findById(2L)).thenReturn(Optional.of(agent));
    when(userRepository.findAllById(any())).thenReturn(List.of(agent));

    TicketStatusUpdateResponse response = ticketService.updateStatus(203L, resolveRequest(), principal);

    assertThat(agent.getExperience()).isEqualTo(55);
    assertThat(response.getXpResult().getTotalExperience()).isEqualTo(55);
  }

  @Test
  void 累計XPが100に到達するとLevel2かつレベルアップと判定される() {
    ticketService = new TicketService(ticketRepository, userRepository);
    AppUserDetails principal = principalOf(2L, Role.AGENT);
    Ticket ticket = ticketWith(204L, TicketStatus.IN_PROGRESS, 1L, 2L);
    ReflectionTestUtils.setField(ticket, "priority", TicketPriority.HIGH);
    when(ticketRepository.findByIdAndAssigneeIdForUpdate(204L, 2L)).thenReturn(Optional.of(ticket));
    User agent = userWith(2L, "担当AGENT", Role.AGENT, 70);
    when(userRepository.findById(2L)).thenReturn(Optional.of(agent));
    when(userRepository.findAllById(any())).thenReturn(List.of(agent));

    TicketStatusUpdateResponse response = ticketService.updateStatus(204L, resolveRequest(), principal);

    assertThat(agent.getExperience()).isEqualTo(100);
    assertThat(agent.getLevel()).isEqualTo(2);
    assertThat(response.getXpResult().getPreviousLevel()).isEqualTo(1);
    assertThat(response.getXpResult().getCurrentLevel()).isEqualTo(2);
    assertThat(response.getXpResult().isLevelUp()).isTrue();
  }

  @Test
  void 累計120XPでLevel2かつレベル内20XPかつ次まで80XPになる() {
    ticketService = new TicketService(ticketRepository, userRepository);
    AppUserDetails principal = principalOf(2L, Role.AGENT);
    Ticket ticket = ticketWith(205L, TicketStatus.IN_PROGRESS, 1L, 2L);
    ReflectionTestUtils.setField(ticket, "priority", TicketPriority.HIGH);
    when(ticketRepository.findByIdAndAssigneeIdForUpdate(205L, 2L)).thenReturn(Optional.of(ticket));
    User agent = userWith(2L, "担当AGENT", Role.AGENT, 90);
    when(userRepository.findById(2L)).thenReturn(Optional.of(agent));
    when(userRepository.findAllById(any())).thenReturn(List.of(agent));

    ticketService.updateStatus(205L, resolveRequest(), principal);

    assertThat(agent.getExperience()).isEqualTo(120);
    assertThat(agent.getLevel()).isEqualTo(2);
    assertThat(agent.getCurrentLevelExperience()).isEqualTo(20);
    assertThat(agent.getExperienceToNextLevel()).isEqualTo(80);
  }

  @Test
  void 担当者未設定のチケットはXP付与されないがxpAwardedはtrueになる() {
    ticketService = new TicketService(ticketRepository, userRepository);
    AppUserDetails principal = principalOf(9L, Role.ADMIN);
    Ticket ticket = ticketWith(206L, TicketStatus.IN_PROGRESS, 1L, null);
    when(ticketRepository.findByIdForUpdate(206L)).thenReturn(Optional.of(ticket));

    TicketStatusUpdateResponse response = ticketService.updateStatus(206L, resolveRequest(), principal);

    assertThat(response.getXpResult().isAwarded()).isFalse();
    assertThat(ticket.isXpAwarded()).isTrue();
    verify(userRepository, never()).findById(any());
  }

  @Test
  void 担当者がUSERの場合はXP付与されない() {
    ticketService = new TicketService(ticketRepository, userRepository);
    AppUserDetails principal = principalOf(9L, Role.ADMIN);
    Ticket ticket = ticketWith(207L, TicketStatus.IN_PROGRESS, 1L, 5L);
    when(ticketRepository.findByIdForUpdate(207L)).thenReturn(Optional.of(ticket));
    User notAgent = userWith(5L, "USER担当", Role.USER, 0);
    when(userRepository.findById(5L)).thenReturn(Optional.of(notAgent));

    TicketStatusUpdateResponse response = ticketService.updateStatus(207L, resolveRequest(), principal);

    assertThat(response.getXpResult().isAwarded()).isFalse();
    assertThat(notAgent.getExperience()).isZero();
    assertThat(ticket.isXpAwarded()).isTrue();
  }

  @Test
  void 担当者がADMINの場合はXP付与されない() {
    ticketService = new TicketService(ticketRepository, userRepository);
    AppUserDetails principal = principalOf(9L, Role.ADMIN);
    Ticket ticket = ticketWith(208L, TicketStatus.IN_PROGRESS, 1L, 6L);
    when(ticketRepository.findByIdForUpdate(208L)).thenReturn(Optional.of(ticket));
    User adminAssignee = userWith(6L, "ADMIN担当", Role.ADMIN, 0);
    when(userRepository.findById(6L)).thenReturn(Optional.of(adminAssignee));

    TicketStatusUpdateResponse response = ticketService.updateStatus(208L, resolveRequest(), principal);

    assertThat(response.getXpResult().isAwarded()).isFalse();
    assertThat(adminAssignee.getExperience()).isZero();
  }

  @Test
  void RESOLVED以外への変更ではXP判定を行わない() {
    ticketService = new TicketService(ticketRepository, userRepository);
    AppUserDetails principal = principalOf(2L, Role.AGENT);
    Ticket ticket = ticketWith(209L, TicketStatus.RESOLVED, 1L, 2L);
    when(ticketRepository.findByIdAndAssigneeIdForUpdate(209L, 2L)).thenReturn(Optional.of(ticket));
    when(userRepository.findAllById(any())).thenReturn(List.of(userWith(2L, "担当AGENT", Role.AGENT, 0)));

    TicketStatusUpdateRequest request = new TicketStatusUpdateRequest();
    request.setStatus(TicketStatus.CLOSED);

    TicketStatusUpdateResponse response = ticketService.updateStatus(209L, request, principal);

    assertThat(response.getXpResult().isAwarded()).isFalse();
    assertThat(ticket.isXpAwarded()).isFalse();
    verify(userRepository, never()).findById(any());
  }

  @Test
  void 一度RESOLVEDになったチケットを再度RESOLVEDにしても二重付与されない() {
    ticketService = new TicketService(ticketRepository, userRepository);
    AppUserDetails principal = principalOf(2L, Role.AGENT);
    // RESOLVED→IN_PROGRESSに一度戻った状態(xpAwardedはtrueのまま)を再現する。
    Ticket ticket = ticketWith(210L, TicketStatus.IN_PROGRESS, 1L, 2L);
    ReflectionTestUtils.setField(ticket, "xpAwarded", true);
    when(ticketRepository.findByIdAndAssigneeIdForUpdate(210L, 2L)).thenReturn(Optional.of(ticket));
    User agent = userWith(2L, "担当AGENT", Role.AGENT, 30);
    when(userRepository.findAllById(any())).thenReturn(List.of(agent));

    TicketStatusUpdateResponse response = ticketService.updateStatus(210L, resolveRequest(), principal);

    assertThat(response.getXpResult().isAwarded()).isFalse();
    assertThat(agent.getExperience()).isEqualTo(30);
    verify(userRepository, never()).findById(any());
  }

  @Test
  void XP判定済みのチケットは担当者を変更して再度RESOLVEDにしても再付与されない() {
    ticketService = new TicketService(ticketRepository, userRepository);
    AppUserDetails principal = principalOf(9L, Role.ADMIN);
    // 既にXP判定済み(元は別のAGENTが担当)のチケットが、担当者変更後にIN_PROGRESSへ戻り、
    // 再びRESOLVEDにされた状況を再現する。
    Ticket ticket = ticketWith(211L, TicketStatus.IN_PROGRESS, 1L, 8L);
    ReflectionTestUtils.setField(ticket, "xpAwarded", true);
    when(ticketRepository.findByIdForUpdate(211L)).thenReturn(Optional.of(ticket));
    User newAgent = userWith(8L, "新担当AGENT", Role.AGENT, 0);
    when(userRepository.findAllById(any())).thenReturn(List.of(newAgent));

    TicketStatusUpdateResponse response = ticketService.updateStatus(211L, resolveRequest(), principal);

    assertThat(response.getXpResult().isAwarded()).isFalse();
    assertThat(newAgent.getExperience()).isZero();
    verify(userRepository, never()).findById(any());
  }
}
