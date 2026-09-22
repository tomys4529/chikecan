package com.chikecan.backend.security;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import com.chikecan.backend.entity.Role;
import com.chikecan.backend.entity.User;

/**
 * 認証とUserResponse生成に必要な値のみを保持する。
 * User Entity自体をセッションへ保持しない。
 */
public class AppUserDetails implements UserDetails {

  private final Long id;
  private final String name;
  private final String email;
  private final String passwordHash;
  private final Role role;
  private final boolean enabled;
  private final Instant createdAt;

  public AppUserDetails(User user) {
    this.id = user.getId();
    this.name = user.getName();
    this.email = user.getEmail();
    this.passwordHash = user.getPasswordHash();
    this.role = user.getRole();
    this.enabled = user.isEnabled();
    this.createdAt = user.getCreatedAt();
  }

  public Long getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public Role getRole() {
    return role;
  }

  public Instant getCreatedAt() {
    return createdAt;
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

  @Override
  public String toString() {
    return "AppUserDetails{id=" + id + ", email=" + email + ", role=" + role + ", enabled=" + enabled + "}";
  }
}
