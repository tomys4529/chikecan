import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { AuthProvider } from '../context/AuthContext';
import { ProtectedRoute } from '../routes/ProtectedRoute';
import { TicketListPage } from './TicketListPage';
import { csrfResponse, errorResponse, jsonResponse, testUser } from '../test-utils/apiMocks';
import type { PageResponse, TicketResponse } from '../types/ticket';

function renderTicketList(fetchImpl: (input: RequestInfo | URL) => Promise<Response>, initialPath = '/tickets') {
  vi.stubGlobal('fetch', vi.fn(fetchImpl));

  return render(
    <MemoryRouter initialEntries={[initialPath]}>
      <AuthProvider>
        <Routes>
          <Route path="/login" element={<p>ログイン画面</p>} />
          <Route element={<ProtectedRoute />}>
            <Route path="/tickets" element={<TicketListPage />} />
            <Route path="/tickets/:id" element={<p>詳細画面</p>} />
          </Route>
        </Routes>
      </AuthProvider>
    </MemoryRouter>,
  );
}

function ticket(overrides: Partial<TicketResponse> = {}): TicketResponse {
  return {
    id: 1,
    title: 'サンプルチケット',
    description: '内容',
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

function ticketPage(overrides: Partial<PageResponse<TicketResponse>> = {}): PageResponse<TicketResponse> {
  return {
    content: [ticket()],
    page: 0,
    size: 20,
    totalElements: 1,
    totalPages: 1,
    first: true,
    last: true,
    ...overrides,
  };
}

function manyTickets(count: number, idOffset = 0): TicketResponse[] {
  return Array.from({ length: count }, (_, i) => ticket({ id: idOffset + i + 1, title: `チケット${idOffset + i + 1}` }));
}

describe('TicketListPage', () => {
  beforeEach(() => {
    vi.stubGlobal('fetch', vi.fn());
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('取得成功時に一覧を表示しUSERには新規登録リンクが出る', async () => {
    renderTicketList((input) => {
      const url = String(input);
      if (url.endsWith('/api/auth/csrf')) return Promise.resolve(csrfResponse());
      if (url.endsWith('/api/auth/me')) return Promise.resolve(jsonResponse(testUser({ role: 'USER' })));
      if (url.includes('/api/tickets?')) return Promise.resolve(jsonResponse(ticketPage()));
      throw new Error(`unexpected fetch: ${url}`);
    });

    expect(await screen.findByText('サンプルチケット')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: '新規登録' })).toBeInTheDocument();
  });

  it('AGENTには新規登録リンクが表示されない', async () => {
    renderTicketList((input) => {
      const url = String(input);
      if (url.endsWith('/api/auth/csrf')) return Promise.resolve(csrfResponse());
      if (url.endsWith('/api/auth/me')) return Promise.resolve(jsonResponse(testUser({ role: 'AGENT' })));
      if (url.includes('/api/tickets?')) return Promise.resolve(jsonResponse(ticketPage()));
      throw new Error(`unexpected fetch: ${url}`);
    });

    await screen.findByText('サンプルチケット');
    expect(screen.queryByRole('link', { name: '新規登録' })).not.toBeInTheDocument();
  });

  it('0件の場合は該当なしメッセージを表示する', async () => {
    renderTicketList((input) => {
      const url = String(input);
      if (url.endsWith('/api/auth/csrf')) return Promise.resolve(csrfResponse());
      if (url.endsWith('/api/auth/me')) return Promise.resolve(jsonResponse(testUser()));
      if (url.includes('/api/tickets?')) {
        return Promise.resolve(
          jsonResponse(ticketPage({ content: [], totalElements: 0, totalPages: 0, first: true, last: true })),
        );
      }
      throw new Error(`unexpected fetch: ${url}`);
    });

    expect(await screen.findByText('チケットがありません。')).toBeInTheDocument();
  });

  it('チケット取得が401の場合は認証状態を破棄しログイン画面へ遷移する', async () => {
    renderTicketList((input) => {
      const url = String(input);
      if (url.endsWith('/api/auth/csrf')) return Promise.resolve(csrfResponse());
      if (url.endsWith('/api/auth/me')) return Promise.resolve(jsonResponse(testUser()));
      if (url.includes('/api/tickets?')) return Promise.resolve(errorResponse(401, '認証が必要です', '/api/tickets'));
      throw new Error(`unexpected fetch: ${url}`);
    });

    await waitFor(() => expect(screen.getByText('ログイン画面')).toBeInTheDocument());
  });

  it('デフォルトはpage=0size=20でAPIを呼ぶ', async () => {
    const fetchMock = vi.fn((input: RequestInfo | URL) => {
      const url = String(input);
      if (url.endsWith('/api/auth/csrf')) return Promise.resolve(csrfResponse());
      if (url.endsWith('/api/auth/me')) return Promise.resolve(jsonResponse(testUser()));
      if (url.includes('/api/tickets?')) return Promise.resolve(jsonResponse(ticketPage()));
      throw new Error(`unexpected fetch: ${url}`);
    });
    renderTicketList(fetchMock);

    await screen.findByText('サンプルチケット');
    const ticketsCall = fetchMock.mock.calls.map(([input]) => String(input)).find((u) => u.includes('/api/tickets?'));
    expect(ticketsCall).toContain('page=0');
    expect(ticketsCall).toContain('size=20');
  });

  it('1ページのみの場合はページネーションを表示しない', async () => {
    renderTicketList((input) => {
      const url = String(input);
      if (url.endsWith('/api/auth/csrf')) return Promise.resolve(csrfResponse());
      if (url.endsWith('/api/auth/me')) return Promise.resolve(jsonResponse(testUser()));
      if (url.includes('/api/tickets?')) return Promise.resolve(jsonResponse(ticketPage({ totalPages: 1 })));
      throw new Error(`unexpected fetch: ${url}`);
    });

    await screen.findByText('サンプルチケット');
    expect(screen.queryByRole('navigation', { name: 'チケット一覧のページ切り替え' })).not.toBeInTheDocument();
  });

  it('21件以上の場合は2ページ目が表示されページネーションが出る', async () => {
    renderTicketList((input) => {
      const url = String(input);
      if (url.endsWith('/api/auth/csrf')) return Promise.resolve(csrfResponse());
      if (url.endsWith('/api/auth/me')) return Promise.resolve(jsonResponse(testUser()));
      if (url.includes('/api/tickets?')) {
        return Promise.resolve(
          jsonResponse(
            ticketPage({ content: manyTickets(20), totalElements: 21, totalPages: 2, first: true, last: false }),
          ),
        );
      }
      throw new Error(`unexpected fetch: ${url}`);
    });

    await screen.findByText('チケット1');
    const nav = screen.getByRole('navigation', { name: 'チケット一覧のページ切り替え' });
    expect(nav).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '2ページへ' })).toBeInTheDocument();
  });

  it('ページ番号ボタンをクリックするとURLのpageクエリが更新され該当ページを取得する', async () => {
    const user = userEvent.setup();
    renderTicketList((input) => {
      const url = String(input);
      if (url.endsWith('/api/auth/csrf')) return Promise.resolve(csrfResponse());
      if (url.endsWith('/api/auth/me')) return Promise.resolve(jsonResponse(testUser()));
      if (url.includes('page=1')) {
        return Promise.resolve(
          jsonResponse(ticketPage({ content: [ticket({ id: 99, title: '2ページ目チケット' })], page: 1, totalElements: 21, totalPages: 2, first: false, last: true })),
        );
      }
      if (url.includes('/api/tickets?')) {
        return Promise.resolve(
          jsonResponse(ticketPage({ content: manyTickets(20), totalElements: 21, totalPages: 2, first: true, last: false })),
        );
      }
      throw new Error(`unexpected fetch: ${url}`);
    });

    await screen.findByText('チケット1');
    await user.click(screen.getByRole('button', { name: '2ページへ' }));

    expect(await screen.findByText('2ページ目チケット')).toBeInTheDocument();
  });

  it('URLに?page=2を指定すると2ページ目のAPIが呼ばれる', async () => {
    const fetchMock = vi.fn((input: RequestInfo | URL) => {
      const url = String(input);
      if (url.endsWith('/api/auth/csrf')) return Promise.resolve(csrfResponse());
      if (url.endsWith('/api/auth/me')) return Promise.resolve(jsonResponse(testUser()));
      if (url.includes('/api/tickets?')) {
        return Promise.resolve(
          jsonResponse(ticketPage({ content: [ticket({ title: '2ページ目チケット' })], page: 1, totalElements: 21, totalPages: 2, first: false, last: true })),
        );
      }
      throw new Error(`unexpected fetch: ${url}`);
    });
    renderTicketList(fetchMock, '/tickets?page=2');

    await screen.findByText('2ページ目チケット');
    const ticketsCall = fetchMock.mock.calls.map(([input]) => String(input)).find((u) => u.includes('/api/tickets?'));
    expect(ticketsCall).toContain('page=1');
  });

  it.each(['abc', '0', '-1', '1.5'])('不正なpageクエリ(%s)は1ページ目へ補正される', async (invalidPage) => {
    const fetchMock = vi.fn((input: RequestInfo | URL) => {
      const url = String(input);
      if (url.endsWith('/api/auth/csrf')) return Promise.resolve(csrfResponse());
      if (url.endsWith('/api/auth/me')) return Promise.resolve(jsonResponse(testUser()));
      if (url.includes('/api/tickets?')) return Promise.resolve(jsonResponse(ticketPage()));
      throw new Error(`unexpected fetch: ${url}`);
    });
    renderTicketList(fetchMock, `/tickets?page=${invalidPage}`);

    await screen.findByText('サンプルチケット');
    const ticketsCall = fetchMock.mock.calls.map(([input]) => String(input)).find((u) => u.includes('/api/tickets?'));
    expect(ticketsCall).toContain('page=0');
  });

  it('総ページ数を超えるpageは最後のページへ補正される', async () => {
    const fetchMock = vi.fn((input: RequestInfo | URL) => {
      const url = String(input);
      if (url.endsWith('/api/auth/csrf')) return Promise.resolve(csrfResponse());
      if (url.endsWith('/api/auth/me')) return Promise.resolve(jsonResponse(testUser()));
      if (url.includes('page=1')) {
        return Promise.resolve(
          jsonResponse(ticketPage({ content: [ticket({ title: '最終ページチケット' })], page: 1, totalElements: 21, totalPages: 2, first: false, last: true })),
        );
      }
      if (url.includes('/api/tickets?')) {
        return Promise.resolve(
          jsonResponse(ticketPage({ content: [], page: 99, totalElements: 21, totalPages: 2, first: false, last: false })),
        );
      }
      throw new Error(`unexpected fetch: ${url}`);
    });
    renderTicketList(fetchMock, '/tickets?page=100');

    expect(await screen.findByText('最終ページチケット')).toBeInTheDocument();
  });

  it('チケットが0件の場合は1ページ目として扱われる', async () => {
    const fetchMock = vi.fn((input: RequestInfo | URL) => {
      const url = String(input);
      if (url.endsWith('/api/auth/csrf')) return Promise.resolve(csrfResponse());
      if (url.endsWith('/api/auth/me')) return Promise.resolve(jsonResponse(testUser()));
      if (url.includes('/api/tickets?')) {
        return Promise.resolve(
          jsonResponse(ticketPage({ content: [], totalElements: 0, totalPages: 0, first: true, last: true })),
        );
      }
      throw new Error(`unexpected fetch: ${url}`);
    });
    renderTicketList(fetchMock, '/tickets?page=5');

    expect(await screen.findByText('チケットがありません。')).toBeInTheDocument();
  });

  it('一覧の行のリンクから詳細画面へ遷移できる(遷移元のstateはTicketDetailPage側で検証)', async () => {
    const user = userEvent.setup();
    renderTicketList(
      (input) => {
        const url = String(input);
        if (url.endsWith('/api/auth/csrf')) return Promise.resolve(csrfResponse());
        if (url.endsWith('/api/auth/me')) return Promise.resolve(jsonResponse(testUser()));
        if (url.includes('/api/tickets?')) {
          return Promise.resolve(
            jsonResponse(ticketPage({ content: manyTickets(20), totalElements: 21, totalPages: 2, first: false, last: true, page: 1 })),
          );
        }
        throw new Error(`unexpected fetch: ${url}`);
      },
      '/tickets?page=2',
    );

    const link = await screen.findByRole('link', { name: 'チケット1' });
    expect(link).toHaveAttribute('href', '/tickets/1');
    await user.click(link);

    expect(await screen.findByText('詳細画面')).toBeInTheDocument();
  });

  describe('登録日時の表示', () => {
    it('登録日時がyyyy-MM-dd HH:mm:ss形式(ローカルタイムゾーン)で表示される', async () => {
      renderTicketList((input) => {
        const url = String(input);
        if (url.endsWith('/api/auth/csrf')) return Promise.resolve(csrfResponse());
        if (url.endsWith('/api/auth/me')) return Promise.resolve(jsonResponse(testUser()));
        if (url.includes('/api/tickets?')) {
          return Promise.resolve(jsonResponse(ticketPage({ content: [ticket({ createdAt: '2026-09-25T13:00:04.289243Z' })] })));
        }
        throw new Error(`unexpected fetch: ${url}`);
      });

      // vite.config.tsのtest.envでタイムゾーンをAsia/Tokyo(UTC+9)に固定している。
      expect(await screen.findByText('2026-09-25 22:00:04')).toBeInTheDocument();
    });

    it('ミリ秒・T・Zを含む生のISO文字列は表示されない', async () => {
      renderTicketList((input) => {
        const url = String(input);
        if (url.endsWith('/api/auth/csrf')) return Promise.resolve(csrfResponse());
        if (url.endsWith('/api/auth/me')) return Promise.resolve(jsonResponse(testUser()));
        if (url.includes('/api/tickets?')) {
          return Promise.resolve(jsonResponse(ticketPage({ content: [ticket({ createdAt: '2026-09-25T13:00:04.289243Z' })] })));
        }
        throw new Error(`unexpected fetch: ${url}`);
      });

      await screen.findByText('2026-09-25 22:00:04');
      expect(screen.queryByText('2026-09-25T13:00:04.289243Z')).not.toBeInTheDocument();
      expect(screen.queryByText((text) => text.includes('T') && text.includes('Z'))).not.toBeInTheDocument();
    });
  });

  describe('担当者列', () => {
    it('USERの一覧に担当者列が表示され担当者名が表示される', async () => {
      renderTicketList((input) => {
        const url = String(input);
        if (url.endsWith('/api/auth/csrf')) return Promise.resolve(csrfResponse());
        if (url.endsWith('/api/auth/me')) return Promise.resolve(jsonResponse(testUser({ role: 'USER' })));
        if (url.includes('/api/tickets?')) {
          return Promise.resolve(jsonResponse(ticketPage({ content: [ticket({ assigneeId: 2, assigneeName: '担当花子' })] })));
        }
        throw new Error(`unexpected fetch: ${url}`);
      });

      expect(await screen.findByText('担当者')).toBeInTheDocument();
      expect(screen.getByText('担当花子')).toBeInTheDocument();
    });

    it('USERの一覧で担当者未設定の場合は未割り当てと表示される', async () => {
      renderTicketList((input) => {
        const url = String(input);
        if (url.endsWith('/api/auth/csrf')) return Promise.resolve(csrfResponse());
        if (url.endsWith('/api/auth/me')) return Promise.resolve(jsonResponse(testUser({ role: 'USER' })));
        if (url.includes('/api/tickets?')) {
          return Promise.resolve(jsonResponse(ticketPage({ content: [ticket({ assigneeId: null, assigneeName: null })] })));
        }
        throw new Error(`unexpected fetch: ${url}`);
      });

      await screen.findByText('サンプルチケット');
      expect(screen.getByText('未割り当て')).toBeInTheDocument();
    });

    it('ADMINの一覧に担当者列が表示され担当者名が表示される', async () => {
      renderTicketList((input) => {
        const url = String(input);
        if (url.endsWith('/api/auth/csrf')) return Promise.resolve(csrfResponse());
        if (url.endsWith('/api/auth/me')) return Promise.resolve(jsonResponse(testUser({ role: 'ADMIN' })));
        if (url.includes('/api/tickets?')) {
          return Promise.resolve(jsonResponse(ticketPage({ content: [ticket({ assigneeId: 2, assigneeName: '担当花子' })] })));
        }
        throw new Error(`unexpected fetch: ${url}`);
      });

      expect(await screen.findByText('担当者')).toBeInTheDocument();
      expect(screen.getByText('担当花子')).toBeInTheDocument();
    });

    it('ADMINの一覧で担当者未設定の場合は未割り当てと表示される', async () => {
      renderTicketList((input) => {
        const url = String(input);
        if (url.endsWith('/api/auth/csrf')) return Promise.resolve(csrfResponse());
        if (url.endsWith('/api/auth/me')) return Promise.resolve(jsonResponse(testUser({ role: 'ADMIN' })));
        if (url.includes('/api/tickets?')) {
          return Promise.resolve(jsonResponse(ticketPage({ content: [ticket({ assigneeId: null, assigneeName: null })] })));
        }
        throw new Error(`unexpected fetch: ${url}`);
      });

      await screen.findByText('サンプルチケット');
      expect(screen.getByText('未割り当て')).toBeInTheDocument();
    });

    it('AGENTの一覧には担当者列が表示されない', async () => {
      renderTicketList((input) => {
        const url = String(input);
        if (url.endsWith('/api/auth/csrf')) return Promise.resolve(csrfResponse());
        if (url.endsWith('/api/auth/me')) return Promise.resolve(jsonResponse(testUser({ role: 'AGENT' })));
        if (url.includes('/api/tickets?')) {
          return Promise.resolve(jsonResponse(ticketPage({ content: [ticket({ assigneeId: 2, assigneeName: '担当花子' })] })));
        }
        throw new Error(`unexpected fetch: ${url}`);
      });

      await screen.findByText('サンプルチケット');
      expect(screen.queryByText('担当者')).not.toBeInTheDocument();
      expect(screen.queryByText('担当花子')).not.toBeInTheDocument();
    });

    it('AGENTのテーブルヘッダーとデータ行の列数が一致する', async () => {
      renderTicketList((input) => {
        const url = String(input);
        if (url.endsWith('/api/auth/csrf')) return Promise.resolve(csrfResponse());
        if (url.endsWith('/api/auth/me')) return Promise.resolve(jsonResponse(testUser({ role: 'AGENT' })));
        if (url.includes('/api/tickets?')) return Promise.resolve(jsonResponse(ticketPage()));
        throw new Error(`unexpected fetch: ${url}`);
      });

      await screen.findByText('サンプルチケット');
      const headerCells = screen.getAllByRole('columnheader');
      const dataRow = screen.getAllByRole('row')[1];
      const dataCells = dataRow.querySelectorAll('td');
      expect(headerCells).toHaveLength(4);
      expect(dataCells).toHaveLength(4);
    });

    it.each(['USER', 'ADMIN'] as const)('%sのテーブルヘッダーとデータ行の列数が一致する', async (role) => {
      renderTicketList((input) => {
        const url = String(input);
        if (url.endsWith('/api/auth/csrf')) return Promise.resolve(csrfResponse());
        if (url.endsWith('/api/auth/me')) return Promise.resolve(jsonResponse(testUser({ role })));
        if (url.includes('/api/tickets?')) return Promise.resolve(jsonResponse(ticketPage()));
        throw new Error(`unexpected fetch: ${url}`);
      });

      await screen.findByText('サンプルチケット');
      const headerCells = screen.getAllByRole('columnheader');
      const dataRow = screen.getAllByRole('row')[1];
      const dataCells = dataRow.querySelectorAll('td');
      expect(headerCells).toHaveLength(5);
      expect(dataCells).toHaveLength(5);
    });

    it('ページを切り替えても担当者名と日時が正しく表示される', async () => {
      const user = userEvent.setup();
      renderTicketList((input) => {
        const url = String(input);
        if (url.endsWith('/api/auth/csrf')) return Promise.resolve(csrfResponse());
        if (url.endsWith('/api/auth/me')) return Promise.resolve(jsonResponse(testUser({ role: 'ADMIN' })));
        if (url.includes('page=1')) {
          return Promise.resolve(
            jsonResponse(
              ticketPage({
                content: [
                  ticket({
                    id: 99,
                    title: '2ページ目チケット',
                    assigneeId: 3,
                    assigneeName: '2ページ担当',
                    createdAt: '2026-03-01T01:00:00Z',
                  }),
                ],
                page: 1,
                totalElements: 21,
                totalPages: 2,
                first: false,
                last: true,
              }),
            ),
          );
        }
        if (url.includes('/api/tickets?')) {
          return Promise.resolve(
            jsonResponse(
              ticketPage({
                content: manyTickets(20).map((t) => ({ ...t, assigneeId: 2, assigneeName: '1ページ担当', createdAt: '2026-02-01T01:00:00Z' })),
                totalElements: 21,
                totalPages: 2,
                first: true,
                last: false,
              }),
            ),
          );
        }
        throw new Error(`unexpected fetch: ${url}`);
      });

      await screen.findByText('チケット1');
      expect(screen.getAllByText('1ページ担当').length).toBeGreaterThan(0);
      expect(screen.getAllByText('2026-02-01 10:00:00').length).toBeGreaterThan(0);

      await user.click(screen.getByRole('button', { name: '2ページへ' }));

      await screen.findByText('2ページ目チケット');
      expect(screen.getByText('2ページ担当')).toBeInTheDocument();
      expect(screen.getByText('2026-03-01 10:00:00')).toBeInTheDocument();
      expect(screen.queryByText('1ページ担当')).not.toBeInTheDocument();
    });
  });
});
