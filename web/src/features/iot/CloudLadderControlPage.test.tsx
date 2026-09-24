import { describe, expect, it, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { CloudLadderControlPage } from './CloudLadderControlPage';
import { AuthProvider } from '../auth/AuthContext';
import { ToastProvider } from '../../components/ui/Toast';
import { clearSession, setTokens } from '../auth/session';
import * as iotApi from '../../api/iot';
import * as sitesApi from '../../api/sites';
import * as robotsApi from '../../api/robots';
import { ApiRequestError } from '../../api/client';
import type { ElevatorDevice, LadderControlStoreBinding, Site } from '../../types/domain';

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
          <CloudLadderControlPage />
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

const binding: LadderControlStoreBinding = {
  id: 'binding-1', organizationId: 'org-1', siteId: 'site-1', manufacturer: 'OTIS',
  buildingId: 'BLDG-1', clientId: 'CLIENT-1', createdAt: '2026-01-01T00:00:00.000Z', updatedAt: '2026-01-01T00:00:00.000Z',
};

const device: ElevatorDevice = {
  id: 'device-1', organizationId: 'org-1', siteId: 'site-1', deviceId: 'EL-001', deviceName: 'Lobby Elevator',
  building: null, protocol: null, networkingMode: null, communicationMode: null,
  createdAt: '2026-01-01T00:00:00.000Z', updatedAt: '2026-01-01T00:00:00.000Z',
};

describe('CloudLadderControlPage', () => {
  beforeEach(() => {
    clearSession();
    vi.restoreAllMocks();
    vi.spyOn(sitesApi, 'listAllAccessibleSites').mockResolvedValue([site]);
    vi.spyOn(iotApi, 'listElevatorDevices').mockResolvedValue([device]);
    vi.spyOn(iotApi, 'listElevatorConfigurations').mockResolvedValue([]);
    vi.spyOn(iotApi, 'listElevatorConfigurationDeliveries').mockResolvedValue([]);
    vi.spyOn(robotsApi, 'listRobots').mockResolvedValue(pageOf([]));
  });

  it('renders store bindings on the Store binding tab', async () => {
    vi.spyOn(iotApi, 'listLadderControlBindings').mockResolvedValue([binding]);

    renderPage();

    await waitFor(() => expect(screen.getByText('OTIS')).toBeInTheDocument());
    expect(screen.getByText('Sakar robotics office')).toBeInTheDocument();
    expect(screen.getByText('BLDG-1')).toBeInTheDocument();
  });

  it('shows only bound-store devices on the Tripartite ladder control equipment management tab', async () => {
    vi.spyOn(iotApi, 'listLadderControlBindings').mockResolvedValue([binding]);

    renderPage();
    await waitFor(() => expect(screen.getByText('OTIS')).toBeInTheDocument());

    await userEvent.click(screen.getByRole('tab', { name: 'Tripartite ladder control equipment management' }));

    await waitFor(() => expect(screen.getByText('EL-001')).toBeInTheDocument());
  });

  it('creates a new store binding through the real POST contract', async () => {
    vi.spyOn(iotApi, 'listLadderControlBindings').mockResolvedValue([]);
    const createSpy = vi.spyOn(iotApi, 'createLadderControlBinding').mockResolvedValue(binding);

    renderPage();
    await waitFor(() => expect(screen.getByText('Bindings (0)')).toBeInTheDocument());

    await userEvent.click(screen.getByRole('button', { name: 'New store binding' }));
    await userEvent.selectOptions(screen.getByLabelText('Store'), 'site-1');
    await userEvent.type(screen.getByLabelText('Ladder control manufacturer'), 'OTIS');
    await userEvent.click(screen.getByRole('button', { name: 'Create' }));

    await waitFor(() =>
      expect(createSpy).toHaveBeenCalledWith(expect.objectContaining({ organizationId: 'org-1', siteId: 'site-1', manufacturer: 'OTIS' })),
    );
  });

  it('shows an error state when the backend call fails', async () => {
    vi.spyOn(iotApi, 'listLadderControlBindings').mockRejectedValue(new ApiRequestError('Service unavailable', 503, null));

    renderPage();

    await waitFor(() => expect(screen.getByText('Could not load store bindings')).toBeInTheDocument());
  });
});
