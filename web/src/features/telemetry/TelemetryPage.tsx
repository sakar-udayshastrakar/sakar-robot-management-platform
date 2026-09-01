import { useState } from 'react';
import { useRobotOptions } from '../shared/useRobotOptions';
import { RobotPicker } from '../shared/RobotPicker';
import { listRobotTelemetry } from '../../api/telemetry';
import { useApi } from '../../hooks/useApi';
import { PageHeader } from '../../components/ui/PageHeader';
import { Card } from '../../components/ui/Card';
import { DataTable } from '../../components/ui/DataTable';
import { Sparkline } from '../../components/ui/Sparkline';
import { EmptyState, LoadingState, ErrorState } from '../../components/ui/States';

const RANGES = [
  { label: 'Last 1 hour', minutes: 60 },
  { label: 'Last 6 hours', minutes: 360 },
  { label: 'Last 24 hours', minutes: 1440 },
  { label: 'All fetched', minutes: null },
] as const;

export function TelemetryPage() {
  const { robots } = useRobotOptions();
  const [robotId, setRobotId] = useState('');
  const [rangeIdx, setRangeIdx] = useState(3);
  const { data, status, error, refetch } = useApi(
    () => (robotId ? listRobotTelemetry(robotId, 0, 200) : Promise.resolve(null)),
    [robotId],
  );

  const allReadings = data?.content ?? [];
  const range = RANGES[rangeIdx];
  const cutoff = range.minutes === null ? 0 : Date.now() - range.minutes * 60_000;
  const readings = allReadings.filter((r) => new Date(r.recordedAt).getTime() >= cutoff);
  const batteryReadings = readings
    .filter((r) => r.metric === 'battery_percent' && r.valueNumeric !== null)
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
      <PageHeader title="Telemetry" subtitle="Per-robot telemetry history, ingested live from MQTT." />
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
          <EmptyState title="Select a robot" detail="Choose a robot above to view its telemetry history." />
        </Card>
      ) : status === 'loading' || status === 'idle' ? (
        <LoadingState title="Loading telemetry…" />
      ) : status === 'error' ? (
        <ErrorState
          title="Could not load telemetry"
          detail={error ?? 'Unknown error'}
          action={<button type="button" className="sakar-btn sakar-btn--secondary" onClick={refetch}>Retry</button>}
        />
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
              rowKey={(r) => String(r.id)}
              emptyTitle="No telemetry ingested yet for this robot in this time range"
              columns={[
                { key: 'time', header: 'Timestamp', render: (r) => new Date(r.recordedAt).toLocaleString() },
                { key: 'metric', header: 'Metric', render: (r) => r.metric },
                { key: 'value', header: 'Value', render: (r) => r.valueNumeric ?? r.valueText ?? '—' },
              ]}
            />
          </Card>
        </>
      )}
    </div>
  );
}
