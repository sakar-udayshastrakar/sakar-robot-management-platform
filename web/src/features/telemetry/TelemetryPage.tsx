import { useMemo, useState } from 'react';
import { useRobotOptions } from '../shared/useRobotOptions';
import { RobotPicker } from '../shared/RobotPicker';
import { generateTelemetry } from '../../mocks/simulated';
import { Card } from '../../components/ui/Card';
import { DataTable } from '../../components/ui/DataTable';
import { SimulatedDataBanner } from '../../components/ui/SimulatedDataBanner';
import { EmptyState } from '../../components/ui/States';

export function TelemetryPage() {
  const { robots } = useRobotOptions();
  const [robotId, setRobotId] = useState('');
  const readings = useMemo(() => (robotId ? generateTelemetry(robotId, 30) : []), [robotId]);

  return (
    <div>
      <div className="sakar-page-header">
        <div>
          <h1 className="sakar-page-title">Telemetry</h1>
          <p className="sakar-page-subtitle">Per-robot telemetry readings.</p>
        </div>
      </div>
      <SimulatedDataBanner label="No GET /robots/{id}/telemetry endpoint exists yet — telemetry is persisted (robot_telemetry table, populated live by the Phase 3 MQTT pipeline) but not exposed over REST" />
      <RobotPicker robots={robots} value={robotId} onChange={setRobotId} />
      <Card title="Readings">
        {!robotId ? (
          <EmptyState title="Select a robot" detail="Choose a robot above to preview its telemetry table." />
        ) : (
          <DataTable
            rows={readings}
            rowKey={(r) => r.id}
            columns={[
              { key: 'time', header: 'Timestamp', render: (r) => new Date(r.recordedAt).toLocaleString() },
              { key: 'metric', header: 'Metric', render: (r) => r.metricType },
              { key: 'value', header: 'Value', render: (r) => r.valueNumeric ?? r.valueText ?? '—' },
              { key: 'source', header: 'Source', render: (r) => r.source },
            ]}
          />
        )}
      </Card>
    </div>
  );
}
