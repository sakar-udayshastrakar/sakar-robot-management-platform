import axios, { AxiosError, type InternalAxiosRequestConfig } from 'axios';
import type { ApiErrorBody, ApiResponse } from '../types/api';
import { clearSession, getAccessToken, getRefreshToken, setTokens } from '../features/auth/session';

const BASE_URL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080/api/v1';

// The backend's own origin, with the /api/v1 prefix stripped — for the rare
// non-API-versioned endpoint (springdoc's /api-docs, /swagger-ui.html) that
// a page needs to link to directly (see FileDownloadPage).
export const API_ORIGIN = BASE_URL.replace(/\/api\/v1\/?$/, '');

export const apiClient = axios.create({
  baseURL: BASE_URL,
  timeout: 15_000,
});

apiClient.interceptors.request.use((config) => {
  const token = getAccessToken();
  if (token) {
    config.headers.set('Authorization', `Bearer ${token}`);
  }
  return config;
});

export class ApiRequestError extends Error {
  status: number | undefined;
  body: ApiErrorBody | null;

  constructor(message: string, status: number | undefined, body: ApiErrorBody | null) {
    super(message);
    this.name = 'ApiRequestError';
    this.status = status;
    this.body = body;
  }
}

let refreshInFlight: Promise<string | null> | null = null;

async function refreshAccessToken(): Promise<string | null> {
  const refreshToken = getRefreshToken();
  if (!refreshToken) {
    return null;
  }
  if (!refreshInFlight) {
    refreshInFlight = axios
      .post<ApiResponse<{ accessToken: string; refreshToken: string; expiresIn: number }>>(
        `${BASE_URL}/auth/refresh`,
        { refreshToken },
      )
      .then((response) => {
        const tokens = response.data.data;
        if (!tokens) {
          return null;
        }
        setTokens(tokens.accessToken, tokens.refreshToken);
        return tokens.accessToken;
      })
      .catch(() => null)
      .finally(() => {
        refreshInFlight = null;
      });
  }
  return refreshInFlight;
}

apiClient.interceptors.response.use(
  (response) => response,
  async (error: AxiosError<ApiResponse<unknown>>) => {
    const originalRequest = error.config as (InternalAxiosRequestConfig & { _retried?: boolean }) | undefined;
    const status = error.response?.status;
    const body = error.response?.data?.error ?? null;

    // 401 on anything other than the refresh call itself: try one silent
    // refresh-and-retry before giving up (session expiration handling).
    if (
      status === 401 &&
      originalRequest &&
      !originalRequest._retried &&
      !originalRequest.url?.includes('/auth/refresh') &&
      !originalRequest.url?.includes('/auth/login')
    ) {
      originalRequest._retried = true;
      const newToken = await refreshAccessToken();
      if (newToken) {
        originalRequest.headers.set('Authorization', `Bearer ${newToken}`);
        return apiClient.request(originalRequest);
      }
      clearSession();
      window.dispatchEvent(new CustomEvent('sakar:session-expired'));
    }

    const message = body?.message ?? error.message ?? 'Request failed';
    throw new ApiRequestError(message, status, body);
  },
);

export async function unwrap<T>(promise: Promise<{ data: ApiResponse<T> }>): Promise<T> {
  const response = await promise;
  if (response.data.error) {
    throw new ApiRequestError(response.data.error.message, undefined, response.data.error);
  }
  return response.data.data as T;
}
