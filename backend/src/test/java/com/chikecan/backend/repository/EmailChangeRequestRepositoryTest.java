package com.chikecan.backend.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatException;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import com.chikecan.backend.entity.EmailChangeRequest;
import com.chikecan.backend.entity.Role;
import com.chikecan.backend.entity.User;

@DataJpaTest
@ActiveProfiles("local")
class EmailChangeRequestRepositoryTest {

  @Autowired
  private EmailChangeRequestRepository emailChangeRequestRepository;

  @Autowired
  private UserRepository userRepository;

  private Long seedUser(String email) {
    User user = userRepository.saveAndFlush(new User("テスト太郎", email, "hashed-password", Role.USER, true));
    return user.getId();
  }

  @Test
  void deleteByExpiresAtBeforeは期限切れの申請だけ削除し件数を返す() {
    Long expiredUserId1 = seedUser("expired1@example.com");
    Long expiredUserId2 = seedUser("expired2@example.com");
    Long validUserId = seedUser("valid@example.com");

    emailChangeRequestRepository.saveAndFlush(
        new EmailChangeRequest(expiredUserId1, "new1@example.com", "expired-token-1", Instant.now().minusSeconds(10)));
    emailChangeRequestRepository.saveAndFlush(
        new EmailChangeRequest(expiredUserId2, "new2@example.com", "expired-token-2", Instant.now().minusSeconds(1)));
    emailChangeRequestRepository.saveAndFlush(
        new EmailChangeRequest(validUserId, "new3@example.com", "valid-token", Instant.now().plusSeconds(3600)));

    long deletedCount = emailChangeRequestRepository.deleteByExpiresAtBefore(Instant.now());

    assertThat(deletedCount).isEqualTo(2);
    assertThat(emailChangeRequestRepository.findByUserId(expiredUserId1)).isEmpty();
    assertThat(emailChangeRequestRepository.findByUserId(expiredUserId2)).isEmpty();
    assertThat(emailChangeRequestRepository.findByUserId(validUserId)).isPresent();
  }

  @Test
  void 期限切れが存在しない場合は何も削除されず0が返る() {
    Long userId = seedUser("valid-only@example.com");
    emailChangeRequestRepository.saveAndFlush(
        new EmailChangeRequest(userId, "valid-only-target@example.com", "valid-only-token", Instant.now().plusSeconds(3600)));

    long deletedCount = emailChangeRequestRepository.deleteByExpiresAtBefore(Instant.now());

    assertThat(deletedCount).isZero();
    assertThat(emailChangeRequestRepository.findByUserId(userId)).isPresent();
  }

  @Test
  void findByTokenHashで保存したレコードを取得できる() {
    Long userId = seedUser("token-lookup@example.com");
    emailChangeRequestRepository.saveAndFlush(
        new EmailChangeRequest(userId, "lookup-target@example.com", "lookup-token-hash", Instant.now().plusSeconds(3600)));

    Optional<EmailChangeRequest> found = emailChangeRequestRepository.findByTokenHash("lookup-token-hash");

    assertThat(found).isPresent();
    assertThat(found.get().getUserId()).isEqualTo(userId);
  }

  @Test
  void findByNewEmailで保存したレコードを取得できる() {
    Long userId = seedUser("new-email-lookup@example.com");
    emailChangeRequestRepository.saveAndFlush(
        new EmailChangeRequest(userId, "target-for-lookup@example.com", "some-token-hash", Instant.now().plusSeconds(3600)));

    Optional<EmailChangeRequest> found = emailChangeRequestRepository.findByNewEmail("target-for-lookup@example.com");

    assertThat(found).isPresent();
    assertThat(found.get().getUserId()).isEqualTo(userId);
  }

  /**
   * Service層の事前確認だけに頼らず、DB制約自体でも同時リクエストによる重複new_emailを
   * 防げることを確認する(uk_email_change_requests_new_email)。
   */
  @Test
  void 異なるユーザーが同じnew_emailで保存しようとするとDB制約違反になる() {
    Long userId1 = seedUser("racer1@example.com");
    Long userId2 = seedUser("racer2@example.com");

    emailChangeRequestRepository.saveAndFlush(
        new EmailChangeRequest(userId1, "contested-target@example.com", "racer1-token-hash", Instant.now().plusSeconds(3600)));

    assertThatException()
        .isThrownBy(() -> emailChangeRequestRepository.saveAndFlush(
            new EmailChangeRequest(userId2, "contested-target@example.com", "racer2-token-hash", Instant.now().plusSeconds(3600))))
        .isInstanceOf(DataIntegrityViolationException.class);
  }
}
