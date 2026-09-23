export type TicketStatus = 'OPEN' | 'IN_PROGRESS' | 'RESOLVED' | 'CLOSED';
export type TicketPriority = 'LOW' | 'MEDIUM' | 'HIGH';

export interface TicketResponse {
  id: number;
  title: string;
  description: string;
  status: TicketStatus;
  priority: TicketPriority;
  requesterId: number;
  assigneeId: number | null;
  createdAt: string;
  updatedAt: string;
}

export interface TicketCreateRequest {
  title: string;
  description: string;
  priority: TicketPriority;
}

export interface TicketStatusUpdateRequest {
  status: TicketStatus;
}

export interface TicketAssigneeUpdateRequest {
  assigneeId: number | null;
}
