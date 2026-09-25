import { useEffect, useRef } from 'react';
import { TicketMascot } from './TicketMascot';
import './XpGainCelebration.css';

export interface XpCelebrationData {
  gainedExperience: number;
  levelUp: boolean;
  currentLevel: number;
}

interface XpGainCelebrationProps {
  celebration: XpCelebrationData | null;
  onDismiss: () => void;
}

const AUTO_DISMISS_MS = 3000;

/**
 * AGENT本人がRESOLVEDへ変更し、実際にXPが付与されたレスポンスを受け取った
 * 直後だけ表示する非ブロッキングな演出。画面下部中央に固定表示し、約3秒後に
 * 自動で消える。celebrationがnullの間は何も描画しない(再レンダーだけでは
 * 再表示されない。表示のトリガーは呼び出し側が新しいオブジェクトを渡すことのみ)。
 */
export function XpGainCelebration({ celebration, onDismiss }: XpGainCelebrationProps) {
  const timeoutRef = useRef<number | undefined>(undefined);

  useEffect(() => {
    if (!celebration) {
      return;
    }
    timeoutRef.current = window.setTimeout(() => {
      onDismiss();
    }, AUTO_DISMISS_MS);

    return () => {
      window.clearTimeout(timeoutRef.current);
    };
  }, [celebration, onDismiss]);

  if (!celebration) {
    return null;
  }

  const variantClass = celebration.levelUp ? 'xp-celebration--level-up' : 'xp-celebration--normal';

  return (
    <div className={`xp-celebration ${variantClass}`} role="status" aria-live="polite">
      <div className="xp-celebration__fx" aria-hidden="true">
        <span className="xp-celebration__star xp-celebration__star--1" />
        <span className="xp-celebration__star xp-celebration__star--2" />
        <span className="xp-celebration__star xp-celebration__star--3" />
        {celebration.levelUp && (
          <>
            <span className="xp-celebration__ring" />
            <span className="xp-celebration__confetti xp-celebration__confetti--1" />
            <span className="xp-celebration__confetti xp-celebration__confetti--2" />
            <span className="xp-celebration__confetti xp-celebration__confetti--3" />
            <span className="xp-celebration__confetti xp-celebration__confetti--4" />
          </>
        )}
      </div>
      <div className="xp-celebration__mascot">
        <TicketMascot state={celebration.levelUp ? 'level-up' : 'xp'} />
      </div>
      <div className="xp-celebration__content">
        {celebration.levelUp && (
          <>
            <p className="xp-celebration__title">LEVEL UP!</p>
            <p className="xp-celebration__level">Level {celebration.currentLevel}</p>
            <p className="xp-celebration__message">おめでとう!</p>
          </>
        )}
        <p className="xp-celebration__gain">+{celebration.gainedExperience} XP</p>
      </div>
    </div>
  );
}
