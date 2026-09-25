import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { AuthProvider } from '../context/AuthContext';
import { ProtectedRoute } from '../routes/ProtectedRoute';
import { UserOnlyRoute } from '../routes/UserOnlyRoute';
import { TicketEditPage } from './TicketEditPage';
import { AppRoutes } from '../routes/AppRoutes';
import { csrfResponse, errorResponse, jsonResponse, testUser } from '../test-utils/apiMocks';
import type { TicketResponse } from '../types/ticket';

function renderEditPage(path: string, fetchImpl: typeof fetch) {
  vi.stubGlobal('fetch', vi.fn(fetchImpl));

  return render(
    <MemoryRouter initialEntries={[path]}>
      <AuthProvider>
        <Routes>
          <Route path="/login" element={<p>ログイン画面</p>} />
          <Route path="/tickets/:id" element={<p>チケット詳細</p>} />
          <Route element={<ProtectedRoute />}>
            <Route element={<UserOnlyRoute />}>
              <Route path="/tickets/:id/edit" element={<TicketEditPage />} />
            </Route>
          </Route>
        </Routes>
      </AuthProvider>
    </MemoryRouter>,
  );
}

function ticketResponse(overrides: Partial<TicketResponse> = {}): TicketResponse {
  return {
    id: 7,
    title: '編集前タイトル',
    description: '編集前内容',
    status: 'OPEN',
    priority: 'LOW',
    requesterId: 1,
    requesterName: '依頼太郎',
    assigneeId: null,
    assigneeName: null,
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-01T00:00:00Z',
    ...overrides,
  };
}

describe('TicketEditPage', () => {
  beforeEach(() => {
    vi.stubGlobal('fetch', vi.fn());
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('API取得結果がフォームの初期値として表示される', async () => {
    renderEditPage('/tickets/7/edit', (input) => {
      const url = String(input);
      if (url.endsWith('/api/auth/csrf')) return Promise.resolve(csrfResponse());
      if (url.endsWith('/api/auth/me')) return Promise.resolve(jsonResponse(testUser({ role: 'USER' })));
      if (url.endsWith('/api/tickets/7')) return Promise.resolve(jsonResponse(ticketResponse()));
      throw new Error(`unexpected fetch: ${url}`);
    });

    expect(await screen.findByLabelText('タイトル')).toHaveValue('編集前タイトル');
    expect(screen.getByLabelText('内容')).toHaveValue('編集前内容');
    expect(screen.getByLabelText('優先度')).toHaveValue('LOW');
  });

  it('更新成功後に詳細画面へ遷移する', async () => {
    let patchBody: string | undefined;
    renderEditPage('/tickets/7/edit', (input, init) => {
      const url = String(input);
      const method = (init && (init as RequestInit).method) ?? 'GET';
      if (url.endsWith('/api/auth/csrf')) return Promise.resolve(csrfResponse());
      if (url.endsWith('/api/auth/me')) return Promise.resolve(jsonResponse(testUser({ role: 'USER' })));
      if (url.endsWith('/api/tickets/7') && method === 'PATCH') {
        patchBody = (init as RequestInit).body as string;
        return Promise.resolve(jsonResponse(ticketResponse({ title: '修正後タイトル' })));
      }
      if (url.endsWith('/api/tickets/7') && method === 'GET') {
        return Promise.resolve(jsonResponse(ticketResponse()));
      }
      throw new Error(`unexpected fetch: ${url} ${method}`);
    });

    const user = userEvent.setup();
    await screen.findByLabelText('タイトル');
    await user.clear(screen.getByLabelText('タイトル'));
    await user.type(screen.getByLabelText('タイトル'), '修正後タイトル');
    await user.click(screen.getByRole('button', { name: '更新する' }));

    await waitFor(() => expect(screen.getByText('チケット詳細')).toBeInTheDocument());
    expect(patchBody).toBe(
      JSON.stringify({ title: '修正後タイトル', description: '編集前内容', priority: 'LOW' }),
    );
  });

  it('タイトルを空にするとバリデーションエラーを表示しPATCHを呼ばない', async () => {
    let patchCalled = false;
    renderEditPage('/tickets/7/edit', (input, init) => {
      const url = String(input);
      const method = (init && (init as RequestInit).method) ?? 'GET';
      if (url.endsWith('/api/auth/csrf')) return Promise.resolve(csrfResponse());
      if (url.endsWith('/api/auth/me')) return Promise.resolve(jsonResponse(testUser({ role: 'USER' })));
      if (url.endsWith('/api/tickets/7') && method === 'PATCH') {
        patchCalled = true;
        return Promise.resolve(jsonResponse(ticketResponse()));
      }
      if (url.endsWith('/api/tickets/7') && method === 'GET') {
        return Promise.resolve(jsonResponse(ticketResponse()));
      }
      throw new Error(`unexpected fetch: ${url} ${method}`);
    });

    const user = userEvent.setup();
    await screen.findByLabelText('タイトル');
    await user.clear(screen.getByLabelText('タイトル'));
    await user.click(screen.getByRole('button', { name: '更新する' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('タイトルと内容を入力してください');
    expect(patchCalled).toBe(false);
  });

  it.each([400, 403, 404, 409])('サーバーの%dエラーメッセージを表示する', async (status) => {
    renderEditPage('/tickets/7/edit', (input, init) => {
      const url = String(input);
      const method = (init && (init as RequestInit).method) ?? 'GET';
      if (url.endsWith('/api/auth/csrf')) return Promise.resolve(csrfResponse());
      if (url.endsWith('/api/auth/me')) return Promise.resolve(jsonResponse(testUser({ role: 'USER' })));
      if (url.endsWith('/api/tickets/7') && method === 'PATCH') {
        return Promise.resolve(errorResponse(status, `エラー${status}`, '/api/tickets/7'));
      }
      if (url.endsWith('/api/tickets/7') && method === 'GET') {
        return Promise.resolve(jsonResponse(ticketResponse()));
      }
      throw new Error(`unexpected fetch: ${url} ${method}`);
    });

    const user = userEvent.setup();
    await screen.findByLabelText('タイトル');
    await user.click(screen.getByRole('button', { name: '更新する' }));

    expect(await screen.findByRole('alert')).toHaveTextContent(`エラー${status}`);
  });

  it('更新APIが401の場合はセッションを破棄しログイン画面へ遷移する', async () => {
    renderEditPage('/tickets/7/edit', (input, init) => {
      const url = String(input);
      const method = (init && (init as RequestInit).method) ?? 'GET';
      if (url.endsWith('/api/auth/csrf')) return Promise.resolve(csrfResponse());
      if (url.endsWith('/api/auth/me')) return Promise.resolve(jsonResponse(testUser({ role: 'USER' })));
      if (url.endsWith('/api/tickets/7') && method === 'PATCH') {
        return Promise.resolve(errorResponse(401, '認証が必要です', '/api/tickets/7'));
      }
      if (url.endsWith('/api/tickets/7') && method === 'GET') {
        return Promise.resolve(jsonResponse(ticketResponse()));
      }
      throw new Error(`unexpected fetch: ${url} ${method}`);
    });

    const user = userEvent.setup();
    await screen.findByLabelText('タイトル');
    await user.click(screen.getByRole('button', { name: '更新する' }));

    await waitFor(() => expect(screen.getByText('ログイン画面')).toBeInTheDocument());
  });

  it.each(['abc', '0', '-1', '1.5'])('不正なID(%s)ではAPIを呼ばずNotFoundになる', async (invalidId) => {
    const fetchMock = vi.fn((input: RequestInfo | URL) => {
      const url = String(input);
      if (url.endsWith('/api/auth/csrf')) return Promise.resolve(csrfResponse());
      if (url.endsWith('/api/auth/me')) return Promise.resolve(jsonResponse(testUser({ role: 'USER' })));
      throw new Error(`unexpected fetch (should not be called): ${url}`);
    });
    vi.stubGlobal('fetch', fetchMock);

    render(
      <MemoryRouter initialEntries={[`/tickets/${invalidId}/edit`]}>
        <AuthProvider>
          <Routes>
            <Route path="/login" element={<p>ログイン画面</p>} />
            <Route element={<ProtectedRoute />}>
              <Route element={<UserOnlyRoute />}>
                <Route path="/tickets/:id/edit" element={<TicketEditPage />} />
              </Route>
            </Route>
          </Routes>
        </AuthProvider>
      </MemoryRouter>,
    );

    expect(await screen.findByText('ページが見つかりません。')).toBeInTheDocument();
    const ticketCalls = fetchMock.mock.calls.filter(([input]) => String(input).includes(`/api/tickets/${invalidId}`));
    expect(ticketCalls).toHaveLength(0);
  });

  it('連続submitしてもPATCHは1回しか発行されない', async () => {
    let patchCalls = 0;
    renderEditPage('/tickets/7/edit', (input, init) => {
      const url = String(input);
      const method = (init && (init as RequestInit).method) ?? 'GET';
      if (url.endsWith('/api/auth/csrf')) return Promise.resolve(csrfResponse());
      if (url.endsWith('/api/auth/me')) return Promise.resolve(jsonResponse(testUser({ role: 'USER' })));
      if (url.endsWith('/api/tickets/7') && method === 'PATCH') {
        patchCalls += 1;
        return new Promise<Response>((resolve) => {
          setTimeout(() => resolve(jsonResponse(ticketResponse())), 30);
        });
      }
      if (url.endsWith('/api/tickets/7') && method === 'GET') {
        return Promise.resolve(jsonResponse(ticketResponse()));
      }
      throw new Error(`unexpected fetch: ${url} ${method}`);
    });

    await screen.findByLabelText('タイトル');

    const form = screen.getByRole('button', { name: '更新する' }).closest('form');
    if (!form) throw new Error('form not found');
    fireEvent.submit(form);
    fireEvent.submit(form);

    await waitFor(() => expect(screen.getByText('チケット詳細')).toBeInTheDocument());
    expect(patchCalls).toBe(1);
  });

  it('送信中はボタンがdisabledになる', async () => {
    renderEditPage('/tickets/7/edit', (input, init) => {
      const url = String(input);
      const method = (init && (init as RequestInit).method) ?? 'GET';
      if (url.endsWith('/api/auth/csrf')) return Promise.resolve(csrfResponse());
      if (url.endsWith('/api/auth/me')) return Promise.resolve(jsonResponse(testUser({ role: 'USER' })));
      if (url.endsWith('/api/tickets/7') && method === 'PATCH') {
        return new Promise<Response>((resolve) => {
          setTimeout(() => resolve(jsonResponse(ticketResponse())), 30);
        });
      }
      if (url.endsWith('/api/tickets/7') && method === 'GET') {
        return Promise.resolve(jsonResponse(ticketResponse()));
      }
      throw new Error(`unexpected fetch: ${url} ${method}`);
    });

    const user = userEvent.setup();
    await screen.findByLabelText('タイトル');
    await user.click(screen.getByRole('button', { name: '更新する' }));

    expect(screen.getByRole('button', { name: '更新中...' })).toBeDisabled();
  });
});

describe('チケット編集画面のルーティング', () => {
  function renderAppAt(path: string, role: 'USER' | 'AGENT' | 'ADMIN' | 'unauthenticated') {
    vi.stubGlobal(
      'fetch',
      vi.fn((input: RequestInfo | URL) => {
        const url = String(input);
        if (url.endsWith('/api/auth/csrf')) return Promise.resolve(csrfResponse());
        if (url.endsWith('/api/auth/me')) {
          if (role === 'unauthenticated') {
            return Promise.resolve(errorResponse(401, '認証が必要です', '/api/auth/me'));
          }
          return Promise.resolve(jsonResponse(testUser({ role })));
        }
        if (url.includes('/api/tickets/')) {
          return Promise.resolve(
            jsonResponse({
              id: 7,
              title: 't',
              description: 'd',
              status: 'OPEN',
              priority: 'LOW',
              requesterId: 1,
              requesterName: '依頼太郎',
              assigneeId: null,
              assigneeName: null,
              createdAt: '',
              updatedAt: '',
            }),
          );
        }
        throw new Error(`unexpected fetch: ${url}`);
      }),
    );

    return render(
      <MemoryRouter initialEntries={[path]}>
        <AuthProvider>
          <AppRoutes />
        </AuthProvider>
      </MemoryRouter>,
    );
  }

  beforeEach(() => {
    vi.stubGlobal('fetch', vi.fn());
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('USERは編集画面へアクセスできる', async () => {
    renderAppAt('/tickets/7/edit', 'USER');
    expect(await screen.findByRole('heading', { name: 'チケット編集' })).toBeInTheDocument();
  });

  it.each(['AGENT', 'ADMIN'] as const)('%sはUserOnlyRouteによりチケット一覧へ戻される', async (role) => {
    renderAppAt('/tickets/7/edit', role);
    await waitFor(() => expect(screen.getByRole('heading', { name: 'チケット一覧' })).toBeInTheDocument());
  });

  it('未認証の場合はログイン画面へリダイレクトされる', async () => {
    renderAppAt('/tickets/7/edit', 'unauthenticated');
    await waitFor(() => expect(screen.getByRole('heading', { name: 'ログイン' })).toBeInTheDocument());
  });
});
