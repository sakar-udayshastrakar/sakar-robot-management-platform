import { listRobotEvents } from '../../api/diagnostics';
import { useApi } from '../../hooks/useApi';
import { Card } from '../../components/ui/Card';
import { DataTable } from '../../components/ui/DataTable';
import { LoadingState, ErrorState } from '../../components/ui/States';

// Real event history (GET /robots/{id}/events) — Roadmap Phase 9
// web-platform gap analysis STEP 2.
export function RobotEventsPanel({ robotId }: { robotId: string }) {
  const { data, status, error, refetch } = useApi(() => listRobotEvents(robotId, 0, 25), [robotId]);

  if (status === 'loading' || status === 'idle') {
    return <LoadingState title="Loading events…" />;
  }
  if (status === 'error') {
    return (
      <ErrorState
        title="Could not load events"
        detail={error ?? undefined}
        action={<button type="button" className="sakar-btn sakar-btn--secondary" onClick={refetch}>Retry</button>}
      />
    );
  }

  return (
    <Card title="Recent Events">
      <DataTable
        rows={data?.content ?? []}
        rowKey={(e) => String(e.id)}
        emptyTitle="No events ingested yet for this robot"
        columns={[
          { key: 'time', header: 'Timestamp', render: (e) => new Date(e.occurredAt).toLocaleString() },
          { key: 'severity', header: 'Severity', render: (e) => e.severity },
          { key: 'type', header: 'Event Type', render: (e) => e.eventType },
        ]}
      />
    </Card>
  );
}
