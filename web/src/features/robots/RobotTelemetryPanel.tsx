import { listRobotTelemetry } from '../../api/telemetry';
import { useApi } from '../../hooks/useApi';
import { Card } from '../../components/ui/Card';
import { DataTable } from '../../components/ui/DataTable';
import { LoadingState, ErrorState } from '../../components/ui/States';

// Real telemetry history (GET /robots/{id}/telemetry) — Roadmap Phase 9
// web-platform gap analysis STEP 2.
export function RobotTelemetryPanel({ robotId }: { robotId: string }) {
  const { data, status, error, refetch } = useApi(() => listRobotTelemetry(robotId, 0, 25), [robotId]);

  if (status === 'loading' || status === 'idle') {
    return <LoadingState title="Loading telemetry…" />;
  }
  if (status === 'error') {
    return (
      <ErrorState
        title="Could not load telemetry"
        detail={error ?? undefined}
        action={<button type="button" className="sakar-btn sakar-btn--secondary" onClick={refetch}>Retry</button>}
      />
    );
  }

  return (
    <Card title="Recent Telemetry">
      <DataTable
        rows={data?.content ?? []}
        rowKey={(r) => String(r.id)}
        emptyTitle="No telemetry ingested yet for this robot"
        columns={[
          { key: 'time', header: 'Timestamp', render: (r) => new Date(r.recordedAt).toLocaleString() },
          { key: 'metric', header: 'Metric', render: (r) => r.metric },
          { key: 'value', header: 'Value', render: (r) => r.valueNumeric ?? r.valueText ?? '—' },
        ]}
      />
    </Card>
  );
}
