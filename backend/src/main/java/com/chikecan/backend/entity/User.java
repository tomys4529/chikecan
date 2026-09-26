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

  /**
   * 氏名の入力形式。既存(この機能導入前)のユーザーはすべてLEGACYであり、
   * その場合はnameをそのまま表示に使う。新規登録ではJAPANESE/INTERNATIONALのみ設定される。
   */
  @Enumerated(EnumType.STRING)
  @Column(name = "name_format", nullable = false, length = 20)
  private NameFormat nameFormat;

  // JAPANESE/INTERNATIONAL共通の姓(Last name)。LEGACYユーザーはnull。
  @Column(name = "family_name", length = 30)
  private String familyName;

  // JAPANESE/INTERNATIONAL共通の名(First name)。LEGACYユーザーはnull。
  @Column(name = "given_name", length = 30)
  private String givenName;

  // INTERNATIONALのみで使う任意のMiddle name。
  @Column(name = "middle_name", length = 30)
  private String middleName;

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

  /**
   * 既存呼び出し元(メール認証未対応時代からのテスト・その他のEntity生成箇所)との
   * 互換性を保つため、構造化された氏名を指定しないコンストラクタを維持する。
   * この場合はLEGACY扱いとなり、表示にはnameがそのまま使われる。
   */
  public User(String name, String email, String passwordHash, Role role, boolean enabled) {
    this(name, email, passwordHash, role, enabled, NameFormat.LEGACY, null, null, null);
  }

  public User(String name, String email, String passwordHash, Role role, boolean enabled,
      NameFormat nameFormat, String familyName, String givenName, String middleName) {
    this.name = name;
    this.email = email;
    this.passwordHash = passwordHash;
    this.role = role;
    this.enabled = enabled;
    this.nameFormat = nameFormat;
    this.familyName = familyName;
    this.givenName = givenName;
    this.middleName = middleName;
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

  /** 画面表示用の氏名。JAPANESE/INTERNATIONALは構造化項目から組み立て、LEGACYはnameをそのまま返す。 */
  public String getDisplayName() {
    return DisplayName.build(nameFormat, name, familyName, givenName, middleName);
  }

  public NameFormat getNameFormat() {
    return nameFormat;
  }

  public String getFamilyName() {
    return familyName;
  }

  public String getGivenName() {
    return givenName;
  }

  public String getMiddleName() {
    return middleName;
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
