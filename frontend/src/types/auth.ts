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

export interface RegisterRequest {
  name: string;
  email: string;
  password: string;
}

export interface LoginRequest {
  email: string;
  password: string;
}

export interface CsrfTokenResponse {
  token: string;
  headerName: string;
  parameterName: string;
}

export interface LogoutResponse {
  message: string;
}
