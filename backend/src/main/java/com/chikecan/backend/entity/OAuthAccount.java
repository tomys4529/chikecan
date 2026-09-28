package com.chikecan.backend.entity;

import java.time.Instant;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * usersと外部認証プロバイダ(Google等)のアカウントを紐付ける。
 * usersへプロバイダ専用カラムを直接増やすのではなく、この別テーブルへ分離することで、
 * 将来LINE・GitHub等の追加プロバイダにも同じ構造で対応できるようにする。
 * 本人特定にはメールアドレスではなくprovider+provider_user_id(例: Googleのsub)を使う。
 */
@Entity
@Table(name = "oauth_accounts", uniqueConstraints = {
    @UniqueConstraint(name = "uk_oauth_accounts_provider_user", columnNames = { "provider", "provider_user_id" }),
    @UniqueConstraint(name = "uk_oauth_accounts_user_provider", columnNames = { "user_id", "provider" })
})
public class OAuthAccount {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "user_id", nullable = false)
  private Long userId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private OAuthProvider provider;

  // Googleの場合はOIDCの"sub"claim(不変の外部アカウント識別子)。メールアドレスは使わない。
  @Column(name = "provider_user_id", nullable = false, length = 255)
  private String providerUserId;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected OAuthAccount() {
  }

  public OAuthAccount(Long userId, OAuthProvider provider, String providerUserId) {
    this.userId = userId;
    this.provider = provider;
    this.providerUserId = providerUserId;
  }

  public Long getId() {
    return id;
  }

  public Long getUserId() {
    return userId;
  }

  public OAuthProvider getProvider() {
    return provider;
  }

  public String getProviderUserId() {
    return providerUserId;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  @Override
  public String toString() {
    return "OAuthAccount{id=" + id + ", userId=" + userId + ", provider=" + provider + "}";
  }
}
