import { describe, expect, it, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { RobotsListPage } from './RobotsListPage';
import { AuthProvider } from '../auth/AuthContext';
import { ToastProvider } from '../../components/ui/Toast';
import * as robotsApi from '../../api/robots';
import * as sitesApi from '../../api/sites';
import { ApiRequestError } from '../../api/client';
import type { Robot } from '../../types/domain';

function renderPage() {
  return render(
    <MemoryRouter>
      <ToastProvider>
        <AuthProvider>
          <RobotsListPage />
        </AuthProvider>
      </ToastProvider>
    </MemoryRouter>,
  );
}

const sampleRobots: Robot[] = [
  {
    id: 'robot-1',
    organizationId: 'org-1',
    siteId: 'site-1',
    robotModelId: 'model-1',
    name: 'CleanBot Alpha',
    serialNumber: 'SN-ALPHA-001',
    status: 'ACTIVE',
    capabilities: ['GET_STATUS'],
    createdAt: new Date().toISOString(),
  },
  {
    id: 'robot-2',
    organizationId: 'org-1',
    siteId: 'site-1',
    robotModelId: 'model-1',
    name: 'CleanBot Beta',
    serialNumber: 'SN-BETA-002',
    status: 'REGISTERED',
    capabilities: ['GET_STATUS'],
    createdAt: new Date().toISOString(),
  },
];

function mockPage(content: Robot[]) {
  return {
    content, totalElements: content.length, totalPages: content.length ? 1 : 0, number: 0, size: 25, first: true, last: true, empty: content.length === 0,
  };
}

describe('RobotsListPage', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
    vi.spyOn(sitesApi, 'listSitesByOrganization').mockResolvedValue([]);
    vi.spyOn(robotsApi, 'getRobotStatus').mockRejectedValue(new ApiRequestError('unavailable', 503, null));
  });

  it('renders robots returned by the real GET /robots shape', async () => {
    vi.spyOn(robotsApi, 'listRobots').mockResolvedValue(mockPage(sampleRobots));

    renderPage();

    await waitFor(() => expect(screen.getByText('CleanBot Alpha')).toBeInTheDocument());
    expect(screen.getByText('CleanBot Beta')).toBeInTheDocument();
  });

  it('filters the fleet by name/serial number via the search bar', async () => {
    vi.spyOn(robotsApi, 'listRobots').mockResolvedValue(mockPage(sampleRobots));

    renderPage();
    await waitFor(() => expect(screen.getByText('CleanBot Alpha')).toBeInTheDocument());

    await userEvent.type(screen.getByLabelText('Search robots by name or serial number'), 'BETA');

    expect(screen.queryByText('CleanBot Alpha')).not.toBeInTheDocument();
    expect(screen.getByText('CleanBot Beta')).toBeInTheDocument();
  });

  it('shows an empty state when the fleet has no robots at all', async () => {
    vi.spyOn(robotsApi, 'listRobots').mockResolvedValue(mockPage([]));

    renderPage();

    await waitFor(() => expect(screen.getByText('No robots registered')).toBeInTheDocument());
  });

  it('shows a filtered-empty state when filters exclude every robot', async () => {
    vi.spyOn(robotsApi, 'listRobots').mockResolvedValue(mockPage(sampleRobots));

    renderPage();
    await waitFor(() => expect(screen.getByText('CleanBot Alpha')).toBeInTheDocument());

    await userEvent.type(screen.getByLabelText('Search robots by name or serial number'), 'no-such-robot');

    await waitFor(() => expect(screen.getByText('No robots match these filters')).toBeInTheDocument());
  });

  it('shows an error state when the backend call fails', async () => {
    vi.spyOn(robotsApi, 'listRobots').mockRejectedValue(new ApiRequestError('Service unavailable', 503, null));

    renderPage();

    await waitFor(() => expect(screen.getByText('Could not load robots')).toBeInTheDocument());
    expect(screen.getByText('Service unavailable')).toBeInTheDocument();
  });
});
