import { describe, expect, it, vi, beforeEach } from 'vitest';
import { render, screen, waitFor, within, fireEvent } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { ApplicationManagementPage } from './ApplicationManagementPage';
import { AuthProvider } from '../auth/AuthContext';
import { ToastProvider } from '../../components/ui/Toast';
import { clearSession, setTokens } from '../auth/session';
import * as openPlatformApi from '../../api/openPlatform';
import { ApiRequestError } from '../../api/client';
import type { OpenPlatformApplication } from '../../types/domain';

function fakeToken(perms: string[]) {
  const header = btoa(JSON.stringify({ alg: 'HS384' }));
  const payload = btoa(
    JSON.stringify({
      sub: 'admin-1',
      email: 'admin@sakarrobotics.com',
      role: 'ORG_ADMIN',
      perms,
      org: 'org-1',
      exp: Math.floor(Date.now() / 1000) + 900,
    }),
  );
  return `${header}.${payload}.sig`;
}

function renderPage(perms: string[] = ['ROBOT_VIEW', 'ROBOT_CONFIGURE']) {
  setTokens(fakeToken(perms), 'refresh-token');
  return render(
    <MemoryRouter>
      <ToastProvider>
        <AuthProvider>
          <ApplicationManagementPage />
        </AuthProvider>
      </ToastProvider>
    </MemoryRouter>,
  );
}

const application: OpenPlatformApplication = {
  id: 'app-1', organizationId: 'org-1', appId: '505253441306625', applicationName: 'Sakar Web',
  businessType: 'industry', accessKey: 'U5Fo6M835W1HrXr2', secretKeyMasked: '••••••••agIN',
  createdBy: 'admin@sakarrobotics.com', createdAt: '2026-08-20T19:13:36.000Z', updatedAt: '2026-08-20T19:13:36.000Z',
};

describe('ApplicationManagementPage', () => {
  beforeEach(() => {
    clearSession();
    vi.restoreAllMocks();
  });

  it('renders applications with a masked secret key, never plaintext', async () => {
    vi.spyOn(openPlatformApi, 'listApplications').mockResolvedValue([application]);

    renderPage();

    await waitFor(() => expect(screen.getByText('Sakar Web')).toBeInTheDocument());
    expect(screen.getByText('505253441306625')).toBeInTheDocument();
    expect(screen.getByText('••••••••agIN')).toBeInTheDocument();
  });

  it('creates an application and reveals the plaintext secret exactly once', async () => {
    vi.spyOn(openPlatformApi, 'listApplications').mockResolvedValue([]);
    const createSpy = vi.spyOn(openPlatformApi, 'createApplication').mockResolvedValue({ application, secretKey: 'plaintext-secret-value' });

    renderPage();
    await waitFor(() => expect(screen.getByText('Applications (0)')).toBeInTheDocument());

    await userEvent.click(screen.getByRole('button', { name: '+ New' }));
    const createDialog = await screen.findByRole('dialog');
    const nameField = within(createDialog).getByLabelText('Application Name');
    fireEvent.change(nameField, { target: { value: 'Sakar Web' } });
    await waitFor(() => expect(nameField).toHaveValue('Sakar Web'));
    await userEvent.click(within(createDialog).getByRole('button', { name: 'Create' }));

    await waitFor(() => expect(createSpy).toHaveBeenCalledWith('org-1', 'Sakar Web', null));
    await waitFor(() => expect(screen.getByDisplayValue('plaintext-secret-value')).toBeInTheDocument());
  });

  it('updates an application through the real PUT contract', async () => {
    vi.spyOn(openPlatformApi, 'listApplications').mockResolvedValue([application]);
    const updateSpy = vi.spyOn(openPlatformApi, 'updateApplication').mockResolvedValue({ ...application, applicationName: 'Sakar Web Renamed' });

    renderPage();
    await waitFor(() => expect(screen.getByText('Sakar Web')).toBeInTheDocument());

    await userEvent.click(screen.getByRole('button', { name: 'Edit' }));
    const editDialog = screen.getByRole('dialog');
    const nameInput = within(editDialog).getByLabelText('Application Name');
    await userEvent.clear(nameInput);
    await userEvent.type(nameInput, 'Sakar Web Renamed');
    await userEvent.click(screen.getByRole('button', { name: 'Save changes' }));

    await waitFor(() => expect(updateSpy).toHaveBeenCalledWith('app-1', 'Sakar Web Renamed', 'industry'));
  });

  it('hides write actions for a caller without ROBOT_CONFIGURE', async () => {
    vi.spyOn(openPlatformApi, 'listApplications').mockResolvedValue([application]);

    renderPage(['ROBOT_VIEW']);

    await waitFor(() => expect(screen.getByText('Sakar Web')).toBeInTheDocument());
    expect(screen.queryByRole('button', { name: '+ New' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Edit' })).not.toBeInTheDocument();
  });

  it('shows an error state when the backend call fails', async () => {
    vi.spyOn(openPlatformApi, 'listApplications').mockRejectedValue(new ApiRequestError('Service unavailable', 503, null));

    renderPage();

    await waitFor(() => expect(screen.getByText('Could not load applications')).toBeInTheDocument());
  });
});
