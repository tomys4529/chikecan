package com.chikecan.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.test.util.ReflectionTestUtils;

import com.chikecan.backend.entity.NameFormat;
import com.chikecan.backend.entity.Role;
import com.chikecan.backend.entity.User;
import com.chikecan.backend.security.AppUserDetails;

/**
 * 実際のSessionRegistryImplを使い、AppUserDetails.equals/hashCode(userId基準)によって
 * 同一ユーザーの複数ログインが正しく1人分としてまとめられ、対象ユーザーのセッションだけが
 * 失効することを検証する。
 */
class SessionInvalidationServiceTest {

  private User userOf(Long id, String email) {
    User user = new User("テスト太郎", email, "hash", Role.USER, true,
        NameFormat.JAPANESE, "テスト", "太郎", null);
    ReflectionTestUtils.setField(user, "id", id);
    return user;
  }

  private void registerSession(SessionRegistry sessionRegistry, User user, String sessionId) {
    // ログインのたびにDBから新しく生成される別インスタンスを模擬する
    // (同一ユーザーでも同じインスタンスを使い回さない)。
    sessionRegistry.registerNewSession(sessionId, new AppUserDetails(user));
  }

  @Test
  void 対象ユーザーの全セッションをexpireNowする() {
    SessionRegistry sessionRegistry = new SessionRegistryImpl();
    User targetUser = userOf(1L, "target@example.com");
    registerSession(sessionRegistry, targetUser, "session-a");
    registerSession(sessionRegistry, targetUser, "session-b");

    new SessionInvalidationService(sessionRegistry).invalidateAllSessionsForUser(1L);

    assertThat(sessionRegistry.getSessionInformation("session-a").isExpired()).isTrue();
    assertThat(sessionRegistry.getSessionInformation("session-b").isExpired()).isTrue();
  }

  @Test
  void 他ユーザーのセッションはexpireしない() {
    SessionRegistry sessionRegistry = new SessionRegistryImpl();
    User targetUser = userOf(1L, "target@example.com");
    User otherUser = userOf(2L, "other@example.com");
    registerSession(sessionRegistry, targetUser, "session-target");
    registerSession(sessionRegistry, otherUser, "session-other");

    new SessionInvalidationService(sessionRegistry).invalidateAllSessionsForUser(1L);

    assertThat(sessionRegistry.getSessionInformation("session-target").isExpired()).isTrue();
    assertThat(sessionRegistry.getSessionInformation("session-other").isExpired()).isFalse();
  }

  @Test
  void 対象ユーザーのセッションが0件でもエラーにならない() {
    SessionRegistry sessionRegistry = new SessionRegistryImpl();

    assertThatCode(() -> new SessionInvalidationService(sessionRegistry).invalidateAllSessionsForUser(999L))
        .doesNotThrowAnyException();
  }

  @Test
  void 同一ユーザーの複数セッション全てを失効する_3セッション() {
    SessionRegistry sessionRegistry = new SessionRegistryImpl();
    User targetUser = userOf(3L, "multi-device@example.com");
    registerSession(sessionRegistry, targetUser, "device-1");
    registerSession(sessionRegistry, targetUser, "device-2");
    registerSession(sessionRegistry, targetUser, "device-3");

    new SessionInvalidationService(sessionRegistry).invalidateAllSessionsForUser(3L);

    assertThat(sessionRegistry.getSessionInformation("device-1").isExpired()).isTrue();
    assertThat(sessionRegistry.getSessionInformation("device-2").isExpired()).isTrue();
    assertThat(sessionRegistry.getSessionInformation("device-3").isExpired()).isTrue();
  }

  @Test
  void MockHttpSessionのIDを使っても正しく失効できる() {
    SessionRegistry sessionRegistry = new SessionRegistryImpl();
    User targetUser = userOf(4L, "mock-session@example.com");
    MockHttpSession sessionA = new MockHttpSession();
    MockHttpSession sessionB = new MockHttpSession();
    registerSession(sessionRegistry, targetUser, sessionA.getId());
    registerSession(sessionRegistry, targetUser, sessionB.getId());

    new SessionInvalidationService(sessionRegistry).invalidateAllSessionsForUser(4L);

    assertThat(sessionRegistry.getSessionInformation(sessionA.getId()).isExpired()).isTrue();
    assertThat(sessionRegistry.getSessionInformation(sessionB.getId()).isExpired()).isTrue();
  }
}
