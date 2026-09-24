import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { getOperationRanking } from '../../api/dashboard';
import { useApi } from '../../hooks/useApi';
import { Breadcrumb } from '../../components/ui/Breadcrumb';
import { Card } from '../../components/ui/Card';
import { MetricCard } from '../../components/ui/MetricCard';
import { Tabs } from '../../components/ui/Tabs';
import { BarList } from '../../components/ui/BarList';
import { Icon } from '../../components/ui/Icon';
import { UnavailableFeature } from '../../components/ui/SimulatedDataBanner';
import { LoadingState, ErrorState } from '../../components/ui/States';

const NOT_TRACKED_REASON = 'No distance/odometer or call/summon concept exists anywhere in this platform — this metric cannot be reported honestly, so it is never fabricated as zero.';

// Operational Dashboard → Operation Ranking. Task counts and store/robot
// rankings are computed from real robot_tasks rows; mileage and call counts
// are always "Not tracked" — see OperationRankingResponse's own comment
// (types/domain.ts) for why.
export function OperationRankingPage() {
  const navigate = useNavigate();
  const { data, status, error, refetch } = useApi(() => getOperationRanking(), []);
  const [tab, setTab] = useState<'tasks' | 'mileage'>('tasks');

  if (status === 'loading' || status === 'idle') {
    return <LoadingState title="Loading operation ranking…" />;
  }
  if (status === 'error' || !data) {
    return (
      <ErrorState
        title="Could not load operation ranking"
        detail={error ?? undefined}
        action={<button type="button" className="sakar-btn sakar-btn--secondary" onClick={refetch}>Retry</button>}
      />
    );
  }

  return (
    <div>
      <Breadcrumb items={[{ label: 'Operation Ranking' }]} />
      <button type="button" className="sakar-btn sakar-btn--primary" style={{ marginBottom: 16 }} onClick={() => navigate('/operational-dashboard')}>Back</button>

      <div className="sakar-stat-grid" style={{ marginBottom: 20 }}>
        <MetricCard label="Number of tasks(Times) — Total cumulative" value={data.totalTasks} icon={<Icon.listCheck />} tone="info" />
        <MetricCard label="Mileage(rice) — Total cumulative" value="Not tracked" icon={<Icon.mapPin />} tone="neutral" trend={NOT_TRACKED_REASON} />
        <MetricCard label="Number of calls(Times) — Total cumulative" value="Not tracked" icon={<Icon.bell />} tone="neutral" trend={NOT_TRACKED_REASON} />
      </div>

      <Card>
        <Tabs ariaLabel="Operation ranking metric" tabs={[{ key: 'tasks', label: 'Number of tasks' }, { key: 'mileage', label: 'Mileage' }]} active={tab} onChange={setTab} />
      </Card>

      {tab === 'tasks' ? (
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 16 }}>
          <Card title="Store rankings">
            <BarList items={data.storeRankingsByTasks.map((r) => ({ key: r.id, label: r.label, value: r.count }))} />
          </Card>
          <Card title="Robot Ranking">
            <BarList items={data.robotRankingsByTasks.map((r) => ({ key: r.id, label: r.label, value: r.count }))} />
          </Card>
        </div>
      ) : (
        <Card>
          <UnavailableFeature reason={NOT_TRACKED_REASON} />
        </Card>
      )}
    </div>
  );
}
