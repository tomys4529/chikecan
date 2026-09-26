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
import com.chikecan.backend.entity.EmailChangeRequest;
import com.chikecan.backend.entity.NameFormat;
import com.chikecan.backend.entity.PasswordResetToken;
import com.chikecan.backend.entity.PendingRegistration;
import com.chikecan.backend.entity.Role;
import com.chikecan.backend.entity.User;
import com.chikecan.backend.exception.DuplicateEmailException;
import com.chikecan.backend.exception.InvalidCredentialsException;
import com.chikecan.backend.exception.InvalidEmailChangeTokenException;
import com.chikecan.backend.exception.InvalidPasswordResetTokenException;
import com.chikecan.backend.exception.InvalidVerificationTokenException;
import com.chikecan.backend.exception.SamePasswordException;
import com.chikecan.backend.repository.EmailChangeRequestRepository;
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
  private EmailChangeRequestRepository emailChangeRequestRepository;

  @Mock
  private PasswordEncoder passwordEncoder;

  @Mock
  private VerificationMailService verificationMailService;

  @Mock
  private PasswordResetMailService passwordResetMailService;

  @Mock
  private EmailChangeMailService emailChangeMailService;

  private UserService userService;

  private UserService newService() {
    return new UserService(userRepository, pendingRegistrationRepository, passwordResetTokenRepository,
        emailChangeRequestRepository, passwordEncoder, verificationMailService, passwordResetMailService,
        emailChangeMailService);
  }

  private RegisterRequest requestOf(String familyName, String givenName, String email, String password) {
    RegisterRequest request = new RegisterRequest();
    request.setNameFormat(NameFormat.JAPANESE);
    request.setFamilyName(familyName);
    request.setGivenName(givenName);
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

  private EmailChangeRequest emailChangeRequestOf(Long id, Long userId, String newEmail, String tokenHash,
      Instant expiresAt) {
    EmailChangeRequest request = new EmailChangeRequest(userId, newEmail, tokenHash, expiresAt);
    ReflectionTestUtils.setField(request, "id", id);
    return request;
  }

  // ===== 登録(pending_registrationsへの一時保存) =====

  @Test
  void 正常登録するとメールが正規化されusersへは保存されずpendingへ保存される() {
    userService = newService();
    RegisterRequest request = requestOf("山田", "太郎", "  Yamada@EXAMPLE.com  ", "Passw0rd123!");

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
    // LEGACY互換のname列には、姓名を組み立てた表示名相当の値が入る。
    assertThat(saved.getName()).isEqualTo("山田 太郎");
    assertThat(saved.getPasswordHash()).isEqualTo("hashed-value");
    // 生パスワードがそのままpasswordHashへ入っていないこと。
    assertThat(saved.getPasswordHash()).isNotEqualTo("Passw0rd123!");
  }

  @Test
  void 日本向け氏名で登録するとnameFormatと姓名がpendingへ保存される() {
    userService = newService();
    RegisterRequest request = requestOf("佐藤", "太郎", "japanese-name@example.com", "Passw0rd123!");

    when(userRepository.findByEmail("japanese-name@example.com")).thenReturn(Optional.empty());
    when(pendingRegistrationRepository.findByEmail("japanese-name@example.com")).thenReturn(Optional.empty());
    when(passwordEncoder.encode(anyString())).thenReturn("hashed-value");
    when(pendingRegistrationRepository.save(any(PendingRegistration.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    userService.register(request);

    ArgumentCaptor<PendingRegistration> captor = ArgumentCaptor.forClass(PendingRegistration.class);
    verify(pendingRegistrationRepository).save(captor.capture());
    PendingRegistration saved = captor.getValue();

    assertThat(saved.getNameFormat()).isEqualTo(NameFormat.JAPANESE);
    assertThat(saved.getFamilyName()).isEqualTo("佐藤");
    assertThat(saved.getGivenName()).isEqualTo("太郎");
    assertThat(saved.getMiddleName()).isNull();
    assertThat(saved.getName()).isEqualTo("佐藤 太郎");
  }

  @Test
  void 海外向け氏名で登録するとnameFormatと姓名ミドルネームがpendingへ保存される() {
    userService = newService();
    RegisterRequest request = new RegisterRequest();
    request.setNameFormat(NameFormat.INTERNATIONAL);
    request.setFamilyName("Smith");
    request.setGivenName("John");
    request.setMiddleName("Michael");
    request.setEmail("international-name@example.com");
    request.setPassword("Passw0rd123!");

    when(userRepository.findByEmail("international-name@example.com")).thenReturn(Optional.empty());
    when(pendingRegistrationRepository.findByEmail("international-name@example.com")).thenReturn(Optional.empty());
    when(passwordEncoder.encode(anyString())).thenReturn("hashed-value");
    when(pendingRegistrationRepository.save(any(PendingRegistration.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    userService.register(request);

    ArgumentCaptor<PendingRegistration> captor = ArgumentCaptor.forClass(PendingRegistration.class);
    verify(pendingRegistrationRepository).save(captor.capture());
    PendingRegistration saved = captor.getValue();

    assertThat(saved.getNameFormat()).isEqualTo(NameFormat.INTERNATIONAL);
    assertThat(saved.getFamilyName()).isEqualTo("Smith");
    assertThat(saved.getGivenName()).isEqualTo("John");
    assertThat(saved.getMiddleName()).isEqualTo("Michael");
    assertThat(saved.getName()).isEqualTo("John Michael Smith");
  }

  @Test
  void 登録時にpendingへ保存されるtoken_hashは生tokenの平文ではない() {
    userService = newService();
    RegisterRequest request = requestOf("山田", "太郎", "hash-check@example.com", "Passw0rd123!");

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
    RegisterRequest request = requestOf("山田", "太郎", "expiry@example.com", "Passw0rd123!");

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
    RegisterRequest request = requestOf("山田", "太郎", "mail-check@example.com", "Passw0rd123!");

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
    RegisterRequest request = requestOf("鈴木", "一郎", "duplicate@example.com", "Passw0rd123!");

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
    RegisterRequest request = requestOf("更新後", "花子", "retry@example.com", "NewPassw0rd1!");

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
    assertThat(saved.getFamilyName()).isEqualTo("更新後");
    assertThat(saved.getGivenName()).isEqualTo("花子");
    assertThat(saved.getName()).isEqualTo("更新後 花子");
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
  void 認証成功時にpendingの構造化された氏名がusersへ引き継がれる() {
    userService = newService();
    String rawToken = "structured-name-token";
    String tokenHash = VerificationTokenGenerator.hash(rawToken);
    PendingRegistration pending = new PendingRegistration("鈴木 花子", "structured-verify@example.com",
        "hashed-password", tokenHash, Instant.now().plusSeconds(3600),
        NameFormat.JAPANESE, "鈴木", "花子", null);

    when(pendingRegistrationRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(pending));
    when(userRepository.findByEmail("structured-verify@example.com")).thenReturn(Optional.empty());

    userService.verifyEmail(rawToken);

    ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
    verify(userRepository).save(userCaptor.capture());
    User savedUser = userCaptor.getValue();
    assertThat(savedUser.getNameFormat()).isEqualTo(NameFormat.JAPANESE);
    assertThat(savedUser.getFamilyName()).isEqualTo("鈴木");
    assertThat(savedUser.getGivenName()).isEqualTo("花子");
    assertThat(savedUser.getMiddleName()).isNull();
    assertThat(savedUser.getDisplayName()).isEqualTo("鈴木 花子");
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

  // ===== ログイン後パスワード変更 =====

  @Test
  void 現在のパスワードが正しい場合はパスワードが更新される() {
    userService = newService();
    User user = userOf(50L, "変更太郎", "change-password@example.com", "old-hash");

    when(userRepository.findById(50L)).thenReturn(Optional.of(user));
    when(passwordEncoder.matches("OldPassw0rd1!", "old-hash")).thenReturn(true);
    when(passwordEncoder.encode("NewPassw0rd1!")).thenReturn("new-hashed-value");

    userService.changePassword(50L, "OldPassw0rd1!", "NewPassw0rd1!");

    assertThat(user.getPasswordHash()).isEqualTo("new-hashed-value");
    verify(userRepository).save(user);
  }

  @Test
  void 現在のパスワードが誤っている場合は失敗しパスワードが更新されない() {
    userService = newService();
    User user = userOf(51L, "誤り太郎", "wrong-current@example.com", "old-hash");

    when(userRepository.findById(51L)).thenReturn(Optional.of(user));
    when(passwordEncoder.matches("WrongPassw0rd1!", "old-hash")).thenReturn(false);

    assertThatThrownBy(() -> userService.changePassword(51L, "WrongPassw0rd1!", "NewPassw0rd1!"))
        .isInstanceOf(InvalidCredentialsException.class);

    assertThat(user.getPasswordHash()).isEqualTo("old-hash");
    verify(userRepository, never()).save(any());
  }

  @Test
  void 新しいパスワードが現在のパスワードと同じ場合は失敗する() {
    userService = newService();
    User user = userOf(52L, "同一太郎", "same-password@example.com", "old-hash");

    when(userRepository.findById(52L)).thenReturn(Optional.of(user));
    when(passwordEncoder.matches("SamePassw0rd1!", "old-hash")).thenReturn(true);

    assertThatThrownBy(() -> userService.changePassword(52L, "SamePassw0rd1!", "SamePassw0rd1!"))
        .isInstanceOf(SamePasswordException.class);

    verify(userRepository, never()).save(any());
  }

  // ===== メールアドレス変更申請 =====

  @Test
  void 正常な申請でtokenが作成されメールが送信される() {
    userService = newService();
    User user = userOf(60L, "申請太郎", "current@example.com", "hash");

    when(userRepository.findById(60L)).thenReturn(Optional.of(user));
    when(passwordEncoder.matches("Passw0rd123!", "hash")).thenReturn(true);
    when(userRepository.findByEmail("new-address@example.com")).thenReturn(Optional.empty());
    when(pendingRegistrationRepository.findByEmail("new-address@example.com")).thenReturn(Optional.empty());
    when(emailChangeRequestRepository.findByNewEmail("new-address@example.com")).thenReturn(Optional.empty());
    when(emailChangeRequestRepository.findByUserId(60L)).thenReturn(Optional.empty());
    when(emailChangeRequestRepository.save(any(EmailChangeRequest.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    userService.requestEmailChange(60L, "New-Address@EXAMPLE.com", "Passw0rd123!");

    ArgumentCaptor<EmailChangeRequest> captor = ArgumentCaptor.forClass(EmailChangeRequest.class);
    verify(emailChangeRequestRepository).save(captor.capture());
    EmailChangeRequest saved = captor.getValue();

    assertThat(saved.getUserId()).isEqualTo(60L);
    assertThat(saved.getNewEmail()).isEqualTo("new-address@example.com");
    assertThat(saved.getTokenHash()).hasSize(64).matches("^[0-9a-f]{64}$");
    verify(emailChangeMailService).sendEmailChangeEmail(
        org.mockito.ArgumentMatchers.eq("new-address@example.com"), anyString());
  }

  @Test
  void 現在のパスワードが誤っている場合はメール変更申請が失敗する() {
    userService = newService();
    User user = userOf(61L, "失敗太郎", "current2@example.com", "hash");

    when(userRepository.findById(61L)).thenReturn(Optional.of(user));
    when(passwordEncoder.matches("WrongPassw0rd1!", "hash")).thenReturn(false);

    assertThatThrownBy(() -> userService.requestEmailChange(61L, "new@example.com", "WrongPassw0rd1!"))
        .isInstanceOf(InvalidCredentialsException.class);

    verify(emailChangeRequestRepository, never()).save(any());
  }

  @Test
  void 現在のメールアドレスと同じ場合はメール変更申請が失敗する() {
    userService = newService();
    User user = userOf(62L, "同一太郎", "same-as-current@example.com", "hash");

    when(userRepository.findById(62L)).thenReturn(Optional.of(user));
    when(passwordEncoder.matches("Passw0rd123!", "hash")).thenReturn(true);

    assertThatThrownBy(() -> userService.requestEmailChange(62L, "Same-As-Current@EXAMPLE.com", "Passw0rd123!"))
        .isInstanceOf(DuplicateEmailException.class);

    verify(emailChangeRequestRepository, never()).save(any());
  }

  @Test
  void usersで既に使われているメールアドレスへの変更申請は失敗する() {
    userService = newService();
    User user = userOf(63L, "重複太郎", "requester@example.com", "hash");

    when(userRepository.findById(63L)).thenReturn(Optional.of(user));
    when(passwordEncoder.matches("Passw0rd123!", "hash")).thenReturn(true);
    when(userRepository.findByEmail("already-registered@example.com"))
        .thenReturn(Optional.of(new User("既存", "already-registered@example.com", "hash2", Role.USER, true)));

    assertThatThrownBy(() -> userService.requestEmailChange(63L, "already-registered@example.com", "Passw0rd123!"))
        .isInstanceOf(DuplicateEmailException.class);

    verify(emailChangeRequestRepository, never()).save(any());
  }

  @Test
  void pendingで既に使われているメールアドレスへの変更申請は失敗する() {
    userService = newService();
    User user = userOf(64L, "保留太郎", "requester2@example.com", "hash");

    when(userRepository.findById(64L)).thenReturn(Optional.of(user));
    when(passwordEncoder.matches("Passw0rd123!", "hash")).thenReturn(true);
    when(userRepository.findByEmail("pending-target@example.com")).thenReturn(Optional.empty());
    when(pendingRegistrationRepository.findByEmail("pending-target@example.com"))
        .thenReturn(Optional.of(pendingOf(1L, "pending-target@example.com", "some-token-hash",
            Instant.now().plusSeconds(3600))));

    assertThatThrownBy(() -> userService.requestEmailChange(64L, "pending-target@example.com", "Passw0rd123!"))
        .isInstanceOf(DuplicateEmailException.class);

    verify(emailChangeRequestRepository, never()).save(any());
  }

  @Test
  void 他ユーザーの変更申請で使われているメールアドレスへの変更申請は失敗する() {
    userService = newService();
    User user = userOf(65L, "競合太郎", "requester3@example.com", "hash");
    EmailChangeRequest otherUsersRequest =
        emailChangeRequestOf(90L, 999L, "contested@example.com", "other-token-hash", Instant.now().plusSeconds(3600));

    when(userRepository.findById(65L)).thenReturn(Optional.of(user));
    when(passwordEncoder.matches("Passw0rd123!", "hash")).thenReturn(true);
    when(userRepository.findByEmail("contested@example.com")).thenReturn(Optional.empty());
    when(pendingRegistrationRepository.findByEmail("contested@example.com")).thenReturn(Optional.empty());
    when(emailChangeRequestRepository.findByNewEmail("contested@example.com"))
        .thenReturn(Optional.of(otherUsersRequest));

    assertThatThrownBy(() -> userService.requestEmailChange(65L, "contested@example.com", "Passw0rd123!"))
        .isInstanceOf(DuplicateEmailException.class);

    verify(emailChangeRequestRepository, never()).save(any());
  }

  @Test
  void 同一ユーザーが再申請すると既存申請が新しい内容とtokenへ更新される() {
    userService = newService();
    User user = userOf(66L, "再申請太郎", "reissue-requester@example.com", "hash");
    EmailChangeRequest existing = emailChangeRequestOf(91L, 66L, "old-target@example.com", "old-token-hash",
        Instant.now().plusSeconds(1800));

    when(userRepository.findById(66L)).thenReturn(Optional.of(user));
    when(passwordEncoder.matches("Passw0rd123!", "hash")).thenReturn(true);
    when(userRepository.findByEmail("new-target@example.com")).thenReturn(Optional.empty());
    when(pendingRegistrationRepository.findByEmail("new-target@example.com")).thenReturn(Optional.empty());
    when(emailChangeRequestRepository.findByNewEmail("new-target@example.com")).thenReturn(Optional.empty());
    when(emailChangeRequestRepository.findByUserId(66L)).thenReturn(Optional.of(existing));
    when(emailChangeRequestRepository.save(any(EmailChangeRequest.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    userService.requestEmailChange(66L, "new-target@example.com", "Passw0rd123!");

    ArgumentCaptor<EmailChangeRequest> captor = ArgumentCaptor.forClass(EmailChangeRequest.class);
    verify(emailChangeRequestRepository).save(captor.capture());
    EmailChangeRequest saved = captor.getValue();

    // 同一レコード(id=91)が更新されており、新しいレコードとして追加されていない。
    assertThat(saved.getId()).isEqualTo(91L);
    assertThat(saved.getNewEmail()).isEqualTo("new-target@example.com");
    assertThat(saved.getTokenHash()).isNotEqualTo("old-token-hash");
  }

  @Test
  void メール変更申請のたびに期限切れ申請のクリーンアップが呼ばれる() {
    userService = newService();
    User user = userOf(67L, "掃除太郎", "cleanup-requester@example.com", "hash");

    when(userRepository.findById(67L)).thenReturn(Optional.of(user));
    when(passwordEncoder.matches("Passw0rd123!", "hash")).thenReturn(true);
    when(userRepository.findByEmail("cleanup-target@example.com")).thenReturn(Optional.empty());
    when(pendingRegistrationRepository.findByEmail("cleanup-target@example.com")).thenReturn(Optional.empty());
    when(emailChangeRequestRepository.findByNewEmail("cleanup-target@example.com")).thenReturn(Optional.empty());
    when(emailChangeRequestRepository.findByUserId(67L)).thenReturn(Optional.empty());
    when(emailChangeRequestRepository.save(any(EmailChangeRequest.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    userService.requestEmailChange(67L, "cleanup-target@example.com", "Passw0rd123!");

    verify(emailChangeRequestRepository).deleteByExpiresAtBefore(any(Instant.class));
  }

  // ===== メールアドレス変更確認(email更新) =====

  @Test
  void 正常なtokenでusersのemailが更新され申請が削除される() {
    userService = newService();
    String rawToken = "email-change-raw-token";
    String tokenHash = VerificationTokenGenerator.hash(rawToken);
    User user = userOf(70L, "確認太郎", "old-email@example.com", "hash");
    EmailChangeRequest request =
        emailChangeRequestOf(95L, 70L, "confirmed-new@example.com", tokenHash, Instant.now().plusSeconds(1800));

    when(emailChangeRequestRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(request));
    when(userRepository.findById(70L)).thenReturn(Optional.of(user));
    when(userRepository.findByEmail("confirmed-new@example.com")).thenReturn(Optional.empty());

    userService.confirmEmailChange(rawToken);

    assertThat(user.getEmail()).isEqualTo("confirmed-new@example.com");
    verify(userRepository).save(user);
    verify(emailChangeRequestRepository).delete(request);
    // 旧メール宛に発行済みのパスワードリセットtokenを無効化する。
    verify(passwordResetTokenRepository).deleteByUserId(70L);
  }

  @Test
  void 存在しないtokenでの確認は失敗する() {
    userService = newService();
    String tokenHash = VerificationTokenGenerator.hash("unknown-email-change-token");
    when(emailChangeRequestRepository.findByTokenHash(tokenHash)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> userService.confirmEmailChange("unknown-email-change-token"))
        .isInstanceOf(InvalidEmailChangeTokenException.class);

    verify(userRepository, never()).save(any());
    verify(passwordResetTokenRepository, never()).deleteByUserId(any());
  }

  @Test
  void 期限切れtokenでの確認は失敗しemailが変更されない() {
    userService = newService();
    String rawToken = "expired-email-change-token";
    String tokenHash = VerificationTokenGenerator.hash(rawToken);
    EmailChangeRequest request =
        emailChangeRequestOf(96L, 71L, "expired-target@example.com", tokenHash, Instant.now().minusSeconds(1));

    when(emailChangeRequestRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(request));

    assertThatThrownBy(() -> userService.confirmEmailChange(rawToken))
        .isInstanceOf(InvalidEmailChangeTokenException.class);

    verify(userRepository, never()).save(any());
    verify(emailChangeRequestRepository, never()).delete(any());
    verify(passwordResetTokenRepository, never()).deleteByUserId(any());
  }

  @Test
  void 確認成功後に同じtokenを再使用すると失敗する() {
    userService = newService();
    String rawToken = "one-time-email-change-token";
    String tokenHash = VerificationTokenGenerator.hash(rawToken);
    User user = userOf(72L, "再利用太郎", "reuse-old@example.com", "hash");
    EmailChangeRequest request =
        emailChangeRequestOf(97L, 72L, "reuse-new@example.com", tokenHash, Instant.now().plusSeconds(1800));

    when(emailChangeRequestRepository.findByTokenHash(tokenHash))
        .thenReturn(Optional.of(request))
        .thenReturn(Optional.empty());
    when(userRepository.findById(72L)).thenReturn(Optional.of(user));
    when(userRepository.findByEmail("reuse-new@example.com")).thenReturn(Optional.empty());

    userService.confirmEmailChange(rawToken);
    verify(emailChangeRequestRepository).delete(request);

    // 1回目の確認で申請は削除済みのため、2回目は見つからず失敗する。
    assertThatThrownBy(() -> userService.confirmEmailChange(rawToken))
        .isInstanceOf(InvalidEmailChangeTokenException.class);
  }

  @Test
  void 確認直前に別ユーザーが同じメールアドレスを取得していた場合は失敗し申請を削除する() {
    userService = newService();
    String rawToken = "race-email-change-token";
    String tokenHash = VerificationTokenGenerator.hash(rawToken);
    User user = userOf(73L, "競合太郎", "race-old@example.com", "hash");
    EmailChangeRequest request =
        emailChangeRequestOf(98L, 73L, "race-target@example.com", tokenHash, Instant.now().plusSeconds(1800));

    when(emailChangeRequestRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(request));
    when(userRepository.findById(73L)).thenReturn(Optional.of(user));
    when(userRepository.findByEmail("race-target@example.com"))
        .thenReturn(Optional.of(new User("先に取得済み", "race-target@example.com", "hash2", Role.USER, true)));

    assertThatThrownBy(() -> userService.confirmEmailChange(rawToken))
        .isInstanceOf(InvalidEmailChangeTokenException.class);

    verify(userRepository, never()).save(any());
    verify(emailChangeRequestRepository).delete(request);
    verify(passwordResetTokenRepository, never()).deleteByUserId(any());
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
