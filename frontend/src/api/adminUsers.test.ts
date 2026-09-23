import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { listAgents } from './adminUsers';

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}

describe('api/adminUsers', () => {
  beforeEach(() => {
    vi.stubGlobal('fetch', vi.fn());
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('listAgentsはGET /api/admin/agentsを呼ぶ', async () => {
    const fetchMock = vi.mocked(fetch);
    fetchMock.mockResolvedValue(jsonResponse([{ id: 1, name: '担当太郎', email: 'agent@example.com' }]));

    const result = await listAgents();

    const [url, init] = fetchMock.mock.calls[0];
    expect(String(url)).toContain('/api/admin/agents');
    expect((init as RequestInit).method ?? 'GET').toBe('GET');
    expect(result).toEqual([{ id: 1, name: '担当太郎', email: 'agent@example.com' }]);
  });
});
