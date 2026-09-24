import { describe, expect, it, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { ElevatorConfigurationPage } from './ElevatorConfigurationPage';
import { AuthProvider } from '../auth/AuthContext';
import { ToastProvider } from '../../components/ui/Toast';
import { clearSession, setTokens } from '../auth/session';
import * as iotApi from '../../api/iot';
import * as sitesApi from '../../api/sites';
import * as robotsApi from '../../api/robots';
import { ApiRequestError } from '../../api/client';
import type { ElevatorConfiguration, ElevatorConfigurationEvent, ElevatorDevice, Robot, Site } from '../../types/domain';

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
          <ElevatorConfigurationPage />
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

const device: ElevatorDevice = {
  id: 'device-1', organizationId: 'org-1', siteId: 'site-1', deviceId: 'EL-001', deviceName: 'Lobby Elevator',
  building: 'Tower A', protocol: null, networkingMode: null, communicationMode: null,
  createdAt: '2026-01-01T00:00:00.000Z', updatedAt: '2026-01-01T00:00:00.000Z',
};

const robot: Robot = {
  id: 'robot-1', organizationId: 'org-1', siteId: 'site-1', robotModelId: 'model-1', name: 'CleanBot Alpha',
  serialNumber: 'SN-ALPHA-001', status: 'ACTIVE', capabilities: [], connectionStatus: 'ONLINE',
  lastSeenAt: null, createdAt: '2026-01-01T00:00:00.000Z',
};

const configuration: ElevatorConfiguration = {
  id: 'config-1', organizationId: 'org-1', siteId: 'site-1', elevatorDeviceId: 'device-1', robotId: 'robot-1',
  name: 'Lobby to Floor 3', notes: null, modifiedBy: 'admin@sakarrobotics.com',
  createdAt: '2026-01-01T00:00:00.000Z', updatedAt: '2026-01-01T00:00:00.000Z',
};

const configEvent: ElevatorConfigurationEvent = {
  id: 1, elevatorConfigurationId: 'config-1', eventType: 'CREATED', detail: null, createdAt: '2026-01-01T00:00:00.000Z',
};

describe('ElevatorConfigurationPage', () => {
  beforeEach(() => {
    clearSession();
    vi.restoreAllMocks();
    vi.spyOn(sitesApi, 'listAllAccessibleSites').mockResolvedValue([site]);
    vi.spyOn(iotApi, 'listElevatorDevices').mockResolvedValue([device]);
    vi.spyOn(robotsApi, 'listRobots').mockResolvedValue(pageOf([robot]));
  });

  it('renders configurations with resolved store, elevator, and robot SN', async () => {
    vi.spyOn(iotApi, 'listElevatorConfigurations').mockResolvedValue([configuration]);

    renderPage();

    await waitFor(() => expect(screen.getByText('Lobby to Floor 3')).toBeInTheDocument());
    expect(screen.getByText('Sakar robotics office')).toBeInTheDocument();
    expect(screen.getByText('Lobby Elevator')).toBeInTheDocument();
    expect(screen.getByText('SN-ALPHA-001')).toBeInTheDocument();
  });

  it('creates a configuration through the real POST /iot/elevator-configurations contract', async () => {
    vi.spyOn(iotApi, 'listElevatorConfigurations').mockResolvedValue([]);
    const createSpy = vi.spyOn(iotApi, 'createElevatorConfiguration').mockResolvedValue(configuration);

    renderPage();
    await waitFor(() => expect(screen.getByText('Configurations (0)')).toBeInTheDocument());

    await userEvent.click(screen.getByRole('button', { name: 'Create elevator configuration' }));
    await userEvent.type(screen.getByLabelText('Configuration name'), 'Lobby to Floor 3');
    await userEvent.selectOptions(screen.getByLabelText('Store'), 'site-1');
    await userEvent.selectOptions(screen.getByLabelText('Elevator'), 'device-1');
    await userEvent.selectOptions(screen.getByLabelText('Robot SN'), 'robot-1');
    await userEvent.click(screen.getByRole('button', { name: 'Create' }));

    await waitFor(() =>
      expect(createSpy).toHaveBeenCalledWith(expect.objectContaining({
        organizationId: 'org-1', siteId: 'site-1', elevatorDeviceId: 'device-1', robotId: 'robot-1', name: 'Lobby to Floor 3',
      })),
    );
  });

  it('shows the set record tab with the real change history', async () => {
    vi.spyOn(iotApi, 'listElevatorConfigurations').mockResolvedValue([configuration]);
    vi.spyOn(iotApi, 'listElevatorConfigurationEvents').mockResolvedValue([configEvent]);

    renderPage();
    await waitFor(() => expect(screen.getByText('Lobby to Floor 3')).toBeInTheDocument());

    await userEvent.click(screen.getByRole('tab', { name: 'set record' }));

    await waitFor(() => expect(screen.getByText('CREATED')).toBeInTheDocument());
  });

  it('shows an error state when the backend call fails', async () => {
    vi.spyOn(iotApi, 'listElevatorConfigurations').mockRejectedValue(new ApiRequestError('Service unavailable', 503, null));

    renderPage();

    await waitFor(() => expect(screen.getByText('Could not load elevator configurations')).toBeInTheDocument());
  });
});
