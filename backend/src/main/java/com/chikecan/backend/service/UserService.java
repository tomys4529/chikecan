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
import com.chikecan.backend.entity.DisplayName;
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

@Service
public class UserService {

  // メール認証トークンの有効期限。
  private static final Duration TOKEN_VALIDITY = Duration.ofHours(24);
  private static final String INVALID_TOKEN_MESSAGE = "認証リンクが無効または期限切れです";

  // パスワードリセットはメール認証より機密性が高いため、有効期限を短くする。
  private static final Duration PASSWORD_RESET_TOKEN_VALIDITY = Duration.ofHours(1);
  private static final String INVALID_PASSWORD_RESET_TOKEN_MESSAGE = "再設定リンクが無効または期限切れです";

  // メールアドレス変更もパスワードリセットと同様に機密性が高いため、有効期限を短くする。
  private static final Duration EMAIL_CHANGE_TOKEN_VALIDITY = Duration.ofHours(1);
  private static final String INVALID_EMAIL_CHANGE_TOKEN_MESSAGE = "変更リンクが無効または期限切れです";

  private final UserRepository userRepository;
  private final PendingRegistrationRepository pendingRegistrationRepository;
  private final PasswordResetTokenRepository passwordResetTokenRepository;
  private final EmailChangeRequestRepository emailChangeRequestRepository;
  private final PasswordEncoder passwordEncoder;
  private final VerificationMailService verificationMailService;
  private final PasswordResetMailService passwordResetMailService;
  private final EmailChangeMailService emailChangeMailService;

  public UserService(UserRepository userRepository,
      PendingRegistrationRepository pendingRegistrationRepository,
      PasswordResetTokenRepository passwordResetTokenRepository,
      EmailChangeRequestRepository emailChangeRequestRepository,
      PasswordEncoder passwordEncoder,
      VerificationMailService verificationMailService,
      PasswordResetMailService passwordResetMailService,
      EmailChangeMailService emailChangeMailService) {
    this.userRepository = userRepository;
    this.pendingRegistrationRepository = pendingRegistrationRepository;
    this.passwordResetTokenRepository = passwordResetTokenRepository;
    this.emailChangeRequestRepository = emailChangeRequestRepository;
    this.passwordEncoder = passwordEncoder;
    this.verificationMailService = verificationMailService;
    this.passwordResetMailService = passwordResetMailService;
    this.emailChangeMailService = emailChangeMailService;
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

    NameFormat nameFormat = request.getNameFormat();
    // 構造化されたnameFormat/familyName/givenName/middleNameから、LEGACY互換のname列
    // (NOT NULL)へ入れる表示名相当の値を組み立てる。新規登録ではLEGACYを選べないため、
    // legacyNameフォールバック(第2引数)が実際に使われることはない。
    String legacyName = DisplayName.build(nameFormat, null, request.getFamilyName(), request.getGivenName(),
        request.getMiddleName());

    PendingRegistration pending = pendingRegistrationRepository.findByEmail(normalizedEmail)
        .orElseGet(() -> new PendingRegistration(legacyName, normalizedEmail, passwordHash, tokenHash, expiresAt,
            nameFormat, request.getFamilyName(), request.getGivenName(), request.getMiddleName()));
    pending.setName(legacyName);
    pending.setPasswordHash(passwordHash);
    pending.setTokenHash(tokenHash);
    pending.setExpiresAt(expiresAt);
    pending.setNameFormat(nameFormat);
    pending.setFamilyName(request.getFamilyName());
    pending.setGivenName(request.getGivenName());
    pending.setMiddleName(request.getMiddleName());
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

    User user = new User(pending.getName(), pending.getEmail(), pending.getPasswordHash(), Role.USER, true,
        pending.getNameFormat(), pending.getFamilyName(), pending.getGivenName(), pending.getMiddleName());
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
   * パスワードリセットメールの送信要求。アカウント列挙を防ぐため、メールアドレスの
   * 登録状況に関わらず呼び出し側には常に同じ結果(例外を投げない)を返す。
   * usersに存在する場合のみtokenを発行・保存しメールを送信する。
   * 同一ユーザーに複数の有効tokenを残さないよう、既存tokenがあれば上書きする
   * (古いtokenは上書きにより即座に無効化される)。
   */
  @Transactional
  public void requestPasswordReset(String email) {
    // Spring Scheduler等の定期実行は設けず、新規のリセット要求のたびに期限切れtokenを
    // 削除する遅延クリーンアップ方式にする。
    passwordResetTokenRepository.deleteByExpiresAtBefore(Instant.now());

    String normalizedEmail = normalize(email);
    userRepository.findByEmail(normalizedEmail).ifPresent(user -> {
      String rawToken = VerificationTokenGenerator.generateRawToken();
      String tokenHash = VerificationTokenGenerator.hash(rawToken);
      Instant expiresAt = Instant.now().plus(PASSWORD_RESET_TOKEN_VALIDITY);

      PasswordResetToken resetToken = passwordResetTokenRepository.findByUserId(user.getId())
          .orElseGet(() -> new PasswordResetToken(user.getId(), tokenHash, expiresAt));
      resetToken.setTokenHash(tokenHash);
      resetToken.setExpiresAt(expiresAt);
      passwordResetTokenRepository.save(resetToken);

      passwordResetMailService.sendPasswordResetEmail(normalizedEmail, rawToken);
    });
    // usersに存在しない場合も例外を投げずに正常終了する(呼び出し元には常に同じ結果を返す)。
  }

  /**
   * メール内リンクのtokenを検証し、成功した場合のみ新しいパスワードへ更新する。
   * token検索・パスワード更新・token削除を同一トランザクションで行い、
   * 使用済み・期限切れのtokenでは絶対に更新できないようにする。
   */
  @Transactional
  public void confirmPasswordReset(String rawToken, String newPassword) {
    String tokenHash = VerificationTokenGenerator.hash(rawToken);
    PasswordResetToken resetToken = passwordResetTokenRepository.findByTokenHash(tokenHash)
        .orElseThrow(() -> new InvalidPasswordResetTokenException(INVALID_PASSWORD_RESET_TOKEN_MESSAGE));

    if (resetToken.isExpired(Instant.now())) {
      throw new InvalidPasswordResetTokenException(INVALID_PASSWORD_RESET_TOKEN_MESSAGE);
    }

    User user = userRepository.findById(resetToken.getUserId())
        .orElseThrow(() -> new InvalidPasswordResetTokenException(INVALID_PASSWORD_RESET_TOKEN_MESSAGE));

    user.setPasswordHash(passwordEncoder.encode(newPassword));
    userRepository.save(user);
    // 削除によりtokenは再利用できなくなる。
    passwordResetTokenRepository.delete(resetToken);
  }

  /**
   * ログイン中ユーザー自身によるパスワード変更。現在のパスワードの一致を必須とし、
   * 新しいパスワードが現在のパスワードと同じ場合は拒否する。
   */
  @Transactional
  public void changePassword(Long userId, String currentPassword, String newPassword) {
    User user = userRepository.findById(userId)
        .orElseThrow(() -> new UsernameNotFoundException("ユーザーが見つかりません"));

    if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
      throw new InvalidCredentialsException("現在のパスワードが正しくありません");
    }
    if (currentPassword.equals(newPassword)) {
      throw new SamePasswordException("新しいパスワードは現在のパスワードと異なるものにしてください");
    }

    user.setPasswordHash(passwordEncoder.encode(newPassword));
    userRepository.save(user);
  }

  /**
   * ログイン中ユーザー自身によるメールアドレス変更申請。users.emailは即時更新せず、
   * email_change_requestsへ申請内容を保存したうえで新しいメールアドレス宛に確認メールを送る。
   * 同一ユーザーの再申請時は既存申請を上書きし、古いtokenを即座に無効化する。
   */
  @Transactional
  public void requestEmailChange(Long userId, String newEmail, String currentPassword) {
    // Spring Scheduler等の定期実行は設けず、新規の変更申請のたびに期限切れ申請を
    // 削除する遅延クリーンアップ方式にする。
    emailChangeRequestRepository.deleteByExpiresAtBefore(Instant.now());

    User user = userRepository.findById(userId)
        .orElseThrow(() -> new UsernameNotFoundException("ユーザーが見つかりません"));

    if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
      throw new InvalidCredentialsException("現在のパスワードが正しくありません");
    }

    String normalizedNewEmail = normalize(newEmail);

    if (normalizedNewEmail.equals(user.getEmail())) {
      throw new DuplicateEmailException("新しいメールアドレスが現在のメールアドレスと同じです");
    }
    if (userRepository.findByEmail(normalizedNewEmail).isPresent()) {
      throw new DuplicateEmailException("このメールアドレスは既に使用されています");
    }
    if (pendingRegistrationRepository.findByEmail(normalizedNewEmail).isPresent()) {
      throw new DuplicateEmailException("このメールアドレスは既に使用されています");
    }
    boolean usedByAnotherUsersRequest = emailChangeRequestRepository.findByNewEmail(normalizedNewEmail)
        .filter(existing -> !existing.getUserId().equals(userId))
        .isPresent();
    if (usedByAnotherUsersRequest) {
      throw new DuplicateEmailException("このメールアドレスは既に使用されています");
    }

    String rawToken = VerificationTokenGenerator.generateRawToken();
    String tokenHash = VerificationTokenGenerator.hash(rawToken);
    Instant expiresAt = Instant.now().plus(EMAIL_CHANGE_TOKEN_VALIDITY);

    EmailChangeRequest request = emailChangeRequestRepository.findByUserId(userId)
        .orElseGet(() -> new EmailChangeRequest(userId, normalizedNewEmail, tokenHash, expiresAt));
    request.setNewEmail(normalizedNewEmail);
    request.setTokenHash(tokenHash);
    request.setExpiresAt(expiresAt);
    emailChangeRequestRepository.save(request);

    emailChangeMailService.sendEmailChangeEmail(normalizedNewEmail, rawToken);
  }

  /**
   * メール内リンクのtokenを検証し、成功した場合のみusers.emailを更新する。
   * token検索・email更新・パスワードリセットtoken無効化・申請削除を同一トランザクションで
   * 行い、使用済み・期限切れのtokenでは絶対に更新できないようにする。
   * ログイン有無に関わらず利用可能(メール内URLを別端末で開く可能性があるため)。
   */
  @Transactional
  public void confirmEmailChange(String rawToken) {
    String tokenHash = VerificationTokenGenerator.hash(rawToken);
    EmailChangeRequest request = emailChangeRequestRepository.findByTokenHash(tokenHash)
        .orElseThrow(() -> new InvalidEmailChangeTokenException(INVALID_EMAIL_CHANGE_TOKEN_MESSAGE));

    if (request.isExpired(Instant.now())) {
      throw new InvalidEmailChangeTokenException(INVALID_EMAIL_CHANGE_TOKEN_MESSAGE);
    }

    User user = userRepository.findById(request.getUserId())
        .orElseThrow(() -> new InvalidEmailChangeTokenException(INVALID_EMAIL_CHANGE_TOKEN_MESSAGE));

    // confirm直前に別ユーザーが同じメールアドレスを取得していないか再確認する。該当する場合は
    // このtokenの前提(new_emailが空いていること)が崩れているため、申請ごと削除し無効化する。
    if (userRepository.findByEmail(request.getNewEmail()).isPresent()) {
      emailChangeRequestRepository.delete(request);
      throw new InvalidEmailChangeTokenException(INVALID_EMAIL_CHANGE_TOKEN_MESSAGE);
    }

    user.setEmail(request.getNewEmail());
    userRepository.save(user);
    // 旧メールアドレス宛に発行済みのパスワードリセットtokenが残っていると、
    // 変更後も旧メール経由のリセットリンクが有効になってしまうため、ここで無効化する。
    passwordResetTokenRepository.deleteByUserId(user.getId());
    // 削除によりtokenは再利用できなくなる。
    emailChangeRequestRepository.delete(request);
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
