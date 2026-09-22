package com.chikecan.backend.dto;

import java.time.Instant;

import com.chikecan.backend.entity.Role;
import com.chikecan.backend.entity.User;
import com.chikecan.backend.security.AppUserDetails;

public class UserResponse {

  private final Long id;
  private final String name;
  private final String email;
  private final Role role;
  private final boolean enabled;
  private final Instant createdAt;

  public UserResponse(User user) {
    this.id = user.getId();
    this.name = user.getName();
    this.email = user.getEmail();
    this.role = user.getRole();
    this.enabled = user.isEnabled();
    this.createdAt = user.getCreatedAt();
  }

  public UserResponse(AppUserDetails principal) {
    this.id = principal.getId();
    this.name = principal.getName();
    this.email = principal.getUsername();
    this.role = principal.getRole();
    this.enabled = principal.isEnabled();
    this.createdAt = principal.getCreatedAt();
  }

  public Long getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public String getEmail() {
    return email;
  }

  public Role getRole() {
    return role;
  }

  public boolean isEnabled() {
    return enabled;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
