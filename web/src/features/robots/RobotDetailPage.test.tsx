import { describe, expect, it, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { RobotDetailPage } from './RobotDetailPage';
import { RobotsListPage } from './RobotsListPage';
import { AuthProvider } from '../auth/AuthContext';
import { ToastProvider } from '../../components/ui/Toast';
import * as robotsApi from '../../api/robots';
import * as sitesApi from '../../api/sites';
import { ApiRequestError } from '../../api/client';
import type { Robot, Site } from '../../types/domain';

const sampleRobot: Robot = {
  id: 'robot-1',
  organizationId: 'org-1',
  siteId: 'site-1',
  robotModelId: 'model-1',
  name: 'CleanBot Alpha',
  serialNumber: 'SN-ALPHA-001',
  status: 'ACTIVE',
  capabilities: ['GET_STATUS', 'GET_BATTERY'],
  createdAt: '2026-01-01T00:00:00.000Z',
};

const sampleSite: Site = {
  id: 'site-1',
  organizationId: 'org-1',
  name: 'Sakar HQ',
  address: null,
  timezone: null,
  createdAt: '2026-01-01T00:00:00.000Z',
  updatedAt: '2026-01-01T00:00:00.000Z',
};

function mockPage(content: Robot[]) {
  return {
    content, totalElements: content.length, totalPages: content.length ? 1 : 0, number: 0, size: 25, first: true, last: true, empty: content.length === 0,
  };
}

function renderDetailPage(initialPath = '/robots/robot-1') {
  return render(
    <MemoryRouter initialEntries={[initialPath]}>
      <ToastProvider>
        <AuthProvider>
          <Routes>
            <Route path="/robots/:id" element={<RobotDetailPage />} />
          </Routes>
        </AuthProvider>
      </ToastProvider>
    </MemoryRouter>,
  );
}

function renderListToDetailFlow() {
  return render(
    <MemoryRouter initialEntries={['/robots']}>
      <ToastProvider>
        <AuthProvider>
          <Routes>
            <Route path="/robots" element={<RobotsListPage />} />
            <Route path="/robots/:id" element={<RobotDetailPage />} />
          </Routes>
        </AuthProvider>
      </ToastProvider>
    </MemoryRouter>,
  );
}

describe('RobotDetailPage', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
    vi.spyOn(sitesApi, 'listSitesByOrganization').mockResolvedValue([sampleSite]);
    // The header's compact live-state indicator probes status on mount for
    // every test unless a case overrides it — default to "unavailable" so
    // tests that don't care about the header badge aren't forced to mock it.
    vi.spyOn(robotsApi, 'getRobotStatus').mockRejectedValue(new ApiRequestError('unavailable', 503, null));
  });

  it('shows a loading state while the robot is being fetched', () => {
    vi.spyOn(robotsApi, 'getRobot').mockReturnValue(new Promise(() => {}));

    renderDetailPage();

    expect(screen.getByText('Loading robot…')).toBeInTheDocument();
  });

  it('renders real robot information from GET /robots/{id} once loaded', async () => {
    vi.spyOn(robotsApi, 'getRobot').mockResolvedValue(sampleRobot);

    renderDetailPage();

    await waitFor(() => expect(screen.getByRole('heading', { name: 'CleanBot Alpha' })).toBeInTheDocument());

    expect(screen.getByText('SN-ALPHA-001')).toBeInTheDocument();
    expect((await screen.findAllByText('Sakar HQ')).length).toBeGreaterThan(0);
    expect(screen.getAllByText('ACTIVE').length).toBeGreaterThan(0);
    expect(screen.getByText('GET_STATUS')).toBeInTheDocument();
    expect(screen.getByText('GET_BATTERY')).toBeInTheDocument();
  });

  it('renders live status from GET /robots/{id}/status after checking', async () => {
    vi.spyOn(robotsApi, 'getRobot').mockResolvedValue(sampleRobot);
    vi.spyOn(robotsApi, 'getRobotBattery').mockRejectedValue(new ApiRequestError('unavailable', 503, null));

    renderDetailPage();
    await waitFor(() => expect(screen.getByRole('heading', { name: 'CleanBot Alpha' })).toBeInTheDocument());

    // Override the default rejected mock for the explicit "Check Live
    // Status" click inside the Overview tab (the header's own mount-time
    // probe already ran and failed, which is fine — this call is separate).
    vi.spyOn(robotsApi, 'getRobotStatus').mockResolvedValue({
      mainState: 'IDLE', subState: null, online: true, observedAt: '2026-01-02T00:00:00.000Z', raw: {},
    });

    await userEvent.click(screen.getByRole('button', { name: 'Check Live Status' }));

    expect(await screen.findByText('Connected')).toBeInTheDocument();
    expect(screen.getByText('IDLE')).toBeInTheDocument();
  });

  it('renders battery percentage and charging state from GET /robots/{id}/battery after checking', async () => {
    vi.spyOn(robotsApi, 'getRobot').mockResolvedValue(sampleRobot);
    vi.spyOn(robotsApi, 'getRobotStatus').mockResolvedValue({
      mainState: 'IDLE', subState: null, online: true, observedAt: '2026-01-02T00:00:00.000Z', raw: {},
    });
    vi.spyOn(robotsApi, 'getRobotBattery').mockResolvedValue({
      percentage: 77, charging: false, observedAt: '2026-01-02T00:00:00.000Z',
    });

    renderDetailPage();
    await waitFor(() => expect(screen.getByRole('heading', { name: 'CleanBot Alpha' })).toBeInTheDocument());

    await userEvent.click(screen.getByRole('button', { name: 'Check Live Status' }));

    expect(await screen.findByText('77%')).toBeInTheDocument();
    expect(screen.getByText('Not charging')).toBeInTheDocument();
  });

  it('shows an error state when the robot cannot be loaded', async () => {
    vi.spyOn(robotsApi, 'getRobot').mockRejectedValue(new ApiRequestError('Not found, or outside your access scope.', 404, null));

    renderDetailPage();

    await waitFor(() => expect(screen.getByText('Could not load this robot')).toBeInTheDocument());
    expect(screen.getByText('Not found, or outside your access scope.')).toBeInTheDocument();
  });

  it('shows a clean not-connected empty state for the still-placeholder Keenon-inspired tabs, with no fabricated data', async () => {
    vi.spyOn(robotsApi, 'getRobot').mockResolvedValue(sampleRobot);

    renderDetailPage();
    await waitFor(() => expect(screen.getByRole('heading', { name: 'CleanBot Alpha' })).toBeInTheDocument());

    await userEvent.click(screen.getByRole('button', { name: 'Task Record' }));
    expect(screen.getByText('This feature is not connected yet.')).toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: 'Cleaning Daily Report' }));
    expect(screen.getByText('This feature is not connected yet.')).toBeInTheDocument();
  });

  it('renders real area data from GET /robots/{id}/areas on the Map tab', async () => {
    vi.spyOn(robotsApi, 'getRobot').mockResolvedValue(sampleRobot);
    vi.spyOn(robotsApi, 'getRobotAreas').mockResolvedValue([
      { vendorAreaId: 'area-1', displayName: 'Lobby' },
    ]);

    renderDetailPage();
    await waitFor(() => expect(screen.getByRole('heading', { name: 'CleanBot Alpha' })).toBeInTheDocument());

    await userEvent.click(screen.getByRole('button', { name: 'Map' }));

    expect(await screen.findByText('Lobby')).toBeInTheDocument();
    expect(screen.getByText('area-1')).toBeInTheDocument();
    expect(screen.getByText('Robot position: not available.')).toBeInTheDocument();
    expect(screen.queryByText('This feature is not connected yet.')).not.toBeInTheDocument();
  });

  it('navigates from the Robots list to the Robot Detail page', async () => {
    vi.spyOn(robotsApi, 'listRobots').mockResolvedValue(mockPage([sampleRobot]));
    vi.spyOn(robotsApi, 'getRobot').mockResolvedValue(sampleRobot);

    renderListToDetailFlow();

    await waitFor(() => expect(screen.getByText('CleanBot Alpha')).toBeInTheDocument());
    await userEvent.click(screen.getAllByText('CleanBot Alpha')[0]);

    await waitFor(() => expect(screen.getByRole('heading', { name: 'CleanBot Alpha' })).toBeInTheDocument());
    expect(screen.getByText('Robots')).toBeInTheDocument(); // breadcrumb
  });
});
