import { apiFetch } from './client';
import type {
  PageResponse,
  TicketAssigneeUpdateRequest,
  TicketCreateRequest,
  TicketResponse,
  TicketStatusUpdateRequest,
  TicketStatusUpdateResponse,
  TicketUpdateRequest,
} from '../types/ticket';

/**
 * page・sizeはバックエンドAPIの規約(Spring Data標準)に合わせ0始まりで渡す。
 * 画面上のページ番号(1始まり)との変換は呼び出し側(TicketListPage)で行う。
 */
export function listTickets(page: number, size: number): Promise<PageResponse<TicketResponse>> {
  return apiFetch<PageResponse<TicketResponse>>(`/api/tickets?page=${page}&size=${size}`);
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

export function updateTicket(id: number, request: TicketUpdateRequest): Promise<TicketResponse> {
  return apiFetch<TicketResponse>(`/api/tickets/${id}`, {
    method: 'PATCH',
    body: JSON.stringify(request),
  });
}

export function updateTicketStatus(id: number, request: TicketStatusUpdateRequest): Promise<TicketStatusUpdateResponse> {
  return apiFetch<TicketStatusUpdateResponse>(`/api/tickets/${id}/status`, {
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
