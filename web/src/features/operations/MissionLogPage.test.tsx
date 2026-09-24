import { describe, expect, it, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { MissionLogPage } from './MissionLogPage';
import { AuthProvider } from '../auth/AuthContext';
import { ToastProvider } from '../../components/ui/Toast';
import { clearSession, setTokens } from '../auth/session';
import * as tasksApi from '../../api/tasks';
import * as robotsApi from '../../api/robots';
import * as sitesApi from '../../api/sites';
import { ApiRequestError } from '../../api/client';
import type { Robot, RobotTask, Site } from '../../types/domain';

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
          <MissionLogPage />
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
  serialNumber: 'SN-ALPHA-001', vendorSerialNumber: 'VEN-001', status: 'ACTIVE', capabilities: [],
  connectionStatus: 'ONLINE', lastSeenAt: null, createdAt: '2026-01-01T00:00:00.000Z',
};

const task: RobotTask = {
  id: 'task-1', robotId: 'robot-1', organizationId: 'org-1', createdBy: 'admin-1', taskType: 'SWEEP',
  parameters: JSON.stringify({ areaIds: ['area-9'] }), status: 'COMPLETED',
  createdAt: '2026-01-02T00:00:00.000Z', updatedAt: '2026-01-02T01:00:00.000Z',
};

describe('MissionLogPage', () => {
  beforeEach(() => {
    clearSession();
    vi.restoreAllMocks();
    vi.spyOn(robotsApi, 'listRobots').mockResolvedValue(pageOf([robot]));
    vi.spyOn(sitesApi, 'listAllAccessibleSites').mockResolvedValue([site]);
  });

  it('renders task rows with resolved store, robot SN, and destination', async () => {
    vi.spyOn(tasksApi, 'listAllAccessibleTasks').mockResolvedValue(pageOf([task]));

    renderPage();

    await waitFor(() => expect(screen.getByText('SN-ALPHA-001')).toBeInTheDocument());
    expect(screen.getByText('Sakar robotics office')).toBeInTheDocument();
    expect(screen.getByText('VEN-001')).toBeInTheDocument();
    expect(screen.getByText('area-9')).toBeInTheDocument();
    expect(screen.getAllByText('COMPLETED').length).toBeGreaterThan(0);
  });

  it('filters by business line derived from the store scene type', async () => {
    vi.spyOn(tasksApi, 'listAllAccessibleTasks').mockResolvedValue(pageOf([task]));

    renderPage();
    await waitFor(() => expect(screen.getByText('SN-ALPHA-001')).toBeInTheDocument());

    const catering = screen.queryByRole('button', { name: 'Hotel' });
    expect(catering).toBeInTheDocument();
  });

  it('shows an empty state when there are no tasks', async () => {
    vi.spyOn(tasksApi, 'listAllAccessibleTasks').mockResolvedValue(pageOf([]));

    renderPage();

    await waitFor(() => expect(screen.getByText('No Data')).toBeInTheDocument());
  });

  it('shows an error state when the backend call fails', async () => {
    vi.spyOn(tasksApi, 'listAllAccessibleTasks').mockRejectedValue(new ApiRequestError('Service unavailable', 503, null));

    renderPage();

    await waitFor(() => expect(screen.getByText('Could not load mission log')).toBeInTheDocument());
  });
});
