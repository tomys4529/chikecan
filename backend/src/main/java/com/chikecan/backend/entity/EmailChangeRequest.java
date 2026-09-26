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
 * ログイン中ユーザーのメールアドレス変更申請を一時的に保持する。
 * users.emailは、メール内リンクのtoken検証に成功した時点で初めて更新する
 * (このEntityの段階ではusers.emailを一切更新しない)。
 * メール認証用のpending_registrations、パスワードリセット用のpassword_reset_tokensとは
 * テーブル・用途とも分離する。1ユーザーにつき有効な申請は1件までとするため、
 * userIdをUNIQUE制約で保証する。
 */
@Entity
@Table(name = "email_change_requests", uniqueConstraints = {
    @UniqueConstraint(name = "uk_email_change_requests_user_id", columnNames = "user_id"),
    @UniqueConstraint(name = "uk_email_change_requests_new_email", columnNames = "new_email"),
    @UniqueConstraint(name = "uk_email_change_requests_token_hash", columnNames = "token_hash")
})
public class EmailChangeRequest {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "user_id", nullable = false)
  private Long userId;

  @Column(name = "new_email", nullable = false, length = 100)
  private String newEmail;

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

  protected EmailChangeRequest() {
  }

  public EmailChangeRequest(Long userId, String newEmail, String tokenHash, Instant expiresAt) {
    this.userId = userId;
    this.newEmail = newEmail;
    this.tokenHash = tokenHash;
    this.expiresAt = expiresAt;
  }

  public Long getId() {
    return id;
  }

  public Long getUserId() {
    return userId;
  }

  public String getNewEmail() {
    return newEmail;
  }

  public void setNewEmail(String newEmail) {
    this.newEmail = newEmail;
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
    return "EmailChangeRequest{id=" + id + ", userId=" + userId + ", expiresAt=" + expiresAt + "}";
  }
}
