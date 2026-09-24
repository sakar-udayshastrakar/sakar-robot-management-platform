import { describe, expect, it, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { UpdateRecordPage } from './UpdateRecordPage';
import { AuthProvider } from '../auth/AuthContext';
import { ToastProvider } from '../../components/ui/Toast';
import { clearSession, setTokens } from '../auth/session';
import * as otaApi from '../../api/ota';
import * as robotsApi from '../../api/robots';
import { ApiRequestError } from '../../api/client';
import type { DeploymentRecord, Robot, SoftwareVersion } from '../../types/domain';

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
          <UpdateRecordPage />
        </AuthProvider>
      </ToastProvider>
    </MemoryRouter>,
  );
}

function mockPage() {
  return { content: [], totalElements: 0, totalPages: 0, number: 0, size: 100, first: true, last: true, empty: true };
}

const robot: Robot = {
  id: 'robot-1', organizationId: 'org-1', siteId: null, robotModelId: 'model-1', name: 'CleanBot Alpha',
  serialNumber: 'SN-ALPHA-001', status: 'ACTIVE', capabilities: [], connectionStatus: 'UNKNOWN', lastSeenAt: null,
  createdAt: '2026-01-01T00:00:00.000Z',
};

const version: SoftwareVersion = {
  id: 'version-1', organizationId: 'org-1', packageName: 'clean', wholeMachineSoftware: null,
  packageVersion: 'V258', hardwareVersion: null, grayscale: false, sizeBytes: null,
  createdBy: 'admin@sakarrobotics.com', notes: null, createdAt: '2026-01-01T00:00:00.000Z', updatedAt: '2026-01-01T00:00:00.000Z',
};

const record: DeploymentRecord = {
  id: 1, organizationId: 'org-1', robotId: 'robot-1', softwareVersionId: 'version-1',
  oldVersionNumber: 'V257', newVersionNumber: 'V258', status: 'RECORDED', errorMessage: null,
  grayscale: false, createdAt: '2026-01-02T00:00:00.000Z',
};

describe('UpdateRecordPage', () => {
  beforeEach(() => {
    clearSession();
    vi.restoreAllMocks();
    vi.spyOn(robotsApi, 'listRobots').mockResolvedValue({ ...mockPage(), content: [robot], totalElements: 1 });
    vi.spyOn(otaApi, 'listSoftwareVersions').mockResolvedValue([version]);
  });

  it('renders deployment records with resolved robot serial and package name', async () => {
    vi.spyOn(otaApi, 'listDeploymentRecords').mockResolvedValue([record]);

    renderPage();

    await waitFor(() => expect(screen.getByText('SN-ALPHA-001')).toBeInTheDocument());
    expect(screen.getByText('clean')).toBeInTheDocument();
    expect(screen.getByText('V257')).toBeInTheDocument();
    expect(screen.getByText('V258')).toBeInTheDocument();
    expect(screen.getByText('Recorded')).toBeInTheDocument();
  });

  it('shows an empty state when there are no records', async () => {
    vi.spyOn(otaApi, 'listDeploymentRecords').mockResolvedValue([]);

    renderPage();

    await waitFor(() => expect(screen.getByText('No update records yet')).toBeInTheDocument());
  });

  it('shows an error state when the backend call fails', async () => {
    vi.spyOn(otaApi, 'listDeploymentRecords').mockRejectedValue(new ApiRequestError('Service unavailable', 503, null));

    renderPage();

    await waitFor(() => expect(screen.getByText('Could not load update records')).toBeInTheDocument());
  });
});
