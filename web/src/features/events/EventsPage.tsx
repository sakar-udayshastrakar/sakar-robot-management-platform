import { useMemo, useState } from 'react';
import { useRobotOptions } from '../shared/useRobotOptions';
import { RobotPicker } from '../shared/RobotPicker';
import { generateEvents } from '../../mocks/simulated';
import type { EventCategory, EventSeverity } from '../../types/domain';
import { Card } from '../../components/ui/Card';
import { DataTable } from '../../components/ui/DataTable';
import { Badge } from '../../components/ui/Badge';
import { SimulatedDataBanner } from '../../components/ui/SimulatedDataBanner';
import { EmptyState } from '../../components/ui/States';

const SEVERITIES: (EventSeverity | 'ALL')[] = ['ALL', 'INFO', 'WARNING', 'ERROR', 'CRITICAL'];
const CATEGORIES: (EventCategory | 'ALL')[] = ['ALL', 'SECURITY', 'NAVIGATION', 'BATTERY', 'CHARGING', 'SDK', 'SYSTEM'];

const SEVERITY_TONE: Record<EventSeverity, 'info' | 'warning' | 'danger'> = {
  INFO: 'info',
  WARNING: 'warning',
  ERROR: 'danger',
  CRITICAL: 'danger',
};

export function EventsPage() {
  const { robots } = useRobotOptions();
  const [robotId, setRobotId] = useState('');
  const [severity, setSeverity] = useState<EventSeverity | 'ALL'>('ALL');
  const [category, setCategory] = useState<EventCategory | 'ALL'>('ALL');

  const events = useMemo(() => (robotId ? generateEvents(robotId, 40) : []), [robotId]);
  const filtered = events.filter(
    (e) => (severity === 'ALL' || e.severity === severity) && (category === 'ALL' || e.category === category),
  );

  return (
    <div>
      <div className="sakar-page-header">
        <div>
          <h1 className="sakar-page-title">Events</h1>
          <p className="sakar-page-subtitle">Robot event stream, filterable by severity and category.</p>
        </div>
      </div>
      <SimulatedDataBanner label="No GET /robots/{id}/events endpoint exists yet — robot_events rows are persisted but not exposed over REST" />
      <RobotPicker robots={robots} value={robotId} onChange={setRobotId} />
      {robotId && (
        <div className="sakar-filter-bar">
          <select value={severity} onChange={(e) => setSeverity(e.target.value as typeof severity)} aria-label="Severity">
            {SEVERITIES.map((s) => (
              <option key={s} value={s}>{s === 'ALL' ? 'All severities' : s}</option>
            ))}
          </select>
          <select value={category} onChange={(e) => setCategory(e.target.value as typeof category)} aria-label="Category">
            {CATEGORIES.map((c) => (
              <option key={c} value={c}>{c === 'ALL' ? 'All categories' : c}</option>
            ))}
          </select>
        </div>
      )}
      <Card title="Events">
        {!robotId ? (
          <EmptyState title="Select a robot" detail="Choose a robot above to preview its event stream." />
        ) : (
          <DataTable
            rows={filtered}
            rowKey={(e) => e.id}
            columns={[
              { key: 'time', header: 'Timestamp', render: (e) => new Date(e.recordedAt).toLocaleString() },
              { key: 'severity', header: 'Severity', render: (e) => <Badge tone={SEVERITY_TONE[e.severity]}>{e.severity}</Badge> },
              { key: 'category', header: 'Category', render: (e) => e.category },
              { key: 'message', header: 'Message', render: (e) => e.message },
            ]}
          />
        )}
      </Card>
    </div>
  );
}
