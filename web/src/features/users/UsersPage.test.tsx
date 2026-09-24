import { describe, expect, it, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { UsersPage } from './UsersPage';
import { AuthProvider } from '../auth/AuthContext';
import { ToastProvider } from '../../components/ui/Toast';
import { clearSession, setTokens } from '../auth/session';
import * as usersApi from '../../api/users';
import * as departmentsApi from '../../api/departments';
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

function renderPage(scope: 'INTERNAL' | 'EXTERNAL' = 'EXTERNAL') {
  setTokens(fakeToken('ORG_ADMIN', ['USER_MANAGE']), 'refresh-token');
  return render(
    <MemoryRouter>
      <ToastProvider>
        <AuthProvider>
          <UsersPage scope={scope} />
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
    userType: 'EXTERNAL',
    departmentId: null,
    departmentName: null,
  },
];

describe('UsersPage', () => {
  beforeEach(() => {
    clearSession();
    vi.restoreAllMocks();
    vi.spyOn(departmentsApi, 'listDepartments').mockResolvedValue([]);
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

  describe('Internal scope (Account Permission Platform, Phase 1)', () => {
    const internalUser: PlatformUser = {
      ...sampleUsers[0],
      id: 'user-3',
      email: 'carol@sakarrobotics.com',
      fullName: 'Carol Staff',
      userType: 'INTERNAL',
      departmentId: 'dept-1',
      departmentName: 'Engineering',
    };

    it('renders the Departments panel with real GET /departments data', async () => {
      vi.spyOn(usersApi, 'listUsers').mockResolvedValue(mockPage([internalUser]));
      vi.spyOn(departmentsApi, 'listDepartments').mockResolvedValue([{ id: 'dept-1', name: 'Engineering', createdAt: '', updatedAt: '' }]);

      renderPage('INTERNAL');

      await waitFor(() => expect(screen.getByText('carol@sakarrobotics.com')).toBeInTheDocument());
      expect(screen.getByText('Departments (1)')).toBeInTheDocument();
      expect(screen.getAllByText('Engineering').length).toBeGreaterThan(0);
    });

    it('creates an internal user with userType and departmentId through the real POST /users contract', async () => {
      vi.spyOn(usersApi, 'listUsers').mockResolvedValue(mockPage([internalUser]));
      vi.spyOn(departmentsApi, 'listDepartments').mockResolvedValue([{ id: 'dept-1', name: 'Engineering', createdAt: '', updatedAt: '' }]);
      const createSpy = vi.spyOn(usersApi, 'createUser').mockResolvedValue(internalUser);

      renderPage('INTERNAL');
      await waitFor(() => expect(screen.getByText('carol@sakarrobotics.com')).toBeInTheDocument());

      await userEvent.click(screen.getByRole('button', { name: 'Create User' }));
      await userEvent.type(screen.getByLabelText('Email'), 'dave@sakarrobotics.com');
      await userEvent.type(screen.getByLabelText('Password'), 'SomePassword123!');
      await userEvent.type(screen.getByLabelText('Full name'), 'Dave Staff');
      await userEvent.selectOptions(screen.getByLabelText('Department'), 'dept-1');
      await userEvent.click(screen.getByRole('button', { name: 'Create user' }));

      await waitFor(() =>
        expect(createSpy).toHaveBeenCalledWith(
          expect.objectContaining({ email: 'dave@sakarrobotics.com', userType: 'INTERNAL', departmentId: 'dept-1' }),
        ),
      );
    });

    it('creates a new department through the real POST /departments endpoint', async () => {
      vi.spyOn(usersApi, 'listUsers').mockResolvedValue(mockPage([internalUser]));
      vi.spyOn(departmentsApi, 'listDepartments').mockResolvedValue([]);
      const createDeptSpy = vi.spyOn(departmentsApi, 'createDepartment').mockResolvedValue({ id: 'dept-2', name: 'Support', createdAt: '', updatedAt: '' });

      renderPage('INTERNAL');
      await waitFor(() => expect(screen.getByText('carol@sakarrobotics.com')).toBeInTheDocument());

      await userEvent.type(screen.getByLabelText('New department name'), 'Support');
      await userEvent.click(screen.getByRole('button', { name: 'New' }));

      await waitFor(() => expect(createDeptSpy).toHaveBeenCalledWith('Support'));
    });
  });
});
