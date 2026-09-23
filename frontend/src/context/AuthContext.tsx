import { createContext, useCallback, useContext, useEffect, useState } from 'react';
import type { ReactNode } from 'react';
import { ApiError } from '../api/client';
import { ensureCsrfToken, fetchCurrentUser, login as loginRequest, logout as logoutRequest, register as registerRequest } from '../api/auth';
import type { LoginRequest, RegisterRequest, UserResponse } from '../types/auth';

type AuthStatus = 'loading' | 'authenticated' | 'unauthenticated';

interface AuthContextValue {
  user: UserResponse | null;
  status: AuthStatus;
  initError: string | null;
  login: (input: LoginRequest) => Promise<void>;
  logout: () => Promise<void>;
  register: (input: RegisterRequest) => Promise<UserResponse>;
  invalidateSession: () => void;
}

const AuthContext = createContext<AuthContextValue | undefined>(undefined);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<UserResponse | null>(null);
  const [status, setStatus] = useState<AuthStatus>('loading');
  const [initError, setInitError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;

    async function initialize() {
      await ensureCsrfToken();
      try {
        const currentUser = await fetchCurrentUser();
        if (cancelled) return;
        setUser(currentUser);
        setStatus('authenticated');
      } catch (error) {
        if (cancelled) return;
        if (error instanceof ApiError && error.status === 401) {
          // 初回のGET /api/auth/meが401になるのは未ログイン状態として正常。
          setStatus('unauthenticated');
          return;
        }
        // それ以外(通信エラー等)は未ログイン扱いにしつつ、原因が分かるよう記録する。
        setInitError('ログイン状態の確認に失敗しました。通信環境を確認してください。');
        setStatus('unauthenticated');
      }
    }

    void initialize();
    return () => {
      cancelled = true;
    };
  }, []);

  const login = useCallback(async (input: LoginRequest) => {
    const loggedInUser = await loginRequest(input);
    setUser(loggedInUser);
    setStatus('authenticated');
    // ログイン成功後、古いCSRFトークンはサーバー側で破棄済みのため取り直す。
    // ここで失敗してもログイン自体は成功しているため例外を投げない(ensureCsrfToken内で吸収)。
    await ensureCsrfToken();
  }, []);

  const logout = useCallback(async () => {
    await logoutRequest();
    setUser(null);
    setStatus('unauthenticated');
    await ensureCsrfToken();
  }, []);

  const register = useCallback(async (input: RegisterRequest) => {
    return registerRequest(input);
  }, []);

  /**
   * チケットAPI等で401(セッション切れ)を検知した画面から呼び出す。
   * ここでは状態のリセットのみ行い、/loginへの遷移はProtectedRouteが
   * statusの変化を検知して自動的に行う(呼び出し側でnavigateしない)。
   */
  const invalidateSession = useCallback(() => {
    setUser(null);
    setStatus('unauthenticated');
    void ensureCsrfToken();
  }, []);

  const value: AuthContextValue = { user, status, initError, login, logout, register, invalidateSession };

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

// Context・Provider・カスタムフックを1ファイルにまとめる一般的な構成のため、
// Fast Refresh向けの本ルールでは既知の誤検知となる。
// eslint-disable-next-line react-refresh/only-export-components
export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuthはAuthProviderの内側で使用してください');
  }
  return context;
}
