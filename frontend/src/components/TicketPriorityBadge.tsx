import type { TicketPriority } from '../types/ticket';
import { PRIORITY_LABELS } from '../utils/ticketLabels';

const PRIORITY_MODIFIER_CLASS: Record<TicketPriority, string> = {
  LOW: 'ticket-priority-badge--low',
  MEDIUM: 'ticket-priority-badge--medium',
  HIGH: 'ticket-priority-badge--high',
};

export function TicketPriorityBadge({ priority }: { priority: TicketPriority }) {
  return (
    <span className={`ticket-priority-badge ${PRIORITY_MODIFIER_CLASS[priority]}`}>
      {PRIORITY_LABELS[priority]}
    </span>
  );
}
