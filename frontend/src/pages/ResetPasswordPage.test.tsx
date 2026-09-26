import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
import { AuthProvider } from '../context/AuthContext';
import { ResetPasswordPage } from './ResetPasswordPage';
import { csrfResponse, errorResponse, jsonResponse } from '../test-utils/apiMocks';

function unauthenticatedMeResponse() {
  return errorResponse(401, '認証が必要です', '/api/auth/me');
}

// 現在のURLのクエリ文字列を画面に表示するだけのテスト用コンポーネント。
// ResetPasswordPageがtokenをURLから削除したことをアサーションできるようにする。
function CurrentSearchProbe() {
  const location = useLocation();
  return <span data-testid="current-search">{location.search}</span>;
}

function renderResetPasswordPage(path: string, fetchImpl: (input: RequestInfo | URL) => Promise<Response>) {
  vi.stubGlobal('fetch', vi.fn(fetchImpl));

  return render(
    <MemoryRouter initialEntries={[path]}>
      <AuthProvider>
        <Routes>
          <Route
            path="/reset-password"
            element={
              <>
                <ResetPasswordPage />
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

function defaultFetchImpl(confirmHandler: (body: unknown) => Response | Promise<Response>) {
  return (input: RequestInfo | URL, init?: RequestInit) => {
    const url = String(input);
    if (url.endsWith('/api/auth/csrf')) return Promise.resolve(csrfResponse());
    if (url.endsWith('/api/auth/me')) return Promise.resolve(unauthenticatedMeResponse());
    if (url.endsWith('/api/auth/password-reset/confirm')) {
      const body = JSON.parse(String(init?.body));
      return Promise.resolve(confirmHandler(body));
    }
    throw new Error(`unexpected fetch: ${url}`);
  };
}

describe('ResetPasswordPage', () => {
  beforeEach(() => {
    vi.stubGlobal('fetch', vi.fn());
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('tokenがURLに存在しない場合はフォームを表示せずエラー表示する', async () => {
    renderResetPasswordPage('/reset-password', defaultFetchImpl(() => jsonResponse({ message: 'ok' })));

    expect(await screen.findByRole('alert')).toHaveTextContent('再設定リンクが無効または期限切れです。');
    expect(screen.queryByLabelText('新しいパスワード')).not.toBeInTheDocument();
  });

  it('token検証後にURLのtokenクエリパラメータが削除される', async () => {
    renderResetPasswordPage(
      '/reset-password?token=should-be-removed',
      defaultFetchImpl(() => jsonResponse({ message: 'ok' })),
    );

    await waitFor(() => expect(screen.getByTestId('current-search')).toBeEmptyDOMElement());
    // フォームは通常通り表示され続ける。
    expect(screen.getByLabelText('新しいパスワード')).toBeInTheDocument();
  });

  it('未入力で送信するとエラーメッセージを表示しAPIを呼ばない', async () => {
    let confirmCalled = false;
    renderResetPasswordPage(
      '/reset-password?token=valid-token',
      defaultFetchImpl(() => {
        confirmCalled = true;
        return jsonResponse({ message: 'ok' });
      }),
    );

    const user = userEvent.setup();
    await waitFor(() => expect(screen.getByRole('button', { name: 'パスワードを変更' })).toBeEnabled());
    await user.click(screen.getByRole('button', { name: 'パスワードを変更' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('全ての項目を入力してください');
    expect(confirmCalled).toBe(false);
  });

  it('弱いパスワードの場合はエラーメッセージを表示しAPIを呼ばない', async () => {
    let confirmCalled = false;
    renderResetPasswordPage(
      '/reset-password?token=valid-token',
      defaultFetchImpl(() => {
        confirmCalled = true;
        return jsonResponse({ message: 'ok' });
      }),
    );

    const user = userEvent.setup();
    await waitFor(() => expect(screen.getByRole('button', { name: 'パスワードを変更' })).toBeEnabled());
    await user.type(screen.getByLabelText('新しいパスワード'), 'weakpass');
    await user.type(screen.getByLabelText('新しいパスワード（確認）'), 'weakpass');
    await user.click(screen.getByRole('button', { name: 'パスワードを変更' }));

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'パスワードは8〜72文字で、大文字・小文字・数字・記号をそれぞれ1文字以上含めてください',
    );
    expect(confirmCalled).toBe(false);
  });

  it('パスワードが一致しない場合はエラーメッセージを表示しAPIを呼ばない', async () => {
    let confirmCalled = false;
    renderResetPasswordPage(
      '/reset-password?token=valid-token',
      defaultFetchImpl(() => {
        confirmCalled = true;
        return jsonResponse({ message: 'ok' });
      }),
    );

    const user = userEvent.setup();
    await waitFor(() => expect(screen.getByRole('button', { name: 'パスワードを変更' })).toBeEnabled());
    await user.type(screen.getByLabelText('新しいパスワード'), 'Passw0rd123!');
    await user.type(screen.getByLabelText('新しいパスワード（確認）'), 'Passw0rd123?');
    await user.click(screen.getByRole('button', { name: 'パスワードを変更' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('パスワードが一致しません');
    expect(confirmCalled).toBe(false);
  });

  it('正常に送信すると成功メッセージを表示しログインリンクを表示する', async () => {
    let sentToken: string | undefined;
    renderResetPasswordPage(
      '/reset-password?token=valid-token',
      defaultFetchImpl((body) => {
        sentToken = (body as { token: string }).token;
        return jsonResponse({ message: 'パスワードを変更しました。' });
      }),
    );

    const user = userEvent.setup();
    await waitFor(() => expect(screen.getByRole('button', { name: 'パスワードを変更' })).toBeEnabled());
    await user.type(screen.getByLabelText('新しいパスワード'), 'Passw0rd123!');
    await user.type(screen.getByLabelText('新しいパスワード（確認）'), 'Passw0rd123!');
    await user.click(screen.getByRole('button', { name: 'パスワードを変更' }));

    expect(await screen.findByText('パスワードを変更しました。ログインしてください。')).toBeInTheDocument();
    expect(sentToken).toBe('valid-token');
    expect(screen.getByRole('link', { name: 'ログイン画面へ' })).toBeInTheDocument();
  });

  it('token無効・期限切れの場合はサーバーのエラーメッセージを表示する', async () => {
    renderResetPasswordPage(
      '/reset-password?token=expired-token',
      defaultFetchImpl(() => errorResponse(400, '再設定リンクが無効または期限切れです', '/api/auth/password-reset/confirm')),
    );

    const user = userEvent.setup();
    await waitFor(() => expect(screen.getByRole('button', { name: 'パスワードを変更' })).toBeEnabled());
    await user.type(screen.getByLabelText('新しいパスワード'), 'Passw0rd123!');
    await user.type(screen.getByLabelText('新しいパスワード（確認）'), 'Passw0rd123!');
    await user.click(screen.getByRole('button', { name: 'パスワードを変更' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('再設定リンクが無効または期限切れです');
    // 失敗後はフォームへ戻らない(token自体が無効なため再送信しても無意味)。
    expect(screen.queryByLabelText('新しいパスワード')).not.toBeInTheDocument();
  });
});
