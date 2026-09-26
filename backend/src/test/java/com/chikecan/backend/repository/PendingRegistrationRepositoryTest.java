package com.chikecan.backend.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import com.chikecan.backend.entity.PendingRegistration;

@DataJpaTest
@ActiveProfiles("local")
class PendingRegistrationRepositoryTest {

  @Autowired
  private PendingRegistrationRepository pendingRegistrationRepository;

  private PendingRegistration pendingOf(String email, String tokenHash, Instant expiresAt) {
    return new PendingRegistration("テスト太郎", email, "hashed-password", tokenHash, expiresAt);
  }

  @Test
  void deleteByExpiresAtBeforeは期限切れのpendingだけ削除し件数を返す() {
    pendingRegistrationRepository.saveAndFlush(
        pendingOf("expired1@example.com", "expired-token-1", Instant.now().minusSeconds(10)));
    pendingRegistrationRepository.saveAndFlush(
        pendingOf("expired2@example.com", "expired-token-2", Instant.now().minusSeconds(1)));
    pendingRegistrationRepository.saveAndFlush(
        pendingOf("valid@example.com", "valid-token", Instant.now().plusSeconds(3600)));

    long deletedCount = pendingRegistrationRepository.deleteByExpiresAtBefore(Instant.now());

    assertThat(deletedCount).isEqualTo(2);
    assertThat(pendingRegistrationRepository.findByEmail("expired1@example.com")).isEmpty();
    assertThat(pendingRegistrationRepository.findByEmail("expired2@example.com")).isEmpty();
    assertThat(pendingRegistrationRepository.findByEmail("valid@example.com")).isPresent();
  }

  @Test
  void 期限切れが存在しない場合は何も削除されず0が返る() {
    pendingRegistrationRepository.saveAndFlush(
        pendingOf("valid-only@example.com", "valid-only-token", Instant.now().plusSeconds(3600)));

    long deletedCount = pendingRegistrationRepository.deleteByExpiresAtBefore(Instant.now());

    assertThat(deletedCount).isZero();
    assertThat(pendingRegistrationRepository.findByEmail("valid-only@example.com")).isPresent();
  }

  @Test
  void findByTokenHashで保存したレコードを取得できる() {
    pendingRegistrationRepository.saveAndFlush(
        pendingOf("token-lookup@example.com", "lookup-token-hash", Instant.now().plusSeconds(3600)));

    Optional<PendingRegistration> found = pendingRegistrationRepository.findByTokenHash("lookup-token-hash");

    assertThat(found).isPresent();
    assertThat(found.get().getEmail()).isEqualTo("token-lookup@example.com");
  }
}
