package com.chikecan.backend.entity;

/**
 * 累計XPからレベル・レベル内XP・次のレベルまでのXPを計算する。
 * レベルや残りXPをDBへ重複保存せず、常に累計XP(experience)から算出するための
 * 唯一の計算箇所。UserエンティティとAppUserDetailsの両方から利用する。
 */
public final class ExperienceLevel {

  private static final int EXPERIENCE_PER_LEVEL = 100;

  private ExperienceLevel() {
  }

  public static int level(int experience) {
    return experience / EXPERIENCE_PER_LEVEL + 1;
  }

  public static int currentLevelExperience(int experience) {
    return experience % EXPERIENCE_PER_LEVEL;
  }

  public static int experienceToNextLevel(int experience) {
    return EXPERIENCE_PER_LEVEL - currentLevelExperience(experience);
  }

  public static int progressPercentage(int experience) {
    return currentLevelExperience(experience);
  }
}
