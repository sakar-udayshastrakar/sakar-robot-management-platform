import { useEffect, useState } from 'react';
import { getRobotAreas, getRobotMap } from '../../api/robots';
import { useApi } from '../../hooks/useApi';
import { useRobotMapImage, type RobotMapImageStatus } from './useRobotMapImage';
import { useMapContentCrop } from './useMapContentCrop';
import { computeCropStyle, type ContentBounds } from '../../utils/mapContentBounds';
import { Card } from '../../components/ui/Card';
import { DataTable } from '../../components/ui/DataTable';
import { LoadingState, ErrorState, EmptyState } from '../../components/ui/States';
import { UnavailableFeature } from '../../components/ui/SimulatedDataBanner';
import type { RobotArea, RobotMapMetadata } from '../../types/domain';

// Map image (GET /robots/{id}/map/image, via useRobotMapImage) and map
// metadata (GET /robots/{id}/map) are fetched independently of areas
// (GET /robots/{id}/areas) — three separate backend calls, each with its
// own loading/error/empty handling, so a failure in one never hides data
// the others successfully returned. The image itself always comes from
// Sakar's own backend (never a Keenon URL, never a filesystem path) — see
// getRobotMapImage's own Javadoc.
//
// Overlay scope (evidence-based, not a TODO placeholder): the Keenon
// area-list endpoint returns only an area id and name — no polygon,
// bounding box, or any other geometry field exists anywhere upstream of
// this call (KeenonAreaListParser/KeenonAreaMapping have no geometry
// column). Area *selection* is therefore implemented below as a plain
// list with checkboxes (functionally equivalent to picking an area, per
// the reference Keenon C40 UI), never as a shape drawn on the map, because
// there is no shape to draw. Back/charging points are not rendered at
// all: no REST endpoint exposes them to the frontend today (the backing
// entities, KeenonBackPointMapping and MapPoint, are sync-only), and even
// MapPoint's real x/y values have no documented unit or confirmed
// relationship to this PNG's pixel space — plotting them would be a
// guess, not a rendering. See the Back Points card below for the same
// explanation surfaced to the user.
export function RobotMapPanel({ robotId }: { robotId: string }) {
  const mapImage = useRobotMapImage(robotId);
  const { data: mapMeta } = useApi(() => getRobotMap(robotId), [robotId]);
  const areas = useApi(() => getRobotAreas(robotId), [robotId]);
  // Purely a display-sizing enhancement — see useMapContentCrop/mapContentBounds
  // for why this can never affect the coordinate-safety boundary documented
  // above. `imageUrl` is only non-null once the map image has loaded, so this
  // only ever runs against real, already-fetched bytes, never a placeholder.
  const crop = useMapContentCrop(mapImage.imageUrl);

  return (
    <div style={{ display: 'grid', gap: 16 }}>
      <Card title="Map">
        <MapImageSection
          status={mapImage.status}
          imageUrl={mapImage.imageUrl}
          error={mapImage.error}
          refetch={mapImage.refetch}
          metadata={mapMeta}
          crop={crop}
        />
      </Card>

      <Card title="Areas">
        <AreasSection
          robotId={robotId}
          status={areas.status}
          areas={areas.data ?? []}
          error={areas.error}
          errorStatus={areas.errorStatus}
          refetch={areas.refetch}
        />
      </Card>

      <Card title="Back Points">
        <UnavailableFeature reason="No backend endpoint exposes back/charging points to the frontend yet (they are synced from Keenon for internal command dispatch only). Even once one exists, the stored coordinates have no documented unit or confirmed relationship to this map image's pixel grid, so a position could not be plotted accurately — see docs/KEENON_C40S_MAP_SCENE_INTEGRATION.md." />
      </Card>
    </div>
  );
}

interface MapImageSectionProps {
  status: RobotMapImageStatus;
  imageUrl: string | null;
  error: string | null;
  refetch: () => void;
  metadata: RobotMapMetadata | null;
  crop: { bounds: ContentBounds | null; naturalWidth: number | null; naturalHeight: number | null };
}

function MapImageSection({ status, imageUrl, error, refetch, metadata, crop }: MapImageSectionProps) {
  if (status === 'loading') {
    return <LoadingState title="Loading map…" />;
  }

  if (status === 'not-found') {
    return (
      <EmptyState
        title="Map data unavailable"
        detail="This robot has no synced map image yet."
      />
    );
  }

  if (status === 'error') {
    return (
      <ErrorState
        title="Unable to load map image"
        detail={error ?? undefined}
        action={<button type="button" className="sakar-btn sakar-btn--secondary" onClick={refetch}>Retry</button>}
      />
    );
  }

  // Prefer showing only the map's actual content (computeCropStyle, from
  // useMapContentCrop's pixel-detected bounds) so the floor-plan fills the
  // viewport instead of sitting small inside the raw PNG's own wide margins
  // — verified against the real stored map: content occupies only ~29% of
  // the full 570x763 canvas. Falls back to the full, uncropped image (sized
  // from real backend-confirmed width/height metadata) whenever bounds
  // couldn't be computed — never a blank viewport, and never an invented
  // crop. Either way this is a display-sizing computation only, never a
  // coordinate/world transform.
  const cropStyle = crop.bounds && crop.naturalWidth && crop.naturalHeight
    ? computeCropStyle(crop.bounds, crop.naturalWidth, crop.naturalHeight)
    : null;

  const fallbackAspectRatio = metadata?.width && metadata?.height ? metadata.width / metadata.height : undefined;

  return (
    <div>
      {cropStyle ? (
        <div
          className="sakar-map-viewport sakar-map-viewport--cropped"
          style={{ aspectRatio: cropStyle.containerAspectRatio }}
        >
          <img
            src={imageUrl ?? undefined}
            alt="Robot floor-plan map"
            style={{
              width: `${cropStyle.imgWidthPercent}%`,
              left: `${cropStyle.imgLeftPercent}%`,
              top: `${cropStyle.imgTopPercent}%`,
            }}
          />
        </div>
      ) : (
        <div className="sakar-map-viewport" style={fallbackAspectRatio ? { aspectRatio: fallbackAspectRatio } : undefined}>
          <img src={imageUrl ?? undefined} alt="Robot floor-plan map" />
        </div>
      )}
      {metadata && (
        <p className="sakar-page-subtitle" style={{ marginTop: 12, marginBottom: 0 }}>
          {metadata.name ?? 'Unnamed map'}
          {metadata.width != null && metadata.height != null ? ` · ${metadata.width}×${metadata.height}` : ''}
        </p>
      )}
      <UnavailableFeature reason="Area boundaries are not drawn on the map image: the Keenon area-list endpoint returns only an area id and name for this account, no polygon or bounding-box geometry. Live robot position and zoom/pan are also not part of this view yet." />
    </div>
  );
}

interface AreasSectionProps {
  robotId: string;
  status: 'idle' | 'loading' | 'success' | 'error';
  areas: RobotArea[];
  error: string | null;
  errorStatus: number | undefined;
  refetch: () => void;
}

function areaRowKey(a: RobotArea): string {
  return `${a.vendorAreaId ?? ''}-${a.displayName ?? ''}`;
}

function AreasSection({ robotId, status, areas, error, errorStatus, refetch }: AreasSectionProps) {
  // Selection is a local, view-only concept here — it does not call any
  // API and does not start a task (task creation already has its own,
  // separate area-selection flow in RobotTasksPanel). It exists purely so
  // a user can mark which of a robot's real, dynamically-loaded areas
  // they're looking at, the same functional idea as the reference Keenon
  // UI's checkmarked areas, without drawing shapes that don't exist.
  const [selected, setSelected] = useState<string[]>([]);

  // Never carry a stale selection across robots.
  useEffect(() => {
    setSelected([]);
  }, [robotId]);

  if (status === 'loading' || status === 'idle') {
    return <LoadingState title="Loading areas…" />;
  }

  if (status === 'error') {
    // RESOURCE_NOT_FOUND (no synced Keenon store mapping yet) and
    // UNSUPPORTED_CAPABILITY (this robot model doesn't support GET_AREAS)
    // both mean "no area data for this robot", not a transient failure —
    // offering a plain retry button for either would be misleading.
    if (errorStatus === 404 || errorStatus === 422) {
      return (
        <EmptyState
          title="Area data unavailable"
          detail={error ?? 'This robot has no synced area data yet.'}
        />
      );
    }
    return (
      <ErrorState
        title="Unable to load areas"
        detail={error ?? undefined}
        action={<button type="button" className="sakar-btn sakar-btn--secondary" onClick={refetch}>Retry</button>}
      />
    );
  }

  return (
    <div style={{ display: 'grid', gap: 10 }}>
      {areas.length > 0 && (
        <p className="sakar-page-subtitle" style={{ margin: 0 }}>
          {selected.length} of {areas.length} area{areas.length === 1 ? '' : 's'} selected
        </p>
      )}
      <DataTable
        rows={areas}
        rowKey={areaRowKey}
        emptyTitle="No areas returned for this robot"
        selectable
        selectedKeys={selected}
        onSelectionChange={setSelected}
        columns={[
          { key: 'name', header: 'Area', render: (a) => a.displayName ?? '—' },
          { key: 'id', header: 'Vendor area ID', render: (a) => <span className="sakar-mono">{a.vendorAreaId ?? '—'}</span> },
        ]}
      />
    </div>
  );
}
