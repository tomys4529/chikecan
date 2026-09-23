import { Link } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';

export function Header() {
  const { user, status, logout } = useAuth();

  async function handleLogout() {
    try {
      await logout();
    } catch {
      // ログアウト失敗時もここでは表示を出さない。セッションが残っていれば次の操作で気づける。
    }
  }

  return (
    <header className="app-header">
      <Link to="/" className="app-header__brand">
        チケカン
      </Link>
      {status === 'authenticated' && user && (
        <span className="app-header__user">
          {user.name}様({user.role}){' '}
          <button type="button" className="btn btn--secondary" onClick={() => void handleLogout()}>
            ログアウト
          </button>
        </span>
      )}
      {status === 'unauthenticated' && (
        <nav className="app-header__nav">
          <Link to="/login">ログイン</Link>
          <Link to="/register">ユーザー登録</Link>
        </nav>
      )}
    </header>
  );
}
