import { render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { AuthProvider } from '../context/AuthContext';
import { PublicOnlyRoute } from './PublicOnlyRoute';

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}

function renderWithRoute(fetchImpl: (input: RequestInfo | URL) => Promise<Response>) {
  vi.stubGlobal('fetch', vi.fn(fetchImpl));

  return render(
    <MemoryRouter initialEntries={['/login']}>
      <AuthProvider>
        <Routes>
          <Route path="/tickets" element={<p>チケット一覧</p>} />
          <Route element={<PublicOnlyRoute />}>
            <Route path="/login" element={<p>ログイン画面</p>} />
          </Route>
        </Routes>
      </AuthProvider>
    </MemoryRouter>,
  );
}

describe('PublicOnlyRoute', () => {
  beforeEach(() => {
    vi.stubGlobal('fetch', vi.fn());
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('認証済みの場合はチケット一覧へリダイレクトする', async () => {
    renderWithRoute((input) => {
      const url = String(input);
      if (url.endsWith('/api/auth/csrf')) {
        return Promise.resolve(jsonResponse({ token: 't', headerName: 'X-XSRF-TOKEN', parameterName: '_csrf' }));
      }
      if (url.endsWith('/api/auth/me')) {
        return Promise.resolve(
          jsonResponse({ id: 1, name: '山田太郎', email: 'user@example.com', role: 'USER', enabled: true, createdAt: '2026-01-01T00:00:00Z' }),
        );
      }
      throw new Error(`unexpected fetch: ${url}`);
    });

    await waitFor(() => expect(screen.getByText('チケット一覧')).toBeInTheDocument());
  });

  it('未認証の場合はログイン画面を表示する', async () => {
    renderWithRoute((input) => {
      const url = String(input);
      if (url.endsWith('/api/auth/csrf')) {
        return Promise.resolve(jsonResponse({ token: 't', headerName: 'X-XSRF-TOKEN', parameterName: '_csrf' }));
      }
      if (url.endsWith('/api/auth/me')) {
        return Promise.resolve(
          jsonResponse({ status: 401, error: 'Unauthorized', message: '認証が必要です', path: '/api/auth/me', timestamp: '2026-01-01T00:00:00Z' }, 401),
        );
      }
      throw new Error(`unexpected fetch: ${url}`);
    });

    expect(await screen.findByText('ログイン画面')).toBeInTheDocument();
  });
});
