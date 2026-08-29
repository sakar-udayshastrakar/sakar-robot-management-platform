import { useEffect, useState } from 'react';
import { listRobots, getRobotStatus } from '../../api/robots';
import type { Robot } from '../../types/domain';
import { useApi } from '../../hooks/useApi';
import { Card } from '../../components/ui/Card';
import { StatCard } from '../../components/ui/StatCard';
import { Badge } from '../../components/ui/Badge';
import { LoadingState, ErrorState } from '../../components/ui/States';
import { SimulatedDataBanner, UnavailableFeature } from '../../components/ui/SimulatedDataBanner';
import { generateAlerts, generateErrors, generateEvents } from '../../mocks/simulated';

const LIVE_STATUS_PROBE_LIMIT = 12;

interface LiveStatusSummary {
  online: number;
  offline: number;
  probed: number;
}

function useLiveStatusSummary(robots: Robot[] | null) {
  const [summary, setSummary] = useState<LiveStatusSummary | null>(null);

  useEffect(() => {
    if (!robots || robots.length === 0) {
      setSummary(null);
      return;
    }
    let cancelled = false;
    const sample = robots.slice(0, LIVE_STATUS_PROBE_LIMIT);

    Promise.allSettled(sample.map((r) => getRobotStatus(r.id))).then((results) => {
      if (cancelled) {
        return;
      }
      const fulfilled = results.filter((r) => r.status === 'fulfilled') as PromiseFulfilledResult<
        Awaited<ReturnType<typeof getRobotStatus>>
      >[];
      if (fulfilled.length === 0) {
        setSummary({ online: 0, offline: 0, probed: 0 });
        return;
      }
      const online = fulfilled.filter((r) => r.value.online).length;
      setSummary({ online, offline: fulfilled.length - online, probed: fulfilled.length });
    });

    return () => {
      cancelled = true;
    };
  }, [robots]);

  return summary;
}

export function DashboardPage() {
  const { data: robotsPage, status, error, refetch } = useApi(() => listRobots(0, 100), []);
  const robots = robotsPage?.content ?? null;
  const liveStatus = useLiveStatusSummary(robots);

  if (status === 'loading' || status === 'idle') {
    return <LoadingState title="Loading dashboard…" />;
  }
  if (status === 'error') {
    return (
      <ErrorState
        title="Could not load dashboard data"
        detail={error ?? undefined}
        action={
          <button type="button" className="sakar-btn sakar-btn--secondary" onClick={refetch}>
            Retry
          </button>
        }
      />
    );
  }

  const total = robotsPage?.totalElements ?? 0;
  const active = robots?.filter((r) => r.status === 'ACTIVE').length ?? 0;
  const registered = robots?.filter((r) => r.status === 'REGISTERED').length ?? 0;
  const deactivated = robots?.filter((r) => r.status === 'DEACTIVATED').length ?? 0;

  const simulatedRobotId = robots?.[0]?.id ?? 'sim-robot-0';
  const recentEvents = generateEvents(simulatedRobotId, 5);
  const recentErrors = generateErrors(simulatedRobotId, 5);
  const recentAlerts = generateAlerts(simulatedRobotId, 5);

  return (
    <div>
      <div className="sakar-page-header">
        <div>
          <h1 className="sakar-page-title">Dashboard</h1>
          <p className="sakar-page-subtitle">Fleet overview across every organization you can access.</p>
        </div>
      </div>

      <div className="sakar-stat-grid" style={{ marginBottom: 24 }}>
        <StatCard label="Total Robots" value={total} />
        <StatCard label="Active" value={active} tone="success" />
        <StatCard label="Registered (not activated)" value={registered} />
        <StatCard label="Deactivated" value={deactivated} tone="warning" />
      </div>

      <Card title="Live connectivity">
        {liveStatus === null ? (
          <LoadingState title="Probing robot status…" />
        ) : liveStatus.probed === 0 ? (
          <UnavailableFeature reason="GET /robots/{id}/status returned no usable result for any robot in this fleet (no live adapter connection) — online/offline counts require a connected robot and are not shown as zero to avoid implying a false negative." />
        ) : (
          <div className="sakar-stat-grid">
            <StatCard label="Online" value={liveStatus.online} tone="success" />
            <StatCard label="Offline" value={liveStatus.offline} tone="danger" />
            <StatCard label="Charging (simulated)" value={Math.round(liveStatus.probed * 0.2)} />
            <StatCard label="Low battery (simulated)" value={Math.round(liveStatus.probed * 0.1)} tone="warning" />
          </div>
        )}
        <p className="sakar-page-subtitle" style={{ marginTop: 12 }}>
          Online/offline reflects a live <code>GET /robots/{'{id}'}/status</code> probe of up to{' '}
          {LIVE_STATUS_PROBE_LIMIT} robots. Charging and low-battery are not derivable from the current status
          response shape and are simulated placeholders.
        </p>
      </Card>

      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(320px, 1fr))', gap: 16, marginTop: 24 }}>
        <Card title="Recent robot events">
          <SimulatedDataBanner />
          {recentEvents.map((e) => (
            <div key={e.id} style={{ display: 'flex', justifyContent: 'space-between', padding: '8px 0', borderBottom: '1px solid var(--sakar-border)' }}>
              <span>{e.message}</span>
              <Badge tone={e.severity === 'INFO' ? 'info' : e.severity === 'WARNING' ? 'warning' : 'danger'}>
                {e.severity}
              </Badge>
            </div>
          ))}
        </Card>

        <Card title="Recent errors">
          <SimulatedDataBanner />
          {recentErrors.map((e) => (
            <div key={e.id} style={{ display: 'flex', justifyContent: 'space-between', padding: '8px 0', borderBottom: '1px solid var(--sakar-border)' }}>
              <span>{e.message}</span>
              <Badge tone="danger">{e.errorCode}</Badge>
            </div>
          ))}
        </Card>

        <Card title="Recent alerts">
          <SimulatedDataBanner />
          {recentAlerts.map((a) => (
            <div key={a.id} style={{ display: 'flex', justifyContent: 'space-between', padding: '8px 0', borderBottom: '1px solid var(--sakar-border)' }}>
              <span>{a.message}</span>
              <Badge tone={a.status === 'ACTIVE' ? 'danger' : a.status === 'ACKNOWLEDGED' ? 'warning' : 'success'}>
                {a.status}
              </Badge>
            </div>
          ))}
        </Card>
      </div>
    </div>
  );
}
