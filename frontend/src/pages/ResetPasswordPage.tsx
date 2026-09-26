import { useEffect, useState } from 'react';
import type { FormEvent } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { confirmPasswordReset } from '../api/auth';
import { ApiError } from '../api/client';
import { ErrorMessage } from '../components/ErrorMessage';
import { isPasswordValid, PASSWORD_MAX_LENGTH, PASSWORD_REQUIREMENTS_MESSAGE } from '../utils/passwordValidation';

type PageState = 'form' | 'success' | 'invalid';

const DEFAULT_ERROR_MESSAGE = '再設定リンクが無効または期限切れです。';

/**
 * メール内のパスワード再設定リンク(/reset-password?token=...)の遷移先。
 *
 * セキュリティ上の配慮: tokenはアドレスバー・ブラウザ履歴・URLコピー等に残り続けないよう、
 * マウント直後にメモリ(useStateの遅延初期値)へ退避したうえで、URLのクエリパラメータからは
 * 即座に削除する(VerifyEmailPageと同じ考え方)。以降のAPI呼び出しはこのメモリ上の値を使うため、
 * URLから消えても送信処理自体は継続できる。
 *
 * VerifyEmailPageと異なりtokenの検証はユーザーがパスワードを入力して送信したタイミングで
 * 行う(マウント時に自動送信しない)ため、CSRFトークン取得タイミングとの競合は問題にならない。
 */
export function ResetPasswordPage() {
  const [searchParams, setSearchParams] = useSearchParams();

  // 初回マウント時点のtokenだけをメモリへ保持する。以降URLから削除されても
  // この値は変わらず、API呼び出しに使い続けられる。
  const [token] = useState(() => searchParams.get('token'));

  // tokenがない場合は初期状態から直接無効表示にする(effect内でのsetStateを避けるため、
  // useStateの遅延初期値としてここで決める)。
  const [pageState, setPageState] = useState<PageState>(token ? 'form' : 'invalid');
  // フォーム入力中のバリデーションエラー(修正して再送信できる)。
  const [formError, setFormError] = useState<string | null>(null);
  // token自体が無効・期限切れだった場合の表示文言(フォームは再表示しない)。
  const [invalidMessage, setInvalidMessage] = useState<string>(DEFAULT_ERROR_MESSAGE);
  const [password, setPassword] = useState('');
  const [passwordConfirm, setPasswordConfirm] = useState('');
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    if (!token) {
      return;
    }
    // tokenをメモリへ保持した直後にURLから取り除く。ブラウザ履歴には残さない。
    setSearchParams({}, { replace: true });
  }, [token, setSearchParams]);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setFormError(null);

    if (!password || !passwordConfirm) {
      setFormError('全ての項目を入力してください');
      return;
    }

    if (!isPasswordValid(password)) {
      setFormError(PASSWORD_REQUIREMENTS_MESSAGE);
      return;
    }

    if (password !== passwordConfirm) {
      setFormError('パスワードが一致しません');
      return;
    }

    if (!token) {
      setPageState('invalid');
      return;
    }

    setSubmitting(true);
    try {
      await confirmPasswordReset({ token, password });
      setPageState('success');
    } catch (error) {
      setInvalidMessage(error instanceof ApiError ? error.message : DEFAULT_ERROR_MESSAGE);
      setPageState('invalid');
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <section className="auth-page">
      <div className="card auth-card">
        <h1>パスワードの再設定</h1>

        {pageState === 'form' && (
          <form onSubmit={handleSubmit} noValidate className="form">
            <div className="form-field">
              <label htmlFor="reset-password">新しいパスワード</label>
              <input
                id="reset-password"
                type="password"
                value={password}
                autoComplete="new-password"
                onChange={(event) => setPassword(event.target.value)}
                maxLength={PASSWORD_MAX_LENGTH}
              />
            </div>
            <div className="form-field">
              <label htmlFor="reset-password-confirm">新しいパスワード（確認）</label>
              <input
                id="reset-password-confirm"
                type="password"
                value={passwordConfirm}
                autoComplete="new-password"
                onChange={(event) => setPasswordConfirm(event.target.value)}
                maxLength={PASSWORD_MAX_LENGTH}
              />
            </div>
            {formError && <ErrorMessage message={formError} />}
            <button type="submit" className="btn btn--primary" disabled={submitting}>
              {submitting ? '変更中...' : 'パスワードを変更'}
            </button>
          </form>
        )}

        {pageState === 'success' && (
          <p role="status">パスワードを変更しました。ログインしてください。</p>
        )}

        {pageState === 'invalid' && <ErrorMessage message={invalidMessage} />}

        <p>
          <Link to="/login">ログイン画面へ</Link>
        </p>
      </div>
    </section>
  );
}
