import { useMemo, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { activateRobot, deactivateRobot, listRobots } from '../../api/robots';
import { useApi } from '../../hooks/useApi';
import { usePermissions } from '../../hooks/usePermissions';
import { useToast } from '../../components/ui/Toast';
import { useSiteNames } from '../shared/useSiteNames';
import { useRobotStatusProbe } from '../shared/useRobotStatusProbe';
import { Breadcrumb } from '../../components/ui/Breadcrumb';
import { PageHeader } from '../../components/ui/PageHeader';
import { DataTable } from '../../components/ui/DataTable';
import { Badge } from '../../components/ui/Badge';
import { StatusBadge } from '../../components/ui/StatusBadge';
import { Pagination } from '../../components/ui/Pagination';
import { SearchBar } from '../../components/ui/SearchBar';
import { FilterField } from '../../components/ui/FilterBar';
import { ErrorState } from '../../components/ui/States';
import { ConfirmDialog } from '../../components/ui/ConfirmDialog';
import { Icon } from '../../components/ui/Icon';
import { ApiRequestError } from '../../api/client';
import type { Robot, RobotLifecycleStatus } from '../../types/domain';
import { RegisterRobotForm } from './RegisterRobotForm';

const STATUS_TONE: Record<RobotLifecycleStatus, 'success' | 'neutral' | 'warning'> = {
  ACTIVE: 'success',
  REGISTERED: 'neutral',
  DEACTIVATED: 'warning',
};

type SortKey = 'name' | 'status' | 'site';

const DEFAULT_PAGE_SIZE = 25;

export function RobotsListPage() {
  const navigate = useNavigate();
  const { hasPermission } = usePermissions();
  const toast = useToast();
  const [params, setParams] = useSearchParams();
  const siteIdFilter = params.get('siteId') ?? '';

  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState(DEFAULT_PAGE_SIZE);
  const [showRegister, setShowRegister] = useState(false);
  const [search, setSearch] = useState(params.get('search') ?? '');
  const [statusFilter, setStatusFilter] = useState<RobotLifecycleStatus | 'ALL'>('ALL');
  const [modelFilter, setModelFilter] = useState('ALL');
  const [sortKey, setSortKey] = useState<SortKey>('name');
  const [pendingDeactivate, setPendingDeactivate] = useState<Robot | null>(null);
  const [busyRobotId, setBusyRobotId] = useState<string | null>(null);

  const { data, status, error, refetch } = useApi(() => listRobots(page, pageSize), [page, pageSize]);
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

  const filtersActive =
    Boolean(search.trim()) || statusFilter !== 'ALL' || modelFilter !== 'ALL' || Boolean(siteIdFilter);

  function resetFilters() {
    setSearch('');
    setStatusFilter('ALL');
    setModelFilter('ALL');
    if (siteIdFilter) {
      setParams({});
    }
  }

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

  const loading = status === 'loading' || status === 'idle';
  const failed = status === 'error';

  return (
    <div>
      <Breadcrumb items={[{ label: 'Fleet' }, { label: 'Robots' }]} />
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
        <div style={{ marginBottom: 'var(--sakar-sp-4)' }}>
          <RegisterRobotForm onCreated={() => { setShowRegister(false); refetch(); toast.show('Robot registered', 'success'); }} />
        </div>
      )}

      <div className="sakar-filter-card">
        <div className="sakar-filter-grid">
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
        </div>
        <div className="sakar-filter-actions">
          <button
            type="button"
            className="sakar-btn sakar-btn--secondary"
            onClick={resetFilters}
            disabled={!filtersActive}
          >
            Reset
          </button>
        </div>
      </div>

      <div className="sakar-toolbar">
        <div className="sakar-toolbar-info">
          <span className="sakar-toolbar-count">
            {loading || failed
              ? 'Robots'
              : `Showing ${rows.length} of ${data?.totalElements ?? 0} robot${(data?.totalElements ?? 0) === 1 ? '' : 's'}`}
          </span>
          <span className="sakar-toolbar-note">
            Battery, charging and agent version are not exposed by the robot registry API, so they are not shown.
            Current state and last heartbeat come from a live status probe (up to 12 robots) where it succeeded.
            Filtering and sorting apply to the loaded page.
          </span>
        </div>
        <div className="sakar-toolbar-actions">
          <button type="button" className="sakar-btn sakar-btn--secondary sakar-btn--sm" onClick={refetch} disabled={loading}>
            <Icon.refresh width={13} height={13} /> Refresh
          </button>
        </div>
      </div>

      <div className="sakar-card sakar-card--flush">
        <div className="sakar-card-body">
          {failed ? (
            <div style={{ padding: 'var(--sakar-sp-4)' }}>
              <ErrorState
                title="Could not load robots"
                detail={error ?? undefined}
                action={<button type="button" className="sakar-btn sakar-btn--secondary" onClick={refetch}>Retry</button>}
              />
            </div>
          ) : (
            <>
              <DataTable
                rows={rows}
                rowKey={(r) => r.id}
                loading={loading}
                indexColumn
                indexOffset={page * pageSize}
                emptyTitle={robots.length === 0 ? 'No robots registered' : 'No robots match these filters'}
                emptyDetail={
                  robots.length === 0
                    ? 'Register a robot to add it to the Sakar fleet registry.'
                    : 'Adjust or reset the filters above to see more results.'
                }
                columns={[
                  { key: 'name', header: 'Robot', render: (r) => (
                      <button type="button" className="sakar-link-btn sakar-nowrap" onClick={() => navigate(`/robots/${r.id}`)}>{r.name}</button>
                    ) },
                  { key: 'serial', header: 'Serial number', render: (r) => <span className="sakar-mono sakar-nowrap" style={{ fontSize: 12 }}>{r.serialNumber}</span> },
                  { key: 'model', header: 'Model', render: (r) => <span className="sakar-mono sakar-nowrap" style={{ fontSize: 12 }}>{r.robotModelId.slice(0, 8)}…</span> },
                  { key: 'site', header: 'Site', render: (r) => <span className="sakar-nowrap">{r.siteId ? siteNames.get(r.siteId) ?? r.siteId : '—'}</span> },
                  { key: 'connection', header: 'Connection', render: (r) => {
                      const s = statuses.get(r.id);
                      return s && s !== 'unavailable' ? <StatusBadge status={s.online ? 'ONLINE' : 'OFFLINE'} /> : <StatusBadge status="UNKNOWN" />;
                    } },
                  { key: 'status', header: 'Registration', render: (r) => <Badge tone={STATUS_TONE[r.status]}>{r.status}</Badge> },
                  { key: 'state', header: 'Current state', render: (r) => {
                      const s = statuses.get(r.id);
                      return <span className="sakar-nowrap">{s && s !== 'unavailable' ? s.mainState : <span className="sakar-page-subtitle">—</span>}</span>;
                    } },
                  { key: 'heartbeat', header: 'Last heartbeat', render: (r) => {
                      const s = statuses.get(r.id);
                      return <span className="sakar-nowrap">{s && s !== 'unavailable' ? new Date(s.observedAt).toLocaleString() : <span className="sakar-page-subtitle">—</span>}</span>;
                    } },
                  { key: 'actions', header: 'Actions', align: 'right', render: (r) => (
                      <div style={{ display: 'flex', gap: 6, justifyContent: 'flex-end' }}>
                        <button type="button" className="sakar-btn sakar-btn--secondary sakar-btn--sm" onClick={() => navigate(`/robots/${r.id}`)}>View</button>
                        {hasPermission('ROBOT_CONFIGURE') && r.status !== 'ACTIVE' && (
                          <button type="button" className="sakar-btn sakar-btn--secondary sakar-btn--sm" disabled={busyRobotId === r.id} onClick={() => handleActivate(r)}>
                            Activate
                          </button>
                        )}
                        {hasPermission('ROBOT_CONFIGURE') && r.status !== 'DEACTIVATED' && (
                          <button type="button" className="sakar-btn sakar-btn--danger sakar-btn--sm" disabled={busyRobotId === r.id} onClick={() => setPendingDeactivate(r)}>
                            Deactivate
                          </button>
                        )}
                      </div>
                    ) },
                ]}
              />
              {data && (
                <Pagination
                  page={data.number}
                  totalPages={data.totalPages}
                  onChange={setPage}
                  total={data.totalElements}
                  pageSize={pageSize}
                  // 25 is this page's default, so it must be one of the
                  // offered options — otherwise the select falls back to
                  // displaying the first option and misreports the real size.
                  pageSizeOptions={[10, 25, 50, 100]}
                  onPageSizeChange={(size) => { setPageSize(size); setPage(0); }}
                />
              )}
            </>
          )}
        </div>
      </div>

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
