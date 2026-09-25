import mascotIdle from '../assets/mascot-idle.png';
import mascotXp from '../assets/mascot-xp.png';
import mascotLevelUp from '../assets/mascot-level-up.png';
import './TicketMascot.css';

export type MascotState = 'idle' | 'xp' | 'level-up';

interface TicketMascotProps {
  state: MascotState;
  /** 装飾目的で同じ画像を複数配置する場合はtrueにしてaltを空にする。 */
  decorative?: boolean;
}

const MASCOT_IMAGES: Record<MascotState, string> = {
  idle: mascotIdle,
  xp: mascotXp,
  'level-up': mascotLevelUp,
};

const MASCOT_ALT: Record<MascotState, string> = {
  idle: 'チケット型マスコット',
  xp: 'チケット型マスコットがジャンプしてXP獲得を喜んでいる',
  'level-up': 'チケット型マスコットが高くジャンプしてレベルアップを祝っている',
};

/**
 * 確定済みの透過PNG(idle/xp/level-up)をそのまま表示する。
 * 画像自体は再描画せず、表示・移動・拡大縮小・回転・発光はCSS側(TicketMascot.css)で行う。
 */
export function TicketMascot({ state, decorative = false }: TicketMascotProps) {
  return (
    <img
      src={MASCOT_IMAGES[state]}
      alt={decorative ? '' : MASCOT_ALT[state]}
      className={`ticket-mascot ticket-mascot--${state}`}
    />
  );
}
