package com.chikecan.backend.service;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;

import com.chikecan.backend.entity.Role;

/**
 * UserService#listAgentsの@PreAuthorizeがController経由以外からの呼び出しでも
 * 有効に働くことを検証する。MockMvcを介さず、実際のメソッドセキュリティProxy越しに
 * Serviceを直接呼び出す。
 */
@SpringBootTest
@ActiveProfiles("local")
class UserServiceMethodSecurityTest {

  @Autowired
  private UserService userService;

  @AfterEach
  void clearContext() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void USERロールで直接呼び出すとAccessDeniedExceptionになる() {
    authenticateAs(Role.USER);

    assertThatThrownBy(() -> userService.listAgents())
        .isInstanceOf(AccessDeniedException.class);
  }

  @Test
  void AGENTロールで直接呼び出すとAccessDeniedExceptionになる() {
    authenticateAs(Role.AGENT);

    assertThatThrownBy(() -> userService.listAgents())
        .isInstanceOf(AccessDeniedException.class);
  }

  @Test
  void 未認証で直接呼び出すと認証エラーになる() {
    SecurityContextHolder.clearContext();

    // SecurityContextにAuthenticationが全く存在しない場合、Spring Securityは
    // AccessDeniedExceptionではなくAuthenticationCredentialsNotFoundExceptionを投げる。
    // いずれにせよADMIN以外はlistAgents()の結果を得られないことに変わりはない。
    assertThatThrownBy(() -> userService.listAgents())
        .isInstanceOf(AuthenticationCredentialsNotFoundException.class);
  }

  @Test
  void ADMINロールで直接呼び出すと成功する() {
    authenticateAs(Role.ADMIN);

    assertThatNoException().isThrownBy(() -> userService.listAgents());
  }

  private void authenticateAs(Role role) {
    Authentication authentication = new UsernamePasswordAuthenticationToken(
        "method-security-test@example.com", null,
        List.of(new SimpleGrantedAuthority("ROLE_" + role.name())));
    SecurityContextHolder.getContext().setAuthentication(authentication);
  }
}
