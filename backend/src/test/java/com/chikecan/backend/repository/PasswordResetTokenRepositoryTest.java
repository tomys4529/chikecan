package com.chikecan.backend.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import com.chikecan.backend.entity.PasswordResetToken;
import com.chikecan.backend.entity.Role;
import com.chikecan.backend.entity.User;

@DataJpaTest
@ActiveProfiles("local")
class PasswordResetTokenRepositoryTest {

  @Autowired
  private PasswordResetTokenRepository passwordResetTokenRepository;

  @Autowired
  private UserRepository userRepository;

  private Long seedUser(String email) {
    User user = userRepository.saveAndFlush(new User("テスト太郎", email, "hashed-password", Role.USER, true));
    return user.getId();
  }

  @Test
  void deleteByExpiresAtBeforeは期限切れのtokenだけ削除し件数を返す() {
    Long expiredUserId1 = seedUser("expired1@example.com");
    Long expiredUserId2 = seedUser("expired2@example.com");
    Long validUserId = seedUser("valid@example.com");

    passwordResetTokenRepository.saveAndFlush(
        new PasswordResetToken(expiredUserId1, "expired-token-1", Instant.now().minusSeconds(10)));
    passwordResetTokenRepository.saveAndFlush(
        new PasswordResetToken(expiredUserId2, "expired-token-2", Instant.now().minusSeconds(1)));
    passwordResetTokenRepository.saveAndFlush(
        new PasswordResetToken(validUserId, "valid-token", Instant.now().plusSeconds(3600)));

    long deletedCount = passwordResetTokenRepository.deleteByExpiresAtBefore(Instant.now());

    assertThat(deletedCount).isEqualTo(2);
    assertThat(passwordResetTokenRepository.findByUserId(expiredUserId1)).isEmpty();
    assertThat(passwordResetTokenRepository.findByUserId(expiredUserId2)).isEmpty();
    assertThat(passwordResetTokenRepository.findByUserId(validUserId)).isPresent();
  }

  @Test
  void 期限切れが存在しない場合は何も削除されず0が返る() {
    Long userId = seedUser("valid-only@example.com");
    passwordResetTokenRepository.saveAndFlush(
        new PasswordResetToken(userId, "valid-only-token", Instant.now().plusSeconds(3600)));

    long deletedCount = passwordResetTokenRepository.deleteByExpiresAtBefore(Instant.now());

    assertThat(deletedCount).isZero();
    assertThat(passwordResetTokenRepository.findByUserId(userId)).isPresent();
  }

  @Test
  void findByTokenHashで保存したレコードを取得できる() {
    Long userId = seedUser("token-lookup@example.com");
    passwordResetTokenRepository.saveAndFlush(
        new PasswordResetToken(userId, "lookup-token-hash", Instant.now().plusSeconds(3600)));

    Optional<PasswordResetToken> found = passwordResetTokenRepository.findByTokenHash("lookup-token-hash");

    assertThat(found).isPresent();
    assertThat(found.get().getUserId()).isEqualTo(userId);
  }
}
