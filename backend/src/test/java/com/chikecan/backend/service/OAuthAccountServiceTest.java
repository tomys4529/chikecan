package com.chikecan.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

import com.chikecan.backend.entity.NameFormat;
import com.chikecan.backend.entity.OAuthAccount;
import com.chikecan.backend.entity.OAuthProvider;
import com.chikecan.backend.entity.Role;
import com.chikecan.backend.entity.User;
import com.chikecan.backend.repository.OAuthAccountRepository;
import com.chikecan.backend.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class OAuthAccountServiceTest {

  @Mock
  private UserRepository userRepository;

  @Mock
  private OAuthAccountRepository oAuthAccountRepository;

  @Mock
  private PasswordEncoder passwordEncoder;

  private OAuthAccountService oAuthAccountService;

  private OAuthAccountService service() {
    return new OAuthAccountService(userRepository, oAuthAccountRepository, passwordEncoder);
  }

  private static OidcUser oidcUser(Map<String, Object> claims) {
    OidcIdToken idToken = new OidcIdToken("dummy-id-token", Instant.now(), Instant.now().plusSeconds(3600), claims);
    return new DefaultOidcUser(List.of(new SimpleGrantedAuthority("ROLE_USER")), idToken);
  }

  @Test
  void 既に連携済みのsubの場合は紐づくUserをそのまま返す() {
    oAuthAccountService = service();
    OidcUser oidcUser = oidcUser(Map.of("sub", "existing-sub"));
    User existingUser = new User("山田太郎", "yamada@example.com", "hash", Role.USER, true);
    OAuthAccount existingAccount = new OAuthAccount(42L, OAuthProvider.GOOGLE, "existing-sub");

    when(oAuthAccountRepository.findByProviderAndProviderUserId(OAuthProvider.GOOGLE, "existing-sub"))
        .thenReturn(Optional.of(existingAccount));
    when(userRepository.findById(42L)).thenReturn(Optional.of(existingUser));

    User result = oAuthAccountService.resolveOrCreateGoogleUser(oidcUser);

    assertThat(result).isSameAs(existingUser);
    verify(userRepository, never()).save(any());
    verify(oAuthAccountRepository, never()).save(any());
  }

  @Test
  void 初回ログインでemail_verifiedがfalseの場合は拒否する() {
    oAuthAccountService = service();
    OidcUser oidcUser = oidcUser(Map.of(
        "sub", "new-sub",
        "email", "unverified@example.com",
        "email_verified", false));

    when(oAuthAccountRepository.findByProviderAndProviderUserId(OAuthProvider.GOOGLE, "new-sub"))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> oAuthAccountService.resolveOrCreateGoogleUser(oidcUser))
        .isInstanceOf(OAuth2AuthenticationException.class);
    verify(userRepository, never()).save(any());
  }

  @Test
  void 初回ログインで既存emailと衝突する場合は自動連携せず拒否する() {
    oAuthAccountService = service();
    OidcUser oidcUser = oidcUser(Map.of(
        "sub", "new-sub",
        "email", "taken@example.com",
        "email_verified", true,
        "given_name", "Taro",
        "family_name", "Yamada"));

    when(oAuthAccountRepository.findByProviderAndProviderUserId(OAuthProvider.GOOGLE, "new-sub"))
        .thenReturn(Optional.empty());
    when(userRepository.findByEmail("taken@example.com"))
        .thenReturn(Optional.of(new User("既存太郎", "taken@example.com", "hash", Role.USER, true)));

    assertThatThrownBy(() -> oAuthAccountService.resolveOrCreateGoogleUser(oidcUser))
        .isInstanceOf(OAuth2AuthenticationException.class);
    verify(userRepository, never()).save(any());
    verify(oAuthAccountRepository, never()).save(any());
  }

  @Test
  void 初回ログインで氏名情報が全く取得できない場合は拒否する() {
    oAuthAccountService = service();
    OidcUser oidcUser = oidcUser(Map.of(
        "sub", "new-sub",
        "email", "noname@example.com",
        "email_verified", true));

    when(oAuthAccountRepository.findByProviderAndProviderUserId(OAuthProvider.GOOGLE, "new-sub"))
        .thenReturn(Optional.empty());
    when(userRepository.findByEmail("noname@example.com")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> oAuthAccountService.resolveOrCreateGoogleUser(oidcUser))
        .isInstanceOf(OAuth2AuthenticationException.class);
    verify(userRepository, never()).save(any());
  }

  @Test
  void 初回ログインでgiven_nameとfamily_nameがあればINTERNATIONAL形式でUSERロール固定で作成する() {
    oAuthAccountService = service();
    OidcUser oidcUser = oidcUser(Map.of(
        "sub", "brand-new-sub",
        "email", "New.User@Example.com",
        "email_verified", true,
        "given_name", "Taro",
        "family_name", "Yamada"));

    when(oAuthAccountRepository.findByProviderAndProviderUserId(OAuthProvider.GOOGLE, "brand-new-sub"))
        .thenReturn(Optional.empty());
    when(userRepository.findByEmail("new.user@example.com")).thenReturn(Optional.empty());
    when(passwordEncoder.encode(any())).thenReturn("bcrypt-hash-of-random-value");
    when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

    User result = oAuthAccountService.resolveOrCreateGoogleUser(oidcUser);

    assertThat(result.getEmail()).isEqualTo("new.user@example.com");
    assertThat(result.getRole()).isEqualTo(Role.USER);
    assertThat(result.isEnabled()).isTrue();
    assertThat(result.getNameFormat()).isEqualTo(NameFormat.INTERNATIONAL);
    assertThat(result.getGivenName()).isEqualTo("Taro");
    assertThat(result.getFamilyName()).isEqualTo("Yamada");
    assertThat(result.getPasswordHash()).isEqualTo("bcrypt-hash-of-random-value");

    ArgumentCaptor<String> rawPasswordCaptor = ArgumentCaptor.forClass(String.class);
    verify(passwordEncoder).encode(rawPasswordCaptor.capture());
    // 生のランダム値がpassword_hashへそのまま保存されていない(必ずencode経由)ことを確認する。
    assertThat(rawPasswordCaptor.getValue()).isNotEqualTo("bcrypt-hash-of-random-value");

    ArgumentCaptor<OAuthAccount> accountCaptor = ArgumentCaptor.forClass(OAuthAccount.class);
    verify(oAuthAccountRepository).save(accountCaptor.capture());
    assertThat(accountCaptor.getValue().getProvider()).isEqualTo(OAuthProvider.GOOGLE);
    assertThat(accountCaptor.getValue().getProviderUserId()).isEqualTo("brand-new-sub");
  }

  @Test
  void given_nameまたはfamily_nameが欠けている場合はLEGACY形式でnameクレームを使う() {
    oAuthAccountService = service();
    OidcUser oidcUser = oidcUser(Map.of(
        "sub", "legacy-sub",
        "email", "legacy@example.com",
        "email_verified", true,
        "name", "山田 太郎"));

    when(oAuthAccountRepository.findByProviderAndProviderUserId(OAuthProvider.GOOGLE, "legacy-sub"))
        .thenReturn(Optional.empty());
    when(userRepository.findByEmail("legacy@example.com")).thenReturn(Optional.empty());
    when(passwordEncoder.encode(any())).thenReturn("hash");
    when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

    User result = oAuthAccountService.resolveOrCreateGoogleUser(oidcUser);

    assertThat(result.getNameFormat()).isEqualTo(NameFormat.LEGACY);
    assertThat(result.getName()).isEqualTo("山田 太郎");
    assertThat(result.getFamilyName()).isNull();
    assertThat(result.getGivenName()).isNull();
  }
}
