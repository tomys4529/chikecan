import { useState } from 'react';
import type { FormEvent } from 'react';
import { Link } from 'react-router-dom';
import { requestPasswordReset } from '../api/auth';
import { ApiError } from '../api/client';
import { ErrorMessage } from '../components/ErrorMessage';

/**
 * パスワードリセットの入口画面。メールアドレス登録の有無に関わらず、
 * サーバーからは常に同じ一般化されたメッセージが返る(アカウント列挙防止)。
 * そのメッセージをそのまま表示するだけで、フロント側で追加の判定は行わない。
 */
export function ForgotPasswordPage() {
  const [email, setEmail] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [resultMessage, setResultMessage] = useState<string | null>(null);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setErrorMessage(null);
    setResultMessage(null);

    if (!email.trim()) {
      setErrorMessage('メールアドレスを入力してください');
      return;
    }

    setSubmitting(true);
    try {
      const result = await requestPasswordReset({ email });
      setResultMessage(result.message);
    } catch (error) {
      setErrorMessage(error instanceof ApiError ? error.message : '送信に失敗しました。時間をおいて再度お試しください。');
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <section className="auth-page">
      <div className="card auth-card">
        <h1>パスワードの再設定</h1>

        {!resultMessage && (
          <form onSubmit={handleSubmit} noValidate className="form">
            <div className="form-field">
              <label htmlFor="forgot-password-email">メールアドレス</label>
              <input
                id="forgot-password-email"
                type="email"
                value={email}
                autoComplete="email"
                onChange={(event) => setEmail(event.target.value)}
                maxLength={100}
              />
            </div>
            {errorMessage && <ErrorMessage message={errorMessage} />}
            <button type="submit" className="btn btn--primary" disabled={submitting}>
              {submitting ? '送信中...' : '再設定メールを送信'}
            </button>
          </form>
        )}

        {resultMessage && <p role="status">{resultMessage}</p>}

        <p>
          <Link to="/login">ログイン画面へ</Link>
        </p>
      </div>
    </section>
  );
}
