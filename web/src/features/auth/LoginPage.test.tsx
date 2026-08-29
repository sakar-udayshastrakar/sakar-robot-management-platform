import { describe, expect, it, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { AuthProvider } from './AuthContext';
import { LoginPage } from './LoginPage';
import { clearSession } from './session';
import * as authApi from '../../api/auth';
import { ApiRequestError } from '../../api/client';

function renderLogin() {
  return render(
    <MemoryRouter initialEntries={['/login']}>
      <AuthProvider>
        <Routes>
          <Route path="/login" element={<LoginPage />} />
          <Route path="/dashboard" element={<div>Dashboard content</div>} />
        </Routes>
      </AuthProvider>
    </MemoryRouter>,
  );
}

describe('LoginPage', () => {
  beforeEach(() => {
    clearSession();
    vi.restoreAllMocks();
  });

  it('shows a validation-style error on invalid credentials (401)', async () => {
    vi.spyOn(authApi, 'login').mockRejectedValue(new ApiRequestError('Unauthorized', 401, null));
    renderLogin();

    await userEvent.type(screen.getByLabelText('Email'), 'wrong@sakarrobotics.com');
    await userEvent.type(screen.getByLabelText('Password'), 'wrong-password');
    await userEvent.click(screen.getByRole('button', { name: 'Log in' }));

    await waitFor(() => expect(screen.getByRole('alert')).toHaveTextContent('Invalid email or password.'));
  });

  it('navigates to /dashboard after a successful login', async () => {
    vi.spyOn(authApi, 'login').mockResolvedValue({
      accessToken:
        `${btoa(JSON.stringify({ alg: 'HS384' }))}.${btoa(
          JSON.stringify({ sub: 'u1', email: 'admin@sakarrobotics.com', role: 'SUPER_ADMIN', perms: [], exp: Math.floor(Date.now() / 1000) + 900 }),
        )}.sig`,
      refreshToken: 'refresh-token',
      expiresIn: 900,
      mfaRequired: false,
    });
    renderLogin();

    await userEvent.type(screen.getByLabelText('Email'), 'admin@sakarrobotics.com');
    await userEvent.type(screen.getByLabelText('Password'), 'correct-password');
    await userEvent.click(screen.getByRole('button', { name: 'Log in' }));

    await waitFor(() => expect(screen.getByText('Dashboard content')).toBeInTheDocument());
  });
});
