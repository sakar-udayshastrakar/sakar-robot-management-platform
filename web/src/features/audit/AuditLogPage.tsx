import { useState } from 'react';
import { listAuditLogs } from '../../api/audit';
import { useApi } from '../../hooks/useApi';
import { PageHeader } from '../../components/ui/PageHeader';
import { Card } from '../../components/ui/Card';
import { DataTable } from '../../components/ui/DataTable';
import { Pagination } from '../../components/ui/Pagination';
import { SearchBar } from '../../components/ui/SearchBar';
import { FilterBar } from '../../components/ui/FilterBar';
import { LoadingState, ErrorState } from '../../components/ui/States';

export function AuditLogPage() {
  const [page, setPage] = useState(0);
  const [search, setSearch] = useState('');
  const { data, status, error, refetch } = useApi(() => listAuditLogs(page, 25), [page]);

  const rows = (data?.content ?? []).filter(
    (r) => !search.trim() || r.action.toLowerCase().includes(search.trim().toLowerCase()) || r.result.toLowerCase().includes(search.trim().toLowerCase()),
  );

  return (
    <div>
      <PageHeader title="Audit Logs" subtitle="Append-only record of security-relevant actions. No sensitive credential or token value is ever displayed here." />

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
          <FilterBar>
            <SearchBar value={search} onChange={setSearch} ariaLabel="Search audit log" placeholder="Search action or result…" />
          </FilterBar>
          <DataTable
            rows={rows}
            rowKey={(r) => r.id}
            emptyTitle="No audit records"
            columns={[
              { key: 'time', header: 'Timestamp', render: (r) => new Date(r.createdAt).toLocaleString() },
              { key: 'user', header: 'User', render: (r) => r.userId ? <span className="sakar-mono">{r.userId.slice(0, 8)}…</span> : '—' },
              { key: 'action', header: 'Action', render: (r) => r.action },
              { key: 'robot', header: 'Robot', render: (r) => r.robotId ? <span className="sakar-mono">{r.robotId.slice(0, 8)}…</span> : '—' },
              { key: 'result', header: 'Result', render: (r) => r.result },
              { key: 'requestId', header: 'Request ID', render: (r) => r.requestId ? <span className="sakar-mono">{r.requestId}</span> : '—' },
            ]}
          />
          {data && <Pagination page={data.number} totalPages={data.totalPages} onChange={setPage} />}
        </Card>
      )}
    </div>
  );
}
