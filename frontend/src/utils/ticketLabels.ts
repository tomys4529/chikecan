import type { TicketPriority, TicketStatus } from '../types/ticket';

export const STATUS_LABELS: Record<TicketStatus, string> = {
  OPEN: '未対応',
  IN_PROGRESS: '対応中',
  RESOLVED: '解決済み',
  CLOSED: 'クローズ',
};

export const PRIORITY_LABELS: Record<TicketPriority, string> = {
  LOW: '低',
  MEDIUM: '中',
  HIGH: '高',
};
