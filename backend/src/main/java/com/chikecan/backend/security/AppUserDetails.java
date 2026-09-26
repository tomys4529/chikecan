package com.chikecan.backend.security;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import com.chikecan.backend.entity.DisplayName;
import com.chikecan.backend.entity.NameFormat;
import com.chikecan.backend.entity.Role;
import com.chikecan.backend.entity.User;

/**
 * 認証とUserResponse生成に必要な値のみを保持する。
 * User Entity自体をセッションへ保持しない。
 */
public class AppUserDetails implements UserDetails {

  private final Long id;
  private final String name;
  private final NameFormat nameFormat;
  private final String familyName;
  private final String givenName;
  private final String middleName;
  private final String email;
  private final String passwordHash;
  private final Role role;
  private final boolean enabled;
  private final Instant createdAt;
  private final int experience;

  public AppUserDetails(User user) {
    this.id = user.getId();
    this.name = user.getName();
    this.nameFormat = user.getNameFormat();
    this.familyName = user.getFamilyName();
    this.givenName = user.getGivenName();
    this.middleName = user.getMiddleName();
    this.email = user.getEmail();
    this.passwordHash = user.getPasswordHash();
    this.role = user.getRole();
    this.enabled = user.isEnabled();
    this.createdAt = user.getCreatedAt();
    this.experience = user.getExperience();
  }

  public Long getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  /** 画面表示用の氏名。UserResponse等が返す値と一貫させるため、Userと同じ組み立てロジックを使う。 */
  public String getDisplayName() {
    return DisplayName.build(nameFormat, name, familyName, givenName, middleName);
  }

  public Role getRole() {
    return role;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public int getExperience() {
    return experience;
  }

  @Override
  public String getUsername() {
    return email;
  }

  @Override
  public String getPassword() {
    return passwordHash;
  }

  @Override
  public Collection<? extends GrantedAuthority> getAuthorities() {
    return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
  }

  @Override
  public boolean isEnabled() {
    return enabled;
  }

  @Override
  public boolean isAccountNonExpired() {
    return true;
  }

  @Override
  public boolean isAccountNonLocked() {
    return true;
  }

  @Override
  public boolean isCredentialsNonExpired() {
    return true;
  }

  /**
   * SessionRegistryは principal(このクラスのインスタンス)をMapのキーとして扱う。
   * ログインのたびにDBから新しく生成される別インスタンスであっても、同一ユーザーの
   * 複数セッションを正しく1人分としてまとめられるよう、idのみに基づく同一性にする
   * (email変更後もidは不変のため、email変更の前後で対象ユーザーを見失わない)。
   */
  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (!(obj instanceof AppUserDetails other)) {
      return false;
    }
    return Objects.equals(id, other.id);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(id);
  }

  @Override
  public String toString() {
    return "AppUserDetails{id=" + id + ", email=" + email + ", role=" + role + ", enabled=" + enabled + "}";
  }
}
