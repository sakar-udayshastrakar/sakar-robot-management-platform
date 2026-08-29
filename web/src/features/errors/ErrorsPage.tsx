import { useMemo, useState } from 'react';
import { useRobotOptions } from '../shared/useRobotOptions';
import { RobotPicker } from '../shared/RobotPicker';
import { generateErrors } from '../../mocks/simulated';
import type { ErrorStatus } from '../../types/domain';
import { Card } from '../../components/ui/Card';
import { DataTable } from '../../components/ui/DataTable';
import { Badge } from '../../components/ui/Badge';
import { SimulatedDataBanner } from '../../components/ui/SimulatedDataBanner';
import { EmptyState } from '../../components/ui/States';

const STATUSES: (ErrorStatus | 'ALL')[] = ['ALL', 'OPEN', 'ACKNOWLEDGED', 'RESOLVED'];
const STATUS_TONE: Record<ErrorStatus, 'danger' | 'warning' | 'success'> = {
  OPEN: 'danger',
  ACKNOWLEDGED: 'warning',
  RESOLVED: 'success',
};

export function ErrorsPage() {
  const { robots } = useRobotOptions();
  const [robotId, setRobotId] = useState('');
  const [statusFilter, setStatusFilter] = useState<ErrorStatus | 'ALL'>('ALL');

  const errors = useMemo(() => (robotId ? generateErrors(robotId, 20) : []), [robotId]);
  const filtered = errors.filter((e) => statusFilter === 'ALL' || e.status === statusFilter);

  return (
    <div>
      <div className="sakar-page-header">
        <div>
          <h1 className="sakar-page-title">Errors</h1>
          <p className="sakar-page-subtitle">Robot error management.</p>
        </div>
      </div>
      <SimulatedDataBanner label="No GET /robots/{id}/errors endpoint exists yet — robot_errors rows are persisted but not exposed over REST" />
      <RobotPicker robots={robots} value={robotId} onChange={setRobotId} />
      {robotId && (
        <div className="sakar-filter-bar">
          <select value={statusFilter} onChange={(e) => setStatusFilter(e.target.value as typeof statusFilter)} aria-label="Status">
            {STATUSES.map((s) => (
              <option key={s} value={s}>{s === 'ALL' ? 'All statuses' : s}</option>
            ))}
          </select>
        </div>
      )}
      <Card title="Errors">
        {!robotId ? (
          <EmptyState title="Select a robot" detail="Choose a robot above to preview its error log." />
        ) : (
          <DataTable
            rows={filtered}
            rowKey={(e) => e.id}
            columns={[
              { key: 'code', header: 'Code', render: (e) => e.errorCode },
              { key: 'severity', header: 'Severity', render: (e) => e.severity },
              { key: 'message', header: 'Message', render: (e) => e.message },
              { key: 'time', header: 'Recorded', render: (e) => new Date(e.recordedAt).toLocaleString() },
              { key: 'status', header: 'Status', render: (e) => <Badge tone={STATUS_TONE[e.status]}>{e.status}</Badge> },
              { key: 'resolved', header: 'Resolved by', render: (e) => e.resolvedBy ?? '—' },
            ]}
          />
        )}
      </Card>
    </div>
  );
}
