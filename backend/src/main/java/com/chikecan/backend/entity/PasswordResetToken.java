package com.chikecan.backend.entity;

import java.time.Instant;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * 既に本登録済み(usersテーブルに存在する)のユーザーのパスワード再設定用token。
 * メール認証用のpending_registrationsとはテーブル・用途とも分離する。
 * 1ユーザーにつき有効tokenは1件までとするため、userIdをUNIQUE制約で保証する。
 */
@Entity
@Table(name = "password_reset_tokens", uniqueConstraints = {
    @UniqueConstraint(name = "uk_password_reset_tokens_user_id", columnNames = "user_id"),
    @UniqueConstraint(name = "uk_password_reset_tokens_token_hash", columnNames = "token_hash")
})
public class PasswordResetToken {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "user_id", nullable = false)
  private Long userId;

  /**
   * 生トークンはDBへ保存せず、SHA-256でハッシュ化した値のみを保持する。
   * SHA-256を16進数化した値は常に64文字固定。
   */
  @Column(name = "token_hash", nullable = false, length = 64)
  private String tokenHash;

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected PasswordResetToken() {
  }

  public PasswordResetToken(Long userId, String tokenHash, Instant expiresAt) {
    this.userId = userId;
    this.tokenHash = tokenHash;
    this.expiresAt = expiresAt;
  }

  public Long getId() {
    return id;
  }

  public Long getUserId() {
    return userId;
  }

  public String getTokenHash() {
    return tokenHash;
  }

  public void setTokenHash(String tokenHash) {
    this.tokenHash = tokenHash;
  }

  public Instant getExpiresAt() {
    return expiresAt;
  }

  public void setExpiresAt(Instant expiresAt) {
    this.expiresAt = expiresAt;
  }

  public boolean isExpired(Instant now) {
    return now.isAfter(expiresAt);
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  @Override
  public String toString() {
    return "PasswordResetToken{id=" + id + ", userId=" + userId + ", expiresAt=" + expiresAt + "}";
  }
}
