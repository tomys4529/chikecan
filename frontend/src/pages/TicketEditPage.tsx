import { useEffect, useRef, useState } from 'react';
import type { FormEvent } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { getTicket, updateTicket } from '../api/tickets';
import { ApiError, isUnauthorized } from '../api/client';
import type { TicketPriority } from '../types/ticket';
import { LoadingIndicator } from '../components/LoadingIndicator';
import { ErrorMessage } from '../components/ErrorMessage';
import { NotFoundPage } from './NotFoundPage';

function parseTicketId(idParam: string | undefined): number | null {
  if (idParam === undefined) return null;
  const value = Number(idParam);
  if (!Number.isInteger(value) || value <= 0) {
    return null;
  }
  return value;
}

export function TicketEditPage() {
  const { id: idParam } = useParams();
  const ticketId = parseTicketId(idParam);
  const { invalidateSession } = useAuth();
  const navigate = useNavigate();

  const [title, setTitle] = useState('');
  const [description, setDescription] = useState('');
  const [priority, setPriority] = useState<TicketPriority>('MEDIUM');
  const [loaded, setLoaded] = useState(false);
  const [notFound, setNotFound] = useState(false);
  const [loadErrorMessage, setLoadErrorMessage] = useState<string | null>(null);

  const [submitting, setSubmitting] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  // useStateの更新は非同期でバッチされるため、ごく短時間に連続submitされた場合の
  // ガードにはならない。同期的に確定するrefで二重送信を防ぐ。
  const submittingRef = useRef(false);

  useEffect(() => {
    if (ticketId === null) {
      // 不正なIDの場合はAPIを呼ばない。
      return;
    }
    let cancelled = false;

    async function load(id: number) {
      try {
        const result = await getTicket(id);
        if (cancelled) return;
        setTitle(result.title);
        setDescription(result.description);
        setPriority(result.priority);
        setLoaded(true);
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
        setLoadErrorMessage(error instanceof ApiError ? error.message : 'チケットの取得に失敗しました。');
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

  if (loadErrorMessage) {
    return <ErrorMessage message={loadErrorMessage} />;
  }

  if (!loaded) {
    return <LoadingIndicator />;
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (submittingRef.current || ticketId === null) {
      return;
    }

    setErrorMessage(null);

    if (!title.trim() || !description.trim()) {
      setErrorMessage('タイトルと内容を入力してください');
      return;
    }

    submittingRef.current = true;
    setSubmitting(true);
    try {
      await updateTicket(ticketId, { title, description, priority });
      navigate(`/tickets/${ticketId}`);
    } catch (error) {
      if (isUnauthorized(error)) {
        invalidateSession();
        return;
      }
      setErrorMessage(error instanceof ApiError ? error.message : '更新に失敗しました。時間をおいて再度お試しください。');
    } finally {
      submittingRef.current = false;
      setSubmitting(false);
    }
  }

  return (
    <section className="page">
      <div className="card">
        <h1>チケット編集</h1>
        <form onSubmit={handleSubmit} noValidate className="form">
          <div className="form-field">
            <label htmlFor="ticket-title">タイトル</label>
            <input
              id="ticket-title"
              value={title}
              onChange={(event) => setTitle(event.target.value)}
              disabled={submitting}
            />
          </div>
          <div className="form-field">
            <label htmlFor="ticket-description">内容</label>
            <textarea
              id="ticket-description"
              value={description}
              onChange={(event) => setDescription(event.target.value)}
              disabled={submitting}
              rows={6}
            />
          </div>
          <div className="form-field">
            <label htmlFor="ticket-priority">優先度</label>
            <select
              id="ticket-priority"
              value={priority}
              onChange={(event) => setPriority(event.target.value as TicketPriority)}
              disabled={submitting}
            >
              <option value="LOW">低</option>
              <option value="MEDIUM">中</option>
              <option value="HIGH">高</option>
            </select>
          </div>
          {errorMessage && <ErrorMessage message={errorMessage} />}
          <div className="form-actions">
            <button type="submit" className="btn btn--primary" disabled={submitting}>
              {submitting ? '更新中...' : '更新する'}
            </button>
            <Link to={`/tickets/${ticketId}`} className="btn btn--secondary">
              キャンセル
            </Link>
          </div>
        </form>
      </div>
    </section>
  );
}
