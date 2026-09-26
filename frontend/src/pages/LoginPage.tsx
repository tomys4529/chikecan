import { useState } from 'react';
import type { FormEvent } from 'react';
import { Link, useLocation } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { ApiError } from '../api/client';
import { ErrorMessage } from '../components/ErrorMessage';

interface LocationState {
  justRegistered?: boolean;
  passwordChanged?: boolean;
}

export function LoginPage() {
  const { login } = useAuth();
  const location = useLocation();
  const justRegistered = (location.state as LocationState | null)?.justRegistered ?? false;
  const passwordChanged = (location.state as LocationState | null)?.passwordChanged ?? false;

  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setErrorMessage(null);

    if (!email.trim() || !password) {
      setErrorMessage('メールアドレスとパスワードを入力してください');
      return;
    }

    setSubmitting(true);
    try {
      await login({ email, password });
    } catch (error) {
      setErrorMessage(error instanceof ApiError ? error.message : 'ログインに失敗しました。時間をおいて再度お試しください。');
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <section className="auth-page">
      <div className="card auth-card">
        <h1>ログイン</h1>
        {justRegistered && (
          <p role="status" className="notice">
            登録が完了しました。ログインしてください。
          </p>
        )}
        {passwordChanged && (
          <p role="status" className="notice">
            パスワードを変更しました。再度ログインしてください。
          </p>
        )}
        <form onSubmit={handleSubmit} noValidate className="form">
          <div className="form-field">
            <label htmlFor="login-email">メールアドレス</label>
            <input
              id="login-email"
              type="email"
              value={email}
              autoComplete="email"
              onChange={(event) => setEmail(event.target.value)}
            />
          </div>
          <div className="form-field">
            <label htmlFor="login-password">パスワード</label>
            <input
              id="login-password"
              type="password"
              value={password}
              autoComplete="current-password"
              onChange={(event) => setPassword(event.target.value)}
            />
          </div>
          {errorMessage && <ErrorMessage message={errorMessage} />}
          <button type="submit" className="btn btn--primary" disabled={submitting}>
            {submitting ? 'ログイン中...' : 'ログイン'}
          </button>
        </form>
        <p>
          <Link to="/forgot-password">パスワードを忘れた方</Link>
        </p>
        <p>
          アカウントをお持ちでない方は<Link to="/register">こちら</Link>
        </p>
      </div>
    </section>
  );
}
