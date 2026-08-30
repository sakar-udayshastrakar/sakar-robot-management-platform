import { useMemo, useState } from 'react';
import { useRobotOptions } from '../shared/useRobotOptions';
import { RobotPicker } from '../shared/RobotPicker';
import { generateTelemetry } from '../../mocks/simulated';
import { PageHeader } from '../../components/ui/PageHeader';
import { Card } from '../../components/ui/Card';
import { DataTable } from '../../components/ui/DataTable';
import { Sparkline } from '../../components/ui/Sparkline';
import { SimulatedDataBanner } from '../../components/ui/SimulatedDataBanner';
import { EmptyState } from '../../components/ui/States';

const RANGES = [
  { label: 'Last 1 hour', minutes: 60 },
  { label: 'Last 6 hours', minutes: 360 },
  { label: 'Last 24 hours', minutes: 1440 },
] as const;

export function TelemetryPage() {
  const { robots } = useRobotOptions();
  const [robotId, setRobotId] = useState('');
  const [rangeIdx, setRangeIdx] = useState(1);
  const allReadings = useMemo(() => (robotId ? generateTelemetry(robotId, 60) : []), [robotId]);

  const cutoff = Date.now() - RANGES[rangeIdx].minutes * 60_000;
  const readings = allReadings.filter((r) => new Date(r.recordedAt).getTime() >= cutoff);
  const batteryReadings = readings
    .filter((r) => r.metricType === 'battery_percent' && r.valueNumeric !== null)
    .map((r) => r.valueNumeric as number)
    .reverse();

  const stats = batteryReadings.length
    ? {
        current: batteryReadings[batteryReadings.length - 1],
        min: Math.min(...batteryReadings),
        max: Math.max(...batteryReadings),
        avg: Math.round(batteryReadings.reduce((a, b) => a + b, 0) / batteryReadings.length),
      }
    : null;

  return (
    <div>
      <PageHeader title="Telemetry" subtitle="Per-robot telemetry readings." />
      <SimulatedDataBanner label="No GET /robots/{id}/telemetry endpoint exists yet — telemetry is persisted (robot_telemetry, populated live by the Phase 3 MQTT pipeline) but not exposed over REST" />
      <div className="sakar-filter-bar">
        <RobotPicker robots={robots} value={robotId} onChange={setRobotId} />
        {robotId && (
          <select value={rangeIdx} onChange={(e) => setRangeIdx(Number(e.target.value))} aria-label="Time range">
            {RANGES.map((r, i) => <option key={r.label} value={i}>{r.label}</option>)}
          </select>
        )}
      </div>

      {!robotId ? (
        <Card title="Readings">
          <EmptyState title="Select a robot" detail="Choose a robot above to preview its telemetry table." />
        </Card>
      ) : (
        <>
          {stats && (
            <Card title="Battery % — trend">
              <div style={{ display: 'flex', gap: 24, alignItems: 'center', flexWrap: 'wrap' }}>
                <Sparkline values={batteryReadings} />
                <div style={{ display: 'flex', gap: 20 }}>
                  <div><div className="sakar-stat-label">Current</div><div className="sakar-stat-value" style={{ fontSize: 20 }}>{stats.current}%</div></div>
                  <div><div className="sakar-stat-label">Min</div><div className="sakar-stat-value" style={{ fontSize: 20 }}>{stats.min}%</div></div>
                  <div><div className="sakar-stat-label">Max</div><div className="sakar-stat-value" style={{ fontSize: 20 }}>{stats.max}%</div></div>
                  <div><div className="sakar-stat-label">Average</div><div className="sakar-stat-value" style={{ fontSize: 20 }}>{stats.avg}%</div></div>
                </div>
              </div>
            </Card>
          )}
          <Card title="Historical Readings">
            <DataTable
              rows={readings}
              rowKey={(r) => r.id}
              emptyTitle="No readings in this time range"
              columns={[
                { key: 'time', header: 'Timestamp', render: (r) => new Date(r.recordedAt).toLocaleString() },
                { key: 'metric', header: 'Metric', render: (r) => r.metricType },
                { key: 'value', header: 'Value', render: (r) => r.valueNumeric ?? r.valueText ?? '—' },
                { key: 'source', header: 'Source', render: (r) => r.source },
              ]}
            />
          </Card>
        </>
      )}
    </div>
  );
}
