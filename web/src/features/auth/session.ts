// Token storage. The backend issues the refresh token in the response body
// (not an httpOnly cookie — see AuthController#login), so a browser client
// has no choice but to hold it somewhere in the page's own storage. We use
// localStorage rather than sessionStorage so a refresh/new tab doesn't force
// a re-login, and never write a token anywhere it could be logged (console,
// analytics, error reporting).

const ACCESS_TOKEN_KEY = 'sakar.accessToken';
const REFRESH_TOKEN_KEY = 'sakar.refreshToken';

export function getAccessToken(): string | null {
  return localStorage.getItem(ACCESS_TOKEN_KEY);
}

export function getRefreshToken(): string | null {
  return localStorage.getItem(REFRESH_TOKEN_KEY);
}

export function setTokens(accessToken: string, refreshToken: string): void {
  localStorage.setItem(ACCESS_TOKEN_KEY, accessToken);
  localStorage.setItem(REFRESH_TOKEN_KEY, refreshToken);
}

export function clearSession(): void {
  localStorage.removeItem(ACCESS_TOKEN_KEY);
  localStorage.removeItem(REFRESH_TOKEN_KEY);
}
