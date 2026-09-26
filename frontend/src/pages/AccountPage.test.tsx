import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { AuthProvider } from '../context/AuthContext';
import { ProtectedRoute } from '../routes/ProtectedRoute';
import { AccountPage } from './AccountPage';
import { csrfResponse, errorResponse, jsonResponse, testUser } from '../test-utils/apiMocks';

function renderAccountPage(fetchImpl: (input: RequestInfo | URL, init?: RequestInit) => Promise<Response>) {
  vi.stubGlobal('fetch', vi.fn(fetchImpl));

  return render(
    <MemoryRouter initialEntries={['/account']}>
      <AuthProvider>
        <Routes>
          <Route path="/login" element={<p>ログイン画面</p>} />
          <Route element={<ProtectedRoute />}>
            <Route path="/account" element={<AccountPage />} />
          </Route>
        </Routes>
      </AuthProvider>
    </MemoryRouter>,
  );
}

function authenticatedFetchImpl(
  handlers: Partial<{
    changePassword: (body: unknown) => Response | Promise<Response>;
    emailChangeRequest: (body: unknown) => Response | Promise<Response>;
  }> = {},
) {
  return (input: RequestInfo | URL, init?: RequestInit) => {
    const url = String(input);
    const body = init?.body ? JSON.parse(String(init.body)) : undefined;
    if (url.endsWith('/api/auth/csrf')) return Promise.resolve(csrfResponse());
    if (url.endsWith('/api/auth/me')) {
      return Promise.resolve(jsonResponse(testUser({ name: '山田太郎', email: 'current@example.com' })));
    }
    if (url.endsWith('/api/account/password')) {
      return Promise.resolve(
        handlers.changePassword ? handlers.changePassword(body) : jsonResponse({ message: 'パスワードを変更しました。' }),
      );
    }
    if (url.endsWith('/api/account/email-change/request')) {
      return Promise.resolve(
        handlers.emailChangeRequest
          ? handlers.emailChangeRequest(body)
          : jsonResponse({ message: '新しいメールアドレス宛に確認メールを送信しました。メール内のリンクから変更を完了してください。' }),
      );
    }
    throw new Error(`unexpected fetch: ${url}`);
  };
}

describe('AccountPage', () => {
  beforeEach(() => {
    vi.stubGlobal('fetch', vi.fn());
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('未ログインの場合はログイン画面へリダイレクトされる', async () => {
    renderAccountPage((input) => {
      const url = String(input);
      if (url.endsWith('/api/auth/csrf')) return Promise.resolve(csrfResponse());
      if (url.endsWith('/api/auth/me')) return Promise.resolve(errorResponse(401, '認証が必要です', '/api/auth/me'));
      throw new Error(`unexpected fetch: ${url}`);
    });

    expect(await screen.findByText('ログイン画面')).toBeInTheDocument();
  });

  it('ログイン中の氏名とメールアドレスを表示する', async () => {
    renderAccountPage(authenticatedFetchImpl());

    expect(await screen.findByText('山田太郎')).toBeInTheDocument();
    expect(screen.getByText('current@example.com')).toBeInTheDocument();
  });

  it('パスワード変更に成功するとログイン状態がクリアされログイン画面へ遷移する', async () => {
    renderAccountPage(authenticatedFetchImpl());
    const user = userEvent.setup();

    await waitFor(() => expect(screen.getByRole('button', { name: 'パスワードを変更' })).toBeEnabled());
    await user.type(screen.getByLabelText('現在のパスワード', { selector: '#current-password' }), 'OldPassw0rd1!');
    await user.type(screen.getByLabelText('新しいパスワード'), 'NewPassw0rd1!');
    await user.type(screen.getByLabelText('新しいパスワード（確認）'), 'NewPassw0rd1!');
    await user.click(screen.getByRole('button', { name: 'パスワードを変更' }));

    // サーバー側で既存セッションが失効させられるため、フロントも直ちにログイン画面へ遷移する
    // (遷移後の案内メッセージ自体はLoginPage側の責務としてLoginPage.test.tsxで検証済み)。
    expect(await screen.findByText('ログイン画面')).toBeInTheDocument();
  });

  it('弱い新しいパスワードの場合はAPIを呼ばずエラーメッセージを表示する', async () => {
    let changePasswordCalled = false;
    renderAccountPage(authenticatedFetchImpl({
      changePassword: () => {
        changePasswordCalled = true;
        return jsonResponse({ message: 'ok' });
      },
    }));
    const user = userEvent.setup();

    await waitFor(() => expect(screen.getByRole('button', { name: 'パスワードを変更' })).toBeEnabled());
    await user.type(screen.getByLabelText('現在のパスワード', { selector: '#current-password' }), 'OldPassw0rd1!');
    await user.type(screen.getByLabelText('新しいパスワード'), 'weakpass');
    await user.type(screen.getByLabelText('新しいパスワード（確認）'), 'weakpass');
    await user.click(screen.getByRole('button', { name: 'パスワードを変更' }));

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'パスワードは8〜72文字で、大文字・小文字・数字・記号をそれぞれ1文字以上含めてください',
    );
    expect(changePasswordCalled).toBe(false);
  });

  it('新しいパスワードと確認が一致しない場合はAPIを呼ばずエラーメッセージを表示する', async () => {
    let changePasswordCalled = false;
    renderAccountPage(authenticatedFetchImpl({
      changePassword: () => {
        changePasswordCalled = true;
        return jsonResponse({ message: 'ok' });
      },
    }));
    const user = userEvent.setup();

    await waitFor(() => expect(screen.getByRole('button', { name: 'パスワードを変更' })).toBeEnabled());
    await user.type(screen.getByLabelText('現在のパスワード', { selector: '#current-password' }), 'OldPassw0rd1!');
    await user.type(screen.getByLabelText('新しいパスワード'), 'NewPassw0rd1!');
    await user.type(screen.getByLabelText('新しいパスワード（確認）'), 'NewPassw0rd1?');
    await user.click(screen.getByRole('button', { name: 'パスワードを変更' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('パスワードが一致しません');
    expect(changePasswordCalled).toBe(false);
  });

  it('新しいパスワードが現在のパスワードと同じ場合はAPIを呼ばずエラーメッセージを表示する', async () => {
    let changePasswordCalled = false;
    renderAccountPage(authenticatedFetchImpl({
      changePassword: () => {
        changePasswordCalled = true;
        return jsonResponse({ message: 'ok' });
      },
    }));
    const user = userEvent.setup();

    await waitFor(() => expect(screen.getByRole('button', { name: 'パスワードを変更' })).toBeEnabled());
    await user.type(screen.getByLabelText('現在のパスワード', { selector: '#current-password' }), 'SamePassw0rd1!');
    await user.type(screen.getByLabelText('新しいパスワード'), 'SamePassw0rd1!');
    await user.type(screen.getByLabelText('新しいパスワード（確認）'), 'SamePassw0rd1!');
    await user.click(screen.getByRole('button', { name: 'パスワードを変更' }));

    expect(await screen.findByRole('alert')).toHaveTextContent(
      '新しいパスワードは現在のパスワードと異なるものにしてください',
    );
    expect(changePasswordCalled).toBe(false);
  });

  it('パスワード変更でサーバーエラーが返るとエラーメッセージを表示する', async () => {
    renderAccountPage(authenticatedFetchImpl({
      changePassword: () => errorResponse(401, '現在のパスワードが正しくありません', '/api/account/password'),
    }));
    const user = userEvent.setup();

    await waitFor(() => expect(screen.getByRole('button', { name: 'パスワードを変更' })).toBeEnabled());
    await user.type(screen.getByLabelText('現在のパスワード', { selector: '#current-password' }), 'WrongPassw0rd1!');
    await user.type(screen.getByLabelText('新しいパスワード'), 'NewPassw0rd1!');
    await user.type(screen.getByLabelText('新しいパスワード（確認）'), 'NewPassw0rd1!');
    await user.click(screen.getByRole('button', { name: 'パスワードを変更' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('現在のパスワードが正しくありません');
    // API失敗時はログイン状態をクリアせず、ログイン画面へも遷移しない。
    expect(screen.queryByText('ログイン画面')).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'パスワードを変更' })).toBeInTheDocument();
  });

  it('メールアドレス変更フォームに正常な値を入力すると案内メッセージを表示し現在のメールアドレス表示は変わらない', async () => {
    renderAccountPage(authenticatedFetchImpl());
    const user = userEvent.setup();

    await waitFor(() => expect(screen.getByRole('button', { name: 'メールアドレスを変更' })).toBeEnabled());
    await user.type(screen.getByLabelText('新しいメールアドレス'), 'new-address@example.com');
    await user.type(screen.getByLabelText('現在のパスワード', { selector: '#email-change-current-password' }), 'Passw0rd123!');
    await user.click(screen.getByRole('button', { name: 'メールアドレスを変更' }));

    expect(
      await screen.findByText('新しいメールアドレス宛に確認メールを送信しました。メール内のリンクから変更を完了してください。'),
    ).toBeInTheDocument();
    // 確認前は現在のメールアドレス表示は変わらない。
    expect(screen.getByText('current@example.com')).toBeInTheDocument();
  });

  it('メールアドレス変更でサーバーエラーが返るとエラーメッセージを表示する', async () => {
    renderAccountPage(authenticatedFetchImpl({
      emailChangeRequest: () => errorResponse(409, 'このメールアドレスは既に使用されています', '/api/account/email-change/request'),
    }));
    const user = userEvent.setup();

    await waitFor(() => expect(screen.getByRole('button', { name: 'メールアドレスを変更' })).toBeEnabled());
    await user.type(screen.getByLabelText('新しいメールアドレス'), 'taken@example.com');
    await user.type(screen.getByLabelText('現在のパスワード', { selector: '#email-change-current-password' }), 'Passw0rd123!');
    await user.click(screen.getByRole('button', { name: 'メールアドレスを変更' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('このメールアドレスは既に使用されています');
  });
});
