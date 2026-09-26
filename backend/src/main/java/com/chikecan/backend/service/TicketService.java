package com.chikecan.backend.service;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.chikecan.backend.dto.PageResponse;
import com.chikecan.backend.dto.TicketAssigneeUpdateRequest;
import com.chikecan.backend.dto.TicketCreateRequest;
import com.chikecan.backend.dto.TicketResponse;
import com.chikecan.backend.dto.TicketStatusUpdateRequest;
import com.chikecan.backend.dto.TicketStatusUpdateResponse;
import com.chikecan.backend.dto.TicketUpdateRequest;
import com.chikecan.backend.dto.XpAwardResult;
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

@Service
public class TicketService {

  private static final Map<TicketStatus, Set<TicketStatus>> ALLOWED_TRANSITIONS = Map.of(
      TicketStatus.OPEN, Set.of(TicketStatus.IN_PROGRESS),
      TicketStatus.IN_PROGRESS, Set.of(TicketStatus.RESOLVED),
      TicketStatus.RESOLVED, Set.of(TicketStatus.CLOSED, TicketStatus.IN_PROGRESS),
      TicketStatus.CLOSED, Set.of());

  private static final Map<TicketPriority, Integer> XP_BY_PRIORITY = Map.of(
      TicketPriority.LOW, 10,
      TicketPriority.MEDIUM, 20,
      TicketPriority.HIGH, 30);

  private static final int DEFAULT_PAGE_SIZE = 20;
  // 異常に大きなsizeを指定されても一覧取得で大量取得にならないよう上限を設ける。
  private static final int MAX_PAGE_SIZE = 100;
  // 同じcreatedAtのチケットが複数あっても順序が不安定にならないよう、idを第2ソート条件にする。
  private static final Sort TICKET_LIST_SORT = Sort.by(Sort.Direction.DESC, "createdAt", "id");

  private final TicketRepository ticketRepository;
  private final UserRepository userRepository;

  public TicketService(TicketRepository ticketRepository, UserRepository userRepository) {
    this.ticketRepository = ticketRepository;
    this.userRepository = userRepository;
  }

  @Transactional
  public TicketResponse create(TicketCreateRequest request, AppUserDetails principal) {
    Ticket ticket = new Ticket(
        request.getTitle(), request.getDescription(), TicketStatus.OPEN, request.getPriority(),
        principal.getId(), null);
    Ticket saved = ticketRepository.save(ticket);
    // requesterは常にログイン中の本人(principal)なので、名前解決のための追加クエリは不要。
    return new TicketResponse(saved, principal.getDisplayName(), null);
  }

  /**
   * ロールごとの取得範囲(USER:自分が依頼者/AGENT:自分が担当者/ADMIN:全件)は
   * 従来どおりRepositoryのクエリ段階で絞り込む(全件取得してからJavaで
   * 20件へ切り分けたりはしない)。総件数(totalElements)もこの絞り込み後の
   * Pageから取得するため、ロール外のチケットが件数へ混入することはない。
   */
  @Transactional(readOnly = true)
  public PageResponse<TicketResponse> list(AppUserDetails principal, int page, int size) {
    Pageable pageable = toPageable(page, size);
    Page<Ticket> ticketsPage = switch (principal.getRole()) {
      case USER -> ticketRepository.findByRequesterId(principal.getId(), pageable);
      case AGENT -> ticketRepository.findByAssigneeId(principal.getId(), pageable);
      case ADMIN -> ticketRepository.findAll(pageable);
    };

    return new PageResponse<>(
        toResponses(ticketsPage.getContent()),
        ticketsPage.getNumber(),
        ticketsPage.getSize(),
        ticketsPage.getTotalElements(),
        ticketsPage.getTotalPages(),
        ticketsPage.isFirst(),
        ticketsPage.isLast());
  }

  private Pageable toPageable(int page, int size) {
    int safePage = Math.max(page, 0);
    int safeSize = size < 1 ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);
    return PageRequest.of(safePage, safeSize, TICKET_LIST_SORT);
  }

  @Transactional(readOnly = true)
  public TicketResponse getDetail(Long id, AppUserDetails principal) {
    return toResponse(findAccessible(id, principal));
  }

  /**
   * ステータス更新とXP判定・付与を同一トランザクションで行う。
   * 更新対象チケットは悲観ロック(SELECT ... FOR UPDATE)付きで取得し、
   * 同時に来た複数のステータス更新リクエストがxp_awardedを二重にfalseのまま
   * 読んで二重付与することを防ぐ。ロックは他のチケット参照処理には広げない。
   */
  @Transactional
  public TicketStatusUpdateResponse updateStatus(Long id, TicketStatusUpdateRequest request, AppUserDetails principal) {
    Ticket ticket = findAccessibleForStatusChangeLocked(id, principal);
    TicketStatus next = request.getStatus();
    if (!isAllowedTransition(ticket.getStatus(), next)) {
      throw new InvalidStatusTransitionException("このステータスへは変更できません");
    }
    ticket.setStatus(next);

    XpAwardResult xpResult = XpAwardResult.none();
    // 初めてRESOLVEDになったときだけXPを判定する。判定済みフラグは、
    // 実際に付与できたか(有効なAGENTが担当していたか)に関わらずここで確定させる。
    if (next == TicketStatus.RESOLVED && !ticket.isXpAwarded()) {
      ticket.setXpAwarded(true);
      xpResult = tryAwardXp(ticket);
    }

    return new TicketStatusUpdateResponse(toResponse(ticket), xpResult);
  }

  private XpAwardResult tryAwardXp(Ticket ticket) {
    Long assigneeId = ticket.getAssigneeId();
    if (assigneeId == null) {
      return XpAwardResult.none();
    }
    User assignee = userRepository.findById(assigneeId).orElse(null);
    if (assignee == null || assignee.getRole() != Role.AGENT) {
      return XpAwardResult.none();
    }
    int gainedExperience = XP_BY_PRIORITY.getOrDefault(ticket.getPriority(), 0);
    int previousLevel = assignee.getLevel();
    assignee.addExperience(gainedExperience);
    int currentLevel = assignee.getLevel();
    return XpAwardResult.awarded(gainedExperience, previousLevel, currentLevel, assignee.getExperience());
  }

  @Transactional
  public TicketResponse updateAssignee(Long id, TicketAssigneeUpdateRequest request, AppUserDetails principal) {
    if (principal.getRole() != Role.ADMIN) {
      throw new AccessDeniedException("権限がありません");
    }

    Ticket ticket = ticketRepository.findById(id).orElseThrow(this::notFound);
    Long assigneeId = request.getAssigneeId();

    User assignee = null;
    if (assigneeId != null) {
      assignee = userRepository.findById(assigneeId)
          .orElseThrow(() -> new InvalidAssigneeException("担当者が見つかりません"));
      if (assignee.getRole() != Role.AGENT) {
        throw new InvalidAssigneeException("担当者にはAGENTのみ設定できます");
      }
    }

    ticket.setAssigneeId(assigneeId);

    // assigneeは検証のため既に取得済みなので、名前解決のために再度問い合わせない。
    // requesterNameのみ1件取得する(担当者変更はrequesterと無関係のため)。
    String requesterName = userRepository.findById(ticket.getRequesterId())
        .map(User::getDisplayName)
        .orElse(null);
    String assigneeName = assignee != null ? assignee.getDisplayName() : null;
    return new TicketResponse(ticket, requesterName, assigneeName);
  }

  /**
   * USER本人が自分のOPENチケットのタイトル・内容・優先度のみを編集する。
   * Controllerの@PreAuthorize("hasRole('USER')")だけに依存せず、
   * Service側でもロール・所有者・状態を再確認する(フロントエンドのボタン非表示は認可の代替にならないため)。
   */
  @Transactional
  public TicketResponse updateContent(Long id, TicketUpdateRequest request, AppUserDetails principal) {
    if (principal.getRole() != Role.USER) {
      throw new AccessDeniedException("権限がありません");
    }

    // ID+所有者を同時に判定することで、「存在しない」と「他人のチケット」を
    // 区別不能にし(IDOR対策)、どちらも同じ404にする。
    Ticket ticket = ticketRepository.findByIdAndRequesterId(id, principal.getId()).orElseThrow(this::notFound);

    if (ticket.getStatus() != TicketStatus.OPEN) {
      throw new TicketEditNotAllowedException("OPEN以外のチケットは編集できません");
    }

    ticket.setTitle(request.getTitle());
    ticket.setDescription(request.getDescription());
    ticket.setPriority(request.getPriority());
    // requesterId・assigneeId・status・createdAtには一切触れない。

    return toResponse(ticket);
  }

  private Ticket findAccessible(Long id, AppUserDetails principal) {
    return switch (principal.getRole()) {
      case USER -> ticketRepository.findByIdAndRequesterId(id, principal.getId()).orElseThrow(this::notFound);
      case AGENT -> ticketRepository.findByIdAndAssigneeId(id, principal.getId()).orElseThrow(this::notFound);
      case ADMIN -> ticketRepository.findById(id).orElseThrow(this::notFound);
    };
  }

  private Ticket findAccessibleForStatusChangeLocked(Long id, AppUserDetails principal) {
    return switch (principal.getRole()) {
      case AGENT -> ticketRepository.findByIdAndAssigneeIdForUpdate(id, principal.getId()).orElseThrow(this::notFound);
      case ADMIN -> ticketRepository.findByIdForUpdate(id).orElseThrow(this::notFound);
      case USER -> throw new AccessDeniedException("権限がありません");
    };
  }

  private boolean isAllowedTransition(TicketStatus current, TicketStatus next) {
    return ALLOWED_TRANSITIONS.getOrDefault(current, Set.of()).contains(next);
  }

  private TicketNotFoundException notFound() {
    return new TicketNotFoundException("チケットが見つかりません");
  }

  /**
   * 単一チケットをTicketResponseへ変換する。依頼者・担当者の名前は
   * findAllByIdで1回にまとめて取得する(最大2件のIDでも1クエリ)。
   */
  private TicketResponse toResponse(Ticket ticket) {
    Set<Long> userIds = new HashSet<>();
    userIds.add(ticket.getRequesterId());
    if (ticket.getAssigneeId() != null) {
      userIds.add(ticket.getAssigneeId());
    }
    Map<Long, String> namesById = loadUserNames(userIds);
    return new TicketResponse(ticket, namesById.get(ticket.getRequesterId()),
        ticket.getAssigneeId() == null ? null : namesById.get(ticket.getAssigneeId()));
  }

  /**
   * 複数チケットをまとめてTicketResponseへ変換する。
   * チケットごとにユーザーを1件ずつ取得するN+1クエリを避けるため、
   * 全チケットのrequesterId・assigneeIdを収集してfindAllByIdで1回だけ取得し、
   * IDから名前へのMapを作ってから変換する。
   */
  private List<TicketResponse> toResponses(List<Ticket> tickets) {
    Set<Long> userIds = new HashSet<>();
    for (Ticket ticket : tickets) {
      userIds.add(ticket.getRequesterId());
      if (ticket.getAssigneeId() != null) {
        userIds.add(ticket.getAssigneeId());
      }
    }
    Map<Long, String> namesById = loadUserNames(userIds);
    return tickets.stream()
        .map(ticket -> new TicketResponse(ticket, namesById.get(ticket.getRequesterId()),
            ticket.getAssigneeId() == null ? null : namesById.get(ticket.getAssigneeId())))
        .toList();
  }

  private Map<Long, String> loadUserNames(Set<Long> userIds) {
    if (userIds.isEmpty()) {
      return Map.of();
    }
    return userRepository.findAllById(userIds).stream()
        .collect(Collectors.toMap(User::getId, User::getDisplayName));
  }
}
