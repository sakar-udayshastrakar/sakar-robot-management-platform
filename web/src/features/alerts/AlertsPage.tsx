import { useMemo, useState } from 'react';
import { acknowledgeAlert, listAlerts, resolveAlert } from '../../api/alerts';
import { useApi } from '../../hooks/useApi';
import { useRobotOptions } from '../shared/useRobotOptions';
import { usePermissions } from '../../hooks/usePermissions';
import { useToast } from '../../components/ui/Toast';
import { ApiRequestError } from '../../api/client';
import type { AlertSeverity, RobotAlertStatus, RobotAlertType } from '../../types/domain';
import { PageHeader } from '../../components/ui/PageHeader';
import { Card } from '../../components/ui/Card';
import { DataTable } from '../../components/ui/DataTable';
import { Badge } from '../../components/ui/Badge';
import { SeverityBadge } from '../../components/ui/SeverityBadge';
import { SearchBar } from '../../components/ui/SearchBar';
import { FilterBar } from '../../components/ui/FilterBar';
import { Pagination } from '../../components/ui/Pagination';
import { LoadingState, ErrorState } from '../../components/ui/States';

const SEVERITIES: (AlertSeverity | 'ALL')[] = ['ALL', 'CRITICAL', 'HIGH', 'MEDIUM', 'LOW'];
const STATUSES: (RobotAlertStatus | 'ALL')[] = ['ALL', 'OPEN', 'ACKNOWLEDGED', 'RESOLVED'];
const STATUS_TONE: Record<RobotAlertStatus, 'danger' | 'warning' | 'success'> = {
  OPEN: 'danger',
  ACKNOWLEDGED: 'warning',
  RESOLVED: 'success',
};
const TYPE_LABEL: Record<RobotAlertType, string> = {
  LOW_BATTERY: 'Low Battery',
  OFFLINE: 'Offline',
};

// Real, organization-scoped fleet alerts (GET/POST /api/v1/alerts/*).
// Generated only by two real rules on the backend — low battery and
// offline — so no other alert type will ever appear here.
export function AlertsPage() {
  const { robots } = useRobotOptions();
  const { hasPermission } = usePermissions();
  const toast = useToast();
  const [page, setPage] = useState(0);
  const [search, setSearch] = useState('');
  const [severityFilter, setSeverityFilter] = useState<AlertSeverity | 'ALL'>('ALL');
  const [statusFilter, setStatusFilter] = useState<RobotAlertStatus | 'ALL'>('ALL');
  const [robotFilter, setRobotFilter] = useState('ALL');
  const [busyId, setBusyId] = useState<string | null>(null);

  const { data, status, error, refetch } = useApi(() => listAlerts(page, 25), [page]);
  const robotName = useMemo(() => new Map(robots.map((r) => [r.id, r.name])), [robots]);

  const filtered = (data?.content ?? []).filter((a) => {
    if (severityFilter !== 'ALL' && a.severity !== severityFilter) return false;
    if (statusFilter !== 'ALL' && a.status !== statusFilter) return false;
    if (robotFilter !== 'ALL' && a.robotId !== robotFilter) return false;
    if (search.trim() && !a.message.toLowerCase().includes(search.trim().toLowerCase())) return false;
    return true;
  });

  async function handleAcknowledge(id: string) {
    setBusyId(id);
    try {
      await acknowledgeAlert(id);
      toast.show('Alert acknowledged', 'success');
      refetch();
    } catch (err) {
      toast.show(err instanceof ApiRequestError ? err.message : 'Failed to acknowledge alert', 'error');
    } finally {
      setBusyId(null);
    }
  }

  async function handleResolve(id: string) {
    setBusyId(id);
    try {
      await resolveAlert(id);
      toast.show('Alert resolved', 'success');
      refetch();
    } catch (err) {
      toast.show(err instanceof ApiRequestError ? err.message : 'Failed to resolve alert', 'error');
    } finally {
      setBusyId(null);
    }
  }

  if (status === 'loading' || status === 'idle') {
    return <LoadingState title="Loading alerts…" />;
  }
  if (status === 'error') {
    return (
      <ErrorState
        title="Could not load alerts"
        detail={error ?? undefined}
        action={<button type="button" className="sakar-btn sakar-btn--secondary" onClick={refetch}>Retry</button>}
      />
    );
  }

  const canControl = hasPermission('ROBOT_CONTROL');

  return (
    <div>
      <PageHeader title="Alerts" subtitle="Fleet-wide alert management, scoped to your organization." />

      <Card title={`Alerts (${filtered.length} of ${data?.totalElements ?? 0} on this page)`}>
        <FilterBar>
          <SearchBar value={search} onChange={setSearch} ariaLabel="Search alerts" placeholder="Search alert message…" />
          <select value={severityFilter} onChange={(e) => setSeverityFilter(e.target.value as typeof severityFilter)} aria-label="Filter by severity">
            {SEVERITIES.map((s) => <option key={s} value={s}>{s === 'ALL' ? 'All severities' : s}</option>)}
          </select>
          <select value={statusFilter} onChange={(e) => setStatusFilter(e.target.value as typeof statusFilter)} aria-label="Filter by status">
            {STATUSES.map((s) => <option key={s} value={s}>{s === 'ALL' ? 'All statuses' : s}</option>)}
          </select>
          <select value={robotFilter} onChange={(e) => setRobotFilter(e.target.value)} aria-label="Filter by robot">
            <option value="ALL">All robots</option>
            {robots.map((r) => <option key={r.id} value={r.id}>{r.name}</option>)}
          </select>
        </FilterBar>

        <DataTable
          rows={filtered}
          rowKey={(a) => a.id}
          emptyTitle="No alerts match these filters"
          columns={[
            { key: 'severity', header: 'Severity', render: (a) => <SeverityBadge severity={a.severity} /> },
            { key: 'robot', header: 'Robot', render: (a) => robotName.get(a.robotId) ?? a.robotId },
            { key: 'type', header: 'Alert Type', render: (a) => TYPE_LABEL[a.alertType] ?? a.alertType },
            { key: 'alert', header: 'Message', render: (a) => a.message },
            { key: 'created', header: 'Created', render: (a) => new Date(a.createdAt).toLocaleString() },
            { key: 'status', header: 'Status', render: (a) => <Badge tone={STATUS_TONE[a.status]}>{a.status}</Badge> },
            {
              key: 'actions',
              header: 'Actions',
              render: (a) => {
                if (!canControl) return <span className="sakar-page-subtitle">No control permission</span>;
                if (a.status === 'OPEN') {
                  return (
                    <button type="button" className="sakar-btn sakar-btn--secondary" disabled={busyId === a.id} onClick={() => handleAcknowledge(a.id)}>
                      Acknowledge
                    </button>
                  );
                }
                if (a.status === 'ACKNOWLEDGED') {
                  return (
                    <button type="button" className="sakar-btn sakar-btn--primary" disabled={busyId === a.id} onClick={() => handleResolve(a.id)}>
                      Resolve
                    </button>
                  );
                }
                return <span className="sakar-page-subtitle">—</span>;
              },
            },
          ]}
        />
        {data && <Pagination page={data.number} totalPages={data.totalPages} onChange={setPage} />}
      </Card>
    </div>
  );
}
