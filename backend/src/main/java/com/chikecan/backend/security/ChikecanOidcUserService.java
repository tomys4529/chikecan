package com.chikecan.backend.security;

import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;

import com.chikecan.backend.entity.User;
import com.chikecan.backend.service.OAuthAccountService;

/**
 * Googleからユーザー情報(OIDC標準claim)を取得した後、chikecan内部のUserへ変換する。
 * Spring標準のOidcUserServiceでGoogleのuserinfoエンドポイントへ問い合わせて取得した
 * OidcUserをそのまま使わず、OAuthAccountServiceでの解決・初回作成を経てから、
 * 既存のAppUserDetailsベースのprincipal(OAuth2AppUserDetails)へ包み直す。
 */
@Service
public class ChikecanOidcUserService extends OidcUserService {

  private final OAuthAccountService oAuthAccountService;

  public ChikecanOidcUserService(OAuthAccountService oAuthAccountService) {
    this.oAuthAccountService = oAuthAccountService;
  }

  @Override
  public OidcUser loadUser(OidcUserRequest userRequest) throws OAuth2AuthenticationException {
    OidcUser oidcUser = super.loadUser(userRequest);
    User user = oAuthAccountService.resolveOrCreateGoogleUser(oidcUser);
    return new OAuth2AppUserDetails(user, oidcUser.getIdToken(), oidcUser.getUserInfo());
  }
}
