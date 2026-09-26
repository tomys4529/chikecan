import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { MemoryRouter } from 'react-router-dom';
import { AuthProvider } from '../context/AuthContext';
import { ForgotPasswordPage } from './ForgotPasswordPage';
import { csrfResponse, errorResponse, jsonResponse } from '../test-utils/apiMocks';

const RESET_REQUEST_MESSAGE = '対象のアカウントが確認できた場合、パスワード再設定メールを送信します。';

function unauthenticatedMeResponse() {
  return errorResponse(401, '認証が必要です', '/api/auth/me');
}

function renderPage(fetchImpl: (input: RequestInfo | URL) => Promise<Response>) {
  vi.stubGlobal('fetch', vi.fn(fetchImpl));
  return render(
    <MemoryRouter initialEntries={['/forgot-password']}>
      <AuthProvider>
        <ForgotPasswordPage />
      </AuthProvider>
    </MemoryRouter>,
  );
}

describe('ForgotPasswordPage', () => {
  beforeEach(() => {
    vi.stubGlobal('fetch', vi.fn());
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('メールアドレス未入力で送信するとエラーメッセージを表示しAPIを呼ばない', async () => {
    let requestCalled = false;
    renderPage((input) => {
      const url = String(input);
      if (url.endsWith('/api/auth/csrf')) return Promise.resolve(csrfResponse());
      if (url.endsWith('/api/auth/me')) return Promise.resolve(unauthenticatedMeResponse());
      if (url.endsWith('/api/auth/password-reset/request')) {
        requestCalled = true;
        return Promise.resolve(jsonResponse({ message: RESET_REQUEST_MESSAGE }));
      }
      throw new Error(`unexpected fetch: ${url}`);
    });

    const user = userEvent.setup();
    await waitFor(() => expect(screen.getByRole('button', { name: '再設定メールを送信' })).toBeEnabled());
    await user.click(screen.getByRole('button', { name: '再設定メールを送信' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('メールアドレスを入力してください');
    expect(requestCalled).toBe(false);
  });

  it('登録済みメールアドレスで送信すると一般化されたメッセージを表示する', async () => {
    renderPage((input) => {
      const url = String(input);
      if (url.endsWith('/api/auth/csrf')) return Promise.resolve(csrfResponse());
      if (url.endsWith('/api/auth/me')) return Promise.resolve(unauthenticatedMeResponse());
      if (url.endsWith('/api/auth/password-reset/request')) {
        return Promise.resolve(jsonResponse({ message: RESET_REQUEST_MESSAGE }));
      }
      throw new Error(`unexpected fetch: ${url}`);
    });

    const user = userEvent.setup();
    await waitFor(() => expect(screen.getByRole('button', { name: '再設定メールを送信' })).toBeEnabled());
    await user.type(screen.getByLabelText('メールアドレス'), 'registered@example.com');
    await user.click(screen.getByRole('button', { name: '再設定メールを送信' }));

    expect(await screen.findByText(RESET_REQUEST_MESSAGE)).toBeInTheDocument();
    // フォームは消え、再送信ボタンは表示されない。
    expect(screen.queryByRole('button', { name: '再設定メールを送信' })).not.toBeInTheDocument();
  });

  it('未登録メールアドレスで送信しても同じ一般化されたメッセージを表示する(アカウント列挙防止)', async () => {
    renderPage((input) => {
      const url = String(input);
      if (url.endsWith('/api/auth/csrf')) return Promise.resolve(csrfResponse());
      if (url.endsWith('/api/auth/me')) return Promise.resolve(unauthenticatedMeResponse());
      if (url.endsWith('/api/auth/password-reset/request')) {
        return Promise.resolve(jsonResponse({ message: RESET_REQUEST_MESSAGE }));
      }
      throw new Error(`unexpected fetch: ${url}`);
    });

    const user = userEvent.setup();
    await waitFor(() => expect(screen.getByRole('button', { name: '再設定メールを送信' })).toBeEnabled());
    await user.type(screen.getByLabelText('メールアドレス'), 'unknown@example.com');
    await user.click(screen.getByRole('button', { name: '再設定メールを送信' }));

    expect(await screen.findByText(RESET_REQUEST_MESSAGE)).toBeInTheDocument();
  });

  it('サーバーエラー時はエラーメッセージを表示する', async () => {
    renderPage((input) => {
      const url = String(input);
      if (url.endsWith('/api/auth/csrf')) return Promise.resolve(csrfResponse());
      if (url.endsWith('/api/auth/me')) return Promise.resolve(unauthenticatedMeResponse());
      if (url.endsWith('/api/auth/password-reset/request')) {
        return Promise.resolve(errorResponse(400, 'メールアドレスの形式が正しくありません', '/api/auth/password-reset/request'));
      }
      throw new Error(`unexpected fetch: ${url}`);
    });

    const user = userEvent.setup();
    await waitFor(() => expect(screen.getByRole('button', { name: '再設定メールを送信' })).toBeEnabled());
    await user.type(screen.getByLabelText('メールアドレス'), 'invalid-email');
    await user.click(screen.getByRole('button', { name: '再設定メールを送信' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('メールアドレスの形式が正しくありません');
  });
});
