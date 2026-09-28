import { useState } from 'react';
import type { FormEvent } from 'react';
import { Link, useLocation, useSearchParams } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { ApiError, API_BASE_URL } from '../api/client';
import { ErrorMessage } from '../components/ErrorMessage';

interface LocationState {
  justRegistered?: boolean;
  passwordChanged?: boolean;
}

/**
 * Googleでログインボタン押下時の遷移先。fetchでは呼び出さず、ブラウザナビゲーションで
 * Spring Security標準のOAuth2認可エンドポイントへ遷移させる(このパス自体がGoogleへの
 * リダイレクトを引き起こす)。
 */
function startGoogleLogin() {
  window.location.href = `${API_BASE_URL}/oauth2/authorization/google`;
}

export function LoginPage() {
  const { login } = useAuth();
  const location = useLocation();
  const [searchParams] = useSearchParams();
  const justRegistered = (location.state as LocationState | null)?.justRegistered ?? false;
  const passwordChanged = (location.state as LocationState | null)?.passwordChanged ?? false;
  // Googleログイン失敗時、バックエンドが/login?oauthError=trueへリダイレクトする。
  // 内部エラーの詳細は返ってこないため、ここでは固定の汎用メッセージのみ表示する。
  const oauthError = searchParams.get('oauthError') === 'true';

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
        {oauthError && <ErrorMessage message="Googleログインに失敗しました。" />}
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
        <div className="auth-divider" role="separator" aria-label="または">
          <span>または</span>
        </div>
        <button type="button" className="btn btn--google" onClick={startGoogleLogin}>
          Googleでログイン
        </button>
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
