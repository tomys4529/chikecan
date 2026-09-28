package com.chikecan.backend.service;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Locale;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.chikecan.backend.entity.DisplayName;
import com.chikecan.backend.entity.NameFormat;
import com.chikecan.backend.entity.OAuthAccount;
import com.chikecan.backend.entity.OAuthProvider;
import com.chikecan.backend.entity.Role;
import com.chikecan.backend.entity.User;
import com.chikecan.backend.repository.OAuthAccountRepository;
import com.chikecan.backend.repository.UserRepository;

/**
 * Googleログイン(OpenID Connect)で得られた情報から、chikecan内部のUserを解決・作成する。
 * 本人特定はメールアドレスではなく、provider(GOOGLE)+provider_user_id(Googleの"sub"claim)で行う。
 *
 * 初回ログイン時に同じメールアドレスの既存ユーザーが存在しても、自動連携は行わない
 * (安全側に倒し、通常ログインの利用を促す)。既存アカウントへのGoogle連携は今回の対象外。
 */
@Service
public class OAuthAccountService {

  private static final Logger log = LoggerFactory.getLogger(OAuthAccountService.class);

  private static final int NAME_MAX_LENGTH = 30;
  private static final int LEGACY_NAME_MAX_LENGTH = 100;
  private static final SecureRandom SECURE_RANDOM = new SecureRandom();

  private final UserRepository userRepository;
  private final OAuthAccountRepository oAuthAccountRepository;
  private final PasswordEncoder passwordEncoder;

  public OAuthAccountService(UserRepository userRepository, OAuthAccountRepository oAuthAccountRepository,
      PasswordEncoder passwordEncoder) {
    this.userRepository = userRepository;
    this.oAuthAccountRepository = oAuthAccountRepository;
    this.passwordEncoder = passwordEncoder;
  }

  /**
   * 既にoauth_accountsへ紐付いているsubであれば、そのUserをそのまま返す(emailが
   * 変わっていても同一ユーザーとして扱う)。未紐付けの場合のみ初回ログイン処理を行う。
   */
  @Transactional
  public User resolveOrCreateGoogleUser(OidcUser oidcUser) {
    String providerUserId = oidcUser.getSubject();
    if (providerUserId == null || providerUserId.isBlank()) {
      throw new OAuth2AuthenticationException(new OAuth2Error("invalid_google_account"),
          "Googleアカウントの識別情報を取得できませんでした");
    }

    Optional<OAuthAccount> existingAccount =
        oAuthAccountRepository.findByProviderAndProviderUserId(OAuthProvider.GOOGLE, providerUserId);
    if (existingAccount.isPresent()) {
      return userRepository.findById(existingAccount.get().getUserId())
          .orElseThrow(() -> new OAuth2AuthenticationException(new OAuth2Error("account_not_found"),
              "連携済みのアカウントが見つかりませんでした"));
    }

    return createFromFirstGoogleLogin(providerUserId, oidcUser);
  }

  private User createFromFirstGoogleLogin(String providerUserId, OidcUser oidcUser) {
    Boolean emailVerified = oidcUser.getEmailVerified();
    String email = oidcUser.getEmail();
    if (email == null || email.isBlank() || emailVerified == null || !emailVerified) {
      throw new OAuth2AuthenticationException(new OAuth2Error("email_not_verified"),
          "確認済みのメールアドレスを取得できないGoogleアカウントのため、ログインできません");
    }
    String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);

    // 既存ユーザーとのemail一致だけを理由にした自動連携は行わない(安全側に倒す)。
    // 本人確認済みの連携フローは今回のスコープ外。
    if (userRepository.findByEmail(normalizedEmail).isPresent()) {
      throw new OAuth2AuthenticationException(new OAuth2Error("email_already_registered"),
          "このメールアドレスは既に登録されています。通常ログインを使用してください。");
    }

    User user = buildNewUserFromGoogleProfile(normalizedEmail, oidcUser);
    User saved = userRepository.save(user);
    oAuthAccountRepository.save(new OAuthAccount(saved.getId(), OAuthProvider.GOOGLE, providerUserId));
    log.info("Googleログインにより新規ユーザーを作成しました(userId={})", saved.getId());
    return saved;
  }

  private User buildNewUserFromGoogleProfile(String normalizedEmail, OidcUser oidcUser) {
    String givenName = trimToNull(oidcUser.getGivenName());
    String familyName = trimToNull(oidcUser.getFamilyName());
    String fullName = trimToNull(oidcUser.getFullName());

    NameFormat nameFormat;
    String storedFamilyName = null;
    String storedGivenName = null;
    String legacyName;

    if (givenName != null && familyName != null) {
      nameFormat = NameFormat.INTERNATIONAL;
      storedFamilyName = truncate(familyName, NAME_MAX_LENGTH);
      storedGivenName = truncate(givenName, NAME_MAX_LENGTH);
      legacyName = truncate(
          DisplayName.build(NameFormat.INTERNATIONAL, null, storedFamilyName, storedGivenName, null),
          LEGACY_NAME_MAX_LENGTH);
    } else if (fullName != null) {
      // 姓・名を個別に取得できない場合、プレースホルダー文字列は生成せず、
      // Googleが返した実際のフルネームをLEGACY区分(姓名を分割保存しない形式)でそのまま使う。
      nameFormat = NameFormat.LEGACY;
      legacyName = truncate(fullName, LEGACY_NAME_MAX_LENGTH);
    } else {
      throw new OAuth2AuthenticationException(new OAuth2Error("name_unavailable"),
          "Googleアカウントから氏名情報を取得できなかったため、登録できません");
    }

    String passwordHash = passwordEncoder.encode(generateRandomPassword());
    return new User(legacyName, normalizedEmail, passwordHash, Role.USER, true,
        nameFormat, storedFamilyName, storedGivenName, null);
  }

  private static String trimToNull(String value) {
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }

  private static String truncate(String value, int maxLength) {
    return value.length() <= maxLength ? value : value.substring(0, maxLength);
  }

  /**
   * Google経由で作成したユーザーは通常のメール+パスワードログインを事実上使えないようにする
   * (password_hashをNOT NULLのまま維持するための対応)。生の値はどこにも保存・出力せず、
   * BCryptハッシュのみをpassword_hashへ保存する。この戻り値をログへ出力してはならない。
   */
  private static String generateRandomPassword() {
    byte[] randomBytes = new byte[32];
    SECURE_RANDOM.nextBytes(randomBytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
  }
}
