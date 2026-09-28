package com.chikecan.backend.security;

import java.util.Map;

import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

import com.chikecan.backend.entity.User;

/**
 * Googleログイン(OpenID Connect)で認証されたユーザーのセッションprincipal。
 * 既存のAppUserDetails(userId基準のequals/hashCode)をそのまま継承することで、
 * SessionRegistry・ConcurrentSessionFilter・@AuthenticationPrincipal AppUserDetailsなど、
 * 既存の通常ログイン向けの仕組み全てをGoogleログインでも同じ経路で利用できるようにする
 * (Controller側でGoogleログインかどうかを意識する必要がない)。
 */
public class OAuth2AppUserDetails extends AppUserDetails implements OidcUser {

  private final OidcIdToken idToken;
  private final OidcUserInfo userInfo;

  public OAuth2AppUserDetails(User user, OidcIdToken idToken, OidcUserInfo userInfo) {
    super(user);
    this.idToken = idToken;
    this.userInfo = userInfo;
  }

  @Override
  public Map<String, Object> getClaims() {
    return idToken.getClaims();
  }

  @Override
  public OidcUserInfo getUserInfo() {
    return userInfo;
  }

  @Override
  public OidcIdToken getIdToken() {
    return idToken;
  }

  @Override
  public Map<String, Object> getAttributes() {
    return idToken.getClaims();
  }

  /** OAuth2User#getName()。内部的にはuserId基準のequals/hashCodeで本人識別しており、ここでの用途はない。 */
  @Override
  public String getName() {
    return String.valueOf(getId());
  }
}
