import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import * as authApi from '../../api/auth';
import { clearSession, getAccessToken, getRefreshToken, setTokens } from './session';
import { decodeAccessToken, isExpired, type DecodedAccessToken } from './jwt';
import type { PermissionCode } from '../../types/permissions';

interface AuthContextValue {
  user: DecodedAccessToken | null;
  isAuthenticated: boolean;
  isInitializing: boolean;
  login: (email: string, password: string) => Promise<void>;
  logout: () => Promise<void>;
  hasPermission: (permission: PermissionCode) => boolean;
}

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<DecodedAccessToken | null>(null);
  const [isInitializing, setIsInitializing] = useState(true);

  useEffect(() => {
    const token = getAccessToken();
    if (token) {
      const decoded = decodeAccessToken(token);
      if (decoded && !isExpired(decoded)) {
        setUser(decoded);
      } else {
        clearSession();
      }
    }
    setIsInitializing(false);

    const onExpired = () => setUser(null);
    window.addEventListener('sakar:session-expired', onExpired);
    return () => window.removeEventListener('sakar:session-expired', onExpired);
  }, []);

  const login = useCallback(async (email: string, password: string) => {
    const tokens = await authApi.login(email, password);
    setTokens(tokens.accessToken, tokens.refreshToken);
    const decoded = decodeAccessToken(tokens.accessToken);
    setUser(decoded);
  }, []);

  const logout = useCallback(async () => {
    const refreshToken = getRefreshToken();
    try {
      if (refreshToken) {
        await authApi.logout(refreshToken);
      }
    } finally {
      clearSession();
      setUser(null);
    }
  }, []);

  const hasPermission = useCallback(
    (permission: PermissionCode) => user?.permissions.includes(permission) ?? false,
    [user],
  );

  const value = useMemo<AuthContextValue>(
    () => ({ user, isAuthenticated: user !== null, isInitializing, login, logout, hasPermission }),
    [user, isInitializing, login, logout, hasPermission],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext);
  if (!ctx) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return ctx;
}
