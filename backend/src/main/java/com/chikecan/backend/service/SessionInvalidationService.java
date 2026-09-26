package com.chikecan.backend.service;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.stereotype.Service;

import com.chikecan.backend.security.AppUserDetails;

/**
 * 指定したuserIdに紐づく既存のHTTPセッションを全て無効化する。
 * パスワード変更・パスワードリセット完了時に、対象ユーザーの他端末・他ブラウザの
 * 既存セッション(操作中のセッションを含む)を含めて再ログインを必須にするために使う。
 *
 * 本人特定はemail(変更されうる)ではなく、不変のuserIdで行う。SessionRegistryは
 * principal(AppUserDetailsのインスタンス)をキーとして内部管理しており、ログインのたびに
 * DBから新しく生成される別インスタンスであっても、AppUserDetails.equals/hashCodeをid基準に
 * 実装済みのため、同一ユーザーの複数ログイン(複数端末・複数ブラウザ)がSessionRegistry上で
 * 正しく1人分としてまとめられる。
 *
 * SessionInformation#expireNow()はSessionRegistry上のフラグを立てるのみで、対象の
 * HttpSessionを物理的に即時破棄するわけではない。実際の強制ログアウトは、次回そのセッションで
 * リクエストが来た際にConcurrentSessionFilterがこのフラグを検知して行う
 * (SecurityConfig参照)。
 */
@Service
public class SessionInvalidationService {

  private static final Logger log = LoggerFactory.getLogger(SessionInvalidationService.class);

  private final SessionRegistry sessionRegistry;

  public SessionInvalidationService(SessionRegistry sessionRegistry) {
    this.sessionRegistry = sessionRegistry;
  }

  public void invalidateAllSessionsForUser(Long userId) {
    int expiredCount = 0;
    for (Object principal : sessionRegistry.getAllPrincipals()) {
      if (!(principal instanceof AppUserDetails appUserDetails)) {
        continue;
      }
      if (!userId.equals(appUserDetails.getId())) {
        continue;
      }
      List<SessionInformation> sessions = sessionRegistry.getAllSessions(principal, false);
      for (SessionInformation sessionInformation : sessions) {
        sessionInformation.expireNow();
        expiredCount++;
      }
    }
    // Session ID自体はログへ出力しない(セキュリティ要件)。
    if (expiredCount > 0) {
      log.info("パスワード変更等に伴い対象ユーザー(userId={})の既存セッションを{}件失効させました。", userId, expiredCount);
    }
  }
}
