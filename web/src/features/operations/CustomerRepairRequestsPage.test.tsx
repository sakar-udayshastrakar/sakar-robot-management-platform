import { describe, expect, it, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { CustomerRepairRequestsPage } from './CustomerRepairRequestsPage';
import { AuthProvider } from '../auth/AuthContext';
import { ToastProvider } from '../../components/ui/Toast';
import { clearSession, setTokens } from '../auth/session';
import * as repairApi from '../../api/repairRequests';
import * as sitesApi from '../../api/sites';
import * as robotsApi from '../../api/robots';
import * as organizationsApi from '../../api/organizations';
import { ApiRequestError } from '../../api/client';
import type { Organization, RepairRequest, Site } from '../../types/domain';

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
          <CustomerRepairRequestsPage />
        </AuthProvider>
      </ToastProvider>
    </MemoryRouter>,
  );
}

function pageOf<T>(content: T[]) {
  return { content, totalElements: content.length, totalPages: 1, number: 0, size: 200, first: true, last: true, empty: content.length === 0 };
}

const site: Site = {
  id: 'site-1', organizationId: 'org-1', name: 'Sakar robotics office', address: null, timezone: null,
  createdAt: '2026-01-01T00:00:00.000Z', updatedAt: '2026-01-01T00:00:00.000Z', area: 'INDIA',
  contactName: null, phone: null, email: null, sceneType: 'Hotel', chainBrand: false,
};

const org: Organization = {
  id: 'org-1', parentOrganizationId: null, name: 'Sakar Robotics', orgType: 'SAKAR_ROOT',
  status: 'ACTIVE', path: '/org-1/', createdAt: '', updatedAt: '',
};

const repairRequest: RepairRequest = {
  id: 'req-1', organizationId: 'org-1', siteId: 'site-1', robotId: 'robot-1', workOrderNumber: 'WO1A2B3C',
  symptom: 'Left wheel making noise', status: 'OPEN', reportedBy: 'admin@sakarrobotics.com',
  reportedAt: '2026-09-20T00:00:00.000Z', resolvedAt: null, notes: null,
  createdAt: '2026-09-20T00:00:00.000Z', updatedAt: '2026-09-20T00:00:00.000Z',
};

describe('CustomerRepairRequestsPage', () => {
  beforeEach(() => {
    clearSession();
    vi.restoreAllMocks();
    vi.spyOn(sitesApi, 'listAllAccessibleSites').mockResolvedValue([site]);
    vi.spyOn(organizationsApi, 'getOrganization').mockResolvedValue(org);
    vi.spyOn(robotsApi, 'listRobots').mockResolvedValue(pageOf([]));
  });

  it('renders repair requests with resolved store and reseller names', async () => {
    vi.spyOn(repairApi, 'listRepairRequests').mockResolvedValue([repairRequest]);

    renderPage();

    await waitFor(() => expect(screen.getByText('WO1A2B3C')).toBeInTheDocument());
    expect(screen.getByText('Sakar robotics office')).toBeInTheDocument();
    await waitFor(() => expect(screen.getByText('Sakar Robotics')).toBeInTheDocument());
    expect(screen.getByText('Left wheel making noise')).toBeInTheDocument();
  });

  it('logs a repair request through the real POST /repair-requests contract', async () => {
    vi.spyOn(repairApi, 'listRepairRequests').mockResolvedValue([]);
    const createSpy = vi.spyOn(repairApi, 'createRepairRequest').mockResolvedValue(repairRequest);

    renderPage();
    await waitFor(() => expect(screen.getByText('Work Orders (0)')).toBeInTheDocument());

    await userEvent.click(screen.getByRole('button', { name: 'Construction order on behalf of others' }));
    await userEvent.type(screen.getByLabelText('Symptom'), 'Making noise');
    await userEvent.click(screen.getByRole('button', { name: 'Create' }));

    await waitFor(() =>
      expect(createSpy).toHaveBeenCalledWith(expect.objectContaining({ organizationId: 'org-1', symptom: 'Making noise' })),
    );
  });

  it('hides write actions for a caller without ROBOT_CONFIGURE', async () => {
    vi.spyOn(repairApi, 'listRepairRequests').mockResolvedValue([repairRequest]);

    renderPage(['ROBOT_VIEW']);

    await waitFor(() => expect(screen.getByText('WO1A2B3C')).toBeInTheDocument());
    expect(screen.queryByRole('button', { name: 'Construction order on behalf of others' })).not.toBeInTheDocument();
  });

  it('shows an error state when the backend call fails', async () => {
    vi.spyOn(repairApi, 'listRepairRequests').mockRejectedValue(new ApiRequestError('Service unavailable', 503, null));

    renderPage();

    await waitFor(() => expect(screen.getByText('Could not load repair requests')).toBeInTheDocument());
  });
});
