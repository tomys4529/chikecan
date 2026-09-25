import type { UserResponse } from '../types/auth';
import type { AgentSummaryResponse } from '../types/admin';

export function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}

export function errorResponse(status: number, message: string, path = '/'): Response {
  return jsonResponse({ status, error: 'Error', message, path, timestamp: '2026-01-01T00:00:00Z' }, status);
}

export function testUser(overrides: Partial<UserResponse> = {}): UserResponse {
  return {
    id: 1,
    name: '山田太郎',
    email: 'user@example.com',
    role: 'USER',
    enabled: true,
    createdAt: '2026-01-01T00:00:00Z',
    experience: 0,
    level: 1,
    currentLevelExperience: 0,
    experienceToNextLevel: 100,
    experienceProgressPercentage: 0,
    ...overrides,
  };
}

export function csrfResponse(): Response {
  return jsonResponse({ token: 't', headerName: 'X-XSRF-TOKEN', parameterName: '_csrf' });
}

export function agentSummary(overrides: Partial<AgentSummaryResponse> = {}): AgentSummaryResponse {
  return {
    id: 2,
    name: '担当太郎',
    ...overrides,
  };
}
