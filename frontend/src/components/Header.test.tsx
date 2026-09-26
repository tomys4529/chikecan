import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { MemoryRouter } from 'react-router-dom';
import { AuthProvider } from '../context/AuthContext';
import { Header } from './Header';

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}

describe('Header', () => {
  beforeEach(() => {
    vi.stubGlobal('fetch', vi.fn());
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('未認証時はログイン・登録リンクを表示する', async () => {
    const fetchMock = vi.mocked(fetch);
    fetchMock.mockImplementation((input) => {
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

    render(
      <MemoryRouter>
        <AuthProvider>
          <Header />
        </AuthProvider>
      </MemoryRouter>,
    );

    expect(await screen.findByRole('link', { name: 'ログイン' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'ユーザー登録' })).toBeInTheDocument();
  });

  it('ブランド名としてchikecanが表示される', async () => {
    const fetchMock = vi.mocked(fetch);
    fetchMock.mockImplementation((input) => {
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

    render(
      <MemoryRouter>
        <AuthProvider>
          <Header />
        </AuthProvider>
      </MemoryRouter>,
    );

    expect(await screen.findByRole('link', { name: 'chikecan' })).toBeInTheDocument();
  });

  it('認証済みの場合はユーザー名とログアウトボタンを表示する', async () => {
    const fetchMock = vi.mocked(fetch);
    fetchMock.mockImplementation((input) => {
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

    render(
      <MemoryRouter>
        <AuthProvider>
          <Header />
        </AuthProvider>
      </MemoryRouter>,
    );

    await waitFor(() => expect(screen.getByRole('button', { name: 'ログアウト' })).toBeInTheDocument());
    expect(screen.getByText(/山田太郎様/)).toBeInTheDocument();
  });

  it('認証済みの場合はアカウント設定へのリンクを表示する', async () => {
    const fetchMock = vi.mocked(fetch);
    fetchMock.mockImplementation((input) => {
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

    render(
      <MemoryRouter>
        <AuthProvider>
          <Header />
        </AuthProvider>
      </MemoryRouter>,
    );

    expect(await screen.findByRole('link', { name: 'アカウント設定' })).toHaveAttribute('href', '/account');
  });

  it('ログアウトボタンをクリックするとPOST /api/auth/logoutが実行され未認証表示へ切り替わる', async () => {
    let csrfCallCount = 0;
    let logoutCalled = false;
    const fetchMock = vi.mocked(fetch);
    fetchMock.mockImplementation((input, init) => {
      const url = String(input);
      const method = ((init as RequestInit | undefined)?.method ?? 'GET').toUpperCase();
      if (url.endsWith('/api/auth/csrf')) {
        csrfCallCount += 1;
        return Promise.resolve(jsonResponse({ token: 't', headerName: 'X-XSRF-TOKEN', parameterName: '_csrf' }));
      }
      if (url.endsWith('/api/auth/me')) {
        return Promise.resolve(
          jsonResponse({ id: 1, name: '山田太郎', email: 'user@example.com', role: 'USER', enabled: true, createdAt: '2026-01-01T00:00:00Z' }),
        );
      }
      if (url.endsWith('/api/auth/logout') && method === 'POST') {
        logoutCalled = true;
        return Promise.resolve(jsonResponse({ message: 'ログアウトしました' }));
      }
      throw new Error(`unexpected fetch: ${url} ${method}`);
    });

    const user = userEvent.setup();
    render(
      <MemoryRouter>
        <AuthProvider>
          <Header />
        </AuthProvider>
      </MemoryRouter>,
    );

    await waitFor(() => expect(screen.getByRole('button', { name: 'ログアウト' })).toBeInTheDocument());
    expect(screen.getByText(/山田太郎様/)).toBeInTheDocument();
    const csrfCallsBeforeLogout = csrfCallCount;

    await user.click(screen.getByRole('button', { name: 'ログアウト' }));

    await waitFor(() => expect(screen.getByRole('link', { name: 'ログイン' })).toBeInTheDocument());
    expect(screen.getByRole('link', { name: 'ユーザー登録' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'ログアウト' })).not.toBeInTheDocument();
    expect(screen.queryByText(/山田太郎様/)).not.toBeInTheDocument();
    expect(logoutCalled).toBe(true);
    // ログアウト後にCSRFトークンを再取得していること(ログイン成功時と対になる仕様)。
    expect(csrfCallCount).toBeGreaterThan(csrfCallsBeforeLogout);
  });

  it('ログアウト後のCSRF再取得が失敗しても未認証状態は維持される', async () => {
    let csrfCallCount = 0;
    const fetchMock = vi.mocked(fetch);
    fetchMock.mockImplementation((input, init) => {
      const url = String(input);
      const method = ((init as RequestInit | undefined)?.method ?? 'GET').toUpperCase();
      if (url.endsWith('/api/auth/csrf')) {
        csrfCallCount += 1;
        if (csrfCallCount === 1) {
          return Promise.resolve(jsonResponse({ token: 't', headerName: 'X-XSRF-TOKEN', parameterName: '_csrf' }));
        }
        // ログアウト後の再取得は失敗させる。
        return Promise.reject(new TypeError('network error'));
      }
      if (url.endsWith('/api/auth/me')) {
        return Promise.resolve(
          jsonResponse({ id: 1, name: '山田太郎', email: 'user@example.com', role: 'USER', enabled: true, createdAt: '2026-01-01T00:00:00Z' }),
        );
      }
      if (url.endsWith('/api/auth/logout') && method === 'POST') {
        return Promise.resolve(jsonResponse({ message: 'ログアウトしました' }));
      }
      throw new Error(`unexpected fetch: ${url} ${method}`);
    });

    const user = userEvent.setup();
    render(
      <MemoryRouter>
        <AuthProvider>
          <Header />
        </AuthProvider>
      </MemoryRouter>,
    );

    await waitFor(() => expect(screen.getByRole('button', { name: 'ログアウト' })).toBeInTheDocument());

    await user.click(screen.getByRole('button', { name: 'ログアウト' }));

    // CSRF再取得の失敗はログアウト自体の成否に影響せず、未認証表示のままになる。
    await waitFor(() => expect(screen.getByRole('link', { name: 'ログイン' })).toBeInTheDocument());
    expect(screen.queryByRole('button', { name: 'ログアウト' })).not.toBeInTheDocument();
  });
});
