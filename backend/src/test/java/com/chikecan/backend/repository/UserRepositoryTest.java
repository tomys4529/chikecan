package com.chikecan.backend.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatNoException;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import com.chikecan.backend.entity.Role;
import com.chikecan.backend.entity.User;

@DataJpaTest
@ActiveProfiles("local")
class UserRepositoryTest {

  @Autowired
  private UserRepository userRepository;

  @Test
  void 保存したユーザーをfindByEmailで取得できる() {
    User user = new User("山田太郎", "yamada@example.com", "hashed-password", Role.USER, true);

    userRepository.saveAndFlush(user);

    Optional<User> found = userRepository.findByEmail("yamada@example.com");

    assertThat(found).isPresent();
    assertThat(found.get().getName()).isEqualTo("山田太郎");
    assertThat(found.get().getRole()).isEqualTo(Role.USER);
  }

  @Test
  void 存在しないメールアドレスはfindByEmailで空になる() {
    Optional<User> found = userRepository.findByEmail("notfound@example.com");

    assertThat(found).isEmpty();
  }

  @Test
  void 同じメールアドレスを重複登録すると拒否される() {
    userRepository.saveAndFlush(new User("鈴木一郎", "duplicate@example.com", "hashed-password", Role.USER, true));

    assertThatThrownBy(() -> userRepository
        .saveAndFlush(new User("佐藤次郎", "duplicate@example.com", "hashed-password", Role.AGENT, true)))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void Roleは文字列として保存され取得時も列挙値として復元される() {
    userRepository.saveAndFlush(new User("管理者", "admin@example.com", "hashed-password", Role.ADMIN, true));

    User found = userRepository.findByEmail("admin@example.com").orElseThrow();

    assertThat(found.getRole()).isEqualTo(Role.ADMIN);
  }

  @Test
  void createdAtとupdatedAtが自動設定される() {
    assertThatNoException().isThrownBy(() -> {
      User saved = userRepository.saveAndFlush(
          new User("田中花子", "tanaka@example.com", "hashed-password", Role.USER, true));

      assertThat(saved.getCreatedAt()).isNotNull();
      assertThat(saved.getUpdatedAt()).isNotNull();
    });
  }
}
