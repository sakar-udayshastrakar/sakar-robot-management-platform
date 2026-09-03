import { useMemo, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { activateRobot, deactivateRobot, listRobots } from '../../api/robots';
import { useApi } from '../../hooks/useApi';
import { usePermissions } from '../../hooks/usePermissions';
import { useToast } from '../../components/ui/Toast';
import { useSiteNames } from '../shared/useSiteNames';
import { useRobotStatusProbe } from '../shared/useRobotStatusProbe';
import { PageHeader } from '../../components/ui/PageHeader';
import { Card } from '../../components/ui/Card';
import { DataTable } from '../../components/ui/DataTable';
import { Badge } from '../../components/ui/Badge';
import { StatusBadge } from '../../components/ui/StatusBadge';
import { Pagination } from '../../components/ui/Pagination';
import { SearchBar } from '../../components/ui/SearchBar';
import { FilterBar, FilterField } from '../../components/ui/FilterBar';
import { LoadingState, ErrorState } from '../../components/ui/States';
import { ConfirmDialog } from '../../components/ui/ConfirmDialog';
import { ApiRequestError } from '../../api/client';
import type { Robot, RobotLifecycleStatus } from '../../types/domain';
import { RegisterRobotForm } from './RegisterRobotForm';

const STATUS_TONE: Record<RobotLifecycleStatus, 'success' | 'neutral' | 'warning'> = {
  ACTIVE: 'success',
  REGISTERED: 'neutral',
  DEACTIVATED: 'warning',
};

type SortKey = 'name' | 'status' | 'site';

export function RobotsListPage() {
  const navigate = useNavigate();
  const { hasPermission } = usePermissions();
  const toast = useToast();
  const [params, setParams] = useSearchParams();
  const siteIdFilter = params.get('siteId') ?? '';

  const [page, setPage] = useState(0);
  const [showRegister, setShowRegister] = useState(false);
  const [search, setSearch] = useState(params.get('search') ?? '');
  const [statusFilter, setStatusFilter] = useState<RobotLifecycleStatus | 'ALL'>('ALL');
  const [modelFilter, setModelFilter] = useState('ALL');
  const [sortKey, setSortKey] = useState<SortKey>('name');
  const [pendingDeactivate, setPendingDeactivate] = useState<Robot | null>(null);
  const [busyRobotId, setBusyRobotId] = useState<string | null>(null);

  const { data, status, error, refetch } = useApi(() => listRobots(page, 25), [page]);
  // Memoized so this stays referentially stable while `data` is still
  // loading — an inline `data?.content ?? []` would mint a fresh empty
  // array every render, which would re-trigger any effect keyed on it
  // (useSiteNames/useRobotStatusProbe below) on every single render and
  // spin forever.
  const robots = useMemo(() => data?.content ?? [], [data]);
  const siteNames = useSiteNames(robots);
  const { statuses } = useRobotStatusProbe(robots);

  const modelOptions = useMemo(() => Array.from(new Set(robots.map((r) => r.robotModelId))), [robots]);

  const rows = useMemo(() => {
    let filtered = siteIdFilter ? robots.filter((r) => r.siteId === siteIdFilter) : robots;
    if (statusFilter !== 'ALL') {
      filtered = filtered.filter((r) => r.status === statusFilter);
    }
    if (modelFilter !== 'ALL') {
      filtered = filtered.filter((r) => r.robotModelId === modelFilter);
    }
    if (search.trim()) {
      const term = search.trim().toLowerCase();
      filtered = filtered.filter((r) => r.name.toLowerCase().includes(term) || r.serialNumber.toLowerCase().includes(term));
    }
    const sorted = [...filtered].sort((a, b) => {
      if (sortKey === 'name') return a.name.localeCompare(b.name);
      if (sortKey === 'status') return a.status.localeCompare(b.status);
      const siteA = a.siteId ? siteNames.get(a.siteId) ?? a.siteId : '';
      const siteB = b.siteId ? siteNames.get(b.siteId) ?? b.siteId : '';
      return siteA.localeCompare(siteB);
    });
    return sorted;
  }, [robots, siteIdFilter, statusFilter, modelFilter, search, sortKey, siteNames]);

  async function handleActivate(robot: Robot) {
    setBusyRobotId(robot.id);
    try {
      await activateRobot(robot.id);
      toast.show(`${robot.name} activated`, 'success');
      refetch();
    } catch (err) {
      toast.show(err instanceof ApiRequestError ? err.message : 'Failed to activate robot', 'error');
    } finally {
      setBusyRobotId(null);
    }
  }

  async function handleConfirmDeactivate() {
    if (!pendingDeactivate) return;
    setBusyRobotId(pendingDeactivate.id);
    try {
      await deactivateRobot(pendingDeactivate.id);
      toast.show(`${pendingDeactivate.name} deactivated`, 'success');
      refetch();
    } catch (err) {
      toast.show(err instanceof ApiRequestError ? err.message : 'Failed to deactivate robot', 'error');
    } finally {
      setBusyRobotId(null);
      setPendingDeactivate(null);
    }
  }

  return (
    <div>
      <PageHeader
        title="Robots"
        subtitle={`Sakar Robot registry${siteIdFilter ? ' — filtered by site' : ''}.`}
        actions={
          hasPermission('ROBOT_CONFIGURE') && (
            <button type="button" className="sakar-btn sakar-btn--primary" onClick={() => setShowRegister((v) => !v)}>
              {showRegister ? 'Cancel' : 'Register Robot'}
            </button>
          )
        }
      />

      {showRegister && (
        <div style={{ marginBottom: 20 }}>
          <RegisterRobotForm onCreated={() => { setShowRegister(false); refetch(); toast.show('Robot registered', 'success'); }} />
        </div>
      )}

      {status === 'loading' || status === 'idle' ? (
        <LoadingState title="Loading robots…" />
      ) : status === 'error' ? (
        <ErrorState
          title="Could not load robots"
          detail={error ?? undefined}
          action={<button type="button" className="sakar-btn sakar-btn--secondary" onClick={refetch}>Retry</button>}
        />
      ) : (
        <Card title={`Robots (${data?.totalElements ?? 0})`}>
          <FilterBar>
            <FilterField label="Search">
              <SearchBar value={search} onChange={setSearch} ariaLabel="Search robots by name or serial number" placeholder="Name or serial…" />
            </FilterField>
            <FilterField label="Status">
              <select value={statusFilter} onChange={(e) => setStatusFilter(e.target.value as typeof statusFilter)} aria-label="Filter by status">
                <option value="ALL">All statuses</option>
                <option value="ACTIVE">Active</option>
                <option value="REGISTERED">Registered</option>
                <option value="DEACTIVATED">Deactivated</option>
              </select>
            </FilterField>
            <FilterField label="Model">
              <select value={modelFilter} onChange={(e) => setModelFilter(e.target.value)} aria-label="Filter by model">
                <option value="ALL">All models</option>
                {modelOptions.map((m) => (
                  <option key={m} value={m}>{m.slice(0, 8)}…</option>
                ))}
              </select>
            </FilterField>
            <FilterField label="Sort">
              <select value={sortKey} onChange={(e) => setSortKey(e.target.value as SortKey)} aria-label="Sort by">
                <option value="name">Name</option>
                <option value="status">Status</option>
                <option value="site">Site</option>
              </select>
            </FilterField>
            {siteIdFilter && (
              <button type="button" className="sakar-btn sakar-btn--secondary" onClick={() => setParams({})}>
                Clear site filter
              </button>
            )}
          </FilterBar>

          <p className="sakar-page-subtitle" style={{ marginBottom: 12 }}>
            Battery and Agent Version are not exposed by the robot registry API. Current State/Last Heartbeat reflect
            a live status probe (up to 12 robots) where it succeeded.
          </p>

          <DataTable
            rows={rows}
            rowKey={(r) => r.id}
            emptyTitle={robots.length === 0 ? 'No robots registered' : 'No robots match these filters'}
            columns={[
              { key: 'name', header: 'Robot', render: (r) => (
                  <button type="button" className="sakar-link-btn" onClick={() => navigate(`/robots/${r.id}`)}>{r.name}</button>
                ) },
              { key: 'model', header: 'Model', render: (r) => <span className="sakar-mono" style={{ fontSize: 12 }}>{r.robotModelId.slice(0, 8)}…</span> },
              { key: 'site', header: 'Site', render: (r) => (r.siteId ? siteNames.get(r.siteId) ?? r.siteId : '—') },
              { key: 'status', header: 'Status', render: (r) => {
                  const s = statuses.get(r.id);
                  return (
                    <div style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
                      {s && s !== 'unavailable' ? <StatusBadge status={s.online ? 'ONLINE' : 'OFFLINE'} /> : <StatusBadge status="UNKNOWN" />}
                      <Badge tone={STATUS_TONE[r.status]}>{r.status}</Badge>
                    </div>
                  );
                } },
              { key: 'battery', header: 'Battery', render: () => <span className="sakar-page-subtitle">Not available</span> },
              { key: 'charging', header: 'Charging', render: () => <span className="sakar-page-subtitle">Not available</span> },
              { key: 'state', header: 'Current State', render: (r) => {
                  const s = statuses.get(r.id);
                  return s && s !== 'unavailable' ? s.mainState : <span className="sakar-page-subtitle">—</span>;
                } },
              { key: 'heartbeat', header: 'Last Heartbeat', render: (r) => {
                  const s = statuses.get(r.id);
                  return s && s !== 'unavailable' ? new Date(s.observedAt).toLocaleString() : <span className="sakar-page-subtitle">—</span>;
                } },
              { key: 'agent', header: 'Agent Version', render: () => <span className="sakar-page-subtitle">Not available</span> },
              { key: 'actions', header: 'Actions', render: (r) => (
                  <div style={{ display: 'flex', gap: 6 }}>
                    <button type="button" className="sakar-btn sakar-btn--secondary" onClick={() => navigate(`/robots/${r.id}`)}>View</button>
                    {hasPermission('ROBOT_CONFIGURE') && r.status !== 'ACTIVE' && (
                      <button type="button" className="sakar-btn sakar-btn--secondary" disabled={busyRobotId === r.id} onClick={() => handleActivate(r)}>
                        Activate
                      </button>
                    )}
                    {hasPermission('ROBOT_CONFIGURE') && r.status !== 'DEACTIVATED' && (
                      <button type="button" className="sakar-btn sakar-btn--danger" disabled={busyRobotId === r.id} onClick={() => setPendingDeactivate(r)}>
                        Deactivate
                      </button>
                    )}
                  </div>
                ) },
            ]}
          />
          {data && <Pagination page={data.number} totalPages={data.totalPages} onChange={setPage} />}
        </Card>
      )}

      <ConfirmDialog
        open={pendingDeactivate !== null}
        title="Deactivate robot"
        message={`Deactivate ${pendingDeactivate?.name}? It will stop being treated as an active fleet member until reactivated.`}
        confirmLabel="Deactivate"
        danger
        busy={busyRobotId === pendingDeactivate?.id}
        onConfirm={handleConfirmDeactivate}
        onCancel={() => setPendingDeactivate(null)}
      />
    </div>
  );
}
