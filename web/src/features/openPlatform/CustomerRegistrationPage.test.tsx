import { describe, expect, it, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { CustomerRegistrationPage } from './CustomerRegistrationPage';
import { AuthProvider } from '../auth/AuthContext';
import { ToastProvider } from '../../components/ui/Toast';
import { clearSession, setTokens } from '../auth/session';
import * as openPlatformApi from '../../api/openPlatform';
import { ApiRequestError } from '../../api/client';
import type { OpenPlatformRegistration } from '../../types/domain';

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
          <CustomerRegistrationPage />
        </AuthProvider>
      </ToastProvider>
    </MemoryRouter>,
  );
}

const pendingRegistration: OpenPlatformRegistration = {
  id: 'reg-1', organizationId: 'org-1', companyName: 'Sakar Robotics', area: 'IN',
  companyAddress: '1st floor, ANSEC house', systemMatcher: 'Clean', contactInformation: '+919665999862',
  dockingRequirements: 'Robot Remote Call test', status: 'PENDING', submittedBy: 'admin@sakarrobotics.com',
  reviewedBy: null, reviewedAt: null, createdAt: '2026-01-01T00:00:00.000Z', updatedAt: '2026-01-01T00:00:00.000Z',
};

describe('CustomerRegistrationPage', () => {
  beforeEach(() => {
    clearSession();
    vi.restoreAllMocks();
  });

  it('shows a submit form when no registration exists yet', async () => {
    vi.spyOn(openPlatformApi, 'getRegistration').mockResolvedValue(null);

    renderPage();

    await waitFor(() => expect(screen.getByLabelText('Company Name')).toBeInTheDocument());
    expect(screen.queryByText('Pending review')).not.toBeInTheDocument();
  });

  it('shows the read-only summary with a Pending review badge, never an automatic pass', async () => {
    vi.spyOn(openPlatformApi, 'getRegistration').mockResolvedValue(pendingRegistration);

    renderPage(['ROBOT_VIEW']);

    await waitFor(() => expect(screen.getByText('Sakar Robotics')).toBeInTheDocument());
    expect(screen.getByText('Pending review')).toBeInTheDocument();
    expect(screen.queryByText('pass')).not.toBeInTheDocument();
  });

  it('submits a registration through the real POST /open-platform/registration contract', async () => {
    vi.spyOn(openPlatformApi, 'getRegistration').mockResolvedValue(null);
    const submitSpy = vi.spyOn(openPlatformApi, 'submitRegistration').mockResolvedValue(pendingRegistration);

    renderPage();
    await waitFor(() => expect(screen.getByLabelText('Company Name')).toBeInTheDocument());

    await userEvent.type(screen.getByLabelText('Company Name'), 'Sakar Robotics');
    await userEvent.click(screen.getByRole('button', { name: 'Submit' }));

    await waitFor(() =>
      expect(submitSpy).toHaveBeenCalledWith(expect.objectContaining({ organizationId: 'org-1', companyName: 'Sakar Robotics' })),
    );
  });

  it('shows Approve/Reject only for a caller with ROLE_MANAGE', async () => {
    vi.spyOn(openPlatformApi, 'getRegistration').mockResolvedValue(pendingRegistration);
    vi.spyOn(openPlatformApi, 'listRegistrations').mockResolvedValue([pendingRegistration]);

    renderPage(['ROBOT_VIEW', 'ROLE_MANAGE']);

    await waitFor(() => expect(screen.getAllByRole('button', { name: 'Approve' }).length).toBeGreaterThan(0));
    expect(screen.getAllByRole('button', { name: 'Reject' }).length).toBeGreaterThan(0);
  });

  it('shows an error state when the backend call fails', async () => {
    vi.spyOn(openPlatformApi, 'getRegistration').mockRejectedValue(new ApiRequestError('Service unavailable', 503, null));

    renderPage();

    await waitFor(() => expect(screen.getByText('Could not load customer registration')).toBeInTheDocument());
  });
});
