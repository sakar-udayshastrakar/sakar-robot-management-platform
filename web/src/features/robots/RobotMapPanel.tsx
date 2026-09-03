import { getRobotAreas } from '../../api/robots';
import { useApi } from '../../hooks/useApi';
import { Card } from '../../components/ui/Card';
import { DataTable } from '../../components/ui/DataTable';
import { LoadingState, ErrorState, EmptyState } from '../../components/ui/States';
import { UnavailableFeature } from '../../components/ui/SimulatedDataBanner';

// Real GET_AREAS adapter call (GET /robots/{id}/areas) — metadata only
// (vendor area id + display name). No floor-plan image, polygon geometry,
// live robot position, charging-point location, or navigation points are
// rendered here: none of that is available from any adapter today (see the
// Robot Detail → Map implementation report). Showing a fabricated floor
// plan or marker would misrepresent real robot capability, so this panel
// states plainly what is and isn't available instead of faking the rest.
export function RobotMapPanel({ robotId }: { robotId: string }) {
  const { data, status, error, errorStatus, refetch } = useApi(() => getRobotAreas(robotId), [robotId]);

  if (status === 'loading' || status === 'idle') {
    return <LoadingState title="Loading map…" />;
  }

  if (status === 'error') {
    // RESOURCE_NOT_FOUND (no synced Keenon store mapping yet) and
    // UNSUPPORTED_CAPABILITY (this robot model doesn't support GET_AREAS)
    // both mean "no map data for this robot", not a transient failure —
    // offering a plain retry button for either would be misleading.
    if (errorStatus === 404 || errorStatus === 422) {
      return (
        <Card title="Map">
          <EmptyState
            title="Map data unavailable"
            detail={error ?? 'This robot has no synced map/area data yet.'}
          />
        </Card>
      );
    }
    return (
      <Card title="Map">
        <ErrorState
          title="Unable to load map"
          detail={error ?? undefined}
          action={<button type="button" className="sakar-btn sakar-btn--secondary" onClick={refetch}>Retry</button>}
        />
      </Card>
    );
  }

  const areas = data ?? [];

  return (
    <div style={{ display: 'grid', gap: 16 }}>
      <Card title="Map">
        <UnavailableFeature reason="Floor-plan image and area geometry are not available from this robot's adapter today — the vendor API returns only area names/ids, so no map image or polygon shapes are drawn here. Live robot position, charging-point location, and navigation points are unavailable for the same reason." />
        <p className="sakar-page-subtitle" style={{ marginTop: 12, marginBottom: 0 }}>
          Robot position: not available.
        </p>
      </Card>

      <Card title="Areas">
        <DataTable
          rows={areas}
          rowKey={(a) => `${a.vendorAreaId ?? ''}-${a.displayName ?? ''}`}
          emptyTitle="No areas returned for this robot"
          columns={[
            { key: 'name', header: 'Area', render: (a) => a.displayName ?? '—' },
            { key: 'id', header: 'Vendor area ID', render: (a) => <span className="sakar-mono">{a.vendorAreaId ?? '—'}</span> },
          ]}
        />
      </Card>
    </div>
  );
}
