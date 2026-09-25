package com.chikecan.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import com.chikecan.backend.dto.AgentSummaryResponse;
import com.chikecan.backend.dto.RegisterRequest;
import com.chikecan.backend.dto.UserResponse;
import com.chikecan.backend.entity.Role;
import com.chikecan.backend.entity.User;
import com.chikecan.backend.exception.DuplicateEmailException;
import com.chikecan.backend.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

  @Mock
  private UserRepository userRepository;

  @Mock
  private PasswordEncoder passwordEncoder;

  private UserService userService;

  @Test
  void 正常登録するとメールが正規化されUSERロールでパスワードがハッシュ化されて保存される() {
    userService = new UserService(userRepository, passwordEncoder);

    RegisterRequest request = new RegisterRequest();
    request.setName("山田太郎");
    request.setEmail("  Yamada@EXAMPLE.com  ");
    request.setPassword("Passw0rd123");

    when(userRepository.findByEmail("yamada@example.com")).thenReturn(Optional.empty());
    when(passwordEncoder.encode("Passw0rd123")).thenReturn("hashed-value");
    when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

    UserResponse response = userService.register(request);

    ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
    verify(userRepository).save(captor.capture());
    User savedArg = captor.getValue();

    assertThat(savedArg.getEmail()).isEqualTo("yamada@example.com");
    assertThat(savedArg.getPasswordHash()).isEqualTo("hashed-value");
    assertThat(savedArg.getRole()).isEqualTo(Role.USER);
    assertThat(response.getEmail()).isEqualTo("yamada@example.com");
    assertThat(response.getRole()).isEqualTo(Role.USER);
  }

  @Test
  void 既に登録済みのメールアドレスは重複エラーになり保存されない() {
    userService = new UserService(userRepository, passwordEncoder);

    RegisterRequest request = new RegisterRequest();
    request.setName("鈴木一郎");
    request.setEmail("duplicate@example.com");
    request.setPassword("Passw0rd123");

    when(userRepository.findByEmail("duplicate@example.com"))
        .thenReturn(Optional.of(new User("既存ユーザー", "duplicate@example.com", "hash", Role.USER, true)));

    assertThatThrownBy(() -> userService.register(request))
        .isInstanceOf(DuplicateEmailException.class);

    verify(userRepository, never()).save(any());
  }

  @Test
  void listAgentsはRepositoryの結果をid名前のみのDTOへ変換する() {
    userService = new UserService(userRepository, passwordEncoder);

    User agent = new User("鈴木一郎", "suzuki@example.com", "hashed-password", Role.AGENT, true);
    ReflectionTestUtils.setField(agent, "id", 5L);
    when(userRepository.findByRoleAndEnabledTrueOrderByNameAscIdAsc(Role.AGENT)).thenReturn(List.of(agent));

    List<AgentSummaryResponse> result = userService.listAgents();

    assertThat(result).hasSize(1);
    assertThat(result.get(0).getId()).isEqualTo(5L);
    assertThat(result.get(0).getName()).isEqualTo("鈴木一郎");
  }

  @Test
  void listAgentsは候補が存在しない場合は空リストを返す() {
    userService = new UserService(userRepository, passwordEncoder);

    when(userRepository.findByRoleAndEnabledTrueOrderByNameAscIdAsc(Role.AGENT)).thenReturn(List.of());

    List<AgentSummaryResponse> result = userService.listAgents();

    assertThat(result).isEmpty();
  }
}
