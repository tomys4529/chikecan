import { useEffect, useRef, useState } from 'react';
import { useAuth } from '../context/AuthContext';
import { TicketMascot } from './TicketMascot';
import { EXPERIENCE_PER_LEVEL, levelFromExperience } from '../utils/experienceLevel';
import './XpPanel.css';

// XpPanel.cssの.xp-panel__bar-fillのtransition時間と合わせる。
const BAR_TRANSITION_MS = 600;

/**
 * AGENTでログインしている場合のみ、現在レベル・累計XP・次のレベルまでのXP・
 * 進捗バーを表示する。共通レイアウト(App.tsx)からメインコンテンツ直下に配置される。
 *
 * XPバーのアニメーションは、直前に表示していた累計XPと現在のuser.experienceを
 * 比較するだけで完結させている(ステータス更新のレスポンス自体には依存しない)。
 * こうすることで、状態同期の方法(レスポンス反映か/api/auth/me再取得か)に関わらず、
 * user.experienceが変化しさえすれば正しくアニメーションする。
 */
export function XpPanel() {
  const { user } = useAuth();
  const isAgent = user?.role === 'AGENT';

  const experience = user?.experience ?? 0;
  const level = user?.level ?? 1;
  const currentLevelExperience = user?.currentLevelExperience ?? 0;
  const experienceToNextLevel = user?.experienceToNextLevel ?? EXPERIENCE_PER_LEVEL;
  const progressPercentage = user?.experienceProgressPercentage ?? 0;

  const previousExperienceRef = useRef<number | null>(null);
  const [barPercentage, setBarPercentage] = useState(progressPercentage);
  const [skipTransition, setSkipTransition] = useState(false);
  const timeoutRefs = useRef<number[]>([]);

  useEffect(
    () => () => {
      timeoutRefs.current.forEach((id) => window.clearTimeout(id));
    },
    [],
  );

  useEffect(() => {
    if (!isAgent) {
      return;
    }
    const previous = previousExperienceRef.current;
    previousExperienceRef.current = experience;

    if (previous === null || previous === experience) {
      // 初回表示、または値に変化がない場合はアニメーションなしで反映する。
      setBarPercentage(progressPercentage);
      return;
    }

    const previousLevel = levelFromExperience(previous);
    if (previousLevel === level) {
      // レベルをまたがない通常の伸び。CSSのtransitionにまかせる。
      setBarPercentage(progressPercentage);
      return;
    }

    // レベルをまたいだ場合: 旧レベルの残り分まで100%へ伸ばし、0%へ瞬時に戻してから
    // 新レベルの割合まで改めて伸ばす。
    timeoutRefs.current.forEach((id) => window.clearTimeout(id));
    timeoutRefs.current = [];
    setBarPercentage(100);

    const resetTimeout = window.setTimeout(() => {
      setSkipTransition(true);
      setBarPercentage(0);

      const regrowTimeout = window.setTimeout(() => {
        setSkipTransition(false);
        setBarPercentage(progressPercentage);
      }, 30);
      timeoutRefs.current.push(regrowTimeout);
    }, BAR_TRANSITION_MS + 50);
    timeoutRefs.current.push(resetTimeout);
  }, [experience, level, progressPercentage, isAgent]);

  if (!isAgent) {
    return null;
  }

  return (
    <section className="xp-panel" aria-label="AGENTのレベルとXP">
      <div className="xp-panel__mascot">
        <TicketMascot state="idle" />
      </div>
      <div className="xp-panel__body">
        <span className="xp-panel__level-badge">LEVEL {level}</span>
        <div className="xp-panel__progress">
          <div className="xp-panel__stats">
            <span className="xp-panel__total">累計 {experience} XP</span>
            <span className="xp-panel__next">次のレベルまで {experienceToNextLevel} XP</span>
          </div>
          <div
            className="xp-panel__bar-track"
            role="progressbar"
            aria-valuenow={currentLevelExperience}
            aria-valuemin={0}
            aria-valuemax={100}
            aria-label="現在のレベル内の進捗"
          >
            <div
              className={`xp-panel__bar-fill${skipTransition ? ' xp-panel__bar-fill--no-transition' : ''}`}
              style={{ width: `${barPercentage}%` }}
            />
          </div>
          <span className="xp-panel__percentage">{progressPercentage}%</span>
        </div>
      </div>
    </section>
  );
}
