import { useEffect, useRef, useState } from 'react';
import type { FormEvent } from 'react';
import { useParams } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { getTicket, updateTicketAssignee, updateTicketStatus } from '../api/tickets';
import { listAgents } from '../api/adminUsers';
import { ApiError, isUnauthorized } from '../api/client';
import type { TicketResponse, TicketStatus } from '../types/ticket';
import type { AgentSummaryResponse } from '../types/admin';
import { ALLOWED_STATUS_TRANSITIONS } from '../utils/ticketStatusTransitions';
import { LoadingIndicator } from '../components/LoadingIndicator';
import { ErrorMessage } from '../components/ErrorMessage';
import { TicketStatusBadge } from '../components/TicketStatusBadge';
import { TicketPriorityBadge } from '../components/TicketPriorityBadge';
import { STATUS_LABELS } from '../utils/ticketLabels';
import { NotFoundPage } from './NotFoundPage';

function parseTicketId(idParam: string | undefined): number | null {
  if (idParam === undefined) return null;
  const value = Number(idParam);
  if (!Number.isInteger(value) || value <= 0) {
    return null;
  }
  return value;
}

export function TicketDetailPage() {
  const { id: idParam } = useParams();
  const ticketId = parseTicketId(idParam);
  const { user, invalidateSession } = useAuth();
  const isAdmin = user?.role === 'ADMIN';

  const [ticket, setTicket] = useState<TicketResponse | null>(null);
  const [notFound, setNotFound] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [updating, setUpdating] = useState(false);
  const [statusError, setStatusError] = useState<string | null>(null);
  const [nextStatus, setNextStatus] = useState<TicketStatus | ''>('');
  // useStateの更新は非同期でバッチされるため、ごく短時間に連続submitされた場合の
  // ガードにはならない。同期的に確定するrefで二重送信を防ぐ。
  const updatingRef = useRef(false);

  const [agents, setAgents] = useState<AgentSummaryResponse[] | null>(null);
  const [agentsError, setAgentsError] = useState<string | null>(null);
  const [selectedAssigneeId, setSelectedAssigneeId] = useState<number | null>(null);
  // 直前にselectedAssigneeIdへ同期したticket.assigneeIdの値。undefinedは未同期を表す。
  const [syncedAssigneeId, setSyncedAssigneeId] = useState<number | null | undefined>(undefined);
  const [assigneeUpdating, setAssigneeUpdating] = useState(false);
  const [assigneeError, setAssigneeError] = useState<string | null>(null);
  const assigneeUpdatingRef = useRef(false);

  useEffect(() => {
    if (ticketId === null) {
      // 不正なIDの場合はAPIを呼ばない。
      return;
    }
    let cancelled = false;

    async function load(id: number) {
      try {
        const result = await getTicket(id);
        if (!cancelled) {
          setTicket(result);
        }
      } catch (error) {
        if (cancelled) return;
        if (isUnauthorized(error)) {
          invalidateSession();
          return;
        }
        if (error instanceof ApiError && error.status === 404) {
          setNotFound(true);
          return;
        }
        setErrorMessage(error instanceof ApiError ? error.message : 'チケットの取得に失敗しました。');
      }
    }

    void load(ticketId);
    return () => {
      cancelled = true;
    };
  }, [ticketId, invalidateSession]);

  // ADMINの場合のみ、チケット取得とは独立してAGENT候補を取得する。
  useEffect(() => {
    if (!isAdmin) {
      return;
    }
    let cancelled = false;

    async function loadAgents() {
      try {
        const result = await listAgents();
        if (!cancelled) {
          setAgents(result);
        }
      } catch (error) {
        if (cancelled) return;
        if (isUnauthorized(error)) {
          invalidateSession();
          return;
        }
        setAgentsError(error instanceof ApiError ? error.message : '担当者候補の取得に失敗しました。');
      }
    }

    void loadAgents();
    return () => {
      cancelled = true;
    };
  }, [isAdmin, invalidateSession]);

  if (ticketId === null) {
    return <NotFoundPage />;
  }

  if (notFound) {
    return <ErrorMessage message="チケットが見つかりません" />;
  }

  if (errorMessage) {
    return <ErrorMessage message={errorMessage} />;
  }

  if (ticket === null) {
    return <LoadingIndicator />;
  }

  // チケットの担当者が変わるたび(初回取得時・PATCH成功時)にselectの表示値を同期する。
  // useEffectではなく、レンダー中に直接setStateする(Reactが推奨する「propの変化に
  // あわせてstateを調整する」パターン)ことで、不要な追加レンダーを避ける。
  if (ticket.assigneeId !== syncedAssigneeId) {
    setSyncedAssigneeId(ticket.assigneeId);
    setSelectedAssigneeId(ticket.assigneeId);
  }

  const canChangeStatus = user?.role === 'AGENT' || user?.role === 'ADMIN';
  const availableTransitions = ALLOWED_STATUS_TRANSITIONS[ticket.status];

  const agentsLoading = agents === null;
  // 現在の担当者が無効化等でAGENT候補一覧から外れている場合、
  // selectを不自然に「未割り当て」表示にせず、選択不可の専用optionで現状を示す。
  const currentAssigneeMissing =
    ticket.assigneeId !== null && agents !== null && !agents.some((agent) => agent.id === ticket.assigneeId);
  const assigneeUnchanged = selectedAssigneeId === ticket.assigneeId;

  async function handleStatusSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (updatingRef.current || !nextStatus || !ticket) {
      return;
    }
    setStatusError(null);
    updatingRef.current = true;
    setUpdating(true);
    try {
      const updated = await updateTicketStatus(ticket.id, { status: nextStatus });
      // GETで取り直さず、PATCHのレスポンスをそのまま画面へ反映する。
      setTicket(updated);
      setNextStatus('');
    } catch (error) {
      if (isUnauthorized(error)) {
        invalidateSession();
        return;
      }
      setStatusError(error instanceof ApiError ? error.message : 'ステータス更新に失敗しました。');
    } finally {
      updatingRef.current = false;
      setUpdating(false);
    }
  }

  async function handleAssigneeSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (assigneeUpdatingRef.current || !ticket || assigneeUnchanged) {
      return;
    }
    setAssigneeError(null);
    assigneeUpdatingRef.current = true;
    setAssigneeUpdating(true);
    try {
      const updated = await updateTicketAssignee(ticket.id, { assigneeId: selectedAssigneeId });
      // GETで取り直さず、PATCHのレスポンスをそのまま画面へ反映する。
      setTicket(updated);
    } catch (error) {
      if (isUnauthorized(error)) {
        invalidateSession();
        return;
      }
      setAssigneeError(error instanceof ApiError ? error.message : '担当者の更新に失敗しました。');
    } finally {
      assigneeUpdatingRef.current = false;
      setAssigneeUpdating(false);
    }
  }

  return (
    <section className="page">
      <div className="card">
        <h1>{ticket.title}</h1>
        <p>{ticket.description}</p>
        <dl className="ticket-detail__info">
          <dt>ステータス</dt>
          <dd>
            <TicketStatusBadge status={ticket.status} />
          </dd>
          <dt>優先度</dt>
          <dd>
            <TicketPriorityBadge priority={ticket.priority} />
          </dd>
          <dt>依頼者</dt>
          <dd>{ticket.requesterName}</dd>
          <dt>担当者</dt>
          <dd>{ticket.assigneeName ?? '未割り当て'}</dd>
          <dt>更新日時</dt>
          <dd>{ticket.updatedAt}</dd>
        </dl>

        {canChangeStatus && availableTransitions.length > 0 && (
          <form onSubmit={handleStatusSubmit} className="form status-form">
            <div className="form-field">
              <label htmlFor="next-status">ステータス変更</label>
              <select
                id="next-status"
                value={nextStatus}
                onChange={(event) => setNextStatus(event.target.value as TicketStatus)}
                disabled={updating}
              >
                <option value="">選択してください</option>
                {availableTransitions.map((status) => (
                  <option key={status} value={status}>
                    {STATUS_LABELS[status]}
                  </option>
                ))}
              </select>
            </div>
            {statusError && <ErrorMessage message={statusError} />}
            <button type="submit" className="btn btn--primary" disabled={updating || !nextStatus}>
              {updating ? '更新中...' : '更新する'}
            </button>
          </form>
        )}

        {isAdmin && (
          <form onSubmit={handleAssigneeSubmit} className="form assignee-form">
            <div className="form-field">
              <label htmlFor="assignee-select">担当者設定</label>
              <select
                id="assignee-select"
                value={selectedAssigneeId ?? ''}
                onChange={(event) =>
                  setSelectedAssigneeId(event.target.value === '' ? null : Number(event.target.value))
                }
                disabled={assigneeUpdating || agentsLoading}
              >
                <option value="">未割り当て</option>
                {currentAssigneeMissing && ticket.assigneeId !== null && (
                  <option value={ticket.assigneeId} disabled>
                    現在の担当者 ID: {ticket.assigneeId}(選択不可)
                  </option>
                )}
                {agents?.map((agent) => (
                  <option key={agent.id} value={agent.id}>
                    {agent.name}({agent.email})
                  </option>
                ))}
              </select>
            </div>
            {agentsError && <ErrorMessage message={agentsError} />}
            {assigneeError && <ErrorMessage message={assigneeError} />}
            <button
              type="submit"
              className="btn btn--primary"
              disabled={assigneeUpdating || agentsLoading || assigneeUnchanged}
            >
              {assigneeUpdating ? '更新中...' : '担当者を更新する'}
            </button>
          </form>
        )}
      </div>
    </section>
  );
}
