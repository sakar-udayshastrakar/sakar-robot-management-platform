import { listRobotErrors } from '../../api/diagnostics';
import { useApi } from '../../hooks/useApi';
import { Card } from '../../components/ui/Card';
import { DataTable } from '../../components/ui/DataTable';
import { LoadingState, ErrorState } from '../../components/ui/States';

// Real error history (GET /robots/{id}/errors) — Roadmap Phase 9
// web-platform gap analysis STEP 2.
export function RobotErrorsPanel({ robotId }: { robotId: string }) {
  const { data, status, error, refetch } = useApi(() => listRobotErrors(robotId, 0, 25), [robotId]);

  if (status === 'loading' || status === 'idle') {
    return <LoadingState title="Loading errors…" />;
  }
  if (status === 'error') {
    return (
      <ErrorState
        title="Could not load errors"
        detail={error ?? undefined}
        action={<button type="button" className="sakar-btn sakar-btn--secondary" onClick={refetch}>Retry</button>}
      />
    );
  }

  return (
    <Card title="Recent Errors">
      <DataTable
        rows={data?.content ?? []}
        rowKey={(e) => e.id}
        emptyTitle="No errors ingested yet for this robot"
        columns={[
          { key: 'code', header: 'Code', render: (e) => e.errorCode },
          { key: 'message', header: 'Message', render: (e) => e.message ?? '—' },
          { key: 'status', header: 'Status', render: (e) => e.status },
        ]}
      />
    </Card>
  );
}
