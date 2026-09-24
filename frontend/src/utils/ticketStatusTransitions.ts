import type { TicketStatus } from '../types/ticket';

/**
 * バックエンド(TicketService.ALLOWED_TRANSITIONS)と同じ内容。
 * ここでの絞り込みはUXの補助であり、実際の遷移可否判定は常にバックエンドが行う。
 */
export const ALLOWED_STATUS_TRANSITIONS: Record<TicketStatus, TicketStatus[]> = {
  OPEN: ['IN_PROGRESS'],
  IN_PROGRESS: ['RESOLVED'],
  RESOLVED: ['CLOSED', 'IN_PROGRESS'],
  CLOSED: [],
};
