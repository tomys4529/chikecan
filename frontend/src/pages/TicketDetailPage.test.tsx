import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
import { AuthProvider } from '../context/AuthContext';
import { ProtectedRoute } from '../routes/ProtectedRoute';
import { TicketDetailPage } from './TicketDetailPage';
import { XpPanel } from '../components/XpPanel';
import { agentSummary, csrfResponse, errorResponse, jsonResponse, testUser } from '../test-utils/apiMocks';
import type { TicketResponse, TicketStatusUpdateResponse, XpAwardResult } from '../types/ticket';

// 「チケット一覧へ戻る」で実際にどのURL(クエリ込み)へ着地したかを検証するためのスタブ。
function TicketListStub() {
  const location = useLocation();
  return <p>チケット一覧画面:{location.pathname}{location.search}</p>;
}

function renderDetailPage(path: string | { pathname: string; state?: unknown }, fetchImpl: typeof fetch) {
  vi.stubGlobal('fetch', vi.fn(fetchImpl));

  return render(
    <MemoryRouter initialEntries={[path]}>
      <AuthProvider>
        <Routes>
          <Route path="/login" element={<p>ログイン画面</p>} />
          <Route path="/tickets" element={<TicketListStub />} />
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
    requesterName: '依頼太郎',
    assigneeId: 2,
    assigneeName: '担当花子',
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-01T00:00:00Z',
    ...overrides,
  };
}

function xpNotAwarded(): XpAwardResult {
  return { awarded: false, gainedExperience: 0, previousLevel: 0, currentLevel: 0, totalExperience: 0, levelUp: false };
}

function statusUpdateResponse(
  ticketOverrides: Partial<TicketResponse> = {},
  xpResult: XpAwardResult = xpNotAwarded(),
): TicketStatusUpdateResponse {
  return { ticket: ticketResponse(ticketOverrides), xpResult };
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
      if (url.endsWith('/api/admin/agents')) return Promise.resolve(jsonResponse([]));
      throw new Error(`unexpected fetch: ${url}`);
    });

    expect(await screen.findByText('サンプルチケット')).toBeInTheDocument();
    expect(screen.getByText('内容説明')).toBeInTheDocument();
  });

  it('依頼者名・担当者名が表示され「依頼者ID」というラベルは表示されない', async () => {
    renderDetailPage('/tickets/7', (input) => {
      const url = String(input);
      if (url.endsWith('/api/auth/csrf')) return Promise.resolve(csrfResponse());
      if (url.endsWith('/api/auth/me')) return Promise.resolve(jsonResponse(testUser({ role: 'ADMIN' })));
      if (url.endsWith('/api/tickets/7')) {
        return Promise.resolve(
          jsonResponse(ticketResponse({ requesterName: '山田太郎', assigneeName: '鈴木花子' })),
        );
      }
      if (url.endsWith('/api/admin/agents')) return Promise.resolve(jsonResponse([]));
      throw new Error(`unexpected fetch: ${url}`);
    });

    await screen.findByText('サンプルチケット');
    expect(screen.getByText('依頼者')).toBeInTheDocument();
    expect(screen.getByText('山田太郎')).toBeInTheDocument();
    expect(screen.getByText('鈴木花子')).toBeInTheDocument();
    expect(screen.queryByText('依頼者ID')).not.toBeInTheDocument();
  });

  it('担当者未設定時は「未割り当て」と表示される', async () => {
    renderDetailPage('/tickets/7', (input) => {
      const url = String(input);
      if (url.endsWith('/api/auth/csrf')) return Promise.resolve(csrfResponse());
      if (url.endsWith('/api/auth/me')) return Promise.resolve(jsonResponse(testUser({ role: 'USER' })));
      if (url.endsWith('/api/tickets/7')) {
        return Promise.resolve(
          jsonResponse(ticketResponse({ requesterName: '山田太郎', assigneeId: null, assigneeName: null })),
        );
      }
      throw new Error(`unexpected fetch: ${url}`);
    });

    await screen.findByText('サンプルチケット');
    expect(screen.getByText('未割り当て')).toBeInTheDocument();
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
          jsonResponse(statusUpdateResponse({ status: 'IN_PROGRESS', updatedAt: '2026-02-02T00:00:00Z' })),
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
      if (url.endsWith('/api/admin/agents')) return Promise.resolve(jsonResponse([]));
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
      if (url.endsWith('/api/admin/agents')) return Promise.resolve(jsonResponse([]));
      if (url.endsWith('/api/tickets/7/status') && method === 'PATCH') {
        patchCalls += 1;
        return new Promise<Response>((resolve) => {
          setTimeout(() => resolve(jsonResponse(statusUpdateResponse({ status: 'IN_PROGRESS' }))), 30);
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

  describe('編集リンク', () => {
    it('USER本人かつOPENの場合は「編集する」リンクが表示される', async () => {
      renderDetailPage('/tickets/7', (input) => {
        const url = String(input);
        if (url.endsWith('/api/auth/csrf')) return Promise.resolve(csrfResponse());
        if (url.endsWith('/api/auth/me')) return Promise.resolve(jsonResponse(testUser({ role: 'USER', id: 1 })));
        if (url.endsWith('/api/tickets/7')) {
          return Promise.resolve(jsonResponse(ticketResponse({ requesterId: 1, status: 'OPEN' })));
        }
        throw new Error(`unexpected fetch: ${url}`);
      });

      await screen.findByText('サンプルチケット');
      const editLink = await screen.findByRole('link', { name: '編集する' });
      expect(editLink).toHaveAttribute('href', '/tickets/7/edit');
    });

    it('AGENTには「編集する」リンクが表示されない', async () => {
      renderDetailPage('/tickets/7', (input) => {
        const url = String(input);
        if (url.endsWith('/api/auth/csrf')) return Promise.resolve(csrfResponse());
        if (url.endsWith('/api/auth/me')) return Promise.resolve(jsonResponse(testUser({ role: 'AGENT', id: 1 })));
        if (url.endsWith('/api/tickets/7')) {
          return Promise.resolve(jsonResponse(ticketResponse({ requesterId: 1, status: 'OPEN' })));
        }
        throw new Error(`unexpected fetch: ${url}`);
      });

      await screen.findByText('サンプルチケット');
      expect(screen.queryByRole('link', { name: '編集する' })).not.toBeInTheDocument();
    });

    it('ADMINには「編集する」リンクが表示されない', async () => {
      renderDetailPage('/tickets/7', (input) => {
        const url = String(input);
        if (url.endsWith('/api/auth/csrf')) return Promise.resolve(csrfResponse());
        if (url.endsWith('/api/auth/me')) return Promise.resolve(jsonResponse(testUser({ role: 'ADMIN', id: 1 })));
        if (url.endsWith('/api/tickets/7')) {
          return Promise.resolve(jsonResponse(ticketResponse({ requesterId: 1, status: 'OPEN' })));
        }
        if (url.endsWith('/api/admin/agents')) return Promise.resolve(jsonResponse([]));
        throw new Error(`unexpected fetch: ${url}`);
      });

      await screen.findByText('サンプルチケット');
      expect(screen.queryByRole('link', { name: '編集する' })).not.toBeInTheDocument();
    });

    it('OPEN以外のチケットでは「編集する」リンクが表示されない', async () => {
      renderDetailPage('/tickets/7', (input) => {
        const url = String(input);
        if (url.endsWith('/api/auth/csrf')) return Promise.resolve(csrfResponse());
        if (url.endsWith('/api/auth/me')) return Promise.resolve(jsonResponse(testUser({ role: 'USER', id: 1 })));
        if (url.endsWith('/api/tickets/7')) {
          return Promise.resolve(jsonResponse(ticketResponse({ requesterId: 1, status: 'IN_PROGRESS' })));
        }
        throw new Error(`unexpected fetch: ${url}`);
      });

      await screen.findByText('サンプルチケット');
      expect(screen.queryByRole('link', { name: '編集する' })).not.toBeInTheDocument();
    });

    it('他人が発行したチケットでは「編集する」リンクが表示されない', async () => {
      renderDetailPage('/tickets/7', (input) => {
        const url = String(input);
        if (url.endsWith('/api/auth/csrf')) return Promise.resolve(csrfResponse());
        if (url.endsWith('/api/auth/me')) return Promise.resolve(jsonResponse(testUser({ role: 'USER', id: 1 })));
        if (url.endsWith('/api/tickets/7')) {
          return Promise.resolve(jsonResponse(ticketResponse({ requesterId: 999, status: 'OPEN' })));
        }
        throw new Error(`unexpected fetch: ${url}`);
      });

      await screen.findByText('サンプルチケット');
      expect(screen.queryByRole('link', { name: '編集する' })).not.toBeInTheDocument();
    });
  });

  it('依頼者名・担当者名が表示され「依頼者ID」というラベルは表示されない', async () => {
    renderDetailPage('/tickets/7', (input) => {
      const url = String(input);
      if (url.endsWith('/api/auth/csrf')) return Promise.resolve(csrfResponse());
      if (url.endsWith('/api/auth/me')) return Promise.resolve(jsonResponse(testUser({ role: 'ADMIN' })));
      if (url.endsWith('/api/tickets/7')) {
        return Promise.resolve(
          jsonResponse(ticketResponse({ requesterName: '山田太郎', assigneeName: '鈴木花子' })),
        );
      }
      if (url.endsWith('/api/admin/agents')) return Promise.resolve(jsonResponse([]));
      throw new Error(`unexpected fetch: ${url}`);
    });

    await screen.findByText('サンプルチケット');
    expect(screen.getByText('依頼者')).toBeInTheDocument();
    expect(screen.getByText('山田太郎')).toBeInTheDocument();
    expect(screen.getByText('鈴木花子')).toBeInTheDocument();
    expect(screen.queryByText('依頼者ID')).not.toBeInTheDocument();
  });

  it('担当者未設定時は「未割り当て」と表示される', async () => {
    renderDetailPage('/tickets/7', (input) => {
      const url = String(input);
      if (url.endsWith('/api/auth/csrf')) return Promise.resolve(csrfResponse());
      if (url.endsWith('/api/auth/me')) return Promise.resolve(jsonResponse(testUser({ role: 'USER' })));
      if (url.endsWith('/api/tickets/7')) {
        return Promise.resolve(
          jsonResponse(ticketResponse({ requesterName: '山田太郎', assigneeId: null, assigneeName: null })),
        );
      }
      throw new Error(`unexpected fetch: ${url}`);
    });

    await screen.findByText('サンプルチケット');
    expect(screen.getByText('未割り当て')).toBeInTheDocument();
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

  describe('担当者設定(ADMIN)', () => {
    it('ADMINには担当者設定フォームが表示されAGENT候補がselectの選択肢になる', async () => {
      renderDetailPage('/tickets/7', (input) => {
        const url = String(input);
        if (url.endsWith('/api/auth/csrf')) return Promise.resolve(csrfResponse());
        if (url.endsWith('/api/auth/me')) return Promise.resolve(jsonResponse(testUser({ role: 'ADMIN' })));
        if (url.endsWith('/api/tickets/7')) return Promise.resolve(jsonResponse(ticketResponse({ assigneeId: null })));
        if (url.endsWith('/api/admin/agents')) {
          return Promise.resolve(jsonResponse([agentSummary({ id: 3, name: '鈴木一郎' })]));
        }
        throw new Error(`unexpected fetch: ${url}`);
      });

      await screen.findByText('サンプルチケット');
      const select = (await screen.findByLabelText('担当者設定')) as HTMLSelectElement;
      const optionLabels = Array.from(select.options).map((option) => option.textContent);
      expect(optionLabels).toEqual(['未割り当て', '鈴木一郎']);
    });

    it.each(['USER', 'AGENT'] as const)('%sには担当者設定フォームが表示されずAGENT一覧も取得されない', async (role) => {
      renderDetailPage('/tickets/7', (input) => {
        const url = String(input);
        if (url.endsWith('/api/auth/csrf')) return Promise.resolve(csrfResponse());
        if (url.endsWith('/api/auth/me')) return Promise.resolve(jsonResponse(testUser({ role })));
        if (url.endsWith('/api/tickets/7')) return Promise.resolve(jsonResponse(ticketResponse()));
        if (url.endsWith('/api/admin/agents')) throw new Error('AGENT一覧は呼ばれないはず');
        throw new Error(`unexpected fetch: ${url}`);
      });

      await screen.findByText('サンプルチケット');
      expect(screen.queryByLabelText('担当者設定')).not.toBeInTheDocument();
    });

    it('現在の担当者がselectの初期値として選択されている', async () => {
      renderDetailPage('/tickets/7', (input) => {
        const url = String(input);
        if (url.endsWith('/api/auth/csrf')) return Promise.resolve(csrfResponse());
        if (url.endsWith('/api/auth/me')) return Promise.resolve(jsonResponse(testUser({ role: 'ADMIN' })));
        if (url.endsWith('/api/tickets/7')) return Promise.resolve(jsonResponse(ticketResponse({ assigneeId: 4 })));
        if (url.endsWith('/api/admin/agents')) {
          return Promise.resolve(jsonResponse([agentSummary({ id: 4, name: '担当花子' })]));
        }
        throw new Error(`unexpected fetch: ${url}`);
      });

      await screen.findByText('サンプルチケット');
      const select = (await screen.findByLabelText('担当者設定')) as HTMLSelectElement;
      await waitFor(() => expect(select.value).toBe('4'));
    });

    it('未割り当てを選択して送信するとassigneeId:nullで送信され担当者表示が更新される(再GETしない)', async () => {
      let getCalls = 0;
      let patchCalls = 0;
      let patchBody: string | undefined;
      renderDetailPage('/tickets/7', (input, init) => {
        const url = String(input);
        const method = (init && (init as RequestInit).method) ?? 'GET';
        if (url.endsWith('/api/auth/csrf')) return Promise.resolve(csrfResponse());
        if (url.endsWith('/api/auth/me')) return Promise.resolve(jsonResponse(testUser({ role: 'ADMIN' })));
        if (url.endsWith('/api/admin/agents')) return Promise.resolve(jsonResponse([agentSummary({ id: 2 })]));
        if (url.endsWith('/api/tickets/7/assignee') && method === 'PATCH') {
          patchCalls += 1;
          patchBody = (init as RequestInit).body as string;
          return Promise.resolve(jsonResponse(ticketResponse({ assigneeId: null })));
        }
        if (url.endsWith('/api/tickets/7') && method === 'GET') {
          getCalls += 1;
          return Promise.resolve(jsonResponse(ticketResponse({ assigneeId: 2 })));
        }
        throw new Error(`unexpected fetch: ${url} ${method}`);
      });

      const user = userEvent.setup();
      await screen.findByText('サンプルチケット');
      const select = await screen.findByLabelText('担当者設定');
      expect(getCalls).toBe(1);

      await user.selectOptions(select, '');
      await user.click(screen.getByRole('button', { name: '担当者を更新する' }));

      await waitFor(() => expect(patchCalls).toBe(1));
      expect(patchBody).toBe(JSON.stringify({ assigneeId: null }));
      expect(getCalls).toBe(1);
    });

    it('AGENTを選択して送信すると正しいIDで送信され担当者表示が更新される', async () => {
      let patchBody: string | undefined;
      renderDetailPage('/tickets/7', (input, init) => {
        const url = String(input);
        const method = (init && (init as RequestInit).method) ?? 'GET';
        if (url.endsWith('/api/auth/csrf')) return Promise.resolve(csrfResponse());
        if (url.endsWith('/api/auth/me')) return Promise.resolve(jsonResponse(testUser({ role: 'ADMIN' })));
        if (url.endsWith('/api/admin/agents')) {
          return Promise.resolve(jsonResponse([agentSummary({ id: 9, name: '鈴木一郎' })]));
        }
        if (url.endsWith('/api/tickets/7/assignee') && method === 'PATCH') {
          patchBody = (init as RequestInit).body as string;
          return Promise.resolve(jsonResponse(ticketResponse({ assigneeId: 9 })));
        }
        if (url.endsWith('/api/tickets/7') && method === 'GET') {
          return Promise.resolve(jsonResponse(ticketResponse({ assigneeId: null })));
        }
        throw new Error(`unexpected fetch: ${url} ${method}`);
      });

      const user = userEvent.setup();
      await screen.findByText('サンプルチケット');
      await user.selectOptions(await screen.findByLabelText('担当者設定'), '9');
      await user.click(screen.getByRole('button', { name: '担当者を更新する' }));

      await waitFor(() => expect(patchBody).toBe(JSON.stringify({ assigneeId: 9 })));
    });

    it('担当者更新の404はErrorMessageで表示される', async () => {
      renderDetailPage('/tickets/7', (input, init) => {
        const url = String(input);
        const method = (init && (init as RequestInit).method) ?? 'GET';
        if (url.endsWith('/api/auth/csrf')) return Promise.resolve(csrfResponse());
        if (url.endsWith('/api/auth/me')) return Promise.resolve(jsonResponse(testUser({ role: 'ADMIN' })));
        if (url.endsWith('/api/admin/agents')) return Promise.resolve(jsonResponse([agentSummary({ id: 2 })]));
        if (url.endsWith('/api/tickets/7/assignee') && method === 'PATCH') {
          return Promise.resolve(errorResponse(404, 'チケットが見つかりません', '/api/tickets/7/assignee'));
        }
        if (url.endsWith('/api/tickets/7') && method === 'GET') {
          return Promise.resolve(jsonResponse(ticketResponse({ assigneeId: null })));
        }
        throw new Error(`unexpected fetch: ${url} ${method}`);
      });

      const user = userEvent.setup();
      await screen.findByText('サンプルチケット');
      await user.selectOptions(await screen.findByLabelText('担当者設定'), '2');
      await user.click(screen.getByRole('button', { name: '担当者を更新する' }));

      expect(await screen.findByRole('alert')).toHaveTextContent('チケットが見つかりません');
    });

    it('担当者更新が401の場合は認証状態を破棄しログイン画面へ遷移する', async () => {
      renderDetailPage('/tickets/7', (input, init) => {
        const url = String(input);
        const method = (init && (init as RequestInit).method) ?? 'GET';
        if (url.endsWith('/api/auth/csrf')) return Promise.resolve(csrfResponse());
        if (url.endsWith('/api/auth/me')) return Promise.resolve(jsonResponse(testUser({ role: 'ADMIN' })));
        if (url.endsWith('/api/admin/agents')) return Promise.resolve(jsonResponse([agentSummary({ id: 2 })]));
        if (url.endsWith('/api/tickets/7/assignee') && method === 'PATCH') {
          return Promise.resolve(errorResponse(401, '認証が必要です', '/api/tickets/7/assignee'));
        }
        if (url.endsWith('/api/tickets/7') && method === 'GET') {
          return Promise.resolve(jsonResponse(ticketResponse({ assigneeId: null })));
        }
        throw new Error(`unexpected fetch: ${url} ${method}`);
      });

      const user = userEvent.setup();
      await screen.findByText('サンプルチケット');
      await user.selectOptions(await screen.findByLabelText('担当者設定'), '2');
      await user.click(screen.getByRole('button', { name: '担当者を更新する' }));

      await waitFor(() => expect(screen.getByText('ログイン画面')).toBeInTheDocument());
    });

    it('担当者更新中に連続submitしてもPATCHは1回しか発行されない', async () => {
      let patchCalls = 0;
      renderDetailPage('/tickets/7', (input, init) => {
        const url = String(input);
        const method = (init && (init as RequestInit).method) ?? 'GET';
        if (url.endsWith('/api/auth/csrf')) return Promise.resolve(csrfResponse());
        if (url.endsWith('/api/auth/me')) return Promise.resolve(jsonResponse(testUser({ role: 'ADMIN' })));
        if (url.endsWith('/api/admin/agents')) return Promise.resolve(jsonResponse([agentSummary({ id: 2 })]));
        if (url.endsWith('/api/tickets/7/assignee') && method === 'PATCH') {
          patchCalls += 1;
          return new Promise<Response>((resolve) => {
            setTimeout(() => resolve(jsonResponse(ticketResponse({ assigneeId: 2 }))), 30);
          });
        }
        if (url.endsWith('/api/tickets/7') && method === 'GET') {
          return Promise.resolve(jsonResponse(ticketResponse({ assigneeId: null })));
        }
        throw new Error(`unexpected fetch: ${url} ${method}`);
      });

      const user = userEvent.setup();
      await screen.findByText('サンプルチケット');
      await user.selectOptions(await screen.findByLabelText('担当者設定'), '2');

      const form = screen.getByRole('button', { name: '担当者を更新する' }).closest('form');
      if (!form) throw new Error('form not found');
      fireEvent.submit(form);
      fireEvent.submit(form);

      await waitFor(() => expect(patchCalls).toBe(1));
    });

    it('AGENT一覧の取得に失敗してもページ全体はクラッシュせずエラー表示と操作不可に留まる', async () => {
      renderDetailPage('/tickets/7', (input) => {
        const url = String(input);
        if (url.endsWith('/api/auth/csrf')) return Promise.resolve(csrfResponse());
        if (url.endsWith('/api/auth/me')) return Promise.resolve(jsonResponse(testUser({ role: 'ADMIN' })));
        if (url.endsWith('/api/tickets/7')) return Promise.resolve(jsonResponse(ticketResponse({ assigneeId: null })));
        if (url.endsWith('/api/admin/agents')) {
          return Promise.resolve(errorResponse(500, 'サーバー内部でエラーが発生しました', '/api/admin/agents'));
        }
        throw new Error(`unexpected fetch: ${url}`);
      });

      await screen.findByText('サンプルチケット');
      expect(await screen.findByRole('alert')).toHaveTextContent('サーバー内部でエラーが発生しました');
      const select = screen.getByLabelText('担当者設定') as HTMLSelectElement;
      expect(select).toBeDisabled();
    });

    it('現在の担当者がAGENT候補一覧に存在しない場合、選択不可のoptionで現状を示す', async () => {
      renderDetailPage('/tickets/7', (input) => {
        const url = String(input);
        if (url.endsWith('/api/auth/csrf')) return Promise.resolve(csrfResponse());
        if (url.endsWith('/api/auth/me')) return Promise.resolve(jsonResponse(testUser({ role: 'ADMIN' })));
        if (url.endsWith('/api/tickets/7')) return Promise.resolve(jsonResponse(ticketResponse({ assigneeId: 99 })));
        if (url.endsWith('/api/admin/agents')) return Promise.resolve(jsonResponse([agentSummary({ id: 2 })]));
        throw new Error(`unexpected fetch: ${url}`);
      });

      await screen.findByText('サンプルチケット');
      const select = (await screen.findByLabelText('担当者設定')) as HTMLSelectElement;
      await waitFor(() => expect(select.value).toBe('99'));
      const missingOption = Array.from(select.options).find((option) => option.value === '99');
      expect(missingOption).toBeDefined();
      expect(missingOption?.disabled).toBe(true);
      expect(missingOption?.textContent).toContain('選択不可');
    });

    it('現在の担当者が候補一覧に存在しないだけでは担当解除PATCHは自動送信されない', async () => {
      let patchCalls = 0;
      renderDetailPage('/tickets/7', (input, init) => {
        const url = String(input);
        const method = (init && (init as RequestInit).method) ?? 'GET';
        if (url.endsWith('/api/auth/csrf')) return Promise.resolve(csrfResponse());
        if (url.endsWith('/api/auth/me')) return Promise.resolve(jsonResponse(testUser({ role: 'ADMIN' })));
        if (url.endsWith('/api/tickets/7') && method === 'GET') {
          return Promise.resolve(jsonResponse(ticketResponse({ assigneeId: 99 })));
        }
        if (url.endsWith('/api/admin/agents')) return Promise.resolve(jsonResponse([agentSummary({ id: 2 })]));
        if (url.endsWith('/api/tickets/7/assignee')) {
          patchCalls += 1;
          return Promise.resolve(jsonResponse(ticketResponse({ assigneeId: null })));
        }
        throw new Error(`unexpected fetch: ${url} ${method}`);
      });

      await screen.findByText('サンプルチケット');
      await screen.findByLabelText('担当者設定');
      await new Promise((resolve) => setTimeout(resolve, 50));
      expect(patchCalls).toBe(0);
    });

    it('担当者を変更していない場合、更新ボタンはdisabledでPATCHされない', async () => {
      renderDetailPage('/tickets/7', (input) => {
        const url = String(input);
        if (url.endsWith('/api/auth/csrf')) return Promise.resolve(csrfResponse());
        if (url.endsWith('/api/auth/me')) return Promise.resolve(jsonResponse(testUser({ role: 'ADMIN' })));
        if (url.endsWith('/api/tickets/7')) return Promise.resolve(jsonResponse(ticketResponse({ assigneeId: 2 })));
        if (url.endsWith('/api/admin/agents')) return Promise.resolve(jsonResponse([agentSummary({ id: 2 })]));
        throw new Error(`unexpected fetch: ${url}`);
      });

      await screen.findByText('サンプルチケット');
      await screen.findByLabelText('担当者設定');
      const button = await screen.findByRole('button', { name: '担当者を更新する' });
      await waitFor(() => expect(button).toBeDisabled());
    });

    it('AGENT一覧読み込み中はselectとボタンが操作できない', async () => {
      let resolveAgents: (value: Response) => void = () => {};
      const agentsPromise = new Promise<Response>((resolve) => {
        resolveAgents = resolve;
      });
      renderDetailPage('/tickets/7', (input) => {
        const url = String(input);
        if (url.endsWith('/api/auth/csrf')) return Promise.resolve(csrfResponse());
        if (url.endsWith('/api/auth/me')) return Promise.resolve(jsonResponse(testUser({ role: 'ADMIN' })));
        if (url.endsWith('/api/tickets/7')) return Promise.resolve(jsonResponse(ticketResponse({ assigneeId: null })));
        if (url.endsWith('/api/admin/agents')) return agentsPromise;
        throw new Error(`unexpected fetch: ${url}`);
      });

      await screen.findByText('サンプルチケット');
      const select = (await screen.findByLabelText('担当者設定')) as HTMLSelectElement;
      expect(select).toBeDisabled();
      expect(screen.getByRole('button', { name: '担当者を更新する' })).toBeDisabled();

      resolveAgents(jsonResponse([agentSummary({ id: 2 })]));
      await waitFor(() => expect(select).not.toBeDisabled());
    });

    it('AGENT候補が0件でも明示的な担当解除ができる', async () => {
      let patchBody: string | undefined;
      renderDetailPage('/tickets/7', (input, init) => {
        const url = String(input);
        const method = (init && (init as RequestInit).method) ?? 'GET';
        if (url.endsWith('/api/auth/csrf')) return Promise.resolve(csrfResponse());
        if (url.endsWith('/api/auth/me')) return Promise.resolve(jsonResponse(testUser({ role: 'ADMIN' })));
        if (url.endsWith('/api/admin/agents')) return Promise.resolve(jsonResponse([]));
        if (url.endsWith('/api/tickets/7/assignee') && method === 'PATCH') {
          patchBody = (init as RequestInit).body as string;
          return Promise.resolve(jsonResponse(ticketResponse({ assigneeId: null })));
        }
        if (url.endsWith('/api/tickets/7') && method === 'GET') {
          return Promise.resolve(jsonResponse(ticketResponse({ assigneeId: 2 })));
        }
        throw new Error(`unexpected fetch: ${url} ${method}`);
      });

      const user = userEvent.setup();
      await screen.findByText('サンプルチケット');
      const select = (await screen.findByLabelText('担当者設定')) as HTMLSelectElement;
      await waitFor(() => expect(select).not.toBeDisabled());
      await user.selectOptions(select, '');
      await user.click(screen.getByRole('button', { name: '担当者を更新する' }));

      await waitFor(() => expect(patchBody).toBe(JSON.stringify({ assigneeId: null })));
    });
  });

  describe('XP獲得演出', () => {
    afterEach(() => {
      vi.useRealTimers();
    });

    function mockResolveWithXp(xpResult: XpAwardResult) {
      return (input: RequestInfo | URL, init?: RequestInit) => {
        const url = String(input);
        const method = (init && init.method) ?? 'GET';
        if (url.endsWith('/api/auth/csrf')) return Promise.resolve(csrfResponse());
        if (url.endsWith('/api/auth/me')) return Promise.resolve(jsonResponse(testUser({ role: 'AGENT' })));
        if (url.endsWith('/api/tickets/7/status') && method === 'PATCH') {
          return Promise.resolve(jsonResponse(statusUpdateResponse({ status: 'RESOLVED' }, xpResult)));
        }
        if (url.endsWith('/api/tickets/7')) {
          return Promise.resolve(jsonResponse(ticketResponse({ status: 'IN_PROGRESS' })));
        }
        throw new Error(`unexpected fetch: ${url} ${method}`);
      };
    }

    async function resolveTicket(user: ReturnType<typeof userEvent.setup> = userEvent.setup()) {
      await screen.findByText('サンプルチケット');
      await user.selectOptions(screen.getByLabelText('ステータス変更'), 'RESOLVED');
      await user.click(screen.getByRole('button', { name: '更新する' }));
    }

    it('通常のXP獲得時は+30XPが表示されLEVEL UPは表示されない', async () => {
      renderDetailPage(
        '/tickets/7',
        mockResolveWithXp({ awarded: true, gainedExperience: 30, previousLevel: 1, currentLevel: 1, totalExperience: 30, levelUp: false }),
      );

      await resolveTicket();

      expect(await screen.findByText('+30 XP')).toBeInTheDocument();
      expect(screen.queryByText('LEVEL UP!')).not.toBeInTheDocument();
    });

    it('レベルアップ時はLEVEL UP!とおめでとう!が表示される', async () => {
      renderDetailPage(
        '/tickets/7',
        mockResolveWithXp({ awarded: true, gainedExperience: 30, previousLevel: 1, currentLevel: 2, totalExperience: 100, levelUp: true }),
      );

      await resolveTicket();

      expect(await screen.findByText('LEVEL UP!')).toBeInTheDocument();
      expect(screen.getByText('おめでとう!')).toBeInTheDocument();
      expect(screen.getByText('Level 2')).toBeInTheDocument();
      expect(screen.getByText('+30 XP')).toBeInTheDocument();
    });

    it('通常獲得とレベルアップでは適用されるクラスが異なる', async () => {
      renderDetailPage(
        '/tickets/7',
        mockResolveWithXp({ awarded: true, gainedExperience: 10, previousLevel: 1, currentLevel: 1, totalExperience: 10, levelUp: false }),
      );

      await resolveTicket();
      const normalCelebration = (await screen.findByText('+10 XP')).closest('.xp-celebration');
      expect(normalCelebration).toHaveClass('xp-celebration--normal');
      expect(normalCelebration).not.toHaveClass('xp-celebration--level-up');
    });

    it('XP非付与時は演出が表示されない', async () => {
      renderDetailPage(
        '/tickets/7',
        mockResolveWithXp({ awarded: false, gainedExperience: 0, previousLevel: 0, currentLevel: 0, totalExperience: 0, levelUp: false }),
      );

      await resolveTicket();

      await waitFor(() => expect(screen.getByText('解決済み')).toBeInTheDocument());
      expect(screen.queryByText(/XP$/)).not.toBeInTheDocument();
    });

    it('約3秒後に演出が自動的に消える', async () => {
      vi.useFakeTimers({ shouldAdvanceTime: true });
      const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
      renderDetailPage(
        '/tickets/7',
        mockResolveWithXp({ awarded: true, gainedExperience: 20, previousLevel: 1, currentLevel: 1, totalExperience: 20, levelUp: false }),
      );

      await resolveTicket(user);
      expect(await screen.findByText('+20 XP')).toBeInTheDocument();

      await act(async () => {
        await vi.advanceTimersByTimeAsync(3000);
      });

      expect(screen.queryByText('+20 XP')).not.toBeInTheDocument();
    });

    it('演出が消えた後に再レンダリングしても演出は再表示されない', async () => {
      vi.useFakeTimers({ shouldAdvanceTime: true });
      const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
      const view = renderDetailPage(
        '/tickets/7',
        mockResolveWithXp({ awarded: true, gainedExperience: 20, previousLevel: 1, currentLevel: 1, totalExperience: 20, levelUp: false }),
      );

      await resolveTicket(user);
      expect(await screen.findByText('+20 XP')).toBeInTheDocument();

      await act(async () => {
        await vi.advanceTimersByTimeAsync(3000);
      });
      expect(screen.queryByText('+20 XP')).not.toBeInTheDocument();

      // propsを変えずに再レンダリングを強制しても、演出は再表示されない。
      view.rerender(
        <MemoryRouter initialEntries={['/tickets/7']}>
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

      expect(screen.queryByText('+20 XP')).not.toBeInTheDocument();
    });

    it('XP獲得後にXPパネルが最新値へ更新される', async () => {
      vi.stubGlobal(
        'fetch',
        vi.fn(
          mockResolveWithXp({ awarded: true, gainedExperience: 30, previousLevel: 1, currentLevel: 1, totalExperience: 30, levelUp: false }),
        ),
      );

      render(
        <MemoryRouter initialEntries={['/tickets/7']}>
          <AuthProvider>
            <XpPanel />
            <Routes>
              <Route path="/login" element={<p>ログイン画面</p>} />
              <Route element={<ProtectedRoute />}>
                <Route path="/tickets/:id" element={<TicketDetailPage />} />
              </Route>
            </Routes>
          </AuthProvider>
        </MemoryRouter>,
      );

      await screen.findByText('累計 0 XP');
      await resolveTicket();

      await waitFor(() => expect(screen.getByText('累計 30 XP')).toBeInTheDocument());
    });
  });

  describe('チケット一覧へ戻るリンク', () => {
    function renderWithRole(
      role: 'USER' | 'AGENT' | 'ADMIN',
      ticketOverrides: Partial<TicketResponse> = {},
    ) {
      return renderDetailPage('/tickets/7', (input) => {
        const url = String(input);
        if (url.endsWith('/api/auth/csrf')) return Promise.resolve(csrfResponse());
        if (url.endsWith('/api/auth/me')) return Promise.resolve(jsonResponse(testUser({ role })));
        if (url.endsWith('/api/tickets/7')) return Promise.resolve(jsonResponse(ticketResponse(ticketOverrides)));
        if (url.endsWith('/api/admin/agents')) return Promise.resolve(jsonResponse([]));
        throw new Error(`unexpected fetch: ${url}`);
      });
    }

    it('USERの詳細画面に「チケット一覧へ戻る」が表示される', async () => {
      renderWithRole('USER');

      await screen.findByText('サンプルチケット');
      expect(screen.getByRole('link', { name: '← チケット一覧へ戻る' })).toBeInTheDocument();
    });

    it('AGENTの詳細画面に「チケット一覧へ戻る」が表示される', async () => {
      renderWithRole('AGENT');

      await screen.findByText('サンプルチケット');
      expect(screen.getByRole('link', { name: '← チケット一覧へ戻る' })).toBeInTheDocument();
    });

    it('ADMINの詳細画面に「チケット一覧へ戻る」が表示される', async () => {
      renderWithRole('ADMIN');

      await screen.findByText('サンプルチケット');
      expect(screen.getByRole('link', { name: '← チケット一覧へ戻る' })).toBeInTheDocument();
    });

    it('リンクの遷移先は/ticketsである', async () => {
      renderWithRole('ADMIN');

      await screen.findByText('サンプルチケット');
      const link = screen.getByRole('link', { name: '← チケット一覧へ戻る' });
      expect(link).toHaveAttribute('href', '/tickets');
    });

    it('OPEN以外のステータスのチケットでも表示される', async () => {
      renderWithRole('AGENT', { status: 'RESOLVED' });

      await screen.findByText('サンプルチケット');
      expect(screen.getByRole('link', { name: '← チケット一覧へ戻る' })).toBeInTheDocument();
    });

    it('担当者未設定のチケットでも表示される', async () => {
      renderWithRole('ADMIN', { assigneeId: null, assigneeName: null });

      await screen.findByText('サンプルチケット');
      expect(screen.getByRole('link', { name: '← チケット一覧へ戻る' })).toBeInTheDocument();
    });

    it('リンクをクリックするとチケット一覧画面へ遷移する', async () => {
      renderWithRole('USER');

      const user = userEvent.setup();
      await screen.findByText('サンプルチケット');
      await user.click(screen.getByRole('link', { name: '← チケット一覧へ戻る' }));

      expect(await screen.findByText('チケット一覧画面:/tickets')).toBeInTheDocument();
    });

    it('一覧の2ページ目から遷移してきた場合はそのページへ戻れる', async () => {
      renderDetailPage(
        { pathname: '/tickets/7', state: { from: '/tickets?page=3' } },
        (input) => {
          const url = String(input);
          if (url.endsWith('/api/auth/csrf')) return Promise.resolve(csrfResponse());
          if (url.endsWith('/api/auth/me')) return Promise.resolve(jsonResponse(testUser({ role: 'USER' })));
          if (url.endsWith('/api/tickets/7')) return Promise.resolve(jsonResponse(ticketResponse()));
          throw new Error(`unexpected fetch: ${url}`);
        },
      );

      await screen.findByText('サンプルチケット');
      const link = screen.getByRole('link', { name: '← チケット一覧へ戻る' });
      expect(link).toHaveAttribute('href', '/tickets?page=3');

      const user = userEvent.setup();
      await user.click(link);

      expect(await screen.findByText('チケット一覧画面:/tickets?page=3')).toBeInTheDocument();
    });

    it('stateが一覧URLの形式に一致しない場合は/ticketsへ戻る(不正な戻り先を信頼しない)', async () => {
      renderDetailPage(
        { pathname: '/tickets/7', state: { from: 'https://evil.example.com/' } },
        (input) => {
          const url = String(input);
          if (url.endsWith('/api/auth/csrf')) return Promise.resolve(csrfResponse());
          if (url.endsWith('/api/auth/me')) return Promise.resolve(jsonResponse(testUser({ role: 'USER' })));
          if (url.endsWith('/api/tickets/7')) return Promise.resolve(jsonResponse(ticketResponse()));
          throw new Error(`unexpected fetch: ${url}`);
        },
      );

      await screen.findByText('サンプルチケット');
      const link = screen.getByRole('link', { name: '← チケット一覧へ戻る' });
      expect(link).toHaveAttribute('href', '/tickets');
    });

    it('stateが無い(詳細URLへ直接アクセスした)場合は/ticketsへ戻る', async () => {
      renderDetailPage('/tickets/7', (input) => {
        const url = String(input);
        if (url.endsWith('/api/auth/csrf')) return Promise.resolve(csrfResponse());
        if (url.endsWith('/api/auth/me')) return Promise.resolve(jsonResponse(testUser({ role: 'USER' })));
        if (url.endsWith('/api/tickets/7')) return Promise.resolve(jsonResponse(ticketResponse()));
        throw new Error(`unexpected fetch: ${url}`);
      });

      await screen.findByText('サンプルチケット');
      const link = screen.getByRole('link', { name: '← チケット一覧へ戻る' });
      expect(link).toHaveAttribute('href', '/tickets');
    });
  });
});
