package com.chikecan.backend.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import com.chikecan.backend.entity.OAuthAccount;
import com.chikecan.backend.entity.OAuthProvider;
import com.chikecan.backend.entity.Role;
import com.chikecan.backend.entity.User;

@DataJpaTest
@ActiveProfiles("local")
class OAuthAccountRepositoryTest {

  @Autowired
  private OAuthAccountRepository oAuthAccountRepository;

  @Autowired
  private UserRepository userRepository;

  private Long seedUser(String email) {
    User user = userRepository.saveAndFlush(new User("テスト太郎", email, "hashed-password", Role.USER, true));
    return user.getId();
  }

  @Test
  void findByProviderAndProviderUserIdで保存したレコードを取得できる() {
    Long userId = seedUser("oauth-lookup@example.com");
    oAuthAccountRepository.saveAndFlush(new OAuthAccount(userId, OAuthProvider.GOOGLE, "google-sub-1"));

    Optional<OAuthAccount> found =
        oAuthAccountRepository.findByProviderAndProviderUserId(OAuthProvider.GOOGLE, "google-sub-1");

    assertThat(found).isPresent();
    assertThat(found.get().getUserId()).isEqualTo(userId);
  }

  @Test
  void 存在しないprovider_user_idはfindByProviderAndProviderUserIdで見つからない() {
    Optional<OAuthAccount> found =
        oAuthAccountRepository.findByProviderAndProviderUserId(OAuthProvider.GOOGLE, "does-not-exist");

    assertThat(found).isEmpty();
  }

  @Test
  void 同じprovider_user_idを別ユーザーで重複登録するとDB制約違反になる() {
    Long userId1 = seedUser("oauth-user1@example.com");
    Long userId2 = seedUser("oauth-user2@example.com");
    oAuthAccountRepository.saveAndFlush(new OAuthAccount(userId1, OAuthProvider.GOOGLE, "duplicate-sub"));

    assertThatThrownBy(() -> oAuthAccountRepository
        .saveAndFlush(new OAuthAccount(userId2, OAuthProvider.GOOGLE, "duplicate-sub")))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void 同じユーザー_同じproviderの重複登録はDB制約違反になる() {
    Long userId = seedUser("oauth-same-user@example.com");
    oAuthAccountRepository.saveAndFlush(new OAuthAccount(userId, OAuthProvider.GOOGLE, "sub-a"));

    assertThatThrownBy(() -> oAuthAccountRepository
        .saveAndFlush(new OAuthAccount(userId, OAuthProvider.GOOGLE, "sub-b")))
        .isInstanceOf(DataIntegrityViolationException.class);
  }
}
