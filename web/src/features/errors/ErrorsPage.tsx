import { useMemo, useState } from 'react';
import { useRobotOptions } from '../shared/useRobotOptions';
import { RobotPicker } from '../shared/RobotPicker';
import { listRobotErrors } from '../../api/diagnostics';
import { useApi } from '../../hooks/useApi';
import { PageHeader } from '../../components/ui/PageHeader';
import { Card } from '../../components/ui/Card';
import { DataTable } from '../../components/ui/DataTable';
import { Badge } from '../../components/ui/Badge';
import { SearchBar } from '../../components/ui/SearchBar';
import { FilterBar } from '../../components/ui/FilterBar';
import { EmptyState, LoadingState, ErrorState } from '../../components/ui/States';

function statusTone(status: string): 'danger' | 'warning' | 'success' | 'neutral' {
  if (status === 'OPEN') return 'danger';
  if (status === 'ACKNOWLEDGED') return 'warning';
  if (status === 'RESOLVED') return 'success';
  return 'neutral';
}

export function ErrorsPage() {
  const { robots } = useRobotOptions();
  const [robotId, setRobotId] = useState('');
  const [statusFilter, setStatusFilter] = useState('ALL');
  const [search, setSearch] = useState('');
  const { data, status, error, refetch } = useApi(
    () => (robotId ? listRobotErrors(robotId, 0, 200) : Promise.resolve(null)),
    [robotId],
  );

  const errors = data?.content ?? [];
  const statuses = useMemo(() => Array.from(new Set((data?.content ?? []).map((e) => e.status))), [data]);
  const filtered = errors.filter(
    (e) =>
      (statusFilter === 'ALL' || e.status === statusFilter) &&
      (!search.trim() ||
        (e.message ?? '').toLowerCase().includes(search.trim().toLowerCase()) ||
        e.errorCode.toLowerCase().includes(search.trim().toLowerCase())),
  );

  return (
    <div>
      <PageHeader title="Errors" subtitle="Robot error history, ingested live from MQTT ERROR traffic." />
      <RobotPicker robots={robots} value={robotId} onChange={setRobotId} />
      {robotId && (
        <FilterBar>
          <SearchBar value={search} onChange={setSearch} ariaLabel="Search errors" placeholder="Search code or message…" />
          <select value={statusFilter} onChange={(e) => setStatusFilter(e.target.value)} aria-label="Status">
            <option value="ALL">All statuses</option>
            {statuses.map((s) => <option key={s} value={s}>{s}</option>)}
          </select>
        </FilterBar>
      )}
      <Card title="Errors">
        {!robotId ? (
          <EmptyState title="Select a robot" detail="Choose a robot above to view its error history." />
        ) : status === 'loading' || status === 'idle' ? (
          <LoadingState title="Loading errors…" />
        ) : status === 'error' ? (
          <ErrorState
            title="Could not load errors"
            detail={error ?? 'Unknown error'}
            action={<button type="button" className="sakar-btn sakar-btn--secondary" onClick={refetch}>Retry</button>}
          />
        ) : (
          <DataTable
            rows={filtered}
            rowKey={(e) => e.id}
            emptyTitle="No errors ingested yet for this robot"
            columns={[
              { key: 'code', header: 'Code', render: (e) => <span className="sakar-mono">{e.errorCode}</span> },
              { key: 'severity', header: 'Severity', render: (e) => e.severity },
              { key: 'message', header: 'Message', render: (e) => e.message ?? '—' },
              { key: 'time', header: 'Occurred', render: (e) => new Date(e.occurredAt).toLocaleString() },
              { key: 'status', header: 'Status', render: (e) => <Badge tone={statusTone(e.status)}>{e.status}</Badge> },
              { key: 'resolved', header: 'Resolved by', render: (e) => e.resolvedBy ?? '—' },
            ]}
          />
        )}
      </Card>
    </div>
  );
}
