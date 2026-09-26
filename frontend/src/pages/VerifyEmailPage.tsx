import { useEffect, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { verifyEmail } from '../api/auth';
import { ApiError } from '../api/client';
import { ErrorMessage } from '../components/ErrorMessage';
import { LoadingIndicator } from '../components/LoadingIndicator';

type VerificationState = 'verifying' | 'success' | 'error';

const DEFAULT_ERROR_MESSAGE = '認証リンクが無効または期限切れです。';

/**
 * メール内の認証リンク(/verify-email?token=...)の遷移先。
 * マウント時にtokenを検証し、成功・失敗を表示する。
 *
 * AuthProviderの初期化(CSRFトークン取得を含む)が終わるまでは検証APIを呼ばない。
 * ここでのverifyEmail呼び出しはPOST(CSRF必須)であり、ページ読み込み直後に
 * ユーザー操作を待たず自動送信するため、CSRFトークンが確実に用意された後で
 * 送信しないと、稀にCSRF未設定による403が「無効なリンク」と誤表示されうる。
 * statusが'loading'でなくなった時点でCSRF取得が完了していることを利用する。
 *
 * セキュリティ上の配慮: tokenはアドレスバー・ブラウザ履歴・URLコピー等に残り続けないよう、
 * マウント直後にメモリ(useStateの遅延初期値)へ退避したうえで、URLのクエリパラメータからは
 * 即座に削除する(history.replaceStateに相当するreplace:trueで、履歴を汚さずURLだけ書き換える)。
 * 以降のAPI呼び出しはこのメモリ上の値を使うため、URLから消えても検証処理自体は継続できる。
 * 削除後にページを再読み込みした場合はURLにtokenが残っていないため、
 * 誤って認証APIが再実行されることはなく、「tokenなし」のエラー表示になる。
 */
export function VerifyEmailPage() {
  const { status } = useAuth();
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
    if (status === 'loading' || !token) {
      return;
    }

    let cancelled = false;
    async function run(tokenValue: string) {
      try {
        await verifyEmail({ token: tokenValue });
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
  }, [status, token]);

  return (
    <section className="auth-page">
      <div className="card auth-card">
        <h1>メールアドレスの確認</h1>

        {verificationState === 'verifying' && <LoadingIndicator label="メールアドレスを確認しています..." />}

        {verificationState === 'success' && (
          <p role="status">メールアドレスの確認が完了しました。ログインできます。</p>
        )}

        {verificationState === 'error' && <ErrorMessage message={errorMessage} />}

        <p>
          <Link to="/login">ログイン画面へ</Link>
        </p>
      </div>
    </section>
  );
}
