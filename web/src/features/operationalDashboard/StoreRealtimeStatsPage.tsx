import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { getStoreRealtimeStats } from '../../api/dashboard';
import { listAllAccessibleSites } from '../../api/sites';
import { useApi } from '../../hooks/useApi';
import { Breadcrumb } from '../../components/ui/Breadcrumb';
import { Card } from '../../components/ui/Card';
import { MetricCard } from '../../components/ui/MetricCard';
import { Tabs } from '../../components/ui/Tabs';
import { BarList } from '../../components/ui/BarList';
import { Icon } from '../../components/ui/Icon';
import { UnavailableFeature } from '../../components/ui/SimulatedDataBanner';
import { LoadingState, ErrorState } from '../../components/ui/States';

const NOT_TRACKED_REASON = 'No distance/odometer, call/summon, or speed-telemetry concept exists anywhere in this platform — never fabricated as zero.';

// Operational Dashboard → Store Real-Time Data Statistics. Today's task
// count, mode proportion, and active-machine count are computed from real
// robot_tasks/robots rows; calls, mileage, and average speed are always
// "Not tracked" — see StoreRealtimeStatsResponse's own comment (types/domain.ts).
export function StoreRealtimeStatsPage() {
  const navigate = useNavigate();
  const [siteId, setSiteId] = useState('');
  const [appliedSiteId, setAppliedSiteId] = useState<string | null>(null);
  const [tab, setTab] = useState<'tasks' | 'calls' | 'mode'>('tasks');
  const { data: sites } = useApi(() => listAllAccessibleSites(), []);
  const { data, status, error, refetch } = useApi(() => getStoreRealtimeStats(appliedSiteId), [appliedSiteId]);

  if (status === 'loading' || status === 'idle') {
    return <LoadingState title="Loading store real-time data…" />;
  }
  if (status === 'error' || !data) {
    return (
      <ErrorState
        title="Could not load store real-time data"
        detail={error ?? undefined}
        action={<button type="button" className="sakar-btn sakar-btn--secondary" onClick={refetch}>Retry</button>}
      />
    );
  }

  const modeItems = data.taskModeProportionToday.map((s) => ({ key: s.taskType, label: s.taskType, value: s.count }));

  return (
    <div>
      <Breadcrumb items={[{ label: 'Store Real-Time Data Statistics' }]} />
      <button type="button" className="sakar-btn sakar-btn--primary" style={{ marginBottom: 16 }} onClick={() => navigate('/operational-dashboard')}>Back</button>

      <Card>
        <div style={{ display: 'flex', gap: 12, alignItems: 'flex-end' }}>
          <div className="sakar-field" style={{ marginBottom: 0, maxWidth: 260 }}>
            <label htmlFor="store-realtime-site">Store</label>
            <select id="store-realtime-site" value={siteId} onChange={(e) => setSiteId(e.target.value)}>
              <option value="">Please select</option>
              {(sites ?? []).map((s) => <option key={s.id} value={s.id}>{s.name}</option>)}
            </select>
          </div>
          <button type="button" className="sakar-btn sakar-btn--primary" onClick={() => setAppliedSiteId(siteId || null)}>Search</button>
          <button type="button" className="sakar-btn sakar-btn--secondary" onClick={() => { setSiteId(''); setAppliedSiteId(null); }}>Reset</button>
        </div>
      </Card>

      <div className="sakar-stat-grid" style={{ marginBottom: 20 }}>
        <MetricCard label="Number of tasks today" value={data.tasksToday} icon={<Icon.listCheck />} tone="info" />
        <MetricCard
          label="Today's task mode proportion"
          value={modeItems.length > 0 ? `${modeItems[0].label} ${data.taskModeProportionToday[0].percentage.toFixed(0)}%` : 'No data'}
          icon={<Icon.mapPin />}
          tone="default"
        />
        <MetricCard label="Number of calls today" value="Not tracked" icon={<Icon.bell />} tone="neutral" trend={NOT_TRACKED_REASON} />
        <MetricCard
          label="Today's data"
          value={`${data.activeMachines} active`}
          icon={<Icon.activity />}
          tone="success"
          trend="Mileage / avg. speed: Not tracked"
        />
      </div>

      <Card>
        <Tabs
          ariaLabel="Store real-time metric"
          tabs={[{ key: 'tasks', label: 'Number of tasks' }, { key: 'calls', label: 'Number of calls' }, { key: 'mode', label: 'Mode proportion' }]}
          active={tab}
          onChange={setTab}
        />
      </Card>

      {tab === 'tasks' && (
        <Card title={`Today's operating data — Number of tasks (${data.tasksToday})`}>
          <BarList items={modeItems} />
        </Card>
      )}
      {tab === 'calls' && (
        <Card><UnavailableFeature reason={NOT_TRACKED_REASON} /></Card>
      )}
      {tab === 'mode' && (
        <Card title="Today's operating data — Mode proportion">
          <BarList items={data.taskModeProportionToday.map((s) => ({ key: s.taskType, label: `${s.taskType} (${s.percentage.toFixed(0)}%)`, value: s.count }))} />
        </Card>
      )}
    </div>
  );
}
