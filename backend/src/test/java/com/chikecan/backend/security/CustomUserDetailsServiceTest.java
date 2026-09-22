package com.chikecan.backend.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import com.chikecan.backend.entity.Role;
import com.chikecan.backend.entity.User;
import com.chikecan.backend.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class CustomUserDetailsServiceTest {

  @Mock
  private UserRepository userRepository;

  @Test
  void 存在するユーザーはAppUserDetailsとして取得できる() {
    CustomUserDetailsService service = new CustomUserDetailsService(userRepository);
    User user = new User("山田太郎", "yamada@example.com", "hashed", Role.USER, true);
    when(userRepository.findByEmail("yamada@example.com")).thenReturn(Optional.of(user));

    UserDetails details = service.loadUserByUsername("yamada@example.com");

    assertThat(details).isInstanceOf(AppUserDetails.class);
    assertThat(details.getUsername()).isEqualTo("yamada@example.com");
    assertThat(details.getPassword()).isEqualTo("hashed");
    assertThat(details.isEnabled()).isTrue();
  }

  @Test
  void 大文字前後空白付きメールでも正規化されて検索される() {
    CustomUserDetailsService service = new CustomUserDetailsService(userRepository);
    User user = new User("山田太郎", "yamada@example.com", "hashed", Role.USER, true);
    when(userRepository.findByEmail("yamada@example.com")).thenReturn(Optional.of(user));

    UserDetails details = service.loadUserByUsername("  YAMADA@EXAMPLE.com  ");

    assertThat(details.getUsername()).isEqualTo("yamada@example.com");
  }

  @Test
  void 存在しないメールはUsernameNotFoundExceptionになる() {
    CustomUserDetailsService service = new CustomUserDetailsService(userRepository);
    when(userRepository.findByEmail("notfound@example.com")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.loadUserByUsername("notfound@example.com"))
        .isInstanceOf(UsernameNotFoundException.class);
  }

  @Test
  void disabledユーザーはisEnabledがfalseのAppUserDetailsになる() {
    CustomUserDetailsService service = new CustomUserDetailsService(userRepository);
    User user = new User("無効ユーザー", "disabled@example.com", "hashed", Role.USER, false);
    when(userRepository.findByEmail("disabled@example.com")).thenReturn(Optional.of(user));

    UserDetails details = service.loadUserByUsername("disabled@example.com");

    assertThat(details.isEnabled()).isFalse();
  }
}
