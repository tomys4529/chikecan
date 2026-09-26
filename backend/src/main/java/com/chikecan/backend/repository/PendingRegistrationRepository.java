package com.chikecan.backend.repository;

import java.time.Instant;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.chikecan.backend.entity.PendingRegistration;

public interface PendingRegistrationRepository extends JpaRepository<PendingRegistration, Long> {

  Optional<PendingRegistration> findByEmail(String email);

  Optional<PendingRegistration> findByTokenHash(String tokenHash);

  /**
   * 期限切れのpendingを削除する。Spring Schedulerのような定期実行の仕組みは設けず、
   * 新規登録などの操作の延長で呼び出す遅延クリーンアップ方式を採用する。
   * 戻り値は削除件数(呼び出し元でのログ・テスト用)。
   */
  long deleteByExpiresAtBefore(Instant expiresAt);
}
