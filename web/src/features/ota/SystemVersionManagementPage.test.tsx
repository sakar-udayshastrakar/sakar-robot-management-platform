import { describe, expect, it, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { SystemVersionManagementPage } from './SystemVersionManagementPage';
import { AuthProvider } from '../auth/AuthContext';
import { ToastProvider } from '../../components/ui/Toast';
import { clearSession, setTokens } from '../auth/session';
import * as otaApi from '../../api/ota';
import * as robotsApi from '../../api/robots';
import { ApiRequestError } from '../../api/client';
import type { SoftwareVersion, Robot } from '../../types/domain';

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
          <SystemVersionManagementPage />
        </AuthProvider>
      </ToastProvider>
    </MemoryRouter>,
  );
}

function mockPage() {
  return { content: [], totalElements: 0, totalPages: 0, number: 0, size: 100, first: true, last: true, empty: true };
}

const sampleVersion: SoftwareVersion = {
  id: 'version-1',
  organizationId: 'org-1',
  packageName: 'clean',
  wholeMachineSoftware: 'C40 S-LS-M014C00-RW-F00',
  packageVersion: 'V258',
  hardwareVersion: 'C40 S-LS-M014C00',
  grayscale: true,
  sizeBytes: 307_000_000,
  createdBy: 'admin@sakarrobotics.com',
  notes: null,
  createdAt: '2026-01-01T00:00:00.000Z',
  updatedAt: '2026-01-01T00:00:00.000Z',
};

describe('SystemVersionManagementPage', () => {
  beforeEach(() => {
    clearSession();
    vi.restoreAllMocks();
    vi.spyOn(robotsApi, 'listRobots').mockResolvedValue(mockPage());
  });

  it('renders versions from the real GET /ota/versions shape', async () => {
    vi.spyOn(otaApi, 'listSoftwareVersions').mockResolvedValue([sampleVersion]);

    renderPage();

    await waitFor(() => expect(screen.getByText('clean')).toBeInTheDocument());
    expect(screen.getByText('V258')).toBeInTheDocument();
    expect(screen.getByText('307.0 MB')).toBeInTheDocument();
  });

  it('registers a version through the real POST /ota/versions contract', async () => {
    vi.spyOn(otaApi, 'listSoftwareVersions').mockResolvedValue([]);
    const createSpy = vi.spyOn(otaApi, 'createSoftwareVersion').mockResolvedValue(sampleVersion);

    renderPage();
    await waitFor(() => expect(screen.getByText('Whole package versions (0)')).toBeInTheDocument());

    await userEvent.click(screen.getByRole('button', { name: 'Add' }));
    await userEvent.type(screen.getByLabelText('Whole package name'), 'clean');
    await userEvent.type(screen.getByLabelText('Whole package version'), 'V258');
    const addButtons = screen.getAllByRole('button', { name: 'Add' });
    await userEvent.click(addButtons[addButtons.length - 1]);

    await waitFor(() =>
      expect(createSpy).toHaveBeenCalledWith(expect.objectContaining({ organizationId: 'org-1', packageName: 'clean', packageVersion: 'V258' })),
    );
  });

  it('pushes a version to a robot through the real POST /ota/versions/{id}/push contract', async () => {
    vi.spyOn(otaApi, 'listSoftwareVersions').mockResolvedValue([sampleVersion]);
    const robot: Robot = {
      id: 'robot-1', organizationId: 'org-1', siteId: null, robotModelId: 'model-1', name: 'CleanBot Alpha',
      serialNumber: 'SN-ALPHA-001', status: 'ACTIVE', capabilities: [], connectionStatus: 'UNKNOWN', lastSeenAt: null,
      createdAt: '2026-01-01T00:00:00.000Z',
    };
    vi.spyOn(robotsApi, 'listRobots').mockResolvedValue({ ...mockPage(), content: [robot], totalElements: 1 });
    const pushSpy = vi.spyOn(otaApi, 'pushSoftwareVersion').mockResolvedValue({
      id: 1, organizationId: 'org-1', robotId: 'robot-1', softwareVersionId: 'version-1',
      oldVersionNumber: null, newVersionNumber: 'V258', status: 'RECORDED', errorMessage: null, grayscale: true,
      createdAt: '2026-01-01T00:00:00.000Z',
    });

    renderPage();
    await waitFor(() => expect(screen.getByText('clean')).toBeInTheDocument());

    await userEvent.click(screen.getByRole('button', { name: 'Push' }));
    await waitFor(() => expect(screen.getByLabelText('Robot')).toBeInTheDocument());
    await userEvent.selectOptions(screen.getByLabelText('Robot'), 'robot-1');
    const pushButtons = screen.getAllByRole('button', { name: 'Push' });
    await userEvent.click(pushButtons[pushButtons.length - 1]);

    await waitFor(() => expect(pushSpy).toHaveBeenCalledWith('version-1', 'robot-1', null));
  });

  it('hides Add / Push for a caller without ROBOT_CONFIGURE', async () => {
    vi.spyOn(otaApi, 'listSoftwareVersions').mockResolvedValue([sampleVersion]);

    renderPage(['ROBOT_VIEW']);

    await waitFor(() => expect(screen.getByText('clean')).toBeInTheDocument());
    expect(screen.queryByRole('button', { name: 'Add' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Push' })).not.toBeInTheDocument();
  });

  it('shows an error state when the backend call fails', async () => {
    vi.spyOn(otaApi, 'listSoftwareVersions').mockRejectedValue(new ApiRequestError('Service unavailable', 503, null));

    renderPage();

    await waitFor(() => expect(screen.getByText('Could not load versions')).toBeInTheDocument());
  });
});
