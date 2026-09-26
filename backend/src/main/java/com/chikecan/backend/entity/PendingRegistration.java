package com.chikecan.backend.entity;

import java.time.Instant;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * メールアドレス未確認の登録申請を一時的に保持する。
 * usersテーブルへは、認証メールのリンクからtoken検証に成功した時点で初めて登録する
 * (このEntityの段階ではusersへ一切INSERTしない)。
 */
@Entity
@Table(name = "pending_registrations", uniqueConstraints = {
    @UniqueConstraint(name = "uk_pending_registrations_email", columnNames = "email"),
    @UniqueConstraint(name = "uk_pending_registrations_token_hash", columnNames = "token_hash")
})
public class PendingRegistration {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  // RegisterRequestのアプリ側上限(氏名30文字)に合わせる。
  @Column(nullable = false, length = 30)
  private String name;

  // RegisterRequestのアプリ側上限(メールアドレス100文字)に合わせる。
  @Column(nullable = false, length = 100)
  private String email;

  @Column(name = "password_hash", nullable = false, length = 255)
  private String passwordHash;

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

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected PendingRegistration() {
  }

  public PendingRegistration(String name, String email, String passwordHash, String tokenHash, Instant expiresAt) {
    this.name = name;
    this.email = email;
    this.passwordHash = passwordHash;
    this.tokenHash = tokenHash;
    this.expiresAt = expiresAt;
  }

  public Long getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getEmail() {
    return email;
  }

  public String getPasswordHash() {
    return passwordHash;
  }

  public void setPasswordHash(String passwordHash) {
    this.passwordHash = passwordHash;
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

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  @Override
  public String toString() {
    return "PendingRegistration{id=" + id + ", email=" + email + ", expiresAt=" + expiresAt + "}";
  }
}
