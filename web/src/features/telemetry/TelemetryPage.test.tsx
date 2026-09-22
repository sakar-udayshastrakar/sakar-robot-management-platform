import { describe, expect, it, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { TelemetryPage } from './TelemetryPage';
import { AuthProvider } from '../auth/AuthContext';
import { clearSession, setTokens } from '../auth/session';
import * as telemetryApi from '../../api/telemetry';
import * as robotsApi from '../../api/robots';
import { ApiRequestError } from '../../api/client';
import type { Robot, RobotTelemetryEntry } from '../../types/domain';

function fakeToken() {
  const header = btoa(JSON.stringify({ alg: 'HS384' }));
  const payload = btoa(JSON.stringify({
    sub: 'u1', email: 'admin@sakarrobotics.com', role: 'ORG_ADMIN', perms: ['ROBOT_LOG_VIEW'], org: 'org-1',
    exp: Math.floor(Date.now() / 1000) + 900,
  }));
  return `${header}.${payload}.sig`;
}

function renderPage() {
  setTokens(fakeToken(), 'refresh-token');
  return render(
    <MemoryRouter>
      <AuthProvider>
        <TelemetryPage />
      </AuthProvider>
    </MemoryRouter>,
  );
}

function page<T>(content: T[]) {
  return { content, totalElements: content.length, totalPages: content.length ? 1 : 0, number: 0, size: 200, first: true, last: true, empty: content.length === 0 };
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

const sampleReading: RobotTelemetryEntry = {
  id: 1,
  robotId: 'robot-1',
  metric: 'battery_percent',
  valueNumeric: 76,
  valueText: null,
  recordedAt: new Date().toISOString(),
  createdAt: new Date().toISOString(),
};

describe('TelemetryPage', () => {
  beforeEach(() => {
    clearSession();
    vi.restoreAllMocks();
    vi.spyOn(robotsApi, 'listRobots').mockResolvedValue({
      content: [sampleRobot], totalElements: 1, totalPages: 1, number: 0, size: 100, first: true, last: true, empty: false,
    });
  });

  it('renders real telemetry once a robot is picked', async () => {
    vi.spyOn(telemetryApi, 'listRobotTelemetry').mockResolvedValue(page([sampleReading]));
    renderPage();

    await waitFor(() => expect(screen.getByText('CleanBot Alpha (SN-ALPHA-001)')).toBeInTheDocument());
    await userEvent.selectOptions(screen.getByLabelText('Robot'), 'robot-1');

    await waitFor(() => expect(screen.getByText('battery_percent')).toBeInTheDocument());
    expect(screen.getByText('76')).toBeInTheDocument();
  });

  it('shows an empty state when no telemetry has been ingested yet', async () => {
    vi.spyOn(telemetryApi, 'listRobotTelemetry').mockResolvedValue(page([]));
    renderPage();

    await waitFor(() => expect(screen.getByText('CleanBot Alpha (SN-ALPHA-001)')).toBeInTheDocument());
    await userEvent.selectOptions(screen.getByLabelText('Robot'), 'robot-1');

    await waitFor(() => expect(screen.getByText(/No telemetry ingested yet/i)).toBeInTheDocument());
  });

  it('shows an error state when the backend call fails', async () => {
    vi.spyOn(telemetryApi, 'listRobotTelemetry').mockRejectedValue(new ApiRequestError('Service unavailable', 503, null));
    renderPage();

    await waitFor(() => expect(screen.getByText('CleanBot Alpha (SN-ALPHA-001)')).toBeInTheDocument());
    await userEvent.selectOptions(screen.getByLabelText('Robot'), 'robot-1');

    await waitFor(() => expect(screen.getByText('Could not load telemetry')).toBeInTheDocument());
  });
});
