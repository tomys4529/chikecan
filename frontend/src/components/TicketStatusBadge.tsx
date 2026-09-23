import type { TicketStatus } from '../types/ticket';
import { STATUS_LABELS } from '../utils/ticketLabels';

const STATUS_MODIFIER_CLASS: Record<TicketStatus, string> = {
  OPEN: 'ticket-status-badge--open',
  IN_PROGRESS: 'ticket-status-badge--progress',
  RESOLVED: 'ticket-status-badge--resolved',
  CLOSED: 'ticket-status-badge--closed',
};

export function TicketStatusBadge({ status }: { status: TicketStatus }) {
  return (
    <span className={`ticket-status-badge ${STATUS_MODIFIER_CLASS[status]}`}>
      {STATUS_LABELS[status]}
    </span>
  );
}
