import { describe, expect, it, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { RemoteDeploymentPage } from './RemoteDeploymentPage';
import { AuthProvider } from '../auth/AuthContext';
import { ToastProvider } from '../../components/ui/Toast';
import { clearSession, setTokens } from '../auth/session';
import * as remoteDeploymentsApi from '../../api/remoteDeployments';
import * as robotsApi from '../../api/robots';
import * as sitesApi from '../../api/sites';
import { ApiRequestError } from '../../api/client';
import type { Robot, RemoteDeploymentRecord, Site } from '../../types/domain';

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
          <RemoteDeploymentPage />
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

const robot: Robot = {
  id: 'robot-1', organizationId: 'org-1', siteId: 'site-1', robotModelId: 'model-1', name: 'CleanBot Alpha',
  serialNumber: 'A8:B5:8E:B5:E3:E7', status: 'ACTIVE', capabilities: [], connectionStatus: 'ONLINE',
  lastSeenAt: null, createdAt: '2026-01-01T00:00:00.000Z',
};

const record: RemoteDeploymentRecord = {
  id: 'deploy-1', organizationId: 'org-1', siteId: 'site-1', robotId: 'robot-1', deployedBy: 'Sakar Robotics',
  status: 'RECORDED', notes: null, completedAt: null, createdAt: '2026-05-12T18:35:26.000Z', updatedAt: '2026-05-12T18:35:26.000Z',
};

describe('RemoteDeploymentPage', () => {
  beforeEach(() => {
    clearSession();
    vi.restoreAllMocks();
    vi.spyOn(sitesApi, 'listAllAccessibleSites').mockResolvedValue([site]);
    vi.spyOn(robotsApi, 'listRobots').mockResolvedValue(pageOf([robot]));
  });

  it('renders deployment records with resolved store and robot SN', async () => {
    vi.spyOn(remoteDeploymentsApi, 'listRemoteDeployments').mockResolvedValue([record]);

    renderPage();

    await waitFor(() => expect(screen.getByText('A8:B5:8E:B5:E3:E7')).toBeInTheDocument());
    expect(screen.getByText('Sakar robotics office')).toBeInTheDocument();
    expect(screen.getByText('Sakar Robotics')).toBeInTheDocument();
  });

  it('records a deployment through the real POST /remote-deployments contract', async () => {
    vi.spyOn(remoteDeploymentsApi, 'listRemoteDeployments').mockResolvedValue([]);
    const createSpy = vi.spyOn(remoteDeploymentsApi, 'createRemoteDeployment').mockResolvedValue(record);

    renderPage();
    await waitFor(() => expect(screen.getByText('Deployments (0)')).toBeInTheDocument());

    await userEvent.click(screen.getByRole('button', { name: '+ Create' }));
    await userEvent.selectOptions(screen.getByLabelText('Robot'), 'robot-1');
    await userEvent.click(screen.getByRole('button', { name: 'Create' }));

    await waitFor(() => expect(createSpy).toHaveBeenCalledWith('robot-1', null));
  });

  it('updates status through the real PATCH /remote-deployments/{id}/status contract', async () => {
    vi.spyOn(remoteDeploymentsApi, 'listRemoteDeployments').mockResolvedValue([record]);
    const updateSpy = vi.spyOn(remoteDeploymentsApi, 'updateRemoteDeploymentStatus').mockResolvedValue({ ...record, status: 'COMPLETED' });

    renderPage();
    await waitFor(() => expect(screen.getByText('A8:B5:8E:B5:E3:E7')).toBeInTheDocument());

    await userEvent.selectOptions(screen.getByLabelText(`Update status for deployment ${record.id}`), 'COMPLETED');

    await waitFor(() => expect(updateSpy).toHaveBeenCalledWith('deploy-1', 'COMPLETED'));
  });

  it('shows a read-only badge for a caller without ROBOT_CONFIGURE', async () => {
    vi.spyOn(remoteDeploymentsApi, 'listRemoteDeployments').mockResolvedValue([record]);

    renderPage(['ROBOT_VIEW']);

    await waitFor(() => expect(screen.getByText('RECORDED')).toBeInTheDocument());
    expect(screen.queryByRole('button', { name: '+ Create' })).not.toBeInTheDocument();
  });

  it('shows an error state when the backend call fails', async () => {
    vi.spyOn(remoteDeploymentsApi, 'listRemoteDeployments').mockRejectedValue(new ApiRequestError('Service unavailable', 503, null));

    renderPage();

    await waitFor(() => expect(screen.getByText('Could not load remote deployments')).toBeInTheDocument());
  });
});
