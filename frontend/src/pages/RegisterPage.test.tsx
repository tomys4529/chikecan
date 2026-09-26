import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { MemoryRouter } from 'react-router-dom';
import { AuthProvider } from '../context/AuthContext';
import { RegisterPage } from './RegisterPage';

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}

function mockUnauthenticatedInit(fetchMock: ReturnType<typeof vi.mocked<typeof fetch>>) {
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
}

describe('RegisterPage', () => {
  beforeEach(() => {
    vi.stubGlobal('fetch', vi.fn());
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('未入力項目があるとエラーメッセージを表示する', async () => {
    const fetchMock = vi.mocked(fetch);
    mockUnauthenticatedInit(fetchMock);
    const user = userEvent.setup();

    render(
      <MemoryRouter initialEntries={['/register']}>
        <AuthProvider>
          <RegisterPage />
        </AuthProvider>
      </MemoryRouter>,
    );

    await waitFor(() => expect(screen.getByRole('button', { name: '登録する' })).toBeEnabled());
    await user.click(screen.getByRole('button', { name: '登録する' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('全ての項目を入力してください');
  });

  it('重複メールなどサーバーエラーを表示する', async () => {
    const fetchMock = vi.mocked(fetch);
    mockUnauthenticatedInit(fetchMock);
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
      if (url.endsWith('/api/auth/register')) {
        return Promise.resolve(
          jsonResponse(
            { status: 409, error: 'Conflict', message: 'このメールアドレスは既に登録されています', path: '/api/auth/register', timestamp: '2026-01-01T00:00:00Z' },
            409,
          ),
        );
      }
      throw new Error(`unexpected fetch: ${url}`);
    });

    const user = userEvent.setup();
    render(
      <MemoryRouter initialEntries={['/register']}>
        <AuthProvider>
          <RegisterPage />
        </AuthProvider>
      </MemoryRouter>,
    );

    await waitFor(() => expect(screen.getByRole('button', { name: '登録する' })).toBeEnabled());
    await user.type(screen.getByLabelText('氏名'), '山田太郎');
    await user.type(screen.getByLabelText('メールアドレス'), 'dup@example.com');
    await user.type(screen.getByLabelText('パスワード'), 'Passw0rd123!');
    await user.type(screen.getByLabelText('パスワード（確認）'), 'Passw0rd123!');
    await user.click(screen.getByRole('button', { name: '登録する' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('このメールアドレスは既に登録されています');
  });

  it('登録成功後は自動ログインせず確認メール案内画面を表示する', async () => {
    const fetchMock = vi.mocked(fetch);
    let registerCalled = false;
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
      if (url.endsWith('/api/auth/register')) {
        registerCalled = true;
        return Promise.resolve(
          jsonResponse({ message: '確認メールの送信を受け付けました。メール内のリンクから本登録を完了してください。' }),
        );
      }
      throw new Error(`unexpected fetch: ${url}`);
    });

    const user = userEvent.setup();
    render(
      <MemoryRouter initialEntries={['/register']}>
        <AuthProvider>
          <RegisterPage />
        </AuthProvider>
      </MemoryRouter>,
    );

    await waitFor(() => expect(screen.getByRole('button', { name: '登録する' })).toBeEnabled());
    await user.type(screen.getByLabelText('氏名'), '山田太郎');
    await user.type(screen.getByLabelText('メールアドレス'), 'new@example.com');
    await user.type(screen.getByLabelText('パスワード'), 'Passw0rd123!');
    await user.type(screen.getByLabelText('パスワード（確認）'), 'Passw0rd123!');
    await user.click(screen.getByRole('button', { name: '登録する' }));

    expect(registerCalled).toBe(true);
    expect(
      await screen.findByText('確認メールを送信しました。メール内のリンクから本登録を完了してください。'),
    ).toBeInTheDocument();
    // 登録フォームは表示されなくなる(即ログイン画面へも遷移しない)。
    expect(screen.queryByRole('button', { name: '登録する' })).not.toBeInTheDocument();
  });

  it('登録成功後の案内画面には登録したメールアドレスが入った再送フォームが表示される', async () => {
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
      if (url.endsWith('/api/auth/register')) {
        return Promise.resolve(jsonResponse({ message: 'ok' }));
      }
      throw new Error(`unexpected fetch: ${url}`);
    });

    const user = userEvent.setup();
    render(
      <MemoryRouter initialEntries={['/register']}>
        <AuthProvider>
          <RegisterPage />
        </AuthProvider>
      </MemoryRouter>,
    );

    await waitFor(() => expect(screen.getByRole('button', { name: '登録する' })).toBeEnabled());
    await user.type(screen.getByLabelText('氏名'), '山田太郎');
    await user.type(screen.getByLabelText('メールアドレス'), 'prefill@example.com');
    await user.type(screen.getByLabelText('パスワード'), 'Passw0rd123!');
    await user.type(screen.getByLabelText('パスワード（確認）'), 'Passw0rd123!');
    await user.click(screen.getByRole('button', { name: '登録する' }));

    await screen.findByRole('button', { name: '認証メールを再送' });
    expect(screen.getByLabelText('メールアドレス')).toHaveValue('prefill@example.com');
  });

  it('確認メール案内画面で再送ボタンを押すと再送APIを呼び一般化されたメッセージを表示する', async () => {
    const fetchMock = vi.mocked(fetch);
    let resendCalledWithEmail: string | undefined;
    fetchMock.mockImplementation((input, init) => {
      const url = String(input);
      if (url.endsWith('/api/auth/csrf')) {
        return Promise.resolve(jsonResponse({ token: 't', headerName: 'X-XSRF-TOKEN', parameterName: '_csrf' }));
      }
      if (url.endsWith('/api/auth/me')) {
        return Promise.resolve(
          jsonResponse({ status: 401, error: 'Unauthorized', message: '認証が必要です', path: '/api/auth/me', timestamp: '2026-01-01T00:00:00Z' }, 401),
        );
      }
      if (url.endsWith('/api/auth/register')) {
        return Promise.resolve(jsonResponse({ message: 'ok' }));
      }
      if (url.endsWith('/api/auth/resend-verification')) {
        resendCalledWithEmail = JSON.parse((init as RequestInit).body as string).email;
        return Promise.resolve(jsonResponse({ message: '対象のアカウントが確認できた場合、認証メールを送信します。' }));
      }
      throw new Error(`unexpected fetch: ${url}`);
    });

    const user = userEvent.setup();
    render(
      <MemoryRouter initialEntries={['/register']}>
        <AuthProvider>
          <RegisterPage />
        </AuthProvider>
      </MemoryRouter>,
    );

    await waitFor(() => expect(screen.getByRole('button', { name: '登録する' })).toBeEnabled());
    await user.type(screen.getByLabelText('氏名'), '山田太郎');
    await user.type(screen.getByLabelText('メールアドレス'), 'resend-from-register@example.com');
    await user.type(screen.getByLabelText('パスワード'), 'Passw0rd123!');
    await user.type(screen.getByLabelText('パスワード（確認）'), 'Passw0rd123!');
    await user.click(screen.getByRole('button', { name: '登録する' }));

    await user.click(await screen.findByRole('button', { name: '認証メールを再送' }));

    expect(await screen.findByText('対象のアカウントが確認できた場合、認証メールを送信します。')).toBeInTheDocument();
    expect(resendCalledWithEmail).toBe('resend-from-register@example.com');
  });

  it('確認メール案内画面の再送でサーバーエラーが返るとエラーメッセージを表示する', async () => {
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
      if (url.endsWith('/api/auth/register')) {
        return Promise.resolve(jsonResponse({ message: 'ok' }));
      }
      if (url.endsWith('/api/auth/resend-verification')) {
        return Promise.resolve(
          jsonResponse(
            { status: 400, error: 'Bad Request', message: 'メールアドレスの形式が正しくありません', path: '/api/auth/resend-verification', timestamp: '2026-01-01T00:00:00Z' },
            400,
          ),
        );
      }
      throw new Error(`unexpected fetch: ${url}`);
    });

    const user = userEvent.setup();
    render(
      <MemoryRouter initialEntries={['/register']}>
        <AuthProvider>
          <RegisterPage />
        </AuthProvider>
      </MemoryRouter>,
    );

    await waitFor(() => expect(screen.getByRole('button', { name: '登録する' })).toBeEnabled());
    await user.type(screen.getByLabelText('氏名'), '山田太郎');
    await user.type(screen.getByLabelText('メールアドレス'), 'resend-error@example.com');
    await user.type(screen.getByLabelText('パスワード'), 'Passw0rd123!');
    await user.type(screen.getByLabelText('パスワード（確認）'), 'Passw0rd123!');
    await user.click(screen.getByRole('button', { name: '登録する' }));

    await user.click(await screen.findByRole('button', { name: '認証メールを再送' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('メールアドレスの形式が正しくありません');
  });

  it.each([
    ['password1!', '大文字'],
    ['PASSWORD1!', '小文字'],
    ['Password!!', '数字'],
    ['Password1', '記号'],
    ['Pas1!', '8文字未満'],
  ])('パスワードが条件を満たさない場合(%s: %sなし等)はAPIを呼ばずエラーを表示する', async (weakPassword) => {
    const fetchMock = vi.mocked(fetch);
    mockUnauthenticatedInit(fetchMock);
    let registerCalled = false;
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
      if (url.endsWith('/api/auth/register')) {
        registerCalled = true;
        return Promise.resolve(jsonResponse({}, 201));
      }
      throw new Error(`unexpected fetch: ${url}`);
    });

    const user = userEvent.setup();
    render(
      <MemoryRouter initialEntries={['/register']}>
        <AuthProvider>
          <RegisterPage />
        </AuthProvider>
      </MemoryRouter>,
    );

    await waitFor(() => expect(screen.getByRole('button', { name: '登録する' })).toBeEnabled());
    await user.type(screen.getByLabelText('氏名'), '山田太郎');
    await user.type(screen.getByLabelText('メールアドレス'), 'weak-password@example.com');
    await user.type(screen.getByLabelText('パスワード'), weakPassword);
    await user.type(screen.getByLabelText('パスワード（確認）'), weakPassword);
    await user.click(screen.getByRole('button', { name: '登録する' }));

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'パスワードは8〜72文字で、大文字・小文字・数字・記号をそれぞれ1文字以上含めてください',
    );
    expect(registerCalled).toBe(false);
  });

  it('パスワードと確認パスワードが一致しない場合はAPIを呼ばずエラーを表示する', async () => {
    const fetchMock = vi.mocked(fetch);
    mockUnauthenticatedInit(fetchMock);
    let registerCalled = false;
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
      if (url.endsWith('/api/auth/register')) {
        registerCalled = true;
        return Promise.resolve(jsonResponse({}, 201));
      }
      throw new Error(`unexpected fetch: ${url}`);
    });

    const user = userEvent.setup();
    render(
      <MemoryRouter initialEntries={['/register']}>
        <AuthProvider>
          <RegisterPage />
        </AuthProvider>
      </MemoryRouter>,
    );

    await waitFor(() => expect(screen.getByRole('button', { name: '登録する' })).toBeEnabled());
    await user.type(screen.getByLabelText('氏名'), '山田太郎');
    await user.type(screen.getByLabelText('メールアドレス'), 'mismatch@example.com');
    await user.type(screen.getByLabelText('パスワード'), 'Passw0rd123!');
    await user.type(screen.getByLabelText('パスワード（確認）'), 'Passw0rd123?');
    await user.click(screen.getByRole('button', { name: '登録する' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('パスワードが一致しません');
    expect(registerCalled).toBe(false);
  });

  it('氏名・メールアドレス・パスワードの各入力欄に文字数制限が設定されている', async () => {
    const fetchMock = vi.mocked(fetch);
    mockUnauthenticatedInit(fetchMock);

    render(
      <MemoryRouter initialEntries={['/register']}>
        <AuthProvider>
          <RegisterPage />
        </AuthProvider>
      </MemoryRouter>,
    );

    await waitFor(() => expect(screen.getByRole('button', { name: '登録する' })).toBeEnabled());
    expect(screen.getByLabelText('氏名')).toHaveAttribute('maxLength', '30');
    expect(screen.getByLabelText('メールアドレス')).toHaveAttribute('maxLength', '100');
    expect(screen.getByLabelText('パスワード')).toHaveAttribute('maxLength', '72');
    expect(screen.getByLabelText('パスワード（確認）')).toHaveAttribute('maxLength', '72');
  });

  it('パスワード確認欄にもnew-passwordのautoCompleteが設定されている', async () => {
    const fetchMock = vi.mocked(fetch);
    mockUnauthenticatedInit(fetchMock);

    render(
      <MemoryRouter initialEntries={['/register']}>
        <AuthProvider>
          <RegisterPage />
        </AuthProvider>
      </MemoryRouter>,
    );

    await waitFor(() => expect(screen.getByRole('button', { name: '登録する' })).toBeEnabled());
    expect(screen.getByLabelText('パスワード（確認）')).toHaveAttribute('autoComplete', 'new-password');
    expect(screen.getByLabelText('パスワード（確認）')).toHaveAttribute('type', 'password');
  });
});
