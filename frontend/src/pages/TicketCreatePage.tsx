import { useRef, useState } from 'react';
import type { FormEvent } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { createTicket } from '../api/tickets';
import { ApiError, isUnauthorized } from '../api/client';
import type { TicketPriority } from '../types/ticket';
import { ErrorMessage } from '../components/ErrorMessage';

export function TicketCreatePage() {
  const { invalidateSession } = useAuth();
  const navigate = useNavigate();

  const [title, setTitle] = useState('');
  const [description, setDescription] = useState('');
  const [priority, setPriority] = useState<TicketPriority>('MEDIUM');
  const [submitting, setSubmitting] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  // useStateの更新は非同期でバッチされるため、ごく短時間に連続submitされた場合の
  // ガードにはならない。同期的に確定するrefで二重送信を防ぐ。
  const submittingRef = useRef(false);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (submittingRef.current) {
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
      const created = await createTicket({ title, description, priority });
      navigate(`/tickets/${created.id}`);
    } catch (error) {
      if (isUnauthorized(error)) {
        invalidateSession();
        return;
      }
      setErrorMessage(error instanceof ApiError ? error.message : '登録に失敗しました。時間をおいて再度お試しください。');
    } finally {
      submittingRef.current = false;
      setSubmitting(false);
    }
  }

  return (
    <section className="page">
      <div className="card">
        <h1>チケット登録</h1>
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
          <button type="submit" className="btn btn--primary" disabled={submitting}>
            {submitting ? '登録中...' : '登録する'}
          </button>
        </form>
      </div>
    </section>
  );
}
