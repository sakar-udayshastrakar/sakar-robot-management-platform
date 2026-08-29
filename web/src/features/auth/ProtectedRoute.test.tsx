import { describe, expect, it, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { AuthProvider } from './AuthContext';
import { ProtectedRoute } from './ProtectedRoute';
import { setTokens, clearSession } from './session';

function fakeJwt(payload: Record<string, unknown>): string {
  const header = btoa(JSON.stringify({ alg: 'HS384' }));
  const body = btoa(JSON.stringify(payload));
  return `${header}.${body}.signature`;
}

function renderAt(path: string) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <AuthProvider>
        <Routes>
          <Route path="/login" element={<div>Login page</div>} />
          <Route path="/forbidden" element={<div>Forbidden page</div>} />
          <Route
            path="/dashboard"
            element={
              <ProtectedRoute>
                <div>Dashboard content</div>
              </ProtectedRoute>
            }
          />
          <Route
            path="/audit"
            element={
              <ProtectedRoute requirePermission="AUDIT_VIEW">
                <div>Audit content</div>
              </ProtectedRoute>
            }
          />
        </Routes>
      </AuthProvider>
    </MemoryRouter>,
  );
}

describe('ProtectedRoute', () => {
  beforeEach(() => clearSession());

  it('redirects to /login when there is no session', async () => {
    renderAt('/dashboard');
    await waitFor(() => expect(screen.getByText('Login page')).toBeInTheDocument());
  });

  it('renders the protected content when authenticated', async () => {
    const token = fakeJwt({
      sub: 'u1', email: 'viewer@sakarrobotics.com', role: 'VIEWER', perms: ['ROBOT_VIEW'],
      exp: Math.floor(Date.now() / 1000) + 900,
    });
    setTokens(token, 'refresh-token');
    renderAt('/dashboard');
    await waitFor(() => expect(screen.getByText('Dashboard content')).toBeInTheDocument());
  });

  it('redirects to /forbidden when authenticated but missing the required permission', async () => {
    const token = fakeJwt({
      sub: 'u1', email: 'viewer@sakarrobotics.com', role: 'VIEWER', perms: ['ROBOT_VIEW'],
      exp: Math.floor(Date.now() / 1000) + 900,
    });
    setTokens(token, 'refresh-token');
    renderAt('/audit');
    await waitFor(() => expect(screen.getByText('Forbidden page')).toBeInTheDocument());
  });

  it('renders the protected content when the required permission is present', async () => {
    const token = fakeJwt({
      sub: 'u1', email: 'admin@sakarrobotics.com', role: 'SUPER_ADMIN', perms: ['ROBOT_VIEW', 'AUDIT_VIEW'],
      exp: Math.floor(Date.now() / 1000) + 900,
    });
    setTokens(token, 'refresh-token');
    renderAt('/audit');
    await waitFor(() => expect(screen.getByText('Audit content')).toBeInTheDocument());
  });
});
