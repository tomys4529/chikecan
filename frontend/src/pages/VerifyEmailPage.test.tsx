import { render, screen } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
import { AuthProvider } from '../context/AuthContext';
import { VerifyEmailPage } from './VerifyEmailPage';
import { csrfResponse, errorResponse, jsonResponse } from '../test-utils/apiMocks';

function unauthenticatedMeResponse() {
  return errorResponse(401, '認証が必要です', '/api/auth/me');
}

// 現在のURLのクエリ文字列を画面に表示するだけのテスト用コンポーネント。
// VerifyEmailPageがtokenをURLから削除したことをアサーションできるようにする。
function CurrentSearchProbe() {
  const location = useLocation();
  return <span data-testid="current-search">{location.search}</span>;
}

function renderVerifyEmailPage(path: string, fetchImpl: (input: RequestInfo | URL) => Promise<Response>) {
  vi.stubGlobal('fetch', vi.fn(fetchImpl));

  return render(
    <MemoryRouter initialEntries={[path]}>
      <AuthProvider>
        <Routes>
          <Route
            path="/verify-email"
            element={
              <>
                <VerifyEmailPage />
                <CurrentSearchProbe />
              </>
            }
          />
          <Route path="/login" element={<p>ログイン画面</p>} />
        </Routes>
      </AuthProvider>
    </MemoryRouter>,
  );
}

describe('VerifyEmailPage', () => {
  beforeEach(() => {
    vi.stubGlobal('fetch', vi.fn());
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('tokenが正常な場合は成功メッセージを表示する', async () => {
    let verifyCalledWithToken: string | undefined;
    renderVerifyEmailPage('/verify-email?token=valid-token', (input) => {
      const url = String(input);
      if (url.endsWith('/api/auth/csrf')) return Promise.resolve(csrfResponse());
      if (url.endsWith('/api/auth/me')) return Promise.resolve(unauthenticatedMeResponse());
      if (url.endsWith('/api/auth/verify-email')) {
        verifyCalledWithToken = 'called';
        return Promise.resolve(jsonResponse({ message: 'メールアドレスの確認が完了しました。' }));
      }
      throw new Error(`unexpected fetch: ${url}`);
    });

    expect(
      await screen.findByText('メールアドレスの確認が完了しました。ログインできます。'),
    ).toBeInTheDocument();
    expect(verifyCalledWithToken).toBe('called');
    expect(screen.getByRole('link', { name: 'ログイン画面へ' })).toBeInTheDocument();
  });

  it('tokenが無効な場合はサーバーのエラーメッセージを表示する', async () => {
    renderVerifyEmailPage('/verify-email?token=invalid-token', (input) => {
      const url = String(input);
      if (url.endsWith('/api/auth/csrf')) return Promise.resolve(csrfResponse());
      if (url.endsWith('/api/auth/me')) return Promise.resolve(unauthenticatedMeResponse());
      if (url.endsWith('/api/auth/verify-email')) {
        return Promise.resolve(errorResponse(400, '認証リンクが無効または期限切れです', '/api/auth/verify-email'));
      }
      throw new Error(`unexpected fetch: ${url}`);
    });

    expect(await screen.findByRole('alert')).toHaveTextContent('認証リンクが無効または期限切れです');
    expect(screen.getByRole('link', { name: 'ログイン画面へ' })).toBeInTheDocument();
  });

  it('tokenがURLに存在しない場合はAPIを呼ばずエラー表示する', async () => {
    let verifyCalled = false;
    renderVerifyEmailPage('/verify-email', (input) => {
      const url = String(input);
      if (url.endsWith('/api/auth/csrf')) return Promise.resolve(csrfResponse());
      if (url.endsWith('/api/auth/me')) return Promise.resolve(unauthenticatedMeResponse());
      if (url.endsWith('/api/auth/verify-email')) {
        verifyCalled = true;
        return Promise.resolve(jsonResponse({ message: 'ok' }));
      }
      throw new Error(`unexpected fetch: ${url}`);
    });

    expect(await screen.findByRole('alert')).toHaveTextContent('認証リンクが無効または期限切れです。');
    expect(verifyCalled).toBe(false);
  });

  it('検証中はローディング表示になる', async () => {
    let resolveVerify: (() => void) | undefined;
    renderVerifyEmailPage('/verify-email?token=pending-token', (input) => {
      const url = String(input);
      if (url.endsWith('/api/auth/csrf')) return Promise.resolve(csrfResponse());
      if (url.endsWith('/api/auth/me')) return Promise.resolve(unauthenticatedMeResponse());
      if (url.endsWith('/api/auth/verify-email')) {
        return new Promise((resolve) => {
          resolveVerify = () => resolve(jsonResponse({ message: 'ok' }));
        });
      }
      throw new Error(`unexpected fetch: ${url}`);
    });

    expect(await screen.findByText('メールアドレスを確認しています...')).toBeInTheDocument();
    resolveVerify?.();
  });

  it('token検証後にURLのtokenクエリパラメータが削除される', async () => {
    renderVerifyEmailPage('/verify-email?token=should-be-removed', (input) => {
      const url = String(input);
      if (url.endsWith('/api/auth/csrf')) return Promise.resolve(csrfResponse());
      if (url.endsWith('/api/auth/me')) return Promise.resolve(unauthenticatedMeResponse());
      if (url.endsWith('/api/auth/verify-email')) {
        return Promise.resolve(jsonResponse({ message: 'ok' }));
      }
      throw new Error(`unexpected fetch: ${url}`);
    });

    await screen.findByText('メールアドレスの確認が完了しました。ログインできます。');

    // アドレスバー相当のURL(location.search)からtokenが取り除かれていること。
    expect(screen.getByTestId('current-search')).toBeEmptyDOMElement();
  });

  it('token削除後に再読み込みしても認証APIは再実行されない(URLにtokenが残らないため)', async () => {
    let verifyCallCount = 0;
    const fetchImpl = (input: RequestInfo | URL) => {
      const url = String(input);
      if (url.endsWith('/api/auth/csrf')) return Promise.resolve(csrfResponse());
      if (url.endsWith('/api/auth/me')) return Promise.resolve(unauthenticatedMeResponse());
      if (url.endsWith('/api/auth/verify-email')) {
        verifyCallCount += 1;
        return Promise.resolve(jsonResponse({ message: 'ok' }));
      }
      throw new Error(`unexpected fetch: ${url}`);
    };

    const view = renderVerifyEmailPage('/verify-email?token=reload-test', fetchImpl);
    await screen.findByText('メールアドレスの確認が完了しました。ログインできます。');
    expect(verifyCallCount).toBe(1);
    expect(screen.getByTestId('current-search')).toBeEmptyDOMElement();

    // 「再読み込み」を、URLに残っているクエリ(tokenなし)で新規マウントし直すことで模擬する。
    view.unmount();
    vi.stubGlobal('fetch', vi.fn(fetchImpl));
    render(
      <MemoryRouter initialEntries={['/verify-email']}>
        <AuthProvider>
          <Routes>
            <Route path="/verify-email" element={<VerifyEmailPage />} />
            <Route path="/login" element={<p>ログイン画面</p>} />
          </Routes>
        </AuthProvider>
      </MemoryRouter>,
    );

    await screen.findByRole('alert');
    expect(verifyCallCount).toBe(1);
  });
});
