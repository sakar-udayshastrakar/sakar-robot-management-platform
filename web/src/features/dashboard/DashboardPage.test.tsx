import { describe, expect, it, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { DashboardPage } from './DashboardPage';
import { AuthProvider } from '../auth/AuthContext';
import { ToastProvider } from '../../components/ui/Toast';
import * as robotsApi from '../../api/robots';
import * as alertsApi from '../../api/alerts';
import { ApiRequestError } from '../../api/client';
import type { Robot } from '../../types/domain';

function renderPage() {
  return render(
    <MemoryRouter>
      <ToastProvider>
        <AuthProvider>
          <DashboardPage />
        </AuthProvider>
      </ToastProvider>
    </MemoryRouter>,
  );
}

function mockRobotsPage(content: Robot[]) {
  return { content, totalElements: content.length, totalPages: content.length ? 1 : 0, number: 0, size: 100, first: true, last: true, empty: content.length === 0 };
}

function mockAlertsPage() {
  return { content: [], totalElements: 0, totalPages: 0, number: 0, size: 200, first: true, last: true, empty: true };
}

// The KPI cards' own label ("Online"/"Offline"/"Unknown") and the fleet
// table's StatusBadge share the same text, so a bare getByText(label) is
// ambiguous — this targets the KPI card specifically via its label class.
function kpiCard(label: string) {
  return screen.getByText(label, { selector: '.sakar-metric-label' }).closest('.sakar-metric-card')!;
}

function robot(overrides: Partial<Robot>): Robot {
  return {
    id: overrides.id ?? 'robot-1',
    organizationId: 'org-1',
    siteId: null,
    robotModelId: 'model-1',
    name: overrides.name ?? 'CleanBot Alpha',
    serialNumber: 'SN-001',
    status: 'ACTIVE',
    capabilities: [],
    connectionStatus: 'UNKNOWN',
    lastSeenAt: null,
    createdAt: new Date().toISOString(),
    ...overrides,
  };
}

// Robot Connectivity UI Polish slice — the Dashboard's Online/Offline/Unknown
// KPI cards and fleet table must both be computed from `connectionStatus`,
// never from a live vendor probe, and the two must never be able to disagree.
describe('DashboardPage', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
    vi.spyOn(alertsApi, 'listAlerts').mockResolvedValue(mockAlertsPage());
    // Every vendor probe fails — the KPIs and fleet status must still be correct,
    // because they come from connectionStatus on the robots payload, not the probe.
    vi.spyOn(robotsApi, 'getRobotStatus').mockRejectedValue(new ApiRequestError('unavailable', 503, null));
  });

  it('computes the Online/Offline/Unknown KPI counts from connectionStatus, and they never contradict the fleet table', async () => {
    vi.spyOn(robotsApi, 'listRobots').mockResolvedValue(mockRobotsPage([
      robot({ id: 'r1', name: 'Online Bot', connectionStatus: 'ONLINE', lastSeenAt: new Date().toISOString() }),
      robot({ id: 'r2', name: 'Offline Bot A', connectionStatus: 'OFFLINE', lastSeenAt: new Date(Date.now() - 7200_000).toISOString() }),
      robot({ id: 'r3', name: 'Offline Bot B', connectionStatus: 'OFFLINE', lastSeenAt: new Date(Date.now() - 3600_000).toISOString() }),
      robot({ id: 'r4', name: 'Unseen Bot', connectionStatus: 'UNKNOWN', lastSeenAt: null }),
    ]));

    renderPage();

    await waitFor(() => expect(screen.getByText('Online Bot')).toBeInTheDocument());

    // KPI cards.
    expect(kpiCard('Online')).toHaveTextContent('1');
    expect(kpiCard('Offline')).toHaveTextContent('2');
    expect(kpiCard('Unknown')).toHaveTextContent('1');

    // Fleet table rows agree with the KPI counts: exactly 1 Online badge, 2 Offline, 1 Unknown.
    const fleetTable = screen.getByText('Robot Fleet Overview').closest('.sakar-card');
    expect(fleetTable).not.toBeNull();
    expect(fleetTable!.querySelectorAll('.sakar-badge--success').length).toBe(1);
    expect(fleetTable!.querySelectorAll('.sakar-badge--danger').length).toBe(2);
  });

  it('never shows a stale robot as Online on the Dashboard, even when the vendor probe succeeds', async () => {
    vi.spyOn(robotsApi, 'listRobots').mockResolvedValue(mockRobotsPage([
      robot({ id: 'r1', name: 'Stale Bot', connectionStatus: 'OFFLINE', lastSeenAt: new Date(Date.now() - 7200_000).toISOString() }),
    ]));
    vi.spyOn(robotsApi, 'getRobotStatus').mockResolvedValue({
      mainState: 'IDLE', subState: null, online: true, observedAt: new Date().toISOString(), raw: {},
    });

    renderPage();

    await waitFor(() => expect(screen.getByText('Stale Bot')).toBeInTheDocument());
    expect(kpiCard('Online')).toHaveTextContent('0');
    expect(kpiCard('Offline')).toHaveTextContent('1');
  });

  it('shows a loading state, never a default Online reading, while robots are still loading', () => {
    vi.spyOn(robotsApi, 'listRobots').mockReturnValue(new Promise(() => {}));

    renderPage();

    expect(screen.getByText('Loading dashboard…')).toBeInTheDocument();
    expect(screen.queryByText('Online')).not.toBeInTheDocument();
  });

  it('displays lastSeenAt from backend data in the fleet table, not "Never received" for a robot that has reported', async () => {
    // A recent-but-past timestamp so the relative label is deterministic
    // ("X min ago") regardless of exactly when this test runs, while the
    // full absolute timestamp is still checked via its tooltip.
    const seenAt = new Date(Date.now() - 5 * 60_000);
    vi.spyOn(robotsApi, 'listRobots').mockResolvedValue(mockRobotsPage([
      robot({ id: 'r1', name: 'Reporting Bot', connectionStatus: 'OFFLINE', lastSeenAt: seenAt.toISOString() }),
    ]));

    renderPage();

    await waitFor(() => expect(screen.getByText('Reporting Bot')).toBeInTheDocument());
    // Relative text is the visible primary content...
    expect(screen.getByText('5 min ago')).toBeInTheDocument();
    // ...and the absolute timestamp is still available, as a tooltip.
    expect(screen.getByTitle(seenAt.toLocaleString())).toBeInTheDocument();
    expect(screen.queryByText('Never received')).not.toBeInTheDocument();
  });
});
