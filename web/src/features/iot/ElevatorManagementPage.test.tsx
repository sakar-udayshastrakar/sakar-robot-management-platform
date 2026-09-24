import { describe, expect, it, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { ElevatorManagementPage } from './ElevatorManagementPage';
import { AuthProvider } from '../auth/AuthContext';
import { ToastProvider } from '../../components/ui/Toast';
import { clearSession, setTokens } from '../auth/session';
import * as iotApi from '../../api/iot';
import * as sitesApi from '../../api/sites';
import { ApiRequestError } from '../../api/client';
import type { ElevatorDevice, Site } from '../../types/domain';

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
          <ElevatorManagementPage />
        </AuthProvider>
      </ToastProvider>
    </MemoryRouter>,
  );
}

const site: Site = {
  id: 'site-1', organizationId: 'org-1', name: 'Sakar robotics office', address: null, timezone: null,
  createdAt: '2026-01-01T00:00:00.000Z', updatedAt: '2026-01-01T00:00:00.000Z', area: 'INDIA',
  contactName: null, phone: null, email: null, sceneType: 'Hotel', chainBrand: false,
};

const device: ElevatorDevice = {
  id: 'device-1', organizationId: 'org-1', siteId: 'site-1', deviceId: 'EL-001', deviceName: 'Lobby Elevator',
  building: 'Tower A', protocol: 'OTIS-IoT', networkingMode: 'WiFi', communicationMode: 'MQTT',
  createdAt: '2026-01-01T00:00:00.000Z', updatedAt: '2026-01-01T00:00:00.000Z',
};

describe('ElevatorManagementPage', () => {
  beforeEach(() => {
    clearSession();
    vi.restoreAllMocks();
    vi.spyOn(sitesApi, 'listAllAccessibleSites').mockResolvedValue([site]);
  });

  it('renders elevator devices with resolved store name and Unknown online status', async () => {
    vi.spyOn(iotApi, 'listElevatorDevices').mockResolvedValue([device]);

    renderPage();

    await waitFor(() => expect(screen.getByText('EL-001')).toBeInTheDocument());
    expect(screen.getByText('Lobby Elevator')).toBeInTheDocument();
    expect(screen.getByText('Sakar robotics office')).toBeInTheDocument();
    expect(screen.getAllByText('Unknown').length).toBeGreaterThanOrEqual(2);
  });

  it('registers a device through the real POST /iot/elevator-devices contract', async () => {
    vi.spyOn(iotApi, 'listElevatorDevices').mockResolvedValue([]);
    const createSpy = vi.spyOn(iotApi, 'registerElevatorDevice').mockResolvedValue(device);

    renderPage();
    await waitFor(() => expect(screen.getByText('Devices (0)')).toBeInTheDocument());

    await userEvent.click(screen.getByRole('button', { name: 'Device input' }));
    await userEvent.selectOptions(screen.getByLabelText('Store'), 'site-1');
    await userEvent.type(screen.getByLabelText('Device ID'), 'EL-002');
    const deviceInputButtons = screen.getAllByRole('button', { name: 'Device input' });
    await userEvent.click(deviceInputButtons[deviceInputButtons.length - 1]);

    await waitFor(() =>
      expect(createSpy).toHaveBeenCalledWith(expect.objectContaining({ organizationId: 'org-1', siteId: 'site-1', deviceId: 'EL-002' })),
    );
  });

  it('hides Device input for a caller without ROBOT_CONFIGURE', async () => {
    vi.spyOn(iotApi, 'listElevatorDevices').mockResolvedValue([device]);

    renderPage(['ROBOT_VIEW']);

    await waitFor(() => expect(screen.getByText('EL-001')).toBeInTheDocument());
    expect(screen.queryByRole('button', { name: 'Device input' })).not.toBeInTheDocument();
  });

  it('shows an error state when the backend call fails', async () => {
    vi.spyOn(iotApi, 'listElevatorDevices').mockRejectedValue(new ApiRequestError('Service unavailable', 503, null));

    renderPage();

    await waitFor(() => expect(screen.getByText('Could not load elevator devices')).toBeInTheDocument());
  });
});
