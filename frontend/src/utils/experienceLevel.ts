/**
 * 累計XPからレベル・レベル内XP・次のレベルまでのXPを計算する。
 * バックエンドのExperienceLevel(entity)と同じ式を用いる、フロント側の唯一の計算箇所。
 */
export const EXPERIENCE_PER_LEVEL = 100;

export function levelFromExperience(experience: number): number {
  return Math.floor(experience / EXPERIENCE_PER_LEVEL) + 1;
}

export function currentLevelExperience(experience: number): number {
  return experience % EXPERIENCE_PER_LEVEL;
}

export function experienceToNextLevel(experience: number): number {
  return EXPERIENCE_PER_LEVEL - currentLevelExperience(experience);
}

export function experienceProgressPercentage(experience: number): number {
  return currentLevelExperience(experience);
}
