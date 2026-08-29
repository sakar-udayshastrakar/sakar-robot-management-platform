import { apiClient, unwrap } from './client';
import type { ApiResponse } from '../types/api';

export interface TokenResponse {
  accessToken: string;
  refreshToken: string;
  expiresIn: number;
  mfaRequired: boolean;
}

export function login(email: string, password: string): Promise<TokenResponse> {
  return unwrap(
    apiClient.post<ApiResponse<TokenResponse>>('/auth/login', { email, password }),
  );
}

export function logout(refreshToken: string): Promise<void> {
  return unwrap(apiClient.post<ApiResponse<void>>('/auth/logout', { refreshToken }));
}
