import { listAuditLogs } from '../../api/audit';
import { useApi } from '../../hooks/useApi';
import { Card } from '../../components/ui/Card';
import { DataTable } from '../../components/ui/DataTable';
import { LoadingState, ErrorState } from '../../components/ui/States';

// Real data, client-filtered: GET /audit-logs has no robotId query param
// (AuditController only supports page/pageSize), so this fetches a real
// page of the real audit log and filters to this robot's id — honest
// about the limitation rather than fabricating a per-robot endpoint.
export function RobotAuditPanel({ robotId }: { robotId: string }) {
  const { data, status, error, refetch } = useApi(() => listAuditLogs(0, 100), []);

  if (status === 'loading' || status === 'idle') {
    return <LoadingState title="Loading audit trail…" />;
  }
  if (status === 'error') {
    return (
      <ErrorState
        title="Could not load audit trail"
        detail={error ?? undefined}
        action={<button type="button" className="sakar-btn sakar-btn--secondary" onClick={refetch}>Retry</button>}
      />
    );
  }

  const rows = (data?.content ?? []).filter((r) => r.robotId === robotId);

  return (
    <Card title="Audit Trail">
      <p className="sakar-page-subtitle" style={{ marginBottom: 12 }}>
        Filtered from the most recent 100 real audit-log records (no dedicated per-robot audit endpoint exists) —
        older matching entries may not appear here.
      </p>
      <DataTable
        rows={rows}
        rowKey={(r) => r.id}
        emptyTitle="No audit entries for this robot in the most recent records"
        columns={[
          { key: 'time', header: 'Timestamp', render: (r) => new Date(r.createdAt).toLocaleString() },
          { key: 'user', header: 'User', render: (r) => r.userId ?? '—' },
          { key: 'action', header: 'Action', render: (r) => r.action },
          { key: 'result', header: 'Result', render: (r) => r.result },
          { key: 'requestId', header: 'Request ID', render: (r) => r.requestId ?? '—' },
        ]}
      />
    </Card>
  );
}
