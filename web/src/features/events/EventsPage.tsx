import { useMemo, useState } from 'react';
import { useRobotOptions } from '../shared/useRobotOptions';
import { RobotPicker } from '../shared/RobotPicker';
import { listRobotEvents } from '../../api/diagnostics';
import { useApi } from '../../hooks/useApi';
import { PageHeader } from '../../components/ui/PageHeader';
import { Card } from '../../components/ui/Card';
import { DataTable } from '../../components/ui/DataTable';
import { Badge } from '../../components/ui/Badge';
import { SearchBar } from '../../components/ui/SearchBar';
import { FilterBar } from '../../components/ui/FilterBar';
import { EmptyState, LoadingState, ErrorState } from '../../components/ui/States';

function severityTone(severity: string): 'info' | 'warning' | 'danger' | 'neutral' {
  if (severity === 'ERROR' || severity === 'CRITICAL') return 'danger';
  if (severity === 'WARNING' || severity === 'WARN') return 'warning';
  if (severity === 'INFO') return 'info';
  return 'neutral';
}

export function EventsPage() {
  const { robots } = useRobotOptions();
  const [robotId, setRobotId] = useState('');
  const [severity, setSeverity] = useState('ALL');
  const [search, setSearch] = useState('');
  const { data, status, error, refetch } = useApi(
    () => (robotId ? listRobotEvents(robotId, 0, 200) : Promise.resolve(null)),
    [robotId],
  );

  const events = data?.content ?? [];
  const severities = useMemo(() => Array.from(new Set((data?.content ?? []).map((e) => e.severity))), [data]);
  const filtered = events.filter(
    (e) =>
      (severity === 'ALL' || e.severity === severity) &&
      (!search.trim() ||
        e.eventType.toLowerCase().includes(search.trim().toLowerCase()) ||
        (e.payload ?? '').toLowerCase().includes(search.trim().toLowerCase())),
  );

  return (
    <div>
      <PageHeader title="Events" subtitle="Robot event stream, ingested live from MQTT COMMAND_RESULT/EVENT traffic." />
      <RobotPicker robots={robots} value={robotId} onChange={setRobotId} />
      {robotId && (
        <FilterBar>
          <SearchBar value={search} onChange={setSearch} ariaLabel="Search events" placeholder="Search type or payload…" />
          <select value={severity} onChange={(e) => setSeverity(e.target.value)} aria-label="Severity">
            <option value="ALL">All severities</option>
            {severities.map((s) => <option key={s} value={s}>{s}</option>)}
          </select>
        </FilterBar>
      )}
      <Card title="Events">
        {!robotId ? (
          <EmptyState title="Select a robot" detail="Choose a robot above to view its event stream." />
        ) : status === 'loading' || status === 'idle' ? (
          <LoadingState title="Loading events…" />
        ) : status === 'error' ? (
          <ErrorState
            title="Could not load events"
            detail={error ?? 'Unknown error'}
            action={<button type="button" className="sakar-btn sakar-btn--secondary" onClick={refetch}>Retry</button>}
          />
        ) : (
          <DataTable
            rows={filtered}
            rowKey={(e) => String(e.id)}
            emptyTitle="No events ingested yet for this robot"
            columns={[
              { key: 'time', header: 'Timestamp', render: (e) => new Date(e.occurredAt).toLocaleString() },
              { key: 'severity', header: 'Severity', render: (e) => <Badge tone={severityTone(e.severity)}>{e.severity}</Badge> },
              { key: 'type', header: 'Event Type', render: (e) => e.eventType },
              { key: 'payload', header: 'Payload', render: (e) => <span className="sakar-mono" style={{ fontSize: 12 }}>{e.payload ?? '—'}</span> },
            ]}
          />
        )}
      </Card>
    </div>
  );
}
