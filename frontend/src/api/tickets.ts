import { apiFetch } from './client';
import type {
  TicketAssigneeUpdateRequest,
  TicketCreateRequest,
  TicketResponse,
  TicketStatusUpdateRequest,
} from '../types/ticket';

export function listTickets(): Promise<TicketResponse[]> {
  return apiFetch<TicketResponse[]>('/api/tickets');
}

export function getTicket(id: number): Promise<TicketResponse> {
  return apiFetch<TicketResponse>(`/api/tickets/${id}`);
}

export function createTicket(request: TicketCreateRequest): Promise<TicketResponse> {
  return apiFetch<TicketResponse>('/api/tickets', {
    method: 'POST',
    body: JSON.stringify(request),
  });
}

export function updateTicketStatus(id: number, request: TicketStatusUpdateRequest): Promise<TicketResponse> {
  return apiFetch<TicketResponse>(`/api/tickets/${id}/status`, {
    method: 'PATCH',
    body: JSON.stringify(request),
  });
}

export function updateTicketAssignee(id: number, request: TicketAssigneeUpdateRequest): Promise<TicketResponse> {
  return apiFetch<TicketResponse>(`/api/tickets/${id}/assignee`, {
    method: 'PATCH',
    body: JSON.stringify(request),
  });
}
