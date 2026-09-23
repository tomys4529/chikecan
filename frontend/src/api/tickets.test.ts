import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { createTicket, getTicket, listTickets, updateTicketStatus } from './tickets';

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}

describe('api/tickets', () => {
  beforeEach(() => {
    vi.stubGlobal('fetch', vi.fn());
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('listTicketsはGET /api/ticketsを呼ぶ', async () => {
    const fetchMock = vi.mocked(fetch);
    fetchMock.mockResolvedValue(jsonResponse([]));

    await listTickets();

    const [url, init] = fetchMock.mock.calls[0];
    expect(String(url)).toContain('/api/tickets');
    expect((init as RequestInit).method ?? 'GET').toBe('GET');
  });

  it('getTicketはGET /api/tickets/{id}を呼ぶ', async () => {
    const fetchMock = vi.mocked(fetch);
    fetchMock.mockResolvedValue(
      jsonResponse({ id: 5, title: 't', description: 'd', status: 'OPEN', priority: 'LOW', requesterId: 1, assigneeId: null, createdAt: '', updatedAt: '' }),
    );

    await getTicket(5);

    const [url] = fetchMock.mock.calls[0];
    expect(String(url)).toContain('/api/tickets/5');
  });

  it('createTicketはPOST /api/ticketsをbody付きで呼ぶ', async () => {
    const fetchMock = vi.mocked(fetch);
    fetchMock.mockResolvedValue(
      jsonResponse({ id: 1, title: 't', description: 'd', status: 'OPEN', priority: 'LOW', requesterId: 1, assigneeId: null, createdAt: '', updatedAt: '' }, 201),
    );

    await createTicket({ title: 't', description: 'd', priority: 'LOW' });

    const [url, init] = fetchMock.mock.calls[0];
    expect(String(url)).toContain('/api/tickets');
    expect((init as RequestInit).method).toBe('POST');
    expect((init as RequestInit).body).toBe(JSON.stringify({ title: 't', description: 'd', priority: 'LOW' }));
  });

  it('updateTicketStatusはPATCH /api/tickets/{id}/statusを呼ぶ', async () => {
    const fetchMock = vi.mocked(fetch);
    fetchMock.mockResolvedValue(
      jsonResponse({ id: 3, title: 't', description: 'd', status: 'IN_PROGRESS', priority: 'LOW', requesterId: 1, assigneeId: 2, createdAt: '', updatedAt: '' }),
    );

    await updateTicketStatus(3, { status: 'IN_PROGRESS' });

    const [url, init] = fetchMock.mock.calls[0];
    expect(String(url)).toContain('/api/tickets/3/status');
    expect((init as RequestInit).method).toBe('PATCH');
    expect((init as RequestInit).body).toBe(JSON.stringify({ status: 'IN_PROGRESS' }));
  });
});
