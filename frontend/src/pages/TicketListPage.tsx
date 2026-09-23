import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { listTickets } from '../api/tickets';
import { ApiError, isUnauthorized } from '../api/client';
import type { TicketResponse } from '../types/ticket';
import { LoadingIndicator } from '../components/LoadingIndicator';
import { ErrorMessage } from '../components/ErrorMessage';
import { TicketStatusBadge } from '../components/TicketStatusBadge';
import { TicketPriorityBadge } from '../components/TicketPriorityBadge';

export function TicketListPage() {
  const { user, invalidateSession } = useAuth();
  const [tickets, setTickets] = useState<TicketResponse[] | null>(null);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;

    async function load() {
      try {
        const result = await listTickets();
        if (!cancelled) {
          setTickets(result);
        }
      } catch (error) {
        if (cancelled) return;
        if (isUnauthorized(error)) {
          invalidateSession();
          return;
        }
        setErrorMessage(error instanceof ApiError ? error.message : 'チケット一覧の取得に失敗しました。');
      }
    }

    void load();
    return () => {
      cancelled = true;
    };
  }, [invalidateSession]);

  return (
    <section className="page">
      <div className="page-header">
        <h1>チケット一覧</h1>
        {user?.role === 'USER' && (
          <Link to="/tickets/new" className="btn btn--primary">
            新規登録
          </Link>
        )}
      </div>

      {errorMessage && <ErrorMessage message={errorMessage} />}
      {!errorMessage && tickets === null && <LoadingIndicator />}
      {!errorMessage && tickets !== null && tickets.length === 0 && <p>チケットがありません。</p>}
      {!errorMessage && tickets !== null && tickets.length > 0 && (
        <div className="table-scroll" role="region" aria-label="チケット一覧表(横スクロール可能)" tabIndex={0}>
          <table>
            <thead>
              <tr>
                <th>タイトル</th>
                <th>ステータス</th>
                <th>優先度</th>
                <th>登録日時</th>
              </tr>
            </thead>
            <tbody>
              {tickets.map((ticket) => (
                <tr key={ticket.id}>
                  <td>
                    <Link to={`/tickets/${ticket.id}`}>{ticket.title}</Link>
                  </td>
                  <td>
                    <TicketStatusBadge status={ticket.status} />
                  </td>
                  <td>
                    <TicketPriorityBadge priority={ticket.priority} />
                  </td>
                  <td>{ticket.createdAt}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </section>
  );
}
