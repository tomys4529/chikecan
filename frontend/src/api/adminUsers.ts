import { apiFetch } from './client';
import type { AgentSummaryResponse } from '../types/admin';

export function listAgents(): Promise<AgentSummaryResponse[]> {
  return apiFetch<AgentSummaryResponse[]>('/api/admin/agents');
}
