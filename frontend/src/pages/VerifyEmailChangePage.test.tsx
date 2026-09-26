import { render, screen } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
import { AuthProvider } from '../context/AuthContext';
import { VerifyEmailChangePage } from './VerifyEmailChangePage';
import { csrfResponse, errorResponse, jsonResponse } from '../test-utils/apiMocks';

function unauthenticatedMeResponse() {
  return errorResponse(401, '認証が必要です', '/api/auth/me');
}

// 現在のURLのクエリ文字列を画面に表示するだけのテスト用コンポーネント。
// VerifyEmailChangePageがtokenをURLから削除したことをアサーションできるようにする。
function CurrentSearchProbe() {
  const location = useLocation();
  return <span data-testid="current-search">{location.search}</span>;
}

function renderVerifyEmailChangePage(path: string, fetchImpl: (input: RequestInfo | URL) => Promise<Response>) {
  vi.stubGlobal('fetch', vi.fn(fetchImpl));

  return render(
    <MemoryRouter initialEntries={[path]}>
      <AuthProvider>
        <Routes>
          <Route
            path="/verify-email-change"
            element={
              <>
                <VerifyEmailChangePage />
                <CurrentSearchProbe />
              </>
            }
          />
          <Route path="/login" element={<p>ログイン画面</p>} />
          <Route path="/account" element={<p>アカウント設定画面</p>} />
        </Routes>
      </AuthProvider>
    </MemoryRouter>,
  );
}

describe('VerifyEmailChangePage', () => {
  beforeEach(() => {
    vi.stubGlobal('fetch', vi.fn());
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('tokenが正常な場合は成功メッセージを表示する', async () => {
    let confirmCalled = false;
    renderVerifyEmailChangePage('/verify-email-change?token=valid-token', (input) => {
      const url = String(input);
      if (url.endsWith('/api/auth/csrf')) return Promise.resolve(csrfResponse());
      if (url.endsWith('/api/auth/me')) return Promise.resolve(unauthenticatedMeResponse());
      if (url.endsWith('/api/account/email-change/confirm')) {
        confirmCalled = true;
        return Promise.resolve(jsonResponse({ message: 'メールアドレスを変更しました。' }));
      }
      throw new Error(`unexpected fetch: ${url}`);
    });

    expect(await screen.findByText('メールアドレスを変更しました。')).toBeInTheDocument();
    expect(confirmCalled).toBe(true);
    expect(screen.getByRole('link', { name: 'ログイン画面へ' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'アカウント設定へ' })).toBeInTheDocument();
  });

  it('tokenが無効な場合はサーバーのエラーメッセージを表示する', async () => {
    renderVerifyEmailChangePage('/verify-email-change?token=invalid-token', (input) => {
      const url = String(input);
      if (url.endsWith('/api/auth/csrf')) return Promise.resolve(csrfResponse());
      if (url.endsWith('/api/auth/me')) return Promise.resolve(unauthenticatedMeResponse());
      if (url.endsWith('/api/account/email-change/confirm')) {
        return Promise.resolve(errorResponse(400, '変更リンクが無効または期限切れです', '/api/account/email-change/confirm'));
      }
      throw new Error(`unexpected fetch: ${url}`);
    });

    expect(await screen.findByRole('alert')).toHaveTextContent('変更リンクが無効または期限切れです');
  });

  it('tokenがURLに存在しない場合はAPIを呼ばずエラー表示する', async () => {
    let confirmCalled = false;
    renderVerifyEmailChangePage('/verify-email-change', (input) => {
      const url = String(input);
      if (url.endsWith('/api/auth/csrf')) return Promise.resolve(csrfResponse());
      if (url.endsWith('/api/auth/me')) return Promise.resolve(unauthenticatedMeResponse());
      if (url.endsWith('/api/account/email-change/confirm')) {
        confirmCalled = true;
        return Promise.resolve(jsonResponse({ message: 'ok' }));
      }
      throw new Error(`unexpected fetch: ${url}`);
    });

    expect(await screen.findByRole('alert')).toHaveTextContent('変更リンクが無効または期限切れです。');
    expect(confirmCalled).toBe(false);
  });

  it('検証中はローディング表示になる', async () => {
    let resolveConfirm: (() => void) | undefined;
    renderVerifyEmailChangePage('/verify-email-change?token=pending-token', (input) => {
      const url = String(input);
      if (url.endsWith('/api/auth/csrf')) return Promise.resolve(csrfResponse());
      if (url.endsWith('/api/auth/me')) return Promise.resolve(unauthenticatedMeResponse());
      if (url.endsWith('/api/account/email-change/confirm')) {
        return new Promise((resolve) => {
          resolveConfirm = () => resolve(jsonResponse({ message: 'ok' }));
        });
      }
      throw new Error(`unexpected fetch: ${url}`);
    });

    expect(await screen.findByText('メールアドレスの変更を確認しています...')).toBeInTheDocument();
    resolveConfirm?.();
  });

  it('token検証後にURLのtokenクエリパラメータが削除される', async () => {
    renderVerifyEmailChangePage('/verify-email-change?token=should-be-removed', (input) => {
      const url = String(input);
      if (url.endsWith('/api/auth/csrf')) return Promise.resolve(csrfResponse());
      if (url.endsWith('/api/auth/me')) return Promise.resolve(unauthenticatedMeResponse());
      if (url.endsWith('/api/account/email-change/confirm')) {
        return Promise.resolve(jsonResponse({ message: 'ok' }));
      }
      throw new Error(`unexpected fetch: ${url}`);
    });

    await screen.findByText('メールアドレスを変更しました。');

    expect(screen.getByTestId('current-search')).toBeEmptyDOMElement();
  });

  it('token削除後に再読み込みしても確認APIは再実行されない(URLにtokenが残らないため)', async () => {
    let confirmCallCount = 0;
    const fetchImpl = (input: RequestInfo | URL) => {
      const url = String(input);
      if (url.endsWith('/api/auth/csrf')) return Promise.resolve(csrfResponse());
      if (url.endsWith('/api/auth/me')) return Promise.resolve(unauthenticatedMeResponse());
      if (url.endsWith('/api/account/email-change/confirm')) {
        confirmCallCount += 1;
        return Promise.resolve(jsonResponse({ message: 'ok' }));
      }
      throw new Error(`unexpected fetch: ${url}`);
    };

    const view = renderVerifyEmailChangePage('/verify-email-change?token=reload-test', fetchImpl);
    await screen.findByText('メールアドレスを変更しました。');
    expect(confirmCallCount).toBe(1);
    expect(screen.getByTestId('current-search')).toBeEmptyDOMElement();

    // 「再読み込み」を、URLに残っているクエリ(tokenなし)で新規マウントし直すことで模擬する。
    view.unmount();
    vi.stubGlobal('fetch', vi.fn(fetchImpl));
    render(
      <MemoryRouter initialEntries={['/verify-email-change']}>
        <AuthProvider>
          <Routes>
            <Route path="/verify-email-change" element={<VerifyEmailChangePage />} />
            <Route path="/login" element={<p>ログイン画面</p>} />
          </Routes>
        </AuthProvider>
      </MemoryRouter>,
    );

    await screen.findByRole('alert');
    expect(confirmCallCount).toBe(1);
  });
});
