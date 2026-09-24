import { describe, expect, it, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { DeviceManagementPage } from './DeviceManagementPage';
import { AuthProvider } from '../auth/AuthContext';
import { ToastProvider } from '../../components/ui/Toast';
import { clearSession, setTokens } from '../auth/session';
import * as robotsApi from '../../api/robots';
import * as sitesApi from '../../api/sites';
import * as robotModelsApi from '../../api/robotModels';
import { ApiRequestError } from '../../api/client';
import type { Robot, RobotModel, Site } from '../../types/domain';

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

function renderPage() {
  setTokens(fakeToken(['ROBOT_VIEW']), 'refresh-token');
  return render(
    <MemoryRouter>
      <ToastProvider>
        <AuthProvider>
          <DeviceManagementPage />
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

const model: RobotModel = { id: 'model-1', manufacturerName: 'Keenon', name: 'C40 S', sakarProductName: 'Sakar CleanBot 5000' };

const robot: Robot = {
  id: 'robot-1', organizationId: 'org-1', siteId: 'site-1', robotModelId: 'model-1', name: 'CleanBot Alpha',
  serialNumber: '94:BA:06:CA:99:F3', vendorSerialNumber: 'QC402602X0002', status: 'ACTIVE', capabilities: [],
  connectionStatus: 'OFFLINE', lastSeenAt: '2026-09-23T15:56:36.000Z', createdAt: '2026-01-01T00:00:00.000Z',
  warrantyEndDate: '2027-06-12',
};

describe('DeviceManagementPage', () => {
  beforeEach(() => {
    clearSession();
    vi.restoreAllMocks();
    vi.spyOn(sitesApi, 'listAllAccessibleSites').mockResolvedValue([site]);
    vi.spyOn(robotModelsApi, 'listRobotModels').mockResolvedValue([model]);
  });

  it('renders devices reusing existing robot/site/model data', async () => {
    vi.spyOn(robotsApi, 'listRobots').mockResolvedValue(pageOf([robot]));

    renderPage();

    await waitFor(() => expect(screen.getByText('94:BA:06:CA:99:F3')).toBeInTheDocument());
    expect(screen.getByText('Sakar robotics office')).toBeInTheDocument();
    expect(screen.getByText('QC402602X0002')).toBeInTheDocument();
    expect(screen.getByText('Sakar CleanBot 5000')).toBeInTheDocument();
    expect(screen.getByText('Wi-Fi Offline')).toBeInTheDocument();
    expect(screen.getByText('2027-06-12')).toBeInTheDocument();
  });

  it('disables the streaming actions with an explanatory tooltip', async () => {
    vi.spyOn(robotsApi, 'listRobots').mockResolvedValue(pageOf([robot]));

    renderPage();
    await waitFor(() => expect(screen.getByText('94:BA:06:CA:99:F3')).toBeInTheDocument());

    const remoteDesktopButton = screen.getByRole('button', { name: 'remote desktop' });
    expect(remoteDesktopButton).toBeDisabled();
    expect(remoteDesktopButton).toHaveAttribute('title', expect.stringContaining('No device-streaming channel'));
  });

  it('shows an error state when the backend call fails', async () => {
    vi.spyOn(robotsApi, 'listRobots').mockRejectedValue(new ApiRequestError('Service unavailable', 503, null));

    renderPage();

    await waitFor(() => expect(screen.getByText('Could not load devices')).toBeInTheDocument());
  });
});
