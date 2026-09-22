import { useMemo } from 'react';
import { useNavigate } from 'react-router-dom';
import { listRobots } from '../../api/robots';
import { listAlerts } from '../../api/alerts';
import { useApi } from '../../hooks/useApi';
import { useRobotStatusProbe } from '../shared/useRobotStatusProbe';
import { useSiteNames } from '../shared/useSiteNames';
import { PageHeader } from '../../components/ui/PageHeader';
import { Card } from '../../components/ui/Card';
import { MetricCard } from '../../components/ui/MetricCard';
import { Badge } from '../../components/ui/Badge';
import { Icon } from '../../components/ui/Icon';
import { StatusBadge } from '../../components/ui/StatusBadge';
import { SeverityBadge } from '../../components/ui/SeverityBadge';
import { DataTable } from '../../components/ui/DataTable';
import { LoadingState, ErrorState } from '../../components/ui/States';
import { SimulatedDataBanner, UnavailableFeature } from '../../components/ui/SimulatedDataBanner';
import { generateEvents } from '../../mocks/simulated';
import { SystemHealthCard } from './SystemHealthCard';

export function DashboardPage() {
  const navigate = useNavigate();
  const { data: robotsPage, status, error, refetch } = useApi(() => listRobots(0, 100), []);
  const robots = robotsPage?.content ?? null;
  const { statuses } = useRobotStatusProbe(robots);
  const siteNames = useSiteNames(robots);

  // Real, organization-scoped alerts (up to the most recent 200) — used for
  // both the "Active Alerts" card and the Low Battery fleet metric, which is
  // genuinely derivable from real LOW_BATTERY alerts (unlike Locked/Faulted/
  // Cleaning/Charging below, which have no backend-derivable source today).
  const { data: alertsPage } = useApi(() => listAlerts(0, 200), []);
  const openAlerts = useMemo(() => (alertsPage?.content ?? []).filter((a) => a.status === 'OPEN'), [alertsPage]);
  const lowBatteryRobotCount = useMemo(
    () => new Set(openAlerts.filter((a) => a.alertType === 'LOW_BATTERY').map((a) => a.robotId)).size,
    [openAlerts],
  );

  const simulatedRobotId = robots?.[0]?.id ?? 'sim-robot-0';
  const recentEvents = useMemo(() => generateEvents(simulatedRobotId, 5), [simulatedRobotId]);

  if (status === 'loading' || status === 'idle') {
    return <LoadingState title="Loading dashboard…" />;
  }
  if (status === 'error') {
    return (
      <ErrorState
        title="Could not load dashboard data"
        detail={error ?? undefined}
        action={<button type="button" className="sakar-btn sakar-btn--secondary" onClick={refetch}>Retry</button>}
      />
    );
  }

  const total = robotsPage?.totalElements ?? 0;
  // Fleet connectivity comes from the backend's authoritative connectionStatus on each
  // robot, not from the live vendor probe — a successful probe only proves the vendor
  // API answered, which is why these counters used to disagree with the Alerts page.
  const listedRobots = robots ?? [];
  const onlineCount = listedRobots.filter((r) => r.connectionStatus === 'ONLINE').length;
  const offlineCount = listedRobots.filter((r) => r.connectionStatus === 'OFFLINE').length;
  const unknownCount = listedRobots.filter((r) => r.connectionStatus === 'UNKNOWN').length;
  const anyRobots = listedRobots.length > 0;

  return (
    <div>
      <PageHeader title="Dashboard" subtitle="Robot fleet operations control center." />

      <div className="sakar-stat-grid" style={{ marginBottom: 20 }}>
        <MetricCard label="Total Robots" value={total} icon={<Icon.robot />} tone="default" />
        <MetricCard
          label="Online"
          value={anyRobots ? onlineCount : '—'}
          icon={<Icon.wifi />}
          tone="success"
          trend={anyRobots ? `of ${listedRobots.length} listed` : 'Unavailable'}
        />
        <MetricCard
          label="Offline"
          value={anyRobots ? offlineCount : '—'}
          icon={<Icon.xCircle />}
          tone="danger"
          trend={anyRobots ? `of ${listedRobots.length} listed` : 'Unavailable'}
        />
        <MetricCard
          label="Unknown"
          value={anyRobots ? unknownCount : '—'}
          icon={<Icon.alertTriangle />}
          tone="neutral"
          trend={anyRobots ? 'Never reported' : 'Unavailable'}
        />
        <MetricCard label="Locked" value="—" icon={<Icon.lock />} tone="neutral" trend="Unavailable" />
        <MetricCard label="Low Battery" value={lowBatteryRobotCount} icon={<Icon.battery />} tone="warning" trend="Open LOW_BATTERY alerts" />
        <MetricCard label="Faulted" value="—" icon={<Icon.alertOctagon />} tone="neutral" trend="Unavailable" />
        <MetricCard label="Cleaning" value="—" icon={<Icon.spray />} tone="neutral" trend="Unavailable" />
        <MetricCard label="Charging" value="—" icon={<Icon.plug />} tone="neutral" trend="Unavailable" />
      </div>

      <UnavailableFeature reason="Locked, Faulted, Cleaning, and Charging have no backend-derivable source today: robot lock/unlock is not implemented, there is no fleet-wide fault/error status field, and there is no fleet-wide task-list endpoint to derive an active-cleaning count from (only per-robot task listing exists). These are marked Unavailable rather than fabricated. Low Battery is real — a count of robots with an open LOW_BATTERY alert." />
      {anyRobots && unknownCount === listedRobots.length && (
        <UnavailableFeature reason="No robot has ever reported heartbeat or telemetry, so every robot's connection status is Unknown. Connectivity is derived from the robot's own heartbeat/telemetry against the configured offline threshold — a successful Keenon synchronization is not a heartbeat and never marks a robot online." />
      )}

      <Card title="Robot Fleet Overview" actions={<button type="button" className="sakar-btn sakar-btn--secondary" onClick={() => navigate('/robots')}>View all</button>}>
        <p className="sakar-page-subtitle" style={{ marginBottom: 12 }}>
          Status and Last heartbeat come from the backend's authoritative connection status (heartbeat/telemetry
          against the configured offline threshold). Current state reflects a live vendor status probe where it
          succeeded. Battery and Agent version are not exposed by any backend endpoint today.
        </p>
        <DataTable
          rows={(robots ?? []).slice(0, 8)}
          rowKey={(r) => r.id}
          emptyTitle="No robots registered"
          columns={[
            { key: 'name', header: 'Robot', render: (r) => (
                <button type="button" className="sakar-link-btn" onClick={() => navigate(`/robots/${r.id}`)}>{r.name}</button>
              ) },
            { key: 'status', header: 'Status', render: (r) => <StatusBadge status={r.connectionStatus} /> },
            { key: 'battery', header: 'Battery', render: () => <span className="sakar-page-subtitle">Not available</span> },
            { key: 'state', header: 'Current State', render: (r) => {
                const s = statuses.get(r.id);
                return s && s !== 'unavailable' ? s.mainState : <span className="sakar-page-subtitle">—</span>;
              } },
            { key: 'site', header: 'Site', render: (r) => (r.siteId ? siteNames.get(r.siteId) ?? r.siteId : '—') },
            { key: 'heartbeat', header: 'Last Heartbeat', render: (r) => (
                r.lastSeenAt ? new Date(r.lastSeenAt).toLocaleString() : <span className="sakar-page-subtitle">Never received</span>
              ) },
            { key: 'agent', header: 'Agent Version', render: () => <span className="sakar-page-subtitle">Not available</span> },
          ]}
        />
      </Card>

      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(320px, 1fr))', gap: 16, marginTop: 20 }}>
        <Card title="Recent Robot Events">
          <SimulatedDataBanner />
          {recentEvents.map((e) => (
            <div key={e.id} style={{ display: 'flex', justifyContent: 'space-between', padding: '8px 0', borderBottom: '1px solid var(--sakar-border)' }}>
              <span>{e.message}</span>
              <Badge tone={e.severity === 'INFO' ? 'info' : e.severity === 'WARNING' ? 'warning' : 'danger'}>{e.severity}</Badge>
            </div>
          ))}
        </Card>

        <Card title="Active Alerts" actions={<button type="button" className="sakar-btn sakar-btn--secondary" onClick={() => navigate('/alerts')}>View all</button>}>
          {openAlerts.length === 0 ? (
            <p className="sakar-page-subtitle">No open alerts.</p>
          ) : (
            openAlerts.slice(0, 8).map((a) => (
              <div key={a.id} style={{ display: 'flex', justifyContent: 'space-between', padding: '8px 0', borderBottom: '1px solid var(--sakar-border)' }}>
                <span>{a.message}</span>
                <SeverityBadge severity={a.severity} />
              </div>
            ))
          )}
        </Card>

        <SystemHealthCard />
      </div>
    </div>
  );
}
