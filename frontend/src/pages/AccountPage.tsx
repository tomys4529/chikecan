import { useState } from 'react';
import type { FormEvent } from 'react';
import { useAuth } from '../context/AuthContext';
import { changePassword, requestEmailChange } from '../api/auth';
import { ApiError } from '../api/client';
import { ErrorMessage } from '../components/ErrorMessage';
import { isPasswordValid, PASSWORD_MAX_LENGTH, PASSWORD_REQUIREMENTS_MESSAGE } from '../utils/passwordValidation';

/**
 * ログイン済みユーザー自身のアカウント設定画面。
 * パスワード変更とメールアドレス変更申請の2つのフォームを持つ。
 * メールアドレス変更は確認メールのリンクを踏むまでusers.emailを更新しないため、
 * 送信成功後もこの画面に表示中の現在のメールアドレスは変更しない。
 */
export function AccountPage() {
  const { user } = useAuth();

  return (
    <section className="auth-page">
      <div className="card auth-card">
        <h1>アカウント設定</h1>
        {user && (
          <dl>
            <dt>氏名</dt>
            <dd>{user.name}</dd>
            <dt>メールアドレス</dt>
            <dd>{user.email}</dd>
          </dl>
        )}

        <ChangePasswordForm />
        <ChangeEmailForm currentEmail={user?.email ?? ''} />
      </div>
    </section>
  );
}

function ChangePasswordForm() {
  const [currentPassword, setCurrentPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [newPasswordConfirm, setNewPasswordConfirm] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [successMessage, setSuccessMessage] = useState<string | null>(null);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setErrorMessage(null);
    setSuccessMessage(null);

    if (!currentPassword || !newPassword || !newPasswordConfirm) {
      setErrorMessage('全ての項目を入力してください');
      return;
    }
    if (!isPasswordValid(newPassword)) {
      setErrorMessage(PASSWORD_REQUIREMENTS_MESSAGE);
      return;
    }
    if (newPassword !== newPasswordConfirm) {
      setErrorMessage('パスワードが一致しません');
      return;
    }
    if (currentPassword === newPassword) {
      setErrorMessage('新しいパスワードは現在のパスワードと異なるものにしてください');
      return;
    }

    setSubmitting(true);
    try {
      const result = await changePassword({ currentPassword, newPassword });
      setSuccessMessage(result.message);
      setCurrentPassword('');
      setNewPassword('');
      setNewPasswordConfirm('');
    } catch (error) {
      setErrorMessage(error instanceof ApiError ? error.message : 'パスワードの変更に失敗しました。時間をおいて再度お試しください。');
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <section>
      <h2>パスワード変更</h2>
      <form onSubmit={handleSubmit} noValidate className="form">
        <div className="form-field">
          <label htmlFor="current-password">現在のパスワード</label>
          <input
            id="current-password"
            type="password"
            value={currentPassword}
            autoComplete="current-password"
            onChange={(event) => setCurrentPassword(event.target.value)}
            maxLength={PASSWORD_MAX_LENGTH}
          />
        </div>
        <div className="form-field">
          <label htmlFor="new-password">新しいパスワード</label>
          <input
            id="new-password"
            type="password"
            value={newPassword}
            autoComplete="new-password"
            onChange={(event) => setNewPassword(event.target.value)}
            maxLength={PASSWORD_MAX_LENGTH}
          />
        </div>
        <div className="form-field">
          <label htmlFor="new-password-confirm">新しいパスワード（確認）</label>
          <input
            id="new-password-confirm"
            type="password"
            value={newPasswordConfirm}
            autoComplete="new-password"
            onChange={(event) => setNewPasswordConfirm(event.target.value)}
            maxLength={PASSWORD_MAX_LENGTH}
          />
        </div>
        {errorMessage && <ErrorMessage message={errorMessage} />}
        {successMessage && <p role="status">{successMessage}</p>}
        <button type="submit" className="btn btn--primary" disabled={submitting}>
          {submitting ? '変更中...' : 'パスワードを変更'}
        </button>
      </form>
    </section>
  );
}

function ChangeEmailForm({ currentEmail }: { currentEmail: string }) {
  const [newEmail, setNewEmail] = useState('');
  const [currentPassword, setCurrentPassword] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [successMessage, setSuccessMessage] = useState<string | null>(null);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setErrorMessage(null);
    setSuccessMessage(null);

    if (!newEmail.trim() || !currentPassword) {
      setErrorMessage('全ての項目を入力してください');
      return;
    }

    setSubmitting(true);
    try {
      const result = await requestEmailChange({ newEmail, currentPassword });
      // 確認前は現在のメールアドレスを変更しない(画面表示はcurrentEmailのまま)。
      setSuccessMessage(result.message);
      setNewEmail('');
      setCurrentPassword('');
    } catch (error) {
      setErrorMessage(error instanceof ApiError ? error.message : 'メールアドレスの変更申請に失敗しました。時間をおいて再度お試しください。');
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <section>
      <h2>メールアドレス変更</h2>
      <p>現在のメールアドレス: {currentEmail}</p>
      <form onSubmit={handleSubmit} noValidate className="form">
        <div className="form-field">
          <label htmlFor="new-email">新しいメールアドレス</label>
          <input
            id="new-email"
            type="email"
            value={newEmail}
            autoComplete="email"
            onChange={(event) => setNewEmail(event.target.value)}
            maxLength={100}
          />
        </div>
        <div className="form-field">
          <label htmlFor="email-change-current-password">現在のパスワード</label>
          <input
            id="email-change-current-password"
            type="password"
            value={currentPassword}
            autoComplete="current-password"
            onChange={(event) => setCurrentPassword(event.target.value)}
            maxLength={PASSWORD_MAX_LENGTH}
          />
        </div>
        {errorMessage && <ErrorMessage message={errorMessage} />}
        {successMessage && <p role="status">{successMessage}</p>}
        <button type="submit" className="btn btn--primary" disabled={submitting}>
          {submitting ? '送信中...' : 'メールアドレスを変更'}
        </button>
      </form>
    </section>
  );
}
