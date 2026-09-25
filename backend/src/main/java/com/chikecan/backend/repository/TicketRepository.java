package com.chikecan.backend.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import com.chikecan.backend.entity.Ticket;

public interface TicketRepository extends JpaRepository<Ticket, Long> {

  /**
   * 並び順(createdAt降順・id降順)はPageableのSortとして呼び出し側(Service)から
   * 指定する。全件取得(findAll(Pageable))はJpaRepositoryが標準で提供しているため、
   * ADMIN向けに専用メソッドは用意しない。
   */
  Page<Ticket> findByRequesterId(Long requesterId, Pageable pageable);

  Page<Ticket> findByAssigneeId(Long assigneeId, Pageable pageable);

  Optional<Ticket> findByIdAndRequesterId(Long id, Long requesterId);

  Optional<Ticket> findByIdAndAssigneeId(Long id, Long assigneeId);

  /**
   * ステータス更新(XP判定を伴う)専用の悲観ロック付き取得。
   * 通常のチケット参照(一覧・詳細・担当者変更・編集)は不要なロックを避けるため、
   * この2メソッドはステータス更新のユースケースからのみ呼び出す。
   * トランザクション終了までDB行がロックされ、同時に来た複数のステータス更新
   * リクエストが直列化されることでxp_awardedの二重判定・二重付与を防ぐ。
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select t from Ticket t where t.id = :id and t.assigneeId = :assigneeId")
  Optional<Ticket> findByIdAndAssigneeIdForUpdate(@Param("id") Long id, @Param("assigneeId") Long assigneeId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select t from Ticket t where t.id = :id")
  Optional<Ticket> findByIdForUpdate(@Param("id") Long id);
}
