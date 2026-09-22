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
import * as keenonApi from '../../api/keenon';
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
  connectionStatus: 'UNKNOWN',
  lastSeenAt: null,
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
    // Before "Check Live Status" is ever clicked, Keenon reachability reads
    // "Not checked" — never a stale or defaulted "Reachable"/"Online".
    expect(screen.getByText('Not checked')).toBeInTheDocument();
  });

  it('shows Keenon as "Unreachable" (not "Offline") when the vendor probe fails, without touching Connection', async () => {
    vi.spyOn(robotsApi, 'getRobot').mockResolvedValue({ ...sampleRobot, connectionStatus: 'ONLINE', lastSeenAt: new Date().toISOString() });
    vi.spyOn(robotsApi, 'getRobotStatus').mockRejectedValue(new ApiRequestError('unavailable', 503, null));
    vi.spyOn(robotsApi, 'getRobotBattery').mockRejectedValue(new ApiRequestError('unavailable', 503, null));

    renderDetailPage();
    await waitFor(() => expect(screen.getByRole('heading', { name: 'CleanBot Alpha' })).toBeInTheDocument());
    await userEvent.click(screen.getByRole('button', { name: 'Check Live Status' }));

    expect(await screen.findByText('Unreachable')).toBeInTheDocument();
    // A failed vendor call is a Keenon-axis fact only — it must never downgrade a
    // genuinely fresh Sakar heartbeat to Offline.
    expect(screen.getAllByText('Online').length).toBeGreaterThan(0);
    expect(screen.queryByText(/^Offline$/)).not.toBeInTheDocument();
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

    // The live vendor probe still supplies Current state...
    expect(await screen.findByText('IDLE')).toBeInTheDocument();
    // ...and is surfaced as a distinct "Keenon: Reachable" fact...
    expect(screen.getByText('Reachable')).toBeInTheDocument();
    // ...but it must NOT drive the connection badge. This robot's backend
    // connectionStatus is UNKNOWN, and a vendor call returning online:true is not
    // evidence the robot is connected — rendering "Connected"/"Online" here is
    // precisely the bug that let the Robots page contradict an open no-heartbeat alert.
    expect(screen.queryByText('Connected')).not.toBeInTheDocument();
    expect(screen.getAllByText('Unknown').length).toBeGreaterThan(0);
  });

  it('renders the backend connection status, not the live probe, in the header and status panel', async () => {
    vi.spyOn(robotsApi, 'getRobot').mockResolvedValue({
      ...sampleRobot,
      connectionStatus: 'OFFLINE',
      lastSeenAt: new Date(Date.now() - 7200_000).toISOString(),
    });
    // A perfectly successful vendor status call, reporting online.
    vi.spyOn(robotsApi, 'getRobotStatus').mockResolvedValue({
      mainState: 'IDLE', subState: null, online: true, observedAt: new Date().toISOString(), raw: {},
    });
    vi.spyOn(robotsApi, 'getRobotBattery').mockRejectedValue(new ApiRequestError('unavailable', 503, null));

    renderDetailPage();
    await waitFor(() => expect(screen.getByRole('heading', { name: 'CleanBot Alpha' })).toBeInTheDocument());
    await userEvent.click(screen.getByRole('button', { name: 'Check Live Status' }));
    await screen.findByText('IDLE');

    // Stale heartbeat wins over a successful vendor poll, everywhere on the page —
    // and the two facts are shown as separate concepts, not merged into one.
    expect(screen.getAllByText('Offline').length).toBeGreaterThan(0);
    expect(screen.queryByText('Online')).not.toBeInTheDocument();
    expect(screen.getByText('Reachable')).toBeInTheDocument();
  });

  it('renders ONLINE when the backend reports a fresh heartbeat', async () => {
    vi.spyOn(robotsApi, 'getRobot').mockResolvedValue({
      ...sampleRobot,
      connectionStatus: 'ONLINE',
      lastSeenAt: new Date().toISOString(),
    });
    vi.spyOn(robotsApi, 'getRobotStatus').mockRejectedValue(new ApiRequestError('unavailable', 503, null));
    vi.spyOn(robotsApi, 'getRobotBattery').mockRejectedValue(new ApiRequestError('unavailable', 503, null));

    renderDetailPage();
    await waitFor(() => expect(screen.getByRole('heading', { name: 'CleanBot Alpha' })).toBeInTheDocument());

    // A failed vendor probe never downgrades a genuinely fresh heartbeat.
    expect(screen.getAllByText('Online').length).toBeGreaterThan(0);
    expect(screen.queryByText('Offline')).not.toBeInTheDocument();
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
    vi.spyOn(robotsApi, 'getRobotMapImage').mockRejectedValue(new ApiRequestError('Map image not found', 404, null));

    renderDetailPage();
    await waitFor(() => expect(screen.getByRole('heading', { name: 'CleanBot Alpha' })).toBeInTheDocument());

    await userEvent.click(screen.getByRole('button', { name: 'Map' }));

    expect(await screen.findByText('Lobby')).toBeInTheDocument();
    expect(screen.getByText('area-1')).toBeInTheDocument();
    expect(screen.getByText('Map data unavailable')).toBeInTheDocument();
    expect(screen.queryByText('This feature is not connected yet.')).not.toBeInTheDocument();
  });

  it('renders real Keenon scene configuration on the Configuration tab', async () => {
    vi.spyOn(robotsApi, 'getRobot').mockResolvedValue(sampleRobot);
    vi.spyOn(keenonApi, 'getSceneConfig').mockResolvedValue({ sceneCode: '7ClJPR', sceneName: 'F' });

    renderDetailPage();
    await waitFor(() => expect(screen.getByRole('heading', { name: 'CleanBot Alpha' })).toBeInTheDocument());

    await userEvent.click(screen.getByRole('button', { name: 'Configuration' }));

    expect(await screen.findByText('7ClJPR')).toBeInTheDocument();
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
