import { Navigate, Outlet } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { LoadingIndicator } from '../components/LoadingIndicator';

/**
 * USER以外がアクセスした場合は/ticketsへ戻す表示制御。
 * 実際の認可はバックエンドの@PreAuthorize("hasRole('USER')")が最終防御であり、
 * これを迂回されても403が返る。
 *
 * 通常はProtectedRouteの内側でのみ使う想定だが、認証状態確認中(loading)に
 * user未確定のまま誤って/ticketsへ戻さないよう、ここでも明示的に判定する。
 */
export function UserOnlyRoute() {
  const { user, status } = useAuth();

  if (status === 'loading') {
    return <LoadingIndicator label="ログイン状態を確認しています..." />;
  }

  if (user?.role !== 'USER') {
    return <Navigate to="/tickets" replace />;
  }

  return <Outlet />;
}
