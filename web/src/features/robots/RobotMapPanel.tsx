import { getRobotAreas, getRobotMap } from '../../api/robots';
import { useApi } from '../../hooks/useApi';
import { useRobotMapImage, type RobotMapImageStatus } from './useRobotMapImage';
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
// Slice 1 scope: the stored PNG plus name/dimensions metadata only. Map
// points, area polygon geometry, live robot position, and zoom/pan are
// deliberately not implemented here yet — see the UnavailableFeature note
// below, which states this plainly instead of faking any of it.
export function RobotMapPanel({ robotId }: { robotId: string }) {
  const mapImage = useRobotMapImage(robotId);
  const { data: mapMeta } = useApi(() => getRobotMap(robotId), [robotId]);
  const areas = useApi(() => getRobotAreas(robotId), [robotId]);

  return (
    <div style={{ display: 'grid', gap: 16 }}>
      <Card title="Map">
        <MapImageSection
          status={mapImage.status}
          imageUrl={mapImage.imageUrl}
          error={mapImage.error}
          refetch={mapImage.refetch}
          metadata={mapMeta}
        />
      </Card>

      <Card title="Areas">
        <AreasSection
          status={areas.status}
          areas={areas.data ?? []}
          error={areas.error}
          errorStatus={areas.errorStatus}
          refetch={areas.refetch}
        />
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
}

function MapImageSection({ status, imageUrl, error, refetch, metadata }: MapImageSectionProps) {
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

  return (
    <div>
      <img
        src={imageUrl ?? undefined}
        alt="Robot floor-plan map"
        style={{ maxWidth: '100%', display: 'block', border: '1px solid var(--sakar-border, #ddd)' }}
      />
      {metadata && (
        <p className="sakar-page-subtitle" style={{ marginTop: 12, marginBottom: 0 }}>
          {metadata.name ?? 'Unnamed map'}
          {metadata.width != null && metadata.height != null ? ` · ${metadata.width}×${metadata.height}` : ''}
        </p>
      )}
      <UnavailableFeature reason="Map points, area overlays, live robot position, and zoom/pan are not part of this view yet." />
    </div>
  );
}

interface AreasSectionProps {
  status: 'idle' | 'loading' | 'success' | 'error';
  areas: RobotArea[];
  error: string | null;
  errorStatus: number | undefined;
  refetch: () => void;
}

function AreasSection({ status, areas, error, errorStatus, refetch }: AreasSectionProps) {
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
    <DataTable
      rows={areas}
      rowKey={(a) => `${a.vendorAreaId ?? ''}-${a.displayName ?? ''}`}
      emptyTitle="No areas returned for this robot"
      columns={[
        { key: 'name', header: 'Area', render: (a) => a.displayName ?? '—' },
        { key: 'id', header: 'Vendor area ID', render: (a) => <span className="sakar-mono">{a.vendorAreaId ?? '—'}</span> },
      ]}
    />
  );
}
