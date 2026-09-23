import { Navigate, Outlet } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { LoadingIndicator } from '../components/LoadingIndicator';

/**
 * ログイン・登録画面など、未認証時のみ表示する画面用のガード。
 * 認証済みユーザーが/loginや/registerへアクセスした場合は/ticketsへ戻す。
 */
export function PublicOnlyRoute() {
  const { status } = useAuth();

  if (status === 'loading') {
    return <LoadingIndicator label="ログイン状態を確認しています..." />;
  }

  if (status === 'authenticated') {
    return <Navigate to="/tickets" replace />;
  }

  return <Outlet />;
}
