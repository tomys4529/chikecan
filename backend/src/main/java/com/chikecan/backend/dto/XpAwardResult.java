package com.chikecan.backend.dto;

/**
 * ステータス更新1回あたりのXP付与結果。
 * 通常のTicketResponse(チケット取得・一覧等)へは混ぜず、
 * ステータス更新APIのレスポンスにのみ一時的な演出用情報として含める。
 */
public class XpAwardResult {

  private final boolean awarded;
  private final int gainedExperience;
  private final int previousLevel;
  private final int currentLevel;
  private final int totalExperience;
  private final boolean levelUp;

  private XpAwardResult(boolean awarded, int gainedExperience, int previousLevel, int currentLevel,
      int totalExperience, boolean levelUp) {
    this.awarded = awarded;
    this.gainedExperience = gainedExperience;
    this.previousLevel = previousLevel;
    this.currentLevel = currentLevel;
    this.totalExperience = totalExperience;
    this.levelUp = levelUp;
  }

  /** XPが付与されなかった場合(未対象ステータス・判定済み・担当者なし等)。 */
  public static XpAwardResult none() {
    return new XpAwardResult(false, 0, 0, 0, 0, false);
  }

  public static XpAwardResult awarded(int gainedExperience, int previousLevel, int currentLevel,
      int totalExperience) {
    return new XpAwardResult(true, gainedExperience, previousLevel, currentLevel, totalExperience,
        currentLevel > previousLevel);
  }

  public boolean isAwarded() {
    return awarded;
  }

  public int getGainedExperience() {
    return gainedExperience;
  }

  public int getPreviousLevel() {
    return previousLevel;
  }

  public int getCurrentLevel() {
    return currentLevel;
  }

  public int getTotalExperience() {
    return totalExperience;
  }

  public boolean isLevelUp() {
    return levelUp;
  }
}
