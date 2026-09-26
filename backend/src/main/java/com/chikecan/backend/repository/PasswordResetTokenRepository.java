package com.chikecan.backend.repository;

import java.time.Instant;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.chikecan.backend.entity.PasswordResetToken;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

  Optional<PasswordResetToken> findByUserId(Long userId);

  Optional<PasswordResetToken> findByTokenHash(String tokenHash);

  /**
   * 期限切れのtokenを削除する。Spring Schedulerのような定期実行の仕組みは設けず、
   * 新規のパスワードリセット要求の延長で呼び出す遅延クリーンアップ方式を採用する。
   * 戻り値は削除件数(呼び出し元でのログ・テスト用)。
   */
  long deleteByExpiresAtBefore(Instant expiresAt);

  /**
   * 指定ユーザーの有効なパスワードリセットtokenを削除する。メールアドレス変更が
   * 成功した際、旧メールアドレス宛に発行済みのリセットリンクを無効化するために使う。
   * 戻り値は削除件数(呼び出し元でのログ・テスト用)。
   */
  long deleteByUserId(Long userId);
}
