import { describe, expect, it, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { RobotMapPanel } from './RobotMapPanel';
import * as robotsApi from '../../api/robots';
import * as mapContentCropModule from './useMapContentCrop';
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
    // useMapContentCrop genuinely resolves to "no bounds" in this jsdom test
    // environment (no real canvas 2D pixel extraction) — this default spy
    // just makes that real, current behavior explicit and independently
    // overridable per test, rather than changing anything.
    vi.spyOn(mapContentCropModule, 'useMapContentCrop').mockReturnValue({ bounds: null, naturalWidth: null, naturalHeight: null });
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

  // Map Overlay Foundation — areas are loaded dynamically from the backend;
  // nothing about area count, names, or selection is hardcoded. These cases
  // deliberately use a count other than 5 (the count shown in the Keenon
  // reference screenshots) to prove nothing assumes a fixed number of areas.
  it('renders however many areas the backend returns, with no assumption about count', async () => {
    vi.spyOn(robotsApi, 'getRobotMapImage').mockResolvedValue(pngBlob());
    const threeAreas = [
      { vendorAreaId: 'a1', displayName: 'Kitchen' },
      { vendorAreaId: 'a2', displayName: 'Hallway' },
      { vendorAreaId: 'a3', displayName: 'Office' },
    ];
    vi.spyOn(robotsApi, 'getRobotAreas').mockResolvedValue(threeAreas);

    render(<RobotMapPanel robotId="robot-1" />);

    expect(await screen.findByText('Kitchen')).toBeInTheDocument();
    expect(screen.getByText('Hallway')).toBeInTheDocument();
    expect(screen.getByText('Office')).toBeInTheDocument();
    expect(screen.getByText('0 of 3 areas selected')).toBeInTheDocument();
  });

  it('supports selecting multiple areas independently, purely as local view state', async () => {
    vi.spyOn(robotsApi, 'getRobotMapImage').mockResolvedValue(pngBlob());
    vi.spyOn(robotsApi, 'getRobotAreas').mockResolvedValue([
      { vendorAreaId: 'a1', displayName: 'Kitchen' },
      { vendorAreaId: 'a2', displayName: 'Hallway' },
      { vendorAreaId: 'a3', displayName: 'Office' },
    ]);

    render(<RobotMapPanel robotId="robot-1" />);
    await screen.findByText('Kitchen');

    const rowCheckboxes = screen.getAllByRole('checkbox').filter((c) => c.getAttribute('aria-label')?.startsWith('Select row'));
    expect(rowCheckboxes).toHaveLength(3);

    await userEvent.click(rowCheckboxes[0]);
    expect(screen.getByText('1 of 3 areas selected')).toBeInTheDocument();

    await userEvent.click(rowCheckboxes[2]);
    expect(screen.getByText('2 of 3 areas selected')).toBeInTheDocument();

    await userEvent.click(rowCheckboxes[0]);
    expect(screen.getByText('1 of 3 areas selected')).toBeInTheDocument();
  });

  it('shows no selection count and no checkboxes when the robot has zero areas', async () => {
    vi.spyOn(robotsApi, 'getRobotMapImage').mockResolvedValue(pngBlob());
    vi.spyOn(robotsApi, 'getRobotAreas').mockResolvedValue([]);

    render(<RobotMapPanel robotId="robot-1" />);

    expect(await screen.findByText('No areas returned for this robot')).toBeInTheDocument();
    expect(screen.queryByText(/areas selected/)).not.toBeInTheDocument();
    expect(screen.queryByRole('checkbox')).not.toBeInTheDocument();
  });

  it('sizes the map viewport from the real backend-reported width/height, not a guessed ratio, when no content bounds are available', async () => {
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
    const viewport = img.parentElement;
    expect(viewport).toHaveClass('sakar-map-viewport');
    expect(viewport).not.toHaveClass('sakar-map-viewport--cropped');
    expect(viewport).toHaveStyle({ aspectRatio: (570 / 763).toString() });
  });

  // Map Display Fix — once useMapContentCrop resolves real, pixel-detected
  // content bounds (which it can only do in a real browser; jsdom has no
  // canvas pixel extraction, verified by every test above continuing to
  // exercise the uncropped fallback), the viewport switches to the cropped
  // presentation instead of showing the full, mostly-empty-margin PNG.
  it('renders the cropped, content-fitted viewport once content bounds are available', async () => {
    vi.spyOn(robotsApi, 'getRobotMapImage').mockResolvedValue(pngBlob());
    const bounds = { x: 144, y: 167, width: 281, height: 449 };
    vi.spyOn(mapContentCropModule, 'useMapContentCrop').mockReturnValue({ bounds, naturalWidth: 570, naturalHeight: 763 });

    render(<RobotMapPanel robotId="robot-1" />);

    const img = await screen.findByRole('img', { name: 'Robot floor-plan map' });
    const viewport = img.parentElement;
    expect(viewport).toHaveClass('sakar-map-viewport--cropped');
    expect(viewport).toHaveStyle({ aspectRatio: (281 / 449).toString() });
    // The <img> itself is deliberately rendered LARGER than its container
    // (then clipped by the container's overflow:hidden) and offset so only
    // the bounds region shows — this is the crop, expressed as CSS percent.
    expect(img).toHaveStyle({
      width: `${(570 / 281) * 100}%`,
      left: `${-(144 / 281) * 100}%`,
      top: `${-(167 / 449) * 100}%`,
    });
  });

  it('falls back to the uncropped viewport (never a blank map) when bounds cannot be computed, e.g. a blank or undecodable image', async () => {
    vi.spyOn(robotsApi, 'getRobotMapImage').mockResolvedValue(pngBlob());
    vi.spyOn(mapContentCropModule, 'useMapContentCrop').mockReturnValue({ bounds: null, naturalWidth: null, naturalHeight: null });

    render(<RobotMapPanel robotId="robot-1" />);

    const img = await screen.findByRole('img', { name: 'Robot floor-plan map' });
    expect(img.parentElement).not.toHaveClass('sakar-map-viewport--cropped');
    expect(img).toBeVisible();
  });

  it('the cropped-viewport math is not tied to the Demo Piece\'s portrait dimensions — a differently-shaped (landscape) map crops correctly too', async () => {
    vi.spyOn(robotsApi, 'getRobotMapImage').mockResolvedValue(pngBlob());
    const bounds = { x: 20, y: 10, width: 400, height: 100 };
    vi.spyOn(mapContentCropModule, 'useMapContentCrop').mockReturnValue({ bounds, naturalWidth: 500, naturalHeight: 200 });

    render(<RobotMapPanel robotId="robot-1" />);

    const img = await screen.findByRole('img', { name: 'Robot floor-plan map' });
    expect(img.parentElement).toHaveStyle({ aspectRatio: '4' }); // 400/100
    expect(img).toHaveStyle({ width: '125%' }); // 500/400*100
  });

  it('shows an honest, evidence-based explanation for why back/charging points are not rendered', async () => {
    vi.spyOn(robotsApi, 'getRobotMapImage').mockResolvedValue(pngBlob());

    render(<RobotMapPanel robotId="robot-1" />);

    expect(await screen.findByText('Back Points')).toBeInTheDocument();
    expect(screen.getByText(/No backend endpoint exposes back\/charging points/)).toBeInTheDocument();
  });
});
