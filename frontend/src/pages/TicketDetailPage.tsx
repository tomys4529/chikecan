import { useEffect, useRef, useState } from 'react';
import type { FormEvent } from 'react';
import { useParams } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { getTicket, updateTicketStatus } from '../api/tickets';
import { ApiError, isUnauthorized } from '../api/client';
import type { TicketResponse, TicketStatus } from '../types/ticket';
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

  const [ticket, setTicket] = useState<TicketResponse | null>(null);
  const [notFound, setNotFound] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [updating, setUpdating] = useState(false);
  const [statusError, setStatusError] = useState<string | null>(null);
  const [nextStatus, setNextStatus] = useState<TicketStatus | ''>('');
  // useStateの更新は非同期でバッチされるため、ごく短時間に連続submitされた場合の
  // ガードにはならない。同期的に確定するrefで二重送信を防ぐ。
  const updatingRef = useRef(false);

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

  const canChangeStatus = user?.role === 'AGENT' || user?.role === 'ADMIN';
  const availableTransitions = ALLOWED_STATUS_TRANSITIONS[ticket.status];

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
          <dt>依頼者ID</dt>
          <dd>{ticket.requesterId}</dd>
          <dt>担当者</dt>
          <dd>{ticket.assigneeId ?? '未割り当て'}</dd>
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
      </div>
    </section>
  );
}
