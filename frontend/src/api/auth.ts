import { apiFetch } from './client';
import type {
  CsrfTokenResponse,
  LoginRequest,
  LogoutResponse,
  MessageResponse,
  RegisterRequest,
  ResendVerificationRequest,
  UserResponse,
  VerifyEmailRequest,
} from '../types/auth';

/**
 * CSRFトークンをCookieへ発行させる。
 * ログイン・ログアウト成功後はサーバー側で古いトークンが破棄されるため、
 * その都度呼び直して新しいトークンを取得する。
 * 失敗してもここでは例外を投げない(呼び出し元の処理を失敗扱いにしないため)。
 */
export async function ensureCsrfToken(): Promise<void> {
  try {
    await apiFetch<CsrfTokenResponse>('/api/auth/csrf');
  } catch {
    // 次の状態変更リクエストが403になった時点で通常のエラー処理に委ねる。
  }
}

export function register(request: RegisterRequest): Promise<MessageResponse> {
  return apiFetch<MessageResponse>('/api/auth/register', {
    method: 'POST',
    body: JSON.stringify(request),
  });
}

export function verifyEmail(request: VerifyEmailRequest): Promise<MessageResponse> {
  return apiFetch<MessageResponse>('/api/auth/verify-email', {
    method: 'POST',
    body: JSON.stringify(request),
  });
}

export function resendVerification(request: ResendVerificationRequest): Promise<MessageResponse> {
  return apiFetch<MessageResponse>('/api/auth/resend-verification', {
    method: 'POST',
    body: JSON.stringify(request),
  });
}

export function login(request: LoginRequest): Promise<UserResponse> {
  return apiFetch<UserResponse>('/api/auth/login', {
    method: 'POST',
    body: JSON.stringify(request),
  });
}

export function logout(): Promise<LogoutResponse> {
  return apiFetch<LogoutResponse>('/api/auth/logout', { method: 'POST' });
}

export function fetchCurrentUser(): Promise<UserResponse> {
  return apiFetch<UserResponse>('/api/auth/me');
}
