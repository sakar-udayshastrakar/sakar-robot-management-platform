import { useMemo, useState } from 'react';
import { useRobotOptions } from '../shared/useRobotOptions';
import { RobotPicker } from '../shared/RobotPicker';
import { generateAlerts } from '../../mocks/simulated';
import { Card } from '../../components/ui/Card';
import { DataTable } from '../../components/ui/DataTable';
import { Badge } from '../../components/ui/Badge';
import { SimulatedDataBanner } from '../../components/ui/SimulatedDataBanner';
import { EmptyState } from '../../components/ui/States';

export function AlertsPage() {
  const { robots } = useRobotOptions();
  const [robotId, setRobotId] = useState('');
  const alerts = useMemo(() => (robotId ? generateAlerts(robotId, 15) : []), [robotId]);

  return (
    <div>
      <div className="sakar-page-header">
        <div>
          <h1 className="sakar-page-title">Alerts</h1>
          <p className="sakar-page-subtitle">Fleet alerting — read-only preview.</p>
        </div>
      </div>
      <SimulatedDataBanner label="RobotAlert is a persisted entity with no REST controller — acknowledge/resolve actions are not wired to anything real and are intentionally omitted here" />
      <RobotPicker robots={robots} value={robotId} onChange={setRobotId} />
      <Card title="Alerts">
        {!robotId ? (
          <EmptyState title="Select a robot" detail="Choose a robot above to preview its alerts." />
        ) : (
          <DataTable
            rows={alerts}
            rowKey={(a) => a.id}
            columns={[
              { key: 'time', header: 'Created', render: (a) => new Date(a.createdAt).toLocaleString() },
              { key: 'severity', header: 'Severity', render: (a) => a.severity },
              { key: 'message', header: 'Message', render: (a) => a.message },
              { key: 'status', header: 'Status', render: (a) => (
                  <Badge tone={a.status === 'ACTIVE' ? 'danger' : a.status === 'ACKNOWLEDGED' ? 'warning' : 'success'}>
                    {a.status}
                  </Badge>
                ) },
            ]}
          />
        )}
      </Card>
    </div>
  );
}
