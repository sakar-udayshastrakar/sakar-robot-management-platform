import { describe, expect, it, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { StoreManagementPage } from './StoreManagementPage';
import { AuthProvider } from '../auth/AuthContext';
import { ToastProvider } from '../../components/ui/Toast';
import { clearSession, setTokens } from '../auth/session';
import * as sitesApi from '../../api/sites';
import * as organizationsApi from '../../api/organizations';
import { ApiRequestError } from '../../api/client';
import type { Site, Organization } from '../../types/domain';

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
          <StoreManagementPage />
        </AuthProvider>
      </ToastProvider>
    </MemoryRouter>,
  );
}

const sampleSite: Site = {
  id: 'site-1',
  organizationId: 'org-1',
  name: 'Sakar robotics office',
  address: null,
  timezone: null,
  createdAt: '2026-01-01T00:00:00.000Z',
  updatedAt: '2026-01-01T00:00:00.000Z',
  area: 'INDIA',
  contactName: null,
  phone: null,
  email: null,
  sceneType: 'Hotel',
  chainBrand: false,
};

const sampleOrg: Organization = {
  id: 'org-1',
  parentOrganizationId: null,
  name: 'Sakar Robotics',
  orgType: 'SAKAR_ROOT',
  status: 'ACTIVE',
  path: '/org-1/',
  createdAt: '',
  updatedAt: '',
};

describe('StoreManagementPage', () => {
  beforeEach(() => {
    clearSession();
    vi.restoreAllMocks();
    vi.spyOn(organizationsApi, 'getOrganization').mockResolvedValue(sampleOrg);
  });

  it('renders stores with resolved affiliated agent name', async () => {
    vi.spyOn(sitesApi, 'listAllAccessibleSites').mockResolvedValue([sampleSite]);

    renderPage();

    await waitFor(() => expect(screen.getByText('Sakar robotics office')).toBeInTheDocument());
    expect(await screen.findByText('Sakar Robotics')).toBeInTheDocument();
    expect(screen.getByText('INDIA')).toBeInTheDocument();
    expect(screen.getByText('Hotel')).toBeInTheDocument();
    expect(screen.getByText('No')).toBeInTheDocument();
  });

  it('creates a store through the real POST /sites contract', async () => {
    vi.spyOn(sitesApi, 'listAllAccessibleSites').mockResolvedValue([]);
    const createSpy = vi.spyOn(sitesApi, 'createSite').mockResolvedValue(sampleSite);

    renderPage();
    await waitFor(() => expect(screen.getByText('Stores (0)')).toBeInTheDocument());

    await userEvent.click(screen.getByRole('button', { name: 'Add' }));
    await userEvent.type(screen.getByLabelText('Store name'), 'Sakar robotics office');
    const addButtons = screen.getAllByRole('button', { name: 'Add' });
    await userEvent.click(addButtons[addButtons.length - 1]);

    await waitFor(() =>
      expect(createSpy).toHaveBeenCalledWith(expect.objectContaining({ organizationId: 'org-1', name: 'Sakar robotics office' })),
    );
  });

  it('hides Add / Edit / Delete for a caller without ROBOT_CONFIGURE', async () => {
    vi.spyOn(sitesApi, 'listAllAccessibleSites').mockResolvedValue([sampleSite]);

    renderPage(['ROBOT_VIEW']);

    await waitFor(() => expect(screen.getByText('Sakar robotics office')).toBeInTheDocument());
    expect(screen.queryByRole('button', { name: 'Add' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Edit' })).not.toBeInTheDocument();
  });

  it('shows an error state when the backend call fails', async () => {
    vi.spyOn(sitesApi, 'listAllAccessibleSites').mockRejectedValue(new ApiRequestError('Service unavailable', 503, null));

    renderPage();

    await waitFor(() => expect(screen.getByText('Could not load stores')).toBeInTheDocument());
  });
});
