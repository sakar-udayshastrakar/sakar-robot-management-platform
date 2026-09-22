import { describe, expect, it, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { AlertsPage } from './AlertsPage';
import { AuthProvider } from '../auth/AuthContext';
import { ToastProvider } from '../../components/ui/Toast';
import { clearSession, setTokens } from '../auth/session';
import * as alertsApi from '../../api/alerts';
import * as robotsApi from '../../api/robots';
import { ApiRequestError } from '../../api/client';
import type { Robot, RobotAlert } from '../../types/domain';

function fakeToken(role: string, perms: string[]) {
  const header = btoa(JSON.stringify({ alg: 'HS384' }));
  const payload = btoa(JSON.stringify({ sub: 'u1', email: 'admin@sakarrobotics.com', role, perms, org: 'org-1', exp: Math.floor(Date.now() / 1000) + 900 }));
  return `${header}.${payload}.sig`;
}

function renderPage() {
  setTokens(fakeToken('ORG_ADMIN', ['ROBOT_VIEW', 'ROBOT_CONTROL']), 'refresh-token');
  return render(
    <MemoryRouter>
      <ToastProvider>
        <AuthProvider>
          <AlertsPage />
        </AuthProvider>
      </ToastProvider>
    </MemoryRouter>,
  );
}

function mockAlertsPage(content: RobotAlert[]) {
  return { content, totalElements: content.length, totalPages: content.length ? 1 : 0, number: 0, size: 25, first: true, last: true, empty: content.length === 0 };
}

const sampleRobot: Robot = {
  id: 'robot-1',
  organizationId: 'org-1',
  siteId: null,
  robotModelId: 'model-1',
  name: 'CleanBot Alpha',
  serialNumber: 'SN-ALPHA-001',
  status: 'ACTIVE',
  capabilities: [],
  connectionStatus: 'UNKNOWN',
  lastSeenAt: null,
  createdAt: new Date().toISOString(),
};

const sampleAlert: RobotAlert = {
  id: 'alert-1',
  robotId: 'robot-1',
  organizationId: 'org-1',
  alertType: 'LOW_BATTERY',
  severity: 'CRITICAL',
  message: 'Battery at 5%',
  status: 'OPEN',
  acknowledgedBy: null,
  acknowledgedAt: null,
  createdAt: new Date().toISOString(),
};

describe('AlertsPage', () => {
  beforeEach(() => {
    clearSession();
    vi.restoreAllMocks();
    vi.spyOn(robotsApi, 'listRobots').mockResolvedValue({
      content: [sampleRobot], totalElements: 1, totalPages: 1, number: 0, size: 100, first: true, last: true, empty: false,
    });
  });

  it('renders alerts returned by the real GET /alerts shape, using OPEN (not ACTIVE) status', async () => {
    vi.spyOn(alertsApi, 'listAlerts').mockResolvedValue(mockAlertsPage([sampleAlert]));

    renderPage();

    await waitFor(() => expect(screen.getByText('Battery at 5%')).toBeInTheDocument());
    expect(screen.getAllByText('CleanBot Alpha').length).toBeGreaterThan(0);
    expect(screen.getAllByText('OPEN').length).toBeGreaterThan(0);
    expect(screen.getByText('Low Battery')).toBeInTheDocument();
  });

  it('shows an error state when the backend call fails', async () => {
    vi.spyOn(alertsApi, 'listAlerts').mockRejectedValue(new ApiRequestError('Service unavailable', 503, null));

    renderPage();

    await waitFor(() => expect(screen.getByText('Could not load alerts')).toBeInTheDocument());
  });

  it('acknowledges an OPEN alert through the real POST /alerts/{id}/acknowledge endpoint', async () => {
    vi.spyOn(alertsApi, 'listAlerts').mockResolvedValue(mockAlertsPage([sampleAlert]));
    const ackSpy = vi.spyOn(alertsApi, 'acknowledgeAlert').mockResolvedValue({ ...sampleAlert, status: 'ACKNOWLEDGED' });

    renderPage();
    await waitFor(() => expect(screen.getByText('Battery at 5%')).toBeInTheDocument());

    await userEvent.click(screen.getByRole('button', { name: 'Acknowledge' }));

    await waitFor(() => expect(ackSpy).toHaveBeenCalledWith('alert-1'));
  });

  it('shows the robot\'s current connectionStatus as a connectivity badge for an OFFLINE-type alert', async () => {
    vi.spyOn(robotsApi, 'listRobots').mockResolvedValue({
      content: [{ ...sampleRobot, connectionStatus: 'OFFLINE' }],
      totalElements: 1, totalPages: 1, number: 0, size: 100, first: true, last: true, empty: false,
    });
    const offlineAlert: RobotAlert = { ...sampleAlert, id: 'alert-2', alertType: 'OFFLINE', message: 'No heartbeat received' };
    vi.spyOn(alertsApi, 'listAlerts').mockResolvedValue(mockAlertsPage([offlineAlert]));

    renderPage();

    await waitFor(() => expect(screen.getByText('No heartbeat received')).toBeInTheDocument());
    // Two "Offline" texts are expected: the Alert Type column ("Offline", from
    // TYPE_LABEL) and the connectivity badge showing the robot's live status —
    // confirm the latter specifically, by its danger-styled badge class.
    const offlineTexts = screen.getAllByText('Offline');
    expect(offlineTexts.length).toBe(2);
    expect(offlineTexts.some((el) => el.closest('.sakar-badge--danger'))).toBe(true);
  });

  it('does not show a connectivity badge for a LOW_BATTERY-type alert', async () => {
    vi.spyOn(alertsApi, 'listAlerts').mockResolvedValue(mockAlertsPage([sampleAlert]));

    renderPage();

    await waitFor(() => expect(screen.getByText('Battery at 5%')).toBeInTheDocument());
    expect(screen.queryByText('Offline')).not.toBeInTheDocument();
    expect(screen.queryByText('Online')).not.toBeInTheDocument();
    expect(screen.queryByText('Unknown')).not.toBeInTheDocument();
  });
});
