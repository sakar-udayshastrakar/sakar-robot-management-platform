import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { getHotelTaskRecord } from '../../api/dashboard';
import { listAllAccessibleSites } from '../../api/sites';
import { listRobots } from '../../api/robots';
import { useApi } from '../../hooks/useApi';
import { Breadcrumb } from '../../components/ui/Breadcrumb';
import { Card } from '../../components/ui/Card';
import { MetricCard } from '../../components/ui/MetricCard';
import { BarList } from '../../components/ui/BarList';
import { Icon } from '../../components/ui/Icon';
import { LoadingState, ErrorState } from '../../components/ui/States';

const NOT_TRACKED_REASON = 'No distance/odometer or hotel-room concept exists anywhere in this platform — never fabricated as zero.';

function formatDuration(totalSeconds: number): string {
  const hours = Math.floor(totalSeconds / 3600);
  const minutes = Math.floor((totalSeconds % 3600) / 60);
  if (hours === 0 && minutes === 0) return '0 Hour';
  return `${hours}h ${minutes}m`;
}

interface Filters {
  siteId: string;
  robotId: string;
  taskType: string;
  from: string;
  to: string;
}

function defaultFilters(): Filters {
  const today = new Date();
  const weekAgo = new Date(today.getTime() - 7 * 24 * 60 * 60 * 1000);
  return { siteId: '', robotId: '', taskType: '', from: weekAgo.toISOString().slice(0, 10), to: today.toISOString().slice(0, 10) };
}

// Operational Dashboard → Hotel Task Record. Total task volume, cumulative
// duration, and the daily breakdown chart are computed from real robot_tasks
// rows within the filtered scope; cumulative mileage and room count are
// always "Not tracked" — see HotelTaskRecordResponse's own comment (types/domain.ts).
export function HotelTaskRecordPage() {
  const navigate = useNavigate();
  const [form, setForm] = useState<Filters>(defaultFilters());
  const [applied, setApplied] = useState<Filters>(form);
  const { data: sites } = useApi(() => listAllAccessibleSites(), []);
  const { data: robotsPage } = useApi(() => listRobots(0, 200), []);
  const { data, status, error, refetch } = useApi(() => getHotelTaskRecord({
    siteId: applied.siteId || null,
    robotId: applied.robotId || null,
    taskType: applied.taskType || null,
    from: applied.from || null,
    to: applied.to || null,
  }), [applied]);

  if (status === 'loading' || status === 'idle') {
    return <LoadingState title="Loading hotel task record…" />;
  }
  if (status === 'error' || !data) {
    return (
      <ErrorState
        title="Could not load hotel task record"
        detail={error ?? undefined}
        action={<button type="button" className="sakar-btn sakar-btn--secondary" onClick={refetch}>Retry</button>}
      />
    );
  }

  return (
    <div>
      <Breadcrumb items={[{ label: 'Home page' }, { label: 'Hotel Task Record' }]} />
      <button type="button" className="sakar-btn sakar-btn--primary" style={{ marginBottom: 16 }} onClick={() => navigate('/operational-dashboard')}>Back</button>

      <Card>
        <div style={{ display: 'flex', gap: 12, alignItems: 'flex-end', flexWrap: 'wrap' }}>
          <div className="sakar-field" style={{ marginBottom: 0, minWidth: 180 }}>
            <label htmlFor="hotel-store">Store</label>
            <select id="hotel-store" value={form.siteId} onChange={(e) => setForm((f) => ({ ...f, siteId: e.target.value }))}>
              <option value="">Please select</option>
              {(sites ?? []).map((s) => <option key={s.id} value={s.id}>{s.name}</option>)}
            </select>
          </div>
          <div className="sakar-field" style={{ marginBottom: 0, minWidth: 180 }}>
            <label htmlFor="hotel-robot">Robot</label>
            <select id="hotel-robot" value={form.robotId} onChange={(e) => setForm((f) => ({ ...f, robotId: e.target.value }))}>
              <option value="">Please select</option>
              {(robotsPage?.content ?? []).map((r) => <option key={r.id} value={r.id}>{r.name} ({r.serialNumber})</option>)}
            </select>
          </div>
          <div className="sakar-field" style={{ marginBottom: 0, minWidth: 140 }}>
            <label htmlFor="hotel-task-type">Task type</label>
            <input id="hotel-task-type" value={form.taskType} onChange={(e) => setForm((f) => ({ ...f, taskType: e.target.value }))} placeholder="All" />
          </div>
          <div className="sakar-field" style={{ marginBottom: 0 }}>
            <label htmlFor="hotel-from">Duration</label>
            <div style={{ display: 'flex', gap: 6, alignItems: 'center' }}>
              <input id="hotel-from" type="date" value={form.from} onChange={(e) => setForm((f) => ({ ...f, from: e.target.value }))} />
              <span>-</span>
              <input aria-label="To" type="date" value={form.to} onChange={(e) => setForm((f) => ({ ...f, to: e.target.value }))} />
            </div>
          </div>
          <button type="button" className="sakar-btn sakar-btn--secondary" onClick={() => { setForm(defaultFilters()); setApplied(defaultFilters()); }}>Reset</button>
          <button type="button" className="sakar-btn sakar-btn--primary" onClick={() => setApplied(form)}>Search</button>
        </div>
      </Card>

      <div className="sakar-stat-grid" style={{ marginBottom: 20 }}>
        <MetricCard label="Total volume of task" value={data.totalVolumeOfTask} icon={<Icon.listCheck />} tone="info" />
        <MetricCard label="Cumulative Mileage" value="Not tracked" icon={<Icon.mapPin />} tone="neutral" trend={NOT_TRACKED_REASON} />
        <MetricCard label="Cumulative duration" value={formatDuration(data.cumulativeDurationSeconds)} icon={<Icon.timeline />} tone="success" />
        <MetricCard label="Number of rooms" value="Not tracked" icon={<Icon.building />} tone="neutral" trend={NOT_TRACKED_REASON} />
      </div>

      <Card title={`Hotel data statistics chart (${data.dailyBreakdown.length})`}>
        <BarList items={data.dailyBreakdown.map((d) => ({ key: d.date, label: d.date, value: d.count }))} />
      </Card>
    </div>
  );
}
