import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { MemoryRouter } from 'react-router-dom';
import { AuthProvider } from '../context/AuthContext';
import { LoginPage } from './LoginPage';

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}

describe('LoginPage', () => {
  beforeEach(() => {
    vi.stubGlobal('fetch', vi.fn());
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  function setupInitialUnauthenticated() {
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
    return fetchMock;
  }

  it('メールアドレス・パスワード未入力で送信するとエラーメッセージを表示する', async () => {
    setupInitialUnauthenticated();
    const user = userEvent.setup();

    render(
      <MemoryRouter initialEntries={['/login']}>
        <AuthProvider>
          <LoginPage />
        </AuthProvider>
      </MemoryRouter>,
    );

    await waitFor(() => expect(screen.getByRole('button', { name: 'ログイン' })).toBeEnabled());
    await user.click(screen.getByRole('button', { name: 'ログイン' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('メールアドレスとパスワードを入力してください');
  });

  it('ログイン失敗時にサーバーのエラーメッセージを表示する', async () => {
    const fetchMock = setupInitialUnauthenticated();
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
      if (url.endsWith('/api/auth/login')) {
        return Promise.resolve(
          jsonResponse(
            { status: 401, error: 'Unauthorized', message: 'メールアドレスまたはパスワードが正しくありません', path: '/api/auth/login', timestamp: '2026-01-01T00:00:00Z' },
            401,
          ),
        );
      }
      throw new Error(`unexpected fetch: ${url}`);
    });

    const user = userEvent.setup();
    render(
      <MemoryRouter initialEntries={['/login']}>
        <AuthProvider>
          <LoginPage />
        </AuthProvider>
      </MemoryRouter>,
    );

    await waitFor(() => expect(screen.getByRole('button', { name: 'ログイン' })).toBeEnabled());
    await user.type(screen.getByLabelText('メールアドレス'), 'user@example.com');
    await user.type(screen.getByLabelText('パスワード'), 'WrongPassword1');
    await user.click(screen.getByRole('button', { name: 'ログイン' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('メールアドレスまたはパスワードが正しくありません');
  });

  it('パスワードを忘れた方のリンクが表示される', async () => {
    setupInitialUnauthenticated();

    render(
      <MemoryRouter initialEntries={['/login']}>
        <AuthProvider>
          <LoginPage />
        </AuthProvider>
      </MemoryRouter>,
    );

    await waitFor(() => expect(screen.getByRole('button', { name: 'ログイン' })).toBeEnabled());
    expect(screen.getByRole('link', { name: 'パスワードを忘れた方' })).toHaveAttribute('href', '/forgot-password');
  });
});
