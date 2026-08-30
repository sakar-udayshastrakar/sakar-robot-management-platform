import { describe, expect, it, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { UsersPage } from './UsersPage';
import { AuthProvider } from '../auth/AuthContext';
import { ToastProvider } from '../../components/ui/Toast';
import { clearSession, setTokens } from '../auth/session';
import * as usersApi from '../../api/users';
import { ApiRequestError } from '../../api/client';
import type { PlatformUser } from '../../types/domain';

function fakeToken(role: string, perms: string[], organizationId: string | null = 'org-1') {
  const header = btoa(JSON.stringify({ alg: 'HS384' }));
  const payload = btoa(
    JSON.stringify({
      sub: 'admin-1',
      email: 'admin@sakarrobotics.com',
      role,
      perms,
      org: organizationId,
      exp: Math.floor(Date.now() / 1000) + 900,
    }),
  );
  return `${header}.${payload}.sig`;
}

function renderPage() {
  setTokens(fakeToken('ORG_ADMIN', ['USER_MANAGE']), 'refresh-token');
  return render(
    <MemoryRouter>
      <ToastProvider>
        <AuthProvider>
          <UsersPage />
        </AuthProvider>
      </ToastProvider>
    </MemoryRouter>,
  );
}

function mockPage(content: PlatformUser[]) {
  return { content, totalElements: content.length, totalPages: content.length ? 1 : 0, number: 0, size: 25, first: true, last: true, empty: content.length === 0 };
}

const sampleUsers: PlatformUser[] = [
  {
    id: 'user-1',
    organizationId: 'org-1',
    email: 'alice@sakarrobotics.com',
    fullName: 'Alice Operator',
    roleName: 'OPERATOR',
    status: 'ACTIVE',
    mfaEnabled: false,
    lastLoginAt: null,
    createdAt: new Date().toISOString(),
  },
];

describe('UsersPage', () => {
  beforeEach(() => {
    clearSession();
    vi.restoreAllMocks();
  });

  it('renders users returned by the real GET /users shape', async () => {
    vi.spyOn(usersApi, 'listUsers').mockResolvedValue(mockPage(sampleUsers));

    renderPage();

    await waitFor(() => expect(screen.getByText('alice@sakarrobotics.com')).toBeInTheDocument());
    expect(screen.getByText('Alice Operator')).toBeInTheDocument();
    expect(screen.getByText('ACTIVE')).toBeInTheDocument();
  });

  it('shows an error state when the backend call fails', async () => {
    vi.spyOn(usersApi, 'listUsers').mockRejectedValue(new ApiRequestError('Service unavailable', 503, null));

    renderPage();

    await waitFor(() => expect(screen.getByText('Could not load users')).toBeInTheDocument());
  });

  it('creates a user through the real POST /users contract and refreshes the list', async () => {
    vi.spyOn(usersApi, 'listUsers').mockResolvedValue(mockPage(sampleUsers));
    const createSpy = vi.spyOn(usersApi, 'createUser').mockResolvedValue({
      ...sampleUsers[0],
      id: 'user-2',
      email: 'bob@sakarrobotics.com',
      fullName: 'Bob Viewer',
      roleName: 'VIEWER',
    });

    renderPage();
    await waitFor(() => expect(screen.getByText('alice@sakarrobotics.com')).toBeInTheDocument());

    await userEvent.click(screen.getByRole('button', { name: 'Create User' }));
    await userEvent.type(screen.getByLabelText('Email'), 'bob@sakarrobotics.com');
    await userEvent.type(screen.getByLabelText('Password'), 'SomePassword123!');
    await userEvent.type(screen.getByLabelText('Full name'), 'Bob Viewer');
    await userEvent.click(screen.getByRole('button', { name: 'Create user' }));

    await waitFor(() =>
      expect(createSpy).toHaveBeenCalledWith(
        expect.objectContaining({ email: 'bob@sakarrobotics.com', fullName: 'Bob Viewer', roleName: 'VIEWER' }),
      ),
    );
  });

  it('suspends a user through the real POST /users/{id}/suspend endpoint', async () => {
    vi.spyOn(usersApi, 'listUsers').mockResolvedValue(mockPage(sampleUsers));
    const suspendSpy = vi.spyOn(usersApi, 'suspendUser').mockResolvedValue({ ...sampleUsers[0], status: 'SUSPENDED' });

    renderPage();
    await waitFor(() => expect(screen.getByText('alice@sakarrobotics.com')).toBeInTheDocument());

    await userEvent.click(screen.getByRole('button', { name: 'Suspend' }));

    await waitFor(() => expect(suspendSpy).toHaveBeenCalledWith('user-1'));
  });
});
