import { render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { AuthProvider } from '../context/AuthContext';
import { XpPanel } from './XpPanel';
import { csrfResponse, jsonResponse, testUser } from '../test-utils/apiMocks';

function renderPanel(role: 'USER' | 'AGENT' | 'ADMIN', overrides: Partial<ReturnType<typeof testUser>> = {}) {
  vi.stubGlobal(
    'fetch',
    vi.fn((input: RequestInfo | URL) => {
      const url = String(input);
      if (url.endsWith('/api/auth/csrf')) return Promise.resolve(csrfResponse());
      if (url.endsWith('/api/auth/me')) return Promise.resolve(jsonResponse(testUser({ role, ...overrides })));
      throw new Error(`unexpected fetch: ${url}`);
    }),
  );

  return render(
    <AuthProvider>
      <XpPanel />
    </AuthProvider>,
  );
}

describe('XpPanel', () => {
  beforeEach(() => {
    vi.stubGlobal('fetch', vi.fn());
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('AGENTには初期状態(Level1・0XP・次まで100XP)のXPパネルが表示される', async () => {
    renderPanel('AGENT');

    expect(await screen.findByText('LEVEL 1')).toBeInTheDocument();
    expect(screen.getByText('累計 0 XP')).toBeInTheDocument();
    expect(screen.getByText('次のレベルまで 100 XP')).toBeInTheDocument();
    expect(screen.getByText('0%')).toBeInTheDocument();
  });

  it('USERにはXPパネルが表示されない', async () => {
    renderPanel('USER');

    await waitFor(() => expect(screen.queryByLabelText('AGENTのレベルとXP')).not.toBeInTheDocument());
  });

  it('ADMINにはXPパネルが表示されない', async () => {
    renderPanel('ADMIN');

    await waitFor(() => expect(screen.queryByLabelText('AGENTのレベルとXP')).not.toBeInTheDocument());
  });

  it('累計120XPの場合Level2・次まで80XP・バー20%になる', async () => {
    renderPanel('AGENT', {
      experience: 120,
      level: 2,
      currentLevelExperience: 20,
      experienceToNextLevel: 80,
      experienceProgressPercentage: 20,
    });

    expect(await screen.findByText('LEVEL 2')).toBeInTheDocument();
    expect(screen.getByText('累計 120 XP')).toBeInTheDocument();
    expect(screen.getByText('次のレベルまで 80 XP')).toBeInTheDocument();
    expect(screen.getByText('20%')).toBeInTheDocument();

    const progressBar = screen.getByRole('progressbar', { name: '現在のレベル内の進捗' });
    expect(progressBar).toHaveAttribute('aria-valuenow', '20');
  });
});
