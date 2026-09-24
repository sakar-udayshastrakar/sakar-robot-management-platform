import { describe, expect, it, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { RolesPage } from './RolesPage';
import { AuthProvider } from '../auth/AuthContext';
import { ToastProvider } from '../../components/ui/Toast';
import { clearSession, setTokens } from '../auth/session';
import * as rolesApi from '../../api/roles';
import * as permissionsApi from '../../api/permissions';
import { ApiRequestError } from '../../api/client';
import type { RoleWithPermissions, PermissionCatalogEntry } from '../../types/domain';

function fakeToken(perms: string[]) {
  const header = btoa(JSON.stringify({ alg: 'HS384' }));
  const payload = btoa(
    JSON.stringify({
      sub: 'admin-1',
      email: 'admin@sakarrobotics.com',
      role: 'SUPER_ADMIN',
      perms,
      org: null,
      exp: Math.floor(Date.now() / 1000) + 900,
    }),
  );
  return `${header}.${payload}.sig`;
}

function renderPage(perms: string[] = ['USER_MANAGE', 'ROLE_MANAGE']) {
  setTokens(fakeToken(perms), 'refresh-token');
  return render(
    <MemoryRouter>
      <ToastProvider>
        <AuthProvider>
          <RolesPage />
        </AuthProvider>
      </ToastProvider>
    </MemoryRouter>,
  );
}

const sampleRoles: RoleWithPermissions[] = [
  { id: 'role-1', name: 'TECHNICIAN', description: 'Field technician', permissions: ['ROBOT_VIEW'] },
];

const catalog: PermissionCatalogEntry[] = [
  { id: 'perm-1', code: 'ROBOT_VIEW', description: 'View robot identity, status, telemetry, and history' },
  { id: 'perm-2', code: 'ROBOT_DIAGNOSTICS', description: 'View diagnostic-level robot detail' },
];

describe('RolesPage', () => {
  beforeEach(() => {
    clearSession();
    vi.restoreAllMocks();
  });

  it('renders roles from the real GET /roles shape', async () => {
    vi.spyOn(rolesApi, 'listRoles').mockResolvedValue(sampleRoles);

    renderPage();

    await waitFor(() => expect(screen.getByText('TECHNICIAN')).toBeInTheDocument());
    expect(screen.getByText('Field technician')).toBeInTheDocument();
  });

  it('hides the Edit action for a caller without ROLE_MANAGE', async () => {
    vi.spyOn(rolesApi, 'listRoles').mockResolvedValue(sampleRoles);

    renderPage(['USER_MANAGE']);

    await waitFor(() => expect(screen.getByText('TECHNICIAN')).toBeInTheDocument());
    expect(screen.queryByRole('button', { name: 'Edit' })).not.toBeInTheDocument();
  });

  it('edits a role\'s description and permissions through the real PUT /roles/{id} contract', async () => {
    vi.spyOn(rolesApi, 'listRoles').mockResolvedValue(sampleRoles);
    vi.spyOn(permissionsApi, 'listPermissions').mockResolvedValue(catalog);
    const updateSpy = vi.spyOn(rolesApi, 'updateRole').mockResolvedValue({
      ...sampleRoles[0],
      description: 'Updated description',
      permissions: ['ROBOT_VIEW', 'ROBOT_DIAGNOSTICS'],
    });

    renderPage();
    await waitFor(() => expect(screen.getByText('TECHNICIAN')).toBeInTheDocument());

    await userEvent.click(screen.getByRole('button', { name: 'Edit' }));
    await waitFor(() => expect(screen.getByText('ROBOT_DIAGNOSTICS')).toBeInTheDocument());

    await userEvent.click(screen.getByLabelText(/ROBOT_DIAGNOSTICS/));
    await userEvent.click(screen.getByRole('button', { name: 'Save changes' }));

    await waitFor(() =>
      expect(updateSpy).toHaveBeenCalledWith(
        'role-1',
        'Field technician',
        expect.arrayContaining(['ROBOT_VIEW', 'ROBOT_DIAGNOSTICS']),
      ),
    );
  });

  it('shows an error state when the backend call fails', async () => {
    vi.spyOn(rolesApi, 'listRoles').mockRejectedValue(new ApiRequestError('Service unavailable', 503, null));

    renderPage();

    await waitFor(() => expect(screen.getByText('Could not load roles')).toBeInTheDocument());
  });
});
