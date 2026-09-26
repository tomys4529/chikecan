package com.chikecan.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
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
import com.chikecan.backend.entity.PasswordResetToken;
import com.chikecan.backend.entity.PendingRegistration;
import com.chikecan.backend.entity.Role;
import com.chikecan.backend.entity.User;
import com.chikecan.backend.exception.DuplicateEmailException;
import com.chikecan.backend.exception.InvalidPasswordResetTokenException;
import com.chikecan.backend.exception.InvalidVerificationTokenException;
import com.chikecan.backend.repository.PasswordResetTokenRepository;
import com.chikecan.backend.repository.PendingRegistrationRepository;
import com.chikecan.backend.repository.UserRepository;
import com.chikecan.backend.security.VerificationTokenGenerator;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

  @Mock
  private UserRepository userRepository;

  @Mock
  private PendingRegistrationRepository pendingRegistrationRepository;

  @Mock
  private PasswordResetTokenRepository passwordResetTokenRepository;

  @Mock
  private PasswordEncoder passwordEncoder;

  @Mock
  private VerificationMailService verificationMailService;

  @Mock
  private PasswordResetMailService passwordResetMailService;

  private UserService userService;

  private UserService newService() {
    return new UserService(userRepository, pendingRegistrationRepository, passwordResetTokenRepository,
        passwordEncoder, verificationMailService, passwordResetMailService);
  }

  private RegisterRequest requestOf(String name, String email, String password) {
    RegisterRequest request = new RegisterRequest();
    request.setName(name);
    request.setEmail(email);
    request.setPassword(password);
    return request;
  }

  private PendingRegistration pendingOf(Long id, String email, String tokenHash, Instant expiresAt) {
    PendingRegistration pending = new PendingRegistration("既存の名前", email, "old-hash", tokenHash, expiresAt);
    ReflectionTestUtils.setField(pending, "id", id);
    return pending;
  }

  private User userOf(Long id, String name, String email, String passwordHash) {
    User user = new User(name, email, passwordHash, Role.USER, true);
    ReflectionTestUtils.setField(user, "id", id);
    return user;
  }

  private PasswordResetToken passwordResetTokenOf(Long id, Long userId, String tokenHash, Instant expiresAt) {
    PasswordResetToken token = new PasswordResetToken(userId, tokenHash, expiresAt);
    ReflectionTestUtils.setField(token, "id", id);
    return token;
  }

  // ===== 登録(pending_registrationsへの一時保存) =====

  @Test
  void 正常登録するとメールが正規化されusersへは保存されずpendingへ保存される() {
    userService = newService();
    RegisterRequest request = requestOf("山田太郎", "  Yamada@EXAMPLE.com  ", "Passw0rd123!");

    when(userRepository.findByEmail("yamada@example.com")).thenReturn(Optional.empty());
    when(pendingRegistrationRepository.findByEmail("yamada@example.com")).thenReturn(Optional.empty());
    when(passwordEncoder.encode("Passw0rd123!")).thenReturn("hashed-value");
    when(pendingRegistrationRepository.save(any(PendingRegistration.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    userService.register(request);

    verify(userRepository, never()).save(any());
    ArgumentCaptor<PendingRegistration> captor = ArgumentCaptor.forClass(PendingRegistration.class);
    verify(pendingRegistrationRepository).save(captor.capture());
    PendingRegistration saved = captor.getValue();

    assertThat(saved.getEmail()).isEqualTo("yamada@example.com");
    assertThat(saved.getName()).isEqualTo("山田太郎");
    assertThat(saved.getPasswordHash()).isEqualTo("hashed-value");
    // 生パスワードがそのままpasswordHashへ入っていないこと。
    assertThat(saved.getPasswordHash()).isNotEqualTo("Passw0rd123!");
  }

  @Test
  void 登録時にpendingへ保存されるtoken_hashは生tokenの平文ではない() {
    userService = newService();
    RegisterRequest request = requestOf("山田太郎", "hash-check@example.com", "Passw0rd123!");

    when(userRepository.findByEmail("hash-check@example.com")).thenReturn(Optional.empty());
    when(pendingRegistrationRepository.findByEmail("hash-check@example.com")).thenReturn(Optional.empty());
    when(passwordEncoder.encode(anyString())).thenReturn("hashed-value");
    when(pendingRegistrationRepository.save(any(PendingRegistration.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    userService.register(request);

    ArgumentCaptor<PendingRegistration> captor = ArgumentCaptor.forClass(PendingRegistration.class);
    verify(pendingRegistrationRepository).save(captor.capture());
    String tokenHash = captor.getValue().getTokenHash();

    // SHA-256の16進文字列は64文字であり、Base64 URL-safeの生トークン(32byte)とは
    // 長さ・文字種が異なるため、少なくとも生トークンがそのまま保存されていないことを確認できる。
    assertThat(tokenHash).hasSize(64);
    assertThat(tokenHash).matches("^[0-9a-f]{64}$");
  }

  @Test
  void 登録時にexpires_atが現在時刻から24時間後になる() {
    userService = newService();
    RegisterRequest request = requestOf("山田太郎", "expiry@example.com", "Passw0rd123!");

    when(userRepository.findByEmail("expiry@example.com")).thenReturn(Optional.empty());
    when(pendingRegistrationRepository.findByEmail("expiry@example.com")).thenReturn(Optional.empty());
    when(passwordEncoder.encode(anyString())).thenReturn("hashed-value");
    when(pendingRegistrationRepository.save(any(PendingRegistration.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    Instant before = Instant.now();
    userService.register(request);
    Instant after = Instant.now();

    ArgumentCaptor<PendingRegistration> captor = ArgumentCaptor.forClass(PendingRegistration.class);
    verify(pendingRegistrationRepository).save(captor.capture());
    Instant expiresAt = captor.getValue().getExpiresAt();

    assertThat(expiresAt).isAfterOrEqualTo(before.plusSeconds(24 * 3600 - 5));
    assertThat(expiresAt).isBeforeOrEqualTo(after.plusSeconds(24 * 3600 + 5));
  }

  @Test
  void 登録時に確認メールが送信される() {
    userService = newService();
    RegisterRequest request = requestOf("山田太郎", "mail-check@example.com", "Passw0rd123!");

    when(userRepository.findByEmail("mail-check@example.com")).thenReturn(Optional.empty());
    when(pendingRegistrationRepository.findByEmail("mail-check@example.com")).thenReturn(Optional.empty());
    when(passwordEncoder.encode(anyString())).thenReturn("hashed-value");
    when(pendingRegistrationRepository.save(any(PendingRegistration.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    userService.register(request);

    verify(verificationMailService).sendVerificationEmail(org.mockito.ArgumentMatchers.eq("mail-check@example.com"), anyString());
  }

  @Test
  void 既にusersへ正式登録済みのメールアドレスは重複エラーになりpendingへ保存されない() {
    userService = newService();
    RegisterRequest request = requestOf("鈴木一郎", "duplicate@example.com", "Passw0rd123!");

    when(userRepository.findByEmail("duplicate@example.com"))
        .thenReturn(Optional.of(new User("既存ユーザー", "duplicate@example.com", "hash", Role.USER, true)));

    assertThatThrownBy(() -> userService.register(request))
        .isInstanceOf(DuplicateEmailException.class);

    verify(userRepository, never()).save(any());
    verify(pendingRegistrationRepository, never()).save(any());
  }

  @Test
  void pending済みのメールアドレスで再登録すると既存pendingが新しい内容とtokenで更新される() {
    userService = newService();
    RegisterRequest request = requestOf("修正後の名前", "retry@example.com", "NewPassw0rd1!");

    Instant oldExpiresAt = Instant.now().minusSeconds(10);
    PendingRegistration existing = pendingOf(1L, "retry@example.com", "old-token-hash", oldExpiresAt);

    when(userRepository.findByEmail("retry@example.com")).thenReturn(Optional.empty());
    when(pendingRegistrationRepository.findByEmail("retry@example.com")).thenReturn(Optional.of(existing));
    when(passwordEncoder.encode("NewPassw0rd1!")).thenReturn("new-hashed-value");
    when(pendingRegistrationRepository.save(any(PendingRegistration.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    userService.register(request);

    ArgumentCaptor<PendingRegistration> captor = ArgumentCaptor.forClass(PendingRegistration.class);
    verify(pendingRegistrationRepository).save(captor.capture());
    PendingRegistration saved = captor.getValue();

    // 同一インスタンス(id=1)が更新されており、新しいレコードとして追加されていない。
    assertThat(saved.getId()).isEqualTo(1L);
    assertThat(saved.getName()).isEqualTo("修正後の名前");
    assertThat(saved.getPasswordHash()).isEqualTo("new-hashed-value");
    // 古いtoken_hashは新しい値へ置き換わり、もう存在しない。
    assertThat(saved.getTokenHash()).isNotEqualTo("old-token-hash");
    // 期限切れだった古いexpires_atも新しい24時間後の値へ更新される。
    assertThat(saved.getExpiresAt()).isAfter(Instant.now());
  }

  // ===== メール認証(usersへの正式登録) =====

  @Test
  void 正常なtokenで認証するとusersへ保存されpendingが削除される() {
    userService = newService();
    String rawToken = "raw-token-value";
    String tokenHash = VerificationTokenGenerator.hash(rawToken);
    PendingRegistration pending = pendingOf(10L, "verify-ok@example.com", tokenHash, Instant.now().plusSeconds(3600));
    ReflectionTestUtils.setField(pending, "name", "認証太郎");
    ReflectionTestUtils.setField(pending, "passwordHash", "hashed-password");

    when(pendingRegistrationRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(pending));
    when(userRepository.findByEmail("verify-ok@example.com")).thenReturn(Optional.empty());

    userService.verifyEmail(rawToken);

    ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
    verify(userRepository).save(userCaptor.capture());
    User savedUser = userCaptor.getValue();
    assertThat(savedUser.getEmail()).isEqualTo("verify-ok@example.com");
    assertThat(savedUser.getName()).isEqualTo("認証太郎");
    assertThat(savedUser.getPasswordHash()).isEqualTo("hashed-password");
    assertThat(savedUser.getRole()).isEqualTo(Role.USER);
    assertThat(savedUser.isEnabled()).isTrue();

    verify(pendingRegistrationRepository).delete(pending);
  }

  @Test
  void 存在しないtokenで認証すると失敗する() {
    userService = newService();
    String tokenHash = VerificationTokenGenerator.hash("unknown-token");
    when(pendingRegistrationRepository.findByTokenHash(tokenHash)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> userService.verifyEmail("unknown-token"))
        .isInstanceOf(InvalidVerificationTokenException.class);

    verify(userRepository, never()).save(any());
  }

  @Test
  void 期限切れtokenで認証すると失敗しusersへ保存されない() {
    userService = newService();
    String rawToken = "expired-token";
    String tokenHash = VerificationTokenGenerator.hash(rawToken);
    PendingRegistration pending = pendingOf(11L, "expired@example.com", tokenHash, Instant.now().minusSeconds(1));

    when(pendingRegistrationRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(pending));

    assertThatThrownBy(() -> userService.verifyEmail(rawToken))
        .isInstanceOf(InvalidVerificationTokenException.class);

    verify(userRepository, never()).save(any());
    verify(pendingRegistrationRepository, never()).delete(any());
  }

  @Test
  void 認証成功後に同じtokenを再使用すると失敗する() {
    userService = newService();
    String rawToken = "one-time-token";
    String tokenHash = VerificationTokenGenerator.hash(rawToken);
    PendingRegistration pending = pendingOf(12L, "one-time@example.com", tokenHash, Instant.now().plusSeconds(3600));

    when(pendingRegistrationRepository.findByTokenHash(tokenHash))
        .thenReturn(Optional.of(pending))
        .thenReturn(Optional.empty());
    when(userRepository.findByEmail("one-time@example.com")).thenReturn(Optional.empty());

    userService.verifyEmail(rawToken);
    verify(pendingRegistrationRepository).delete(pending);

    // 1回目の認証でpendingは削除済みのため、2回目は見つからず失敗する。
    assertThatThrownBy(() -> userService.verifyEmail(rawToken))
        .isInstanceOf(InvalidVerificationTokenException.class);
  }

  @Test
  void 認証直前に同じメールが既にusersへ登録されていた場合は失敗しpendingを削除する() {
    userService = newService();
    String rawToken = "race-token";
    String tokenHash = VerificationTokenGenerator.hash(rawToken);
    PendingRegistration pending = pendingOf(13L, "race@example.com", tokenHash, Instant.now().plusSeconds(3600));

    when(pendingRegistrationRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(pending));
    when(userRepository.findByEmail("race@example.com"))
        .thenReturn(Optional.of(new User("先に登録済み", "race@example.com", "hash", Role.USER, true)));

    assertThatThrownBy(() -> userService.verifyEmail(rawToken))
        .isInstanceOf(InvalidVerificationTokenException.class);

    verify(userRepository, never()).save(any());
    verify(pendingRegistrationRepository).delete(pending);
  }

  // ===== 認証メール再送 =====

  @Test
  void pendingが存在する場合は新しいtokenが発行され古いtokenが無効化される() {
    userService = newService();
    PendingRegistration pending = pendingOf(20L, "resend@example.com", "old-token-hash", Instant.now().plusSeconds(10));

    when(userRepository.findByEmail("resend@example.com")).thenReturn(Optional.empty());
    when(pendingRegistrationRepository.findByEmail("resend@example.com")).thenReturn(Optional.of(pending));

    userService.resendVerification("resend@example.com");

    ArgumentCaptor<PendingRegistration> captor = ArgumentCaptor.forClass(PendingRegistration.class);
    verify(pendingRegistrationRepository).save(captor.capture());
    assertThat(captor.getValue().getTokenHash()).isNotEqualTo("old-token-hash");
    assertThat(captor.getValue().getExpiresAt()).isAfter(Instant.now().plusSeconds(3600 * 23));

    verify(verificationMailService).sendVerificationEmail(org.mockito.ArgumentMatchers.eq("resend@example.com"), anyString());
  }

  @Test
  void 正式登録済みユーザーへの再送要求は何も送信せず例外も投げない() {
    userService = newService();
    when(userRepository.findByEmail("already-registered@example.com"))
        .thenReturn(Optional.of(new User("登録済み", "already-registered@example.com", "hash", Role.USER, true)));

    userService.resendVerification("already-registered@example.com");

    verify(pendingRegistrationRepository, never()).save(any());
    verify(verificationMailService, never()).sendVerificationEmail(anyString(), anyString());
    // pending検索自体行われないこと(usersに存在する時点で判定を終える)。
    verify(pendingRegistrationRepository, never()).findByEmail(any());
  }

  @Test
  void 存在しないメールアドレスへの再送要求は何も送信せず例外も投げない() {
    userService = newService();
    when(userRepository.findByEmail("unknown@example.com")).thenReturn(Optional.empty());
    when(pendingRegistrationRepository.findByEmail("unknown@example.com")).thenReturn(Optional.empty());

    userService.resendVerification("unknown@example.com");

    verify(pendingRegistrationRepository, never()).save(any());
    verify(verificationMailService, never()).sendVerificationEmail(anyString(), anyString());
  }

  // ===== パスワードリセット要求 =====

  @Test
  void 登録済みメールアドレスへのリセット要求でtokenが作成される() {
    userService = newService();
    User user = userOf(1L, "山田太郎", "reset-target@example.com", "existing-hash");

    when(userRepository.findByEmail("reset-target@example.com")).thenReturn(Optional.of(user));
    when(passwordResetTokenRepository.findByUserId(1L)).thenReturn(Optional.empty());
    when(passwordResetTokenRepository.save(any(PasswordResetToken.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    userService.requestPasswordReset("Reset-Target@EXAMPLE.com");

    ArgumentCaptor<PasswordResetToken> captor = ArgumentCaptor.forClass(PasswordResetToken.class);
    verify(passwordResetTokenRepository).save(captor.capture());
    PasswordResetToken saved = captor.getValue();

    assertThat(saved.getUserId()).isEqualTo(1L);
    // 生tokenがそのまま保存されていないこと(SHA-256の16進64文字であること)。
    assertThat(saved.getTokenHash()).hasSize(64).matches("^[0-9a-f]{64}$");
    verify(passwordResetMailService).sendPasswordResetEmail(
        org.mockito.ArgumentMatchers.eq("reset-target@example.com"), anyString());
  }

  @Test
  void リセット要求時にexpires_atが現在時刻から1時間後になる() {
    userService = newService();
    User user = userOf(2L, "田中花子", "expiry-check@example.com", "hash");

    when(userRepository.findByEmail("expiry-check@example.com")).thenReturn(Optional.of(user));
    when(passwordResetTokenRepository.findByUserId(2L)).thenReturn(Optional.empty());
    when(passwordResetTokenRepository.save(any(PasswordResetToken.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    Instant before = Instant.now();
    userService.requestPasswordReset("expiry-check@example.com");
    Instant after = Instant.now();

    ArgumentCaptor<PasswordResetToken> captor = ArgumentCaptor.forClass(PasswordResetToken.class);
    verify(passwordResetTokenRepository).save(captor.capture());
    Instant expiresAt = captor.getValue().getExpiresAt();

    assertThat(expiresAt).isAfterOrEqualTo(before.plusSeconds(3600 - 5));
    assertThat(expiresAt).isBeforeOrEqualTo(after.plusSeconds(3600 + 5));
  }

  @Test
  void 未登録メールアドレスへのリセット要求は例外を投げず何もしない() {
    userService = newService();
    when(userRepository.findByEmail("unknown-for-reset@example.com")).thenReturn(Optional.empty());

    userService.requestPasswordReset("unknown-for-reset@example.com");

    verify(passwordResetTokenRepository, never()).save(any());
    verify(passwordResetMailService, never()).sendPasswordResetEmail(anyString(), anyString());
  }

  @Test
  void 同一ユーザーが再度リセット要求すると既存tokenが新しいtokenへ上書きされる() {
    userService = newService();
    User user = userOf(3L, "佐藤次郎", "reissue@example.com", "hash");
    PasswordResetToken existing = passwordResetTokenOf(30L, 3L, "old-token-hash", Instant.now().plusSeconds(1800));

    when(userRepository.findByEmail("reissue@example.com")).thenReturn(Optional.of(user));
    when(passwordResetTokenRepository.findByUserId(3L)).thenReturn(Optional.of(existing));
    when(passwordResetTokenRepository.save(any(PasswordResetToken.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    userService.requestPasswordReset("reissue@example.com");

    ArgumentCaptor<PasswordResetToken> captor = ArgumentCaptor.forClass(PasswordResetToken.class);
    verify(passwordResetTokenRepository).save(captor.capture());
    PasswordResetToken saved = captor.getValue();

    // 同一レコード(id=30)が更新されており、新しいレコードとして追加されていない。
    assertThat(saved.getId()).isEqualTo(30L);
    assertThat(saved.getTokenHash()).isNotEqualTo("old-token-hash");
    assertThat(saved.getExpiresAt()).isAfter(Instant.now().plusSeconds(3500));
  }

  @Test
  void リセット要求のたびに期限切れtokenのクリーンアップが呼ばれる() {
    userService = newService();
    when(userRepository.findByEmail("cleanup-check@example.com")).thenReturn(Optional.empty());

    userService.requestPasswordReset("cleanup-check@example.com");

    verify(passwordResetTokenRepository).deleteByExpiresAtBefore(any(Instant.class));
  }

  // ===== パスワードリセット確認(パスワード更新) =====

  @Test
  void 正常なtokenでパスワードを更新するとusersのpassword_hashが更新されtokenが削除される() {
    userService = newService();
    String rawToken = "reset-raw-token";
    String tokenHash = VerificationTokenGenerator.hash(rawToken);
    User user = userOf(4L, "確認太郎", "confirm-ok@example.com", "old-hash");
    PasswordResetToken token = passwordResetTokenOf(40L, 4L, tokenHash, Instant.now().plusSeconds(1800));

    when(passwordResetTokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(token));
    when(userRepository.findById(4L)).thenReturn(Optional.of(user));
    when(passwordEncoder.encode("NewPassw0rd1!")).thenReturn("new-hashed-value");

    userService.confirmPasswordReset(rawToken, "NewPassw0rd1!");

    assertThat(user.getPasswordHash()).isEqualTo("new-hashed-value");
    verify(userRepository).save(user);
    verify(passwordResetTokenRepository).delete(token);
  }

  @Test
  void 存在しないtokenでの更新は失敗する() {
    userService = newService();
    String tokenHash = VerificationTokenGenerator.hash("unknown-reset-token");
    when(passwordResetTokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> userService.confirmPasswordReset("unknown-reset-token", "NewPassw0rd1!"))
        .isInstanceOf(InvalidPasswordResetTokenException.class);

    verify(userRepository, never()).save(any());
  }

  @Test
  void 期限切れtokenでの更新は失敗しpassword_hashが変更されない() {
    userService = newService();
    String rawToken = "expired-reset-token";
    String tokenHash = VerificationTokenGenerator.hash(rawToken);
    PasswordResetToken token = passwordResetTokenOf(41L, 5L, tokenHash, Instant.now().minusSeconds(1));

    when(passwordResetTokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(token));

    assertThatThrownBy(() -> userService.confirmPasswordReset(rawToken, "NewPassw0rd1!"))
        .isInstanceOf(InvalidPasswordResetTokenException.class);

    verify(userRepository, never()).save(any());
    verify(passwordResetTokenRepository, never()).delete(any());
  }

  @Test
  void 更新成功後に同じtokenを再使用すると失敗する() {
    userService = newService();
    String rawToken = "one-time-reset-token";
    String tokenHash = VerificationTokenGenerator.hash(rawToken);
    User user = userOf(6L, "再利用花子", "reset-reuse@example.com", "old-hash");
    PasswordResetToken token = passwordResetTokenOf(42L, 6L, tokenHash, Instant.now().plusSeconds(1800));

    when(passwordResetTokenRepository.findByTokenHash(tokenHash))
        .thenReturn(Optional.of(token))
        .thenReturn(Optional.empty());
    when(userRepository.findById(6L)).thenReturn(Optional.of(user));
    when(passwordEncoder.encode(anyString())).thenReturn("new-hashed-value");

    userService.confirmPasswordReset(rawToken, "NewPassw0rd1!");
    verify(passwordResetTokenRepository).delete(token);

    // 1回目の更新でtokenは削除済みのため、2回目は見つからず失敗する。
    assertThatThrownBy(() -> userService.confirmPasswordReset(rawToken, "AnotherPassw0rd1!"))
        .isInstanceOf(InvalidPasswordResetTokenException.class);
  }

  // ===== 既存機能(担当AGENT一覧) =====

  @Test
  void listAgentsはRepositoryの結果をid名前のみのDTOへ変換する() {
    userService = newService();

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
    userService = newService();

    when(userRepository.findByRoleAndEnabledTrueOrderByNameAscIdAsc(Role.AGENT)).thenReturn(List.of());

    List<AgentSummaryResponse> result = userService.listAgents();

    assertThat(result).isEmpty();
  }
}
