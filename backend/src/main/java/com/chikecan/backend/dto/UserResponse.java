package com.chikecan.backend.dto;

import java.time.Instant;

import com.chikecan.backend.entity.ExperienceLevel;
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
  private final int experience;
  private final int level;
  private final int currentLevelExperience;
  private final int experienceToNextLevel;
  private final int experienceProgressPercentage;

  public UserResponse(User user) {
    this.id = user.getId();
    this.name = user.getDisplayName();
    this.email = user.getEmail();
    this.role = user.getRole();
    this.enabled = user.isEnabled();
    this.createdAt = user.getCreatedAt();
    this.experience = user.getExperience();
    this.level = user.getLevel();
    this.currentLevelExperience = user.getCurrentLevelExperience();
    this.experienceToNextLevel = user.getExperienceToNextLevel();
    this.experienceProgressPercentage = user.getExperienceProgressPercentage();
  }

  public UserResponse(AppUserDetails principal) {
    this.id = principal.getId();
    this.name = principal.getDisplayName();
    this.email = principal.getUsername();
    this.role = principal.getRole();
    this.enabled = principal.isEnabled();
    this.createdAt = principal.getCreatedAt();
    this.experience = principal.getExperience();
    this.level = ExperienceLevel.level(experience);
    this.currentLevelExperience = ExperienceLevel.currentLevelExperience(experience);
    this.experienceToNextLevel = ExperienceLevel.experienceToNextLevel(experience);
    this.experienceProgressPercentage = ExperienceLevel.progressPercentage(experience);
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

  public int getExperience() {
    return experience;
  }

  public int getLevel() {
    return level;
  }

  public int getCurrentLevelExperience() {
    return currentLevelExperience;
  }

  public int getExperienceToNextLevel() {
    return experienceToNextLevel;
  }

  public int getExperienceProgressPercentage() {
    return experienceProgressPercentage;
  }
}
