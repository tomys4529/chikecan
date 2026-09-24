import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { AuthProvider } from '../context/AuthContext';
import { TicketCreatePage } from './TicketCreatePage';
import { csrfResponse, errorResponse, jsonResponse, testUser } from '../test-utils/apiMocks';
import type { TicketResponse } from '../types/ticket';

function renderCreatePage(fetchImpl: (input: RequestInfo | URL) => Promise<Response>) {
  vi.stubGlobal('fetch', vi.fn(fetchImpl));

  return render(
    <MemoryRouter initialEntries={['/tickets/new']}>
      <AuthProvider>
        <Routes>
          <Route path="/tickets/new" element={<TicketCreatePage />} />
          <Route path="/tickets/:id" element={<p>チケット詳細</p>} />
          <Route path="/login" element={<p>ログイン画面</p>} />
        </Routes>
      </AuthProvider>
    </MemoryRouter>,
  );
}

function ticketResponse(overrides: Partial<TicketResponse> = {}): TicketResponse {
  return {
    id: 42,
    title: 't',
    description: 'd',
    status: 'OPEN',
    priority: 'LOW',
    requesterId: 1,
    assigneeId: null,
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-01T00:00:00Z',
    ...overrides,
  };
}

function mockAuthOnly(fetchImpl: (url: string) => Response | Promise<Response> | undefined) {
  return (input: RequestInfo | URL) => {
    const url = String(input);
    if (url.endsWith('/api/auth/csrf')) return Promise.resolve(csrfResponse());
    if (url.endsWith('/api/auth/me')) return Promise.resolve(jsonResponse(testUser()));
    const result = fetchImpl(url);
    if (result) return Promise.resolve(result);
    throw new Error(`unexpected fetch: ${url}`);
  };
}

describe('TicketCreatePage', () => {
  beforeEach(() => {
    vi.stubGlobal('fetch', vi.fn());
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('未入力の場合はエラーメッセージを表示しAPIを呼ばない', async () => {
    let createCalled = false;
    renderCreatePage(
      mockAuthOnly((url) => {
        if (url.endsWith('/api/tickets')) {
          createCalled = true;
          return jsonResponse(ticketResponse(), 201);
        }
        return undefined;
      }),
    );

    const user = userEvent.setup();
    await waitFor(() => expect(screen.getByRole('button', { name: '登録する' })).toBeEnabled());
    await user.click(screen.getByRole('button', { name: '登録する' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('タイトルと内容を入力してください');
    expect(createCalled).toBe(false);
  });

  it('登録成功後に詳細画面へ遷移する', async () => {
    renderCreatePage(
      mockAuthOnly((url) => {
        if (url.endsWith('/api/tickets')) {
          return jsonResponse(ticketResponse({ id: 99 }), 201);
        }
        return undefined;
      }),
    );

    const user = userEvent.setup();
    await waitFor(() => expect(screen.getByRole('button', { name: '登録する' })).toBeEnabled());
    await user.type(screen.getByLabelText('タイトル'), 'タイトル');
    await user.type(screen.getByLabelText('内容'), '内容');
    await user.click(screen.getByRole('button', { name: '登録する' }));

    expect(await screen.findByText('チケット詳細')).toBeInTheDocument();
  });

  it('サーバーエラー時にメッセージを表示する', async () => {
    renderCreatePage(
      mockAuthOnly((url) => {
        if (url.endsWith('/api/tickets')) {
          return errorResponse(400, 'タイトルは200文字以内で入力してください', '/api/tickets');
        }
        return undefined;
      }),
    );

    const user = userEvent.setup();
    await waitFor(() => expect(screen.getByRole('button', { name: '登録する' })).toBeEnabled());
    await user.type(screen.getByLabelText('タイトル'), 'タイトル');
    await user.type(screen.getByLabelText('内容'), '内容');
    await user.click(screen.getByRole('button', { name: '登録する' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('タイトルは200文字以内で入力してください');
  });

  it('送信中に連続でsubmitしてもPOSTは1回しか発行されない', async () => {
    let createCallCount = 0;
    renderCreatePage(
      mockAuthOnly((url) => {
        if (url.endsWith('/api/tickets')) {
          createCallCount += 1;
          return new Promise<Response>((resolve) => {
            setTimeout(() => resolve(jsonResponse(ticketResponse(), 201)), 30);
          });
        }
        return undefined;
      }),
    );

    const user = userEvent.setup();
    await waitFor(() => expect(screen.getByRole('button', { name: '登録する' })).toBeEnabled());
    await user.type(screen.getByLabelText('タイトル'), 'タイトル');
    await user.type(screen.getByLabelText('内容'), '内容');

    const form = screen.getByRole('button', { name: '登録する' }).closest('form');
    if (!form) throw new Error('form not found');

    // ボタンのdisabled化がDOMへ反映される前の、ごく短時間の連続submitを想定した検証。
    fireEvent.submit(form);
    fireEvent.submit(form);

    await waitFor(() => expect(screen.getByText('チケット詳細')).toBeInTheDocument());
    expect(createCallCount).toBe(1);
  });
});
