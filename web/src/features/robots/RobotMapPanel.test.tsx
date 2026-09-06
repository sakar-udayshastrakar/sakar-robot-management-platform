import { describe, expect, it, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { RobotMapPanel } from './RobotMapPanel';
import * as robotsApi from '../../api/robots';
import { ApiRequestError } from '../../api/client';

function pngBlob(): Blob {
  return new Blob([new Uint8Array([0x89, 0x50, 0x4e, 0x47])], { type: 'image/png' });
}

describe('RobotMapPanel', () => {
  let createObjectURL: ReturnType<typeof vi.fn>;
  let revokeObjectURL: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    vi.restoreAllMocks();
    // jsdom does not implement these — stub them per-test so useRobotMapImage's
    // object-URL lifecycle can be exercised and asserted on.
    createObjectURL = vi.fn(() => 'blob:mock-url');
    revokeObjectURL = vi.fn();
    vi.stubGlobal('URL', { ...URL, createObjectURL, revokeObjectURL });
    // Keep the areas card resolved-but-uninteresting by default so each test
    // only needs to stub the map-image/map-metadata behavior it cares about.
    vi.spyOn(robotsApi, 'getRobotAreas').mockResolvedValue([]);
    vi.spyOn(robotsApi, 'getRobotMap').mockRejectedValue(new ApiRequestError('not found', 404, null));
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('shows a loading state while the map image is being fetched', () => {
    vi.spyOn(robotsApi, 'getRobotMapImage').mockReturnValue(new Promise(() => {}));

    render(<RobotMapPanel robotId="robot-1" />);

    expect(screen.getByText('Loading map…')).toBeInTheDocument();
  });

  it('calls GET /robots/{id}/map/image with the correct robot id', () => {
    const spy = vi.spyOn(robotsApi, 'getRobotMapImage').mockReturnValue(new Promise(() => {}));

    render(<RobotMapPanel robotId="robot-42" />);

    expect(spy).toHaveBeenCalledWith('robot-42');
  });

  it('renders the actual PNG returned by the backend as an <img>, built from a local object URL', async () => {
    vi.spyOn(robotsApi, 'getRobotMapImage').mockResolvedValue(pngBlob());
    vi.spyOn(robotsApi, 'getRobotMap').mockResolvedValue({
      vendorMapId: '7ClJPR',
      name: 'F',
      width: 570,
      height: 763,
      mapMd5: 'a3cb0d75faa17c9ab12b9a6434173b42',
      updatedAt: '2026-09-06T00:00:00Z',
    });

    render(<RobotMapPanel robotId="robot-1" />);

    const img = await screen.findByRole('img', { name: 'Robot floor-plan map' });
    expect(img).toHaveAttribute('src', 'blob:mock-url');
    expect(createObjectURL).toHaveBeenCalledTimes(1);
    // Never a Keenon URL and never a filesystem path — only the local object URL.
    expect(img.getAttribute('src')).not.toMatch(/keenon|robotkeenon|\.png$|storage|data\/maps/i);

    expect(await screen.findByText('F · 570×763')).toBeInTheDocument();
  });

  it('shows "Map data unavailable" when no map image has been synced (404)', async () => {
    vi.spyOn(robotsApi, 'getRobotMapImage').mockRejectedValue(new ApiRequestError('Map image not found', 404, null));

    render(<RobotMapPanel robotId="robot-1" />);

    await waitFor(() => expect(screen.getByText('Map data unavailable')).toBeInTheDocument());
    expect(screen.getByText('This robot has no synced map image yet.')).toBeInTheDocument();
    expect(screen.queryByRole('img')).not.toBeInTheDocument();
  });

  it('shows a retry-able generic error state for other failures', async () => {
    vi.spyOn(robotsApi, 'getRobotMapImage').mockRejectedValue(new ApiRequestError('Request failed with status code 500', 500, null));

    render(<RobotMapPanel robotId="robot-1" />);

    await waitFor(() => expect(screen.getByText('Unable to load map image')).toBeInTheDocument());
    expect(screen.getByRole('button', { name: 'Retry' })).toBeInTheDocument();
    expect(screen.queryByRole('img')).not.toBeInTheDocument();
  });

  it('retries the map image fetch when Retry is clicked after a generic error', async () => {
    vi.spyOn(robotsApi, 'getRobotMapImage')
      .mockRejectedValueOnce(new ApiRequestError('Request failed with status code 500', 500, null))
      .mockResolvedValueOnce(pngBlob());

    render(<RobotMapPanel robotId="robot-1" />);

    await waitFor(() => expect(screen.getByText('Unable to load map image')).toBeInTheDocument());
    await userEvent.click(screen.getByRole('button', { name: 'Retry' }));

    expect(await screen.findByRole('img', { name: 'Robot floor-plan map' })).toBeInTheDocument();
  });

  it('revokes the previous object URL when the image is refetched (no leak across refetch)', async () => {
    vi.spyOn(robotsApi, 'getRobotMapImage')
      .mockRejectedValueOnce(new ApiRequestError('Request failed with status code 500', 500, null))
      .mockResolvedValueOnce(pngBlob());

    render(<RobotMapPanel robotId="robot-1" />);

    await waitFor(() => expect(screen.getByText('Unable to load map image')).toBeInTheDocument());
    await userEvent.click(screen.getByRole('button', { name: 'Retry' }));
    await screen.findByRole('img', { name: 'Robot floor-plan map' });

    expect(createObjectURL).toHaveBeenCalledTimes(1);
  });

  it('revokes the created object URL on unmount', async () => {
    vi.spyOn(robotsApi, 'getRobotMapImage').mockResolvedValue(pngBlob());

    const { unmount } = render(<RobotMapPanel robotId="robot-1" />);
    await screen.findByRole('img', { name: 'Robot floor-plan map' });

    unmount();

    expect(revokeObjectURL).toHaveBeenCalledWith('blob:mock-url');
  });

  it('renders real area metadata from GET /robots/{id}/areas, independently of the map image', async () => {
    vi.spyOn(robotsApi, 'getRobotMapImage').mockResolvedValue(pngBlob());
    vi.spyOn(robotsApi, 'getRobotAreas').mockResolvedValue([
      { vendorAreaId: 'area-1', displayName: 'Lobby' },
      { vendorAreaId: 'area-2', displayName: 'Conference Room' },
    ]);

    render(<RobotMapPanel robotId="robot-1" />);

    expect(await screen.findByText('Lobby')).toBeInTheDocument();
    expect(screen.getByText('area-1')).toBeInTheDocument();
    expect(screen.getByText('Conference Room')).toBeInTheDocument();
  });

  it('shows "Area data unavailable" (not a retry-able error) when no Keenon store mapping is synced (404)', async () => {
    vi.spyOn(robotsApi, 'getRobotMapImage').mockReturnValue(new Promise(() => {}));
    vi.spyOn(robotsApi, 'getRobotAreas').mockRejectedValue(
      new ApiRequestError('No synced Keenon store mapping for robot robot-1', 404, null),
    );

    render(<RobotMapPanel robotId="robot-1" />);

    await waitFor(() => expect(screen.getByText('Area data unavailable')).toBeInTheDocument());
    expect(screen.getByText('No synced Keenon store mapping for robot robot-1')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Retry' })).not.toBeInTheDocument();
  });

  it('shows a retry-able generic error state for area failures (e.g. vendor API error)', async () => {
    vi.spyOn(robotsApi, 'getRobotMapImage').mockReturnValue(new Promise(() => {}));
    vi.spyOn(robotsApi, 'getRobotAreas').mockRejectedValue(
      new ApiRequestError('Keenon Open Platform request failed', 502, null),
    );

    render(<RobotMapPanel robotId="robot-1" />);

    await waitFor(() => expect(screen.getByText('Unable to load areas')).toBeInTheDocument());
    expect(screen.getByText('Keenon Open Platform request failed')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Retry' })).toBeInTheDocument();
  });
});
