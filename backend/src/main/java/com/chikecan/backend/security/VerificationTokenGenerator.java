package com.chikecan.backend.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * メールで送るURL向けトークンの生成とハッシュ化を行う汎用ユーティリティ。
 * メール認証・パスワードリセットなど、用途の異なる複数のtoken(別テーブルで管理)から
 * 共通で利用する。生トークンはメールURLへ含める値としてのみ扱い、DBへは保存しない。
 * DBにはこのクラスでハッシュ化した値だけを保存し、検証時は受け取った生トークンを
 * 同じ方法でハッシュ化してから比較・検索する。
 */
public final class VerificationTokenGenerator {

  private static final SecureRandom SECURE_RANDOM = new SecureRandom();
  // 32byte(256bit)のランダム値。UUID(実質122bitのランダム性)より高いエントロピーを持たせる。
  private static final int TOKEN_BYTES = 32;

  private VerificationTokenGenerator() {
  }

  /** URLにそのまま含められるBase64 URL-safe(パディングなし)形式の生トークンを生成する。 */
  public static String generateRawToken() {
    byte[] bytes = new byte[TOKEN_BYTES];
    SECURE_RANDOM.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  /** 生トークンをSHA-256でハッシュ化し、16進文字列として返す。DB保存・検索時の比較に用いる。 */
  public static String hash(String rawToken) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hashBytes = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(hashBytes);
    } catch (NoSuchAlgorithmException e) {
      // SHA-256は全JVM実装で必須のアルゴリズムのため、通常到達しない防御的な例外変換。
      throw new IllegalStateException("SHA-256アルゴリズムが利用できません", e);
    }
  }
}
