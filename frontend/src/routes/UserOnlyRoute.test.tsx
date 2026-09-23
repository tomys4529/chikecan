import { render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { AuthProvider } from '../context/AuthContext';
import { UserOnlyRoute } from './UserOnlyRoute';
import { csrfResponse, jsonResponse, testUser } from '../test-utils/apiMocks';

function renderWithRole(role: 'USER' | 'AGENT' | 'ADMIN') {
  vi.stubGlobal(
    'fetch',
    vi.fn((input: RequestInfo | URL) => {
      const url = String(input);
      if (url.endsWith('/api/auth/csrf')) return Promise.resolve(csrfResponse());
      if (url.endsWith('/api/auth/me')) return Promise.resolve(jsonResponse(testUser({ role })));
      throw new Error(`unexpected fetch: ${url}`);
    }),
  );

  return render(
    <MemoryRouter initialEntries={['/tickets/new']}>
      <AuthProvider>
        <Routes>
          <Route path="/tickets" element={<p>チケット一覧</p>} />
          <Route element={<UserOnlyRoute />}>
            <Route path="/tickets/new" element={<p>チケット登録画面</p>} />
          </Route>
        </Routes>
      </AuthProvider>
    </MemoryRouter>,
  );
}

describe('UserOnlyRoute', () => {
  beforeEach(() => {
    vi.stubGlobal('fetch', vi.fn());
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('USERの場合はチケット登録画面を表示する', async () => {
    renderWithRole('USER');
    expect(await screen.findByText('チケット登録画面')).toBeInTheDocument();
  });

  it('AGENTの場合はチケット一覧へリダイレクトする', async () => {
    renderWithRole('AGENT');
    await waitFor(() => expect(screen.getByText('チケット一覧')).toBeInTheDocument());
  });

  it('ADMINの場合はチケット一覧へリダイレクトする', async () => {
    renderWithRole('ADMIN');
    await waitFor(() => expect(screen.getByText('チケット一覧')).toBeInTheDocument());
  });
});
