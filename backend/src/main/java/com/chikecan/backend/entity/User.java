package com.chikecan.backend.entity;

import java.time.Instant;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "users", uniqueConstraints = @UniqueConstraint(name = "uk_users_email", columnNames = "email"))
public class User {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, length = 100)
  private String name;

  @Column(nullable = false, length = 255)
  private String email;

  @JsonIgnore
  @Column(name = "password_hash", nullable = false, length = 255)
  private String passwordHash;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private Role role;

  @Column(nullable = false)
  private boolean enabled;

  @Column(nullable = false)
  private int experience;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected User() {
  }

  public User(String name, String email, String passwordHash, Role role, boolean enabled) {
    this.name = name;
    this.email = email;
    this.passwordHash = passwordHash;
    this.role = role;
    this.enabled = enabled;
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

  public void setEmail(String email) {
    this.email = email;
  }

  public String getPasswordHash() {
    return passwordHash;
  }

  public void setPasswordHash(String passwordHash) {
    this.passwordHash = passwordHash;
  }

  public Role getRole() {
    return role;
  }

  public void setRole(Role role) {
    this.role = role;
  }

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public int getExperience() {
    return experience;
  }

  /**
   * 累計XPへamountを加算する。負数は業務上発生しない想定だが、
   * 万一渡された場合でも累計XPが負にならないよう下限を0に丸める。
   */
  public void addExperience(int amount) {
    this.experience = Math.max(0, this.experience + amount);
  }

  /**
   * レベル・レベル内XP・次のレベルまでのXPは、DBへ重複保存せず
   * ExperienceLevelを介して累計XPから都度計算する。
   */
  public int getLevel() {
    return ExperienceLevel.level(experience);
  }

  public int getCurrentLevelExperience() {
    return ExperienceLevel.currentLevelExperience(experience);
  }

  public int getExperienceToNextLevel() {
    return ExperienceLevel.experienceToNextLevel(experience);
  }

  public int getExperienceProgressPercentage() {
    return ExperienceLevel.progressPercentage(experience);
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  @Override
  public String toString() {
    return "User{id=" + id + ", name=" + name + ", email=" + email + ", role=" + role + ", enabled=" + enabled + "}";
  }
}
