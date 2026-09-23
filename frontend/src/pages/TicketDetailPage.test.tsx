import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { AuthProvider } from '../context/AuthContext';
import { ProtectedRoute } from '../routes/ProtectedRoute';
import { TicketDetailPage } from './TicketDetailPage';
import { csrfResponse, errorResponse, jsonResponse, testUser } from '../test-utils/apiMocks';
import type { TicketResponse } from '../types/ticket';

function renderDetailPage(path: string, fetchImpl: typeof fetch) {
  vi.stubGlobal('fetch', vi.fn(fetchImpl));

  return render(
    <MemoryRouter initialEntries={[path]}>
      <AuthProvider>
        <Routes>
          <Route path="/login" element={<p>ログイン画面</p>} />
          <Route element={<ProtectedRoute />}>
            <Route path="/tickets/:id" element={<TicketDetailPage />} />
          </Route>
        </Routes>
      </AuthProvider>
    </MemoryRouter>,
  );
}

function ticketResponse(overrides: Partial<TicketResponse> = {}): TicketResponse {
  return {
    id: 7,
    title: 'サンプルチケット',
    description: '内容説明',
    status: 'OPEN',
    priority: 'LOW',
    requesterId: 1,
    assigneeId: 2,
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-01T00:00:00Z',
    ...overrides,
  };
}

describe('TicketDetailPage', () => {
  beforeEach(() => {
    vi.stubGlobal('fetch', vi.fn());
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('正常に取得できた場合はチケット内容を表示する', async () => {
    renderDetailPage('/tickets/7', (input) => {
      const url = String(input);
      if (url.endsWith('/api/auth/csrf')) return Promise.resolve(csrfResponse());
      if (url.endsWith('/api/auth/me')) return Promise.resolve(jsonResponse(testUser({ role: 'ADMIN' })));
      if (url.endsWith('/api/tickets/7')) return Promise.resolve(jsonResponse(ticketResponse()));
      throw new Error(`unexpected fetch: ${url}`);
    });

    expect(await screen.findByText('サンプルチケット')).toBeInTheDocument();
    expect(screen.getByText('内容説明')).toBeInTheDocument();
  });

  it('存在しない・権限がないチケットは同じメッセージで404表示する', async () => {
    renderDetailPage('/tickets/999', (input) => {
      const url = String(input);
      if (url.endsWith('/api/auth/csrf')) return Promise.resolve(csrfResponse());
      if (url.endsWith('/api/auth/me')) return Promise.resolve(jsonResponse(testUser()));
      if (url.endsWith('/api/tickets/999')) return Promise.resolve(errorResponse(404, 'チケットが見つかりません', '/api/tickets/999'));
      throw new Error(`unexpected fetch: ${url}`);
    });

    expect(await screen.findByRole('alert')).toHaveTextContent('チケットが見つかりません');
  });

  it.each(['abc', '0', '-1', '1.5'])('不正なID(%s)ではgetTicketを呼ばずNotFoundになる', async (invalidId) => {
    const fetchMock = vi.fn((input: RequestInfo | URL) => {
      const url = String(input);
      if (url.endsWith('/api/auth/csrf')) return Promise.resolve(csrfResponse());
      if (url.endsWith('/api/auth/me')) return Promise.resolve(jsonResponse(testUser()));
      throw new Error(`unexpected fetch (should not be called for ticket): ${url}`);
    });
    vi.stubGlobal('fetch', fetchMock);

    render(
      <MemoryRouter initialEntries={[`/tickets/${invalidId}`]}>
        <AuthProvider>
          <Routes>
            <Route path="/login" element={<p>ログイン画面</p>} />
            <Route element={<ProtectedRoute />}>
              <Route path="/tickets/:id" element={<TicketDetailPage />} />
            </Route>
          </Routes>
        </AuthProvider>
      </MemoryRouter>,
    );

    expect(await screen.findByText('ページが見つかりません。')).toBeInTheDocument();
    const ticketCalls = fetchMock.mock.calls.filter(([input]) => String(input).includes(`/api/tickets/${invalidId}`));
    expect(ticketCalls).toHaveLength(0);
  });

  it('USERにはステータス変更フォームが表示されない', async () => {
    renderDetailPage('/tickets/7', (input) => {
      const url = String(input);
      if (url.endsWith('/api/auth/csrf')) return Promise.resolve(csrfResponse());
      if (url.endsWith('/api/auth/me')) return Promise.resolve(jsonResponse(testUser({ role: 'USER' })));
      if (url.endsWith('/api/tickets/7')) return Promise.resolve(jsonResponse(ticketResponse()));
      throw new Error(`unexpected fetch: ${url}`);
    });

    await screen.findByText('サンプルチケット');
    expect(screen.queryByLabelText('ステータス変更')).not.toBeInTheDocument();
  });

  it('AGENTには現在のステータスから遷移可能な選択肢だけが表示される', async () => {
    renderDetailPage('/tickets/7', (input) => {
      const url = String(input);
      if (url.endsWith('/api/auth/csrf')) return Promise.resolve(csrfResponse());
      if (url.endsWith('/api/auth/me')) return Promise.resolve(jsonResponse(testUser({ role: 'AGENT' })));
      if (url.endsWith('/api/tickets/7')) return Promise.resolve(jsonResponse(ticketResponse({ status: 'RESOLVED' })));
      throw new Error(`unexpected fetch: ${url}`);
    });

    await screen.findByText('サンプルチケット');
    const select = screen.getByLabelText('ステータス変更') as HTMLSelectElement;
    const optionValues = Array.from(select.options).map((option) => option.value);
    // RESOLVEDからの許可遷移はCLOSEDとIN_PROGRESSのみ(空選択肢込みで3つ)。
    expect(optionValues).toEqual(['', 'CLOSED', 'IN_PROGRESS']);
  });

  it('ステータス更新成功後はPATCHレスポンスの内容がそのまま画面へ反映される(再GETしない)', async () => {
    let getCalls = 0;
    let patchCalls = 0;
    renderDetailPage('/tickets/7', (input, init) => {
      const url = String(input);
      const method = (init && (init as RequestInit).method) ?? 'GET';
      if (url.endsWith('/api/auth/csrf')) return Promise.resolve(csrfResponse());
      if (url.endsWith('/api/auth/me')) return Promise.resolve(jsonResponse(testUser({ role: 'AGENT' })));
      if (url.endsWith('/api/tickets/7/status') && method === 'PATCH') {
        patchCalls += 1;
        return Promise.resolve(
          jsonResponse(ticketResponse({ status: 'IN_PROGRESS', updatedAt: '2026-02-02T00:00:00Z' })),
        );
      }
      if (url.endsWith('/api/tickets/7') && method === 'GET') {
        getCalls += 1;
        return Promise.resolve(jsonResponse(ticketResponse({ status: 'OPEN' })));
      }
      throw new Error(`unexpected fetch: ${url} ${method}`);
    });

    const user = userEvent.setup();
    await screen.findByText('サンプルチケット');
    expect(getCalls).toBe(1);

    await user.selectOptions(screen.getByLabelText('ステータス変更'), 'IN_PROGRESS');
    await user.click(screen.getByRole('button', { name: '更新する' }));

    await waitFor(() => expect(screen.getByText('対応中')).toBeInTheDocument());
    expect(screen.getByText('2026-02-02T00:00:00Z')).toBeInTheDocument();
    expect(patchCalls).toBe(1);
    expect(getCalls).toBe(1); // 再取得していないこと
  });

  it('不正な遷移(409)はエラーメッセージを表示する', async () => {
    renderDetailPage('/tickets/7', (input, init) => {
      const url = String(input);
      const method = (init && (init as RequestInit).method) ?? 'GET';
      if (url.endsWith('/api/auth/csrf')) return Promise.resolve(csrfResponse());
      if (url.endsWith('/api/auth/me')) return Promise.resolve(jsonResponse(testUser({ role: 'ADMIN' })));
      if (url.endsWith('/api/tickets/7/status') && method === 'PATCH') {
        return Promise.resolve(errorResponse(409, 'このステータスへは変更できません', '/api/tickets/7/status'));
      }
      if (url.endsWith('/api/tickets/7')) return Promise.resolve(jsonResponse(ticketResponse({ status: 'OPEN' })));
      throw new Error(`unexpected fetch: ${url} ${method}`);
    });

    const user = userEvent.setup();
    await screen.findByText('サンプルチケット');
    await user.selectOptions(screen.getByLabelText('ステータス変更'), 'IN_PROGRESS');
    await user.click(screen.getByRole('button', { name: '更新する' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('このステータスへは変更できません');
  });

  it('ステータス更新中に連続submitしてもPATCHは1回しか発行されない', async () => {
    let patchCalls = 0;
    renderDetailPage('/tickets/7', (input, init) => {
      const url = String(input);
      const method = (init && (init as RequestInit).method) ?? 'GET';
      if (url.endsWith('/api/auth/csrf')) return Promise.resolve(csrfResponse());
      if (url.endsWith('/api/auth/me')) return Promise.resolve(jsonResponse(testUser({ role: 'ADMIN' })));
      if (url.endsWith('/api/tickets/7/status') && method === 'PATCH') {
        patchCalls += 1;
        return new Promise<Response>((resolve) => {
          setTimeout(() => resolve(jsonResponse(ticketResponse({ status: 'IN_PROGRESS' }))), 30);
        });
      }
      if (url.endsWith('/api/tickets/7')) return Promise.resolve(jsonResponse(ticketResponse({ status: 'OPEN' })));
      throw new Error(`unexpected fetch: ${url} ${method}`);
    });

    const user = userEvent.setup();
    await screen.findByText('サンプルチケット');
    await user.selectOptions(screen.getByLabelText('ステータス変更'), 'IN_PROGRESS');

    const form = screen.getByRole('button', { name: '更新する' }).closest('form');
    if (!form) throw new Error('form not found');
    fireEvent.submit(form);
    fireEvent.submit(form);

    await waitFor(() => expect(screen.getByText('対応中')).toBeInTheDocument());
    expect(patchCalls).toBe(1);
  });

  it('チケット取得が401の場合は認証状態を破棄しログイン画面へ遷移する', async () => {
    renderDetailPage('/tickets/7', (input) => {
      const url = String(input);
      if (url.endsWith('/api/auth/csrf')) return Promise.resolve(csrfResponse());
      if (url.endsWith('/api/auth/me')) return Promise.resolve(jsonResponse(testUser()));
      if (url.endsWith('/api/tickets/7')) return Promise.resolve(errorResponse(401, '認証が必要です', '/api/tickets/7'));
      throw new Error(`unexpected fetch: ${url}`);
    });

    await waitFor(() => expect(screen.getByText('ログイン画面')).toBeInTheDocument());
  });
});
