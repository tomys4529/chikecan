import type { ErrorResponse } from '../types/auth';

// 開発環境は.env.developmentのVITE_API_BASE_URLで別Origin(localhost:8080)へ接続する。
// 本番ビルドはこの環境変数を設定しないため空文字にフォールバックし、相対URL(同一Origin)で呼び出す。
const API_BASE_URL: string = import.meta.env.VITE_API_BASE_URL ?? '';

// CSRF検証が必要なのは状態変更を伴うメソッドのみ。
// GET・HEAD・OPTIONSにはCSRFヘッダーを付与しない。
const CSRF_REQUIRED_METHODS = new Set(['POST', 'PUT', 'PATCH', 'DELETE']);

export class ApiError extends Error {
  readonly status: number;
  readonly body: ErrorResponse | null;

  constructor(status: number, body: ErrorResponse | null) {
    super(body?.message ?? '通信エラーが発生しました');
    this.status = status;
    this.body = body;
  }
}

/**
 * セッション切れ判定専用。400・403・404・409等の他のエラーとは区別し、
 * これがtrueの場合のみ呼び出し側で認証状態の破棄を行う。
 */
export function isUnauthorized(error: unknown): error is ApiError {
  return error instanceof ApiError && error.status === 401;
}

function getCsrfCookie(): string | null {
  const match = document.cookie.match(/(?:^|; )XSRF-TOKEN=([^;]*)/);
  return match ? decodeURIComponent(match[1]) : null;
}

export async function apiFetch<T>(path: string, options: RequestInit = {}): Promise<T> {
  const method = (options.method ?? 'GET').toUpperCase();
  const headers = new Headers(options.headers);

  if (options.body && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json');
  }

  if (CSRF_REQUIRED_METHODS.has(method)) {
    const csrfToken = getCsrfCookie();
    if (csrfToken) {
      headers.set('X-XSRF-TOKEN', csrfToken);
    }
  }

  const response = await fetch(`${API_BASE_URL}${path}`, {
    ...options,
    method,
    headers,
    credentials: 'include',
  });

  if (!response.ok) {
    const body = (await response.json().catch(() => null)) as ErrorResponse | null;
    throw new ApiError(response.status, body);
  }

  if (response.status === 204) {
    return undefined as T;
  }

  return (await response.json()) as T;
}
