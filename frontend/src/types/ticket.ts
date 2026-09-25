export type TicketStatus = 'OPEN' | 'IN_PROGRESS' | 'RESOLVED' | 'CLOSED';
export type TicketPriority = 'LOW' | 'MEDIUM' | 'HIGH';

export interface TicketResponse {
  id: number;
  title: string;
  description: string;
  status: TicketStatus;
  priority: TicketPriority;
  requesterId: number;
  requesterName: string;
  assigneeId: number | null;
  assigneeName: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface TicketCreateRequest {
  title: string;
  description: string;
  priority: TicketPriority;
}

export interface TicketUpdateRequest {
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

export interface XpAwardResult {
  awarded: boolean;
  gainedExperience: number;
  previousLevel: number;
  currentLevel: number;
  totalExperience: number;
  levelUp: boolean;
}

export interface TicketStatusUpdateResponse {
  ticket: TicketResponse;
  xpResult: XpAwardResult;
}

export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  first: boolean;
  last: boolean;
}
