package com.chikecan.backend.service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.chikecan.backend.dto.AgentSummaryResponse;
import com.chikecan.backend.dto.RegisterRequest;
import com.chikecan.backend.dto.UserResponse;
import com.chikecan.backend.entity.PendingRegistration;
import com.chikecan.backend.entity.Role;
import com.chikecan.backend.entity.User;
import com.chikecan.backend.exception.DuplicateEmailException;
import com.chikecan.backend.exception.InvalidVerificationTokenException;
import com.chikecan.backend.repository.PendingRegistrationRepository;
import com.chikecan.backend.repository.UserRepository;
import com.chikecan.backend.security.VerificationTokenGenerator;

@Service
public class UserService {

  // メール認証トークンの有効期限。
  private static final Duration TOKEN_VALIDITY = Duration.ofHours(24);
  private static final String INVALID_TOKEN_MESSAGE = "認証リンクが無効または期限切れです";

  private final UserRepository userRepository;
  private final PendingRegistrationRepository pendingRegistrationRepository;
  private final PasswordEncoder passwordEncoder;
  private final VerificationMailService verificationMailService;

  public UserService(UserRepository userRepository,
      PendingRegistrationRepository pendingRegistrationRepository,
      PasswordEncoder passwordEncoder,
      VerificationMailService verificationMailService) {
    this.userRepository = userRepository;
    this.pendingRegistrationRepository = pendingRegistrationRepository;
    this.passwordEncoder = passwordEncoder;
    this.verificationMailService = verificationMailService;
  }

  /**
   * 登録フォーム送信時点ではusersへ一切保存しない。
   * パスワードをハッシュ化したうえでpending_registrationsへ保存し、認証メールを送信する。
   * 同じメールアドレスで既にpendingが存在する場合(入力ミス後の再登録等)は、
   * 新しい入力内容・新しいトークンで置き換える(古いトークンは即座に無効になる)。
   */
  @Transactional
  public void register(RegisterRequest request) {
    // Spring Scheduler等の定期実行は設けず、新規登録のたびに期限切れpendingを
    // 削除する遅延クリーンアップ方式にする。通常の利用が続く限り自然に掃除される。
    cleanupExpiredPending();

    String normalizedEmail = normalize(request.getEmail());

    if (userRepository.findByEmail(normalizedEmail).isPresent()) {
      throw new DuplicateEmailException("このメールアドレスは既に登録されています");
    }

    String passwordHash = passwordEncoder.encode(request.getPassword());
    String rawToken = VerificationTokenGenerator.generateRawToken();
    String tokenHash = VerificationTokenGenerator.hash(rawToken);
    Instant expiresAt = Instant.now().plus(TOKEN_VALIDITY);

    PendingRegistration pending = pendingRegistrationRepository.findByEmail(normalizedEmail)
        .orElseGet(() -> new PendingRegistration(request.getName(), normalizedEmail, passwordHash, tokenHash, expiresAt));
    pending.setName(request.getName());
    pending.setPasswordHash(passwordHash);
    pending.setTokenHash(tokenHash);
    pending.setExpiresAt(expiresAt);
    pendingRegistrationRepository.save(pending);

    verificationMailService.sendVerificationEmail(normalizedEmail, rawToken);
  }

  /**
   * メール内リンクのtokenを検証し、成功した場合のみusersへ正式登録する。
   * pendingの検索・users保存・pending削除を同一トランザクションで行い、
   * メール送信の成否に関わらずusersへの正式登録はここでしか行わない。
   */
  @Transactional
  public void verifyEmail(String rawToken) {
    String tokenHash = VerificationTokenGenerator.hash(rawToken);
    PendingRegistration pending = pendingRegistrationRepository.findByTokenHash(tokenHash)
        .orElseThrow(() -> new InvalidVerificationTokenException(INVALID_TOKEN_MESSAGE));

    if (pending.isExpired(Instant.now())) {
      throw new InvalidVerificationTokenException(INVALID_TOKEN_MESSAGE);
    }

    // 認証処理の直前に、他経路で同じメールが既にusersへ登録されていないか再確認する。
    // 該当する場合はpendingを削除したうえで無効なリンクとして扱う。
    if (userRepository.findByEmail(pending.getEmail()).isPresent()) {
      pendingRegistrationRepository.delete(pending);
      throw new InvalidVerificationTokenException(INVALID_TOKEN_MESSAGE);
    }

    User user = new User(pending.getName(), pending.getEmail(), pending.getPasswordHash(), Role.USER, true);
    userRepository.save(user);
    // 削除によりtokenは再利用できなくなる。
    pendingRegistrationRepository.delete(pending);
  }

  /**
   * 認証メールの再送。アカウント列挙を防ぐため、メールアドレスの登録状況に関わらず
   * 呼び出し側には常に同じ結果(例外を投げない)を返す。
   * pendingが存在する場合のみ新しいtokenを発行し、古いtokenは上書きにより無効化する。
   */
  @Transactional
  public void resendVerification(String email) {
    String normalizedEmail = normalize(email);

    if (userRepository.findByEmail(normalizedEmail).isPresent()) {
      // 正式登録済みのメールアドレスには何も送らない(呼び出し元には成功と同じ結果を返す)。
      return;
    }

    pendingRegistrationRepository.findByEmail(normalizedEmail).ifPresent(pending -> {
      String rawToken = VerificationTokenGenerator.generateRawToken();
      pending.setTokenHash(VerificationTokenGenerator.hash(rawToken));
      pending.setExpiresAt(Instant.now().plus(TOKEN_VALIDITY));
      pendingRegistrationRepository.save(pending);
      verificationMailService.sendVerificationEmail(normalizedEmail, rawToken);
    });
    // pendingが存在しない場合も例外を投げずに正常終了する。
  }

  private String normalize(String email) {
    return email.trim().toLowerCase(Locale.ROOT);
  }

  private void cleanupExpiredPending() {
    pendingRegistrationRepository.deleteByExpiresAtBefore(Instant.now());
  }

  /**
   * ADMINがチケットへ割り当て可能なAGENT候補一覧を取得する。
   * URL側の/api/admin/**制限・Controllerの@PreAuthorizeに加え、
   * Service自身にも@PreAuthorizeを付与し、将来Controller以外から
   * 呼び出されるようになった場合でもADMIN以外は取得できないようにする。
   */
  @PreAuthorize("hasRole('ADMIN')")
  @Transactional(readOnly = true)
  public List<AgentSummaryResponse> listAgents() {
    return userRepository.findByRoleAndEnabledTrueOrderByNameAscIdAsc(Role.AGENT).stream()
        .map(AgentSummaryResponse::new)
        .toList();
  }

  /**
   * ログイン中のユーザーをDBから再取得して返す。
   * セッションに保持されたAppUserDetailsはログイン時点のスナップショットであり、
   * XP獲得等の更新を反映しないため、/api/auth/meではこちらを使い常に最新値を返す。
   */
  @Transactional(readOnly = true)
  public UserResponse getCurrentUser(Long userId) {
    User user = userRepository.findById(userId)
        .orElseThrow(() -> new UsernameNotFoundException("ユーザーが見つかりません"));
    return new UserResponse(user);
  }
}
