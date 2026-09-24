import { describe, expect, it, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { PhoneDeviceManagementPage } from './PhoneDeviceManagementPage';
import { AuthProvider } from '../auth/AuthContext';
import { ToastProvider } from '../../components/ui/Toast';
import { clearSession, setTokens } from '../auth/session';
import * as iotApi from '../../api/iot';
import * as sitesApi from '../../api/sites';
import { ApiRequestError } from '../../api/client';
import type { PhoneDevice, Site } from '../../types/domain';

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
          <PhoneDeviceManagementPage />
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

const device: PhoneDevice = {
  id: 'device-1', organizationId: 'org-1', siteId: 'site-1', deviceId: 'PH-001', deviceName: 'Front Desk Phone',
  networkingMode: 'WiFi', createdAt: '2026-01-01T00:00:00.000Z', updatedAt: '2026-01-01T00:00:00.000Z',
};

describe('PhoneDeviceManagementPage', () => {
  beforeEach(() => {
    clearSession();
    vi.restoreAllMocks();
    vi.spyOn(sitesApi, 'listAllAccessibleSites').mockResolvedValue([site]);
  });

  it('renders phone devices with resolved store name and Unknown online status', async () => {
    vi.spyOn(iotApi, 'listPhoneDevices').mockResolvedValue([device]);

    renderPage();

    await waitFor(() => expect(screen.getByText('PH-001')).toBeInTheDocument());
    expect(screen.getByText('Front Desk Phone')).toBeInTheDocument();
    expect(screen.getByText('Sakar robotics office')).toBeInTheDocument();
    expect(screen.getByText('Unknown')).toBeInTheDocument();
  });

  it('registers a device through the real POST /iot/phone-devices contract', async () => {
    vi.spyOn(iotApi, 'listPhoneDevices').mockResolvedValue([]);
    const createSpy = vi.spyOn(iotApi, 'registerPhoneDevice').mockResolvedValue(device);

    renderPage();
    await waitFor(() => expect(screen.getByText('Devices (0)')).toBeInTheDocument());

    await userEvent.click(screen.getByRole('button', { name: 'Device input' }));
    await userEvent.selectOptions(screen.getByLabelText('Store'), 'site-1');
    await userEvent.type(screen.getByLabelText('Device ID'), 'PH-002');
    const deviceInputButtons = screen.getAllByRole('button', { name: 'Device input' });
    await userEvent.click(deviceInputButtons[deviceInputButtons.length - 1]);

    await waitFor(() =>
      expect(createSpy).toHaveBeenCalledWith(expect.objectContaining({ organizationId: 'org-1', siteId: 'site-1', deviceId: 'PH-002' })),
    );
  });

  it('hides Device input for a caller without ROBOT_CONFIGURE', async () => {
    vi.spyOn(iotApi, 'listPhoneDevices').mockResolvedValue([device]);

    renderPage(['ROBOT_VIEW']);

    await waitFor(() => expect(screen.getByText('PH-001')).toBeInTheDocument());
    expect(screen.queryByRole('button', { name: 'Device input' })).not.toBeInTheDocument();
  });

  it('shows an error state when the backend call fails', async () => {
    vi.spyOn(iotApi, 'listPhoneDevices').mockRejectedValue(new ApiRequestError('Service unavailable', 503, null));

    renderPage();

    await waitFor(() => expect(screen.getByText('Could not load phone devices')).toBeInTheDocument());
  });
});
