import { useState } from 'react';
import type { FormEvent } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { ApiError } from '../api/client';
import { ErrorMessage } from '../components/ErrorMessage';

export function RegisterPage() {
  const { register } = useAuth();
  const navigate = useNavigate();

  const [name, setName] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setErrorMessage(null);

    if (!name.trim() || !email.trim() || !password) {
      setErrorMessage('全ての項目を入力してください');
      return;
    }

    setSubmitting(true);
    try {
      await register({ name, email, password });
      navigate('/login', { state: { justRegistered: true } });
    } catch (error) {
      setErrorMessage(error instanceof ApiError ? error.message : '登録に失敗しました。時間をおいて再度お試しください。');
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <section className="auth-page">
      <div className="card auth-card">
        <h1>ユーザー登録</h1>
        <form onSubmit={handleSubmit} noValidate className="form">
          <div className="form-field">
            <label htmlFor="register-name">氏名</label>
            <input
              id="register-name"
              value={name}
              autoComplete="name"
              onChange={(event) => setName(event.target.value)}
            />
          </div>
          <div className="form-field">
            <label htmlFor="register-email">メールアドレス</label>
            <input
              id="register-email"
              type="email"
              value={email}
              autoComplete="email"
              onChange={(event) => setEmail(event.target.value)}
            />
          </div>
          <div className="form-field">
            <label htmlFor="register-password">パスワード</label>
            <input
              id="register-password"
              type="password"
              value={password}
              autoComplete="new-password"
              onChange={(event) => setPassword(event.target.value)}
            />
          </div>
          {errorMessage && <ErrorMessage message={errorMessage} />}
          <button type="submit" className="btn btn--primary" disabled={submitting}>
            {submitting ? '登録中...' : '登録する'}
          </button>
        </form>
        <p>
          既にアカウントをお持ちの方は<Link to="/login">こちら</Link>
        </p>
      </div>
    </section>
  );
}
