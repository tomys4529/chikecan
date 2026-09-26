export type Role = 'USER' | 'AGENT' | 'ADMIN';

export interface UserResponse {
  id: number;
  name: string;
  email: string;
  role: Role;
  enabled: boolean;
  createdAt: string;
  experience: number;
  level: number;
  currentLevelExperience: number;
  experienceToNextLevel: number;
  experienceProgressPercentage: number;
}

export interface ErrorResponse {
  status: number;
  error: string;
  message: string;
  path: string;
  timestamp: string;
}

export type NameFormat = 'JAPANESE' | 'INTERNATIONAL';

export interface RegisterRequest {
  nameFormat: NameFormat;
  familyName: string;
  givenName: string;
  middleName: string;
  email: string;
  password: string;
}

export interface LoginRequest {
  email: string;
  password: string;
}

export interface VerifyEmailRequest {
  token: string;
}

export interface ResendVerificationRequest {
  email: string;
}

export interface PasswordResetRequest {
  email: string;
}

export interface PasswordResetConfirmRequest {
  token: string;
  password: string;
}

export interface ChangePasswordRequest {
  currentPassword: string;
  newPassword: string;
}

export interface ChangeEmailRequest {
  newEmail: string;
  currentPassword: string;
}

export interface ConfirmEmailChangeRequest {
  token: string;
}

/** message文字列のみを返す軽量なAPIレスポンス(register/verify-email/resend-verification共通)。 */
export interface MessageResponse {
  message: string;
}

export interface CsrfTokenResponse {
  token: string;
  headerName: string;
  parameterName: string;
}

export interface LogoutResponse {
  message: string;
}
