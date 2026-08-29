import { useState } from 'react';
import { listAuditLogs } from '../../api/audit';
import { useApi } from '../../hooks/useApi';
import { Card } from '../../components/ui/Card';
import { DataTable } from '../../components/ui/DataTable';
import { Pagination } from '../../components/ui/Pagination';
import { LoadingState, ErrorState } from '../../components/ui/States';

export function AuditLogPage() {
  const [page, setPage] = useState(0);
  const { data, status, error, refetch } = useApi(() => listAuditLogs(page, 25), [page]);

  return (
    <div>
      <div className="sakar-page-header">
        <div>
          <h1 className="sakar-page-title">Audit Logs</h1>
          <p className="sakar-page-subtitle">Append-only record of security-relevant actions.</p>
        </div>
      </div>

      {status === 'loading' || status === 'idle' ? (
        <LoadingState title="Loading audit logs…" />
      ) : status === 'error' ? (
        <ErrorState
          title="Could not load audit logs"
          detail={error ?? undefined}
          action={<button type="button" className="sakar-btn sakar-btn--secondary" onClick={refetch}>Retry</button>}
        />
      ) : (
        <Card title={`Records (${data?.totalElements ?? 0})`}>
          <DataTable
            rows={data?.content ?? []}
            rowKey={(r) => r.id}
            emptyTitle="No audit records"
            columns={[
              { key: 'action', header: 'Action', render: (r) => r.action },
              { key: 'result', header: 'Result', render: (r) => r.result },
              { key: 'user', header: 'User', render: (r) => r.userId ?? '—' },
              { key: 'org', header: 'Organization', render: (r) => r.organizationId ?? '—' },
              { key: 'robot', header: 'Robot', render: (r) => r.robotId ?? '—' },
              { key: 'ip', header: 'IP', render: (r) => r.ipAddress ?? '—' },
              { key: 'time', header: 'Timestamp', render: (r) => new Date(r.createdAt).toLocaleString() },
            ]}
          />
          {data && <Pagination page={data.number} totalPages={data.totalPages} onChange={setPage} />}
        </Card>
      )}
    </div>
  );
}
