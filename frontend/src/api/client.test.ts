import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiError, apiFetch } from './client';

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}

describe('apiFetch', () => {
  beforeEach(() => {
    vi.stubGlobal('fetch', vi.fn());
    document.cookie = 'XSRF-TOKEN=test-csrf-token';
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT';
  });

  it('GETリクエストにはX-XSRF-TOKENヘッダーを付与しない', async () => {
    const fetchMock = vi.mocked(fetch);
    fetchMock.mockResolvedValue(jsonResponse({ status: 'UP' }));

    await apiFetch('/api/health');

    const requestInit = fetchMock.mock.calls[0][1] as RequestInit;
    const headers = new Headers(requestInit.headers);
    expect(headers.has('X-XSRF-TOKEN')).toBe(false);
  });

  it('POSTリクエストにはX-XSRF-TOKENヘッダーを付与する', async () => {
    const fetchMock = vi.mocked(fetch);
    fetchMock.mockResolvedValue(jsonResponse({}));

    await apiFetch('/api/auth/login', { method: 'POST', body: JSON.stringify({}) });

    const requestInit = fetchMock.mock.calls[0][1] as RequestInit;
    const headers = new Headers(requestInit.headers);
    expect(headers.get('X-XSRF-TOKEN')).toBe('test-csrf-token');
  });

  it('PATCHリクエストにもX-XSRF-TOKENヘッダーを付与する', async () => {
    const fetchMock = vi.mocked(fetch);
    fetchMock.mockResolvedValue(jsonResponse({}));

    await apiFetch('/api/tickets/1/status', { method: 'PATCH', body: JSON.stringify({}) });

    const requestInit = fetchMock.mock.calls[0][1] as RequestInit;
    const headers = new Headers(requestInit.headers);
    expect(headers.get('X-XSRF-TOKEN')).toBe('test-csrf-token');
  });

  it('全リクエストにcredentials: includeを設定する', async () => {
    const fetchMock = vi.mocked(fetch);
    fetchMock.mockResolvedValue(jsonResponse({}));

    await apiFetch('/api/health');

    const requestInit = fetchMock.mock.calls[0][1] as RequestInit;
    expect(requestInit.credentials).toBe('include');
  });

  it('エラーレスポンスはApiErrorとしてstatusとmessageを保持する', async () => {
    const fetchMock = vi.mocked(fetch);
    fetchMock.mockResolvedValue(
      jsonResponse(
        { status: 404, error: 'Not Found', message: 'チケットが見つかりません', path: '/api/tickets/1', timestamp: '2026-01-01T00:00:00Z' },
        404,
      ),
    );

    await expect(apiFetch('/api/tickets/1')).rejects.toMatchObject({
      status: 404,
      message: 'チケットが見つかりません',
    });
    await expect(apiFetch('/api/tickets/1')).rejects.toBeInstanceOf(ApiError);
  });
});
