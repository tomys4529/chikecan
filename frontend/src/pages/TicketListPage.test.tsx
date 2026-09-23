import { render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { AuthProvider } from '../context/AuthContext';
import { ProtectedRoute } from '../routes/ProtectedRoute';
import { TicketListPage } from './TicketListPage';
import { csrfResponse, errorResponse, jsonResponse, testUser } from '../test-utils/apiMocks';
import type { TicketResponse } from '../types/ticket';

function renderTicketList(fetchImpl: (input: RequestInfo | URL) => Promise<Response>) {
  vi.stubGlobal('fetch', vi.fn(fetchImpl));

  return render(
    <MemoryRouter initialEntries={['/tickets']}>
      <AuthProvider>
        <Routes>
          <Route path="/login" element={<p>ログイン画面</p>} />
          <Route element={<ProtectedRoute />}>
            <Route path="/tickets" element={<TicketListPage />} />
          </Route>
        </Routes>
      </AuthProvider>
    </MemoryRouter>,
  );
}

function ticket(overrides: Partial<TicketResponse> = {}): TicketResponse {
  return {
    id: 1,
    title: 'サンプルチケット',
    description: '内容',
    status: 'OPEN',
    priority: 'LOW',
    requesterId: 1,
    assigneeId: null,
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-01T00:00:00Z',
    ...overrides,
  };
}

describe('TicketListPage', () => {
  beforeEach(() => {
    vi.stubGlobal('fetch', vi.fn());
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('取得成功時に一覧を表示しUSERには新規登録リンクが出る', async () => {
    renderTicketList((input) => {
      const url = String(input);
      if (url.endsWith('/api/auth/csrf')) return Promise.resolve(csrfResponse());
      if (url.endsWith('/api/auth/me')) return Promise.resolve(jsonResponse(testUser({ role: 'USER' })));
      if (url.endsWith('/api/tickets')) return Promise.resolve(jsonResponse([ticket()]));
      throw new Error(`unexpected fetch: ${url}`);
    });

    expect(await screen.findByText('サンプルチケット')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: '新規登録' })).toBeInTheDocument();
  });

  it('AGENTには新規登録リンクが表示されない', async () => {
    renderTicketList((input) => {
      const url = String(input);
      if (url.endsWith('/api/auth/csrf')) return Promise.resolve(csrfResponse());
      if (url.endsWith('/api/auth/me')) return Promise.resolve(jsonResponse(testUser({ role: 'AGENT' })));
      if (url.endsWith('/api/tickets')) return Promise.resolve(jsonResponse([ticket()]));
      throw new Error(`unexpected fetch: ${url}`);
    });

    await screen.findByText('サンプルチケット');
    expect(screen.queryByRole('link', { name: '新規登録' })).not.toBeInTheDocument();
  });

  it('0件の場合は該当なしメッセージを表示する', async () => {
    renderTicketList((input) => {
      const url = String(input);
      if (url.endsWith('/api/auth/csrf')) return Promise.resolve(csrfResponse());
      if (url.endsWith('/api/auth/me')) return Promise.resolve(jsonResponse(testUser()));
      if (url.endsWith('/api/tickets')) return Promise.resolve(jsonResponse([]));
      throw new Error(`unexpected fetch: ${url}`);
    });

    expect(await screen.findByText('チケットがありません。')).toBeInTheDocument();
  });

  it('チケット取得が401の場合は認証状態を破棄しログイン画面へ遷移する', async () => {
    renderTicketList((input) => {
      const url = String(input);
      if (url.endsWith('/api/auth/csrf')) return Promise.resolve(csrfResponse());
      if (url.endsWith('/api/auth/me')) return Promise.resolve(jsonResponse(testUser()));
      if (url.endsWith('/api/tickets')) return Promise.resolve(errorResponse(401, '認証が必要です', '/api/tickets'));
      throw new Error(`unexpected fetch: ${url}`);
    });

    await waitFor(() => expect(screen.getByText('ログイン画面')).toBeInTheDocument());
  });
});
