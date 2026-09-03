import { describe, expect, it, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { RobotMapPanel } from './RobotMapPanel';
import * as robotsApi from '../../api/robots';
import { ApiRequestError } from '../../api/client';

describe('RobotMapPanel', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  it('shows a loading state while areas are being fetched', () => {
    vi.spyOn(robotsApi, 'getRobotAreas').mockReturnValue(new Promise(() => {}));

    render(<RobotMapPanel robotId="robot-1" />);

    expect(screen.getByText('Loading map…')).toBeInTheDocument();
  });

  it('renders real area metadata from GET /robots/{id}/areas, with no fabricated geometry or position', async () => {
    vi.spyOn(robotsApi, 'getRobotAreas').mockResolvedValue([
      { vendorAreaId: 'area-1', displayName: 'Lobby' },
      { vendorAreaId: 'area-2', displayName: 'Conference Room' },
    ]);

    render(<RobotMapPanel robotId="robot-1" />);

    expect(await screen.findByText('Lobby')).toBeInTheDocument();
    expect(screen.getByText('area-1')).toBeInTheDocument();
    expect(screen.getByText('Conference Room')).toBeInTheDocument();
    expect(screen.getByText('area-2')).toBeInTheDocument();

    // Honest about what isn't available — no polygon/image/marker is faked.
    expect(screen.getByText('Robot position: not available.')).toBeInTheDocument();
    expect(screen.getByText(/Floor-plan image and area geometry are not available/)).toBeInTheDocument();
    expect(screen.queryByRole('img')).not.toBeInTheDocument();
  });

  it('shows "Map data unavailable" (not a retry-able error) when no Keenon store mapping is synced (404)', async () => {
    vi.spyOn(robotsApi, 'getRobotAreas').mockRejectedValue(
      new ApiRequestError('No synced Keenon store mapping for robot robot-1', 404, null),
    );

    render(<RobotMapPanel robotId="robot-1" />);

    await waitFor(() => expect(screen.getByText('Map data unavailable')).toBeInTheDocument());
    expect(screen.getByText('No synced Keenon store mapping for robot robot-1')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Retry' })).not.toBeInTheDocument();
  });

  it('shows "Map data unavailable" when this robot model does not support GET_AREAS (422)', async () => {
    vi.spyOn(robotsApi, 'getRobotAreas').mockRejectedValue(
      new ApiRequestError('Capability GET_AREAS is not supported', 422, null),
    );

    render(<RobotMapPanel robotId="robot-1" />);

    await waitFor(() => expect(screen.getByText('Map data unavailable')).toBeInTheDocument());
    expect(screen.getByText('Capability GET_AREAS is not supported')).toBeInTheDocument();
  });

  it('shows a retry-able generic error state for other failures (e.g. vendor API error)', async () => {
    vi.spyOn(robotsApi, 'getRobotAreas').mockRejectedValue(
      new ApiRequestError('Keenon Open Platform request failed', 502, null),
    );

    render(<RobotMapPanel robotId="robot-1" />);

    await waitFor(() => expect(screen.getByText('Unable to load map')).toBeInTheDocument());
    expect(screen.getByText('Keenon Open Platform request failed')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Retry' })).toBeInTheDocument();
  });

  it('retries the fetch when Retry is clicked after a generic error', async () => {
    vi.spyOn(robotsApi, 'getRobotAreas')
      .mockRejectedValueOnce(new ApiRequestError('Keenon Open Platform request failed', 502, null))
      .mockResolvedValueOnce([{ vendorAreaId: 'area-1', displayName: 'Lobby' }]);

    render(<RobotMapPanel robotId="robot-1" />);

    await waitFor(() => expect(screen.getByText('Unable to load map')).toBeInTheDocument());
    await userEvent.click(screen.getByRole('button', { name: 'Retry' }));

    expect(await screen.findByText('Lobby')).toBeInTheDocument();
  });
});
