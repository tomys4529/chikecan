package com.chikecan.backend.repository;

import java.time.Instant;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.chikecan.backend.entity.EmailChangeRequest;

public interface EmailChangeRequestRepository extends JpaRepository<EmailChangeRequest, Long> {

  Optional<EmailChangeRequest> findByUserId(Long userId);

  Optional<EmailChangeRequest> findByTokenHash(String tokenHash);

  Optional<EmailChangeRequest> findByNewEmail(String newEmail);

  /**
   * 期限切れの申請を削除する。Spring Schedulerのような定期実行の仕組みは設けず、
   * 新規のメールアドレス変更申請の延長で呼び出す遅延クリーンアップ方式を採用する。
   * 戻り値は削除件数(呼び出し元でのログ・テスト用)。
   */
  long deleteByExpiresAtBefore(Instant expiresAt);
}
