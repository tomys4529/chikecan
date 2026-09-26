import { useEffect, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { confirmEmailChange } from '../api/auth';
import { ApiError } from '../api/client';
import { ErrorMessage } from '../components/ErrorMessage';
import { LoadingIndicator } from '../components/LoadingIndicator';

type VerificationState = 'verifying' | 'success' | 'error';

const DEFAULT_ERROR_MESSAGE = '変更リンクが無効または期限切れです。';

/**
 * メール内のメールアドレス変更確認リンク(/verify-email-change?token=...)の遷移先。
 * バックエンドはログイン有無に関わらず利用可能(別端末でリンクを開く可能性があるため)なので、
 * ここではAuthContextの初期化状態を待たず、マウント後すぐに確認APIを呼び出す。
 *
 * セキュリティ上の配慮: tokenはアドレスバー・ブラウザ履歴・URLコピー等に残り続けないよう、
 * マウント直後にメモリ(useStateの遅延初期値)へ退避したうえで、URLのクエリパラメータからは
 * 即座に削除する(VerifyEmailPageと同じ考え方)。以降のAPI呼び出しはこのメモリ上の値を使うため、
 * URLから消えても検証処理自体は継続できる。削除後にページを再読み込みした場合はURLにtokenが
 * 残っていないため、誤って確認APIが再実行されることはなく、「tokenなし」のエラー表示になる。
 */
export function VerifyEmailChangePage() {
  const [searchParams, setSearchParams] = useSearchParams();

  // 初回マウント時点のtokenだけをメモリへ保持する。以降URLから削除されても
  // この値は変わらず、API呼び出しに使い続けられる。
  const [token] = useState(() => searchParams.get('token'));

  // tokenがない場合は初期状態から直接エラー扱いにする(effect内でのsetStateを避けるため、
  // useStateの遅延初期値としてここで決める)。
  const [verificationState, setVerificationState] = useState<VerificationState>(token ? 'verifying' : 'error');
  const [errorMessage, setErrorMessage] = useState<string>(DEFAULT_ERROR_MESSAGE);

  useEffect(() => {
    if (!token) {
      return;
    }
    // tokenをメモリへ保持した直後にURLから取り除く。ブラウザ履歴には残さない。
    setSearchParams({}, { replace: true });
  }, [token, setSearchParams]);

  useEffect(() => {
    if (!token) {
      return;
    }

    let cancelled = false;
    async function run(tokenValue: string) {
      try {
        await confirmEmailChange({ token: tokenValue });
        if (!cancelled) {
          setVerificationState('success');
        }
      } catch (error) {
        if (cancelled) return;
        setVerificationState('error');
        setErrorMessage(error instanceof ApiError ? error.message : DEFAULT_ERROR_MESSAGE);
      }
    }

    void run(token);
    return () => {
      cancelled = true;
    };
  }, [token]);

  return (
    <section className="auth-page">
      <div className="card auth-card">
        <h1>メールアドレス変更の確認</h1>

        {verificationState === 'verifying' && <LoadingIndicator label="メールアドレスの変更を確認しています..." />}

        {verificationState === 'success' && (
          <p role="status">メールアドレスを変更しました。</p>
        )}

        {verificationState === 'error' && <ErrorMessage message={errorMessage} />}

        <p>
          <Link to="/login">ログイン画面へ</Link>
        </p>
        <p>
          <Link to="/account">アカウント設定へ</Link>
        </p>
      </div>
    </section>
  );
}
