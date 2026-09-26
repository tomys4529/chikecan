import { useState } from 'react';
import type { FormEvent } from 'react';
import { Link } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { resendVerification } from '../api/auth';
import { ApiError } from '../api/client';
import { ErrorMessage } from '../components/ErrorMessage';
import { isPasswordValid, PASSWORD_MAX_LENGTH, PASSWORD_REQUIREMENTS_MESSAGE } from '../utils/passwordValidation';

export function RegisterPage() {
  const { register } = useAuth();

  const [name, setName] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [passwordConfirm, setPasswordConfirm] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  // nullの間は登録フォームを表示し、登録成功後はメールアドレスを保持して
  // 案内画面(確認メール送信済みの案内・再送フォーム)へ切り替える。
  // 未認証状態のため、ここでは自動ログインへ進めない。
  const [registeredEmail, setRegisteredEmail] = useState<string | null>(null);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setErrorMessage(null);

    if (!name.trim() || !email.trim() || !password || !passwordConfirm) {
      setErrorMessage('全ての項目を入力してください');
      return;
    }

    // バックエンド(RegisterRequestの@Pattern)と同じ強度条件をフロントでも確認し、
    // 満たさない場合はAPIを呼ばずに早期リターンする。実際のパスワード値はtrimしない。
    if (!isPasswordValid(password)) {
      setErrorMessage(PASSWORD_REQUIREMENTS_MESSAGE);
      return;
    }

    // passwordConfirmはDBへ保存せず、フロント側での一致確認だけに用いる
    // (APIへは送信しない)。実際のパスワード値はtrimしない。
    if (password !== passwordConfirm) {
      setErrorMessage('パスワードが一致しません');
      return;
    }

    setSubmitting(true);
    try {
      await register({ name, email, password });
      setRegisteredEmail(email.trim());
    } catch (error) {
      setErrorMessage(error instanceof ApiError ? error.message : '登録に失敗しました。時間をおいて再度お試しください。');
    } finally {
      setSubmitting(false);
    }
  }

  if (registeredEmail) {
    return <RegistrationPendingNotice email={registeredEmail} />;
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
              maxLength={30}
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
              maxLength={100}
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
              maxLength={PASSWORD_MAX_LENGTH}
            />
          </div>
          <div className="form-field">
            <label htmlFor="register-password-confirm">パスワード（確認）</label>
            <input
              id="register-password-confirm"
              type="password"
              value={passwordConfirm}
              autoComplete="new-password"
              onChange={(event) => setPasswordConfirm(event.target.value)}
              maxLength={PASSWORD_MAX_LENGTH}
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

/**
 * 登録成功直後の案内画面。未認証状態のため自動ログインはせず、
 * メール内リンクからの本登録を促す。届かない場合に備えた再送フォームも兼ねる。
 */
function RegistrationPendingNotice({ email }: { email: string }) {
  const [resendEmail, setResendEmail] = useState(email);
  const [resending, setResending] = useState(false);
  const [resendMessage, setResendMessage] = useState<string | null>(null);
  const [resendError, setResendError] = useState<string | null>(null);

  async function handleResend(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setResendError(null);
    setResendMessage(null);

    if (!resendEmail.trim()) {
      setResendError('メールアドレスを入力してください');
      return;
    }

    setResending(true);
    try {
      const result = await resendVerification({ email: resendEmail });
      setResendMessage(result.message);
    } catch (error) {
      setResendError(error instanceof ApiError ? error.message : '再送に失敗しました。時間をおいて再度お試しください。');
    } finally {
      setResending(false);
    }
  }

  return (
    <section className="auth-page">
      <div className="card auth-card">
        <h1>確認メールを送信しました</h1>
        <p role="status">確認メールを送信しました。メール内のリンクから本登録を完了してください。</p>

        <h2>メールが届かない場合</h2>
        <form onSubmit={handleResend} noValidate className="form">
          <div className="form-field">
            <label htmlFor="resend-email">メールアドレス</label>
            <input
              id="resend-email"
              type="email"
              value={resendEmail}
              autoComplete="email"
              onChange={(event) => setResendEmail(event.target.value)}
              maxLength={100}
            />
          </div>
          {resendError && <ErrorMessage message={resendError} />}
          {resendMessage && <p role="status">{resendMessage}</p>}
          <button type="submit" className="btn btn--secondary" disabled={resending}>
            {resending ? '送信中...' : '認証メールを再送'}
          </button>
        </form>

        <p>
          <Link to="/login">ログイン画面へ</Link>
        </p>
      </div>
    </section>
  );
}
