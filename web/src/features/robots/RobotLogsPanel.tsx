import { listRobotLogs } from '../../api/diagnostics';
import { useApi } from '../../hooks/useApi';
import { Card } from '../../components/ui/Card';
import { DataTable } from '../../components/ui/DataTable';
import { LoadingState, ErrorState } from '../../components/ui/States';

// Real application-log history (GET /robots/{id}/logs) — Roadmap Phase 9
// web-platform gap analysis STEP 2.
export function RobotLogsPanel({ robotId }: { robotId: string }) {
  const { data, status, error, refetch } = useApi(() => listRobotLogs(robotId, 0, 25), [robotId]);

  if (status === 'loading' || status === 'idle') {
    return <LoadingState title="Loading logs…" />;
  }
  if (status === 'error') {
    return (
      <ErrorState
        title="Could not load logs"
        detail={error ?? undefined}
        action={<button type="button" className="sakar-btn sakar-btn--secondary" onClick={refetch}>Retry</button>}
      />
    );
  }

  return (
    <Card title="Application Logs">
      <DataTable
        rows={data?.content ?? []}
        rowKey={(l) => String(l.id)}
        emptyTitle="No log entries ingested yet for this robot"
        columns={[
          { key: 'time', header: 'Timestamp', render: (l) => new Date(l.createdAt).toLocaleString() },
          { key: 'level', header: 'Level', render: (l) => l.level },
          { key: 'source', header: 'Source', render: (l) => <span className="sakar-mono">{l.source}</span> },
          { key: 'message', header: 'Message', render: (l) => <span className="sakar-mono">{l.message}</span> },
        ]}
      />
    </Card>
  );
}
