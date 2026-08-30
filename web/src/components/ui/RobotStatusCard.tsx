import type { ReactNode } from 'react';
import { Badge } from './Badge';
import { ConnectionIndicator } from './StatusBadge';

interface RobotStatusCardProps {
  online: boolean | null;
  batteryPercent: number | null;
  charging: boolean | null;
  currentState: string | null;
  lastHeartbeat: string | null;
  agentVersion: string | null;
  cloudConnected: boolean | null;
}

function Row({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="sakar-health-row">
      <span className="sakar-page-subtitle" style={{ margin: 0 }}>{label}</span>
      <span>{children}</span>
    </div>
  );
}

// Compact status visualization — badges only, no decorative gauges/rings,
// per the "status badges rather than huge decorative graphics" direction.
export function RobotStatusCard({
  online,
  batteryPercent,
  charging,
  currentState,
  lastHeartbeat,
  agentVersion,
  cloudConnected,
}: RobotStatusCardProps) {
  return (
    <div>
      <Row label="Connection">
        <ConnectionIndicator connected={online} />
      </Row>
      <Row label="Battery">
        {batteryPercent === null ? (
          <Badge tone="neutral">Not available</Badge>
        ) : (
          <Badge tone={batteryPercent < 20 ? 'danger' : batteryPercent < 40 ? 'warning' : 'success'}>{batteryPercent}%</Badge>
        )}
      </Row>
      <Row label="Charging">
        {charging === null ? <Badge tone="neutral">Not available</Badge> : <Badge tone={charging ? 'info' : 'neutral'}>{charging ? 'Charging' : 'Not charging'}</Badge>}
      </Row>
      <Row label="Current state">
        <Badge tone="neutral">{currentState ?? 'Not available'}</Badge>
      </Row>
      <Row label="Last heartbeat">
        <span>{lastHeartbeat ? new Date(lastHeartbeat).toLocaleString() : 'Not available'}</span>
      </Row>
      <Row label="Agent version">
        <span>{agentVersion ?? 'Not available'}</span>
      </Row>
      <Row label="Cloud connection">
        <ConnectionIndicator connected={cloudConnected} />
      </Row>
    </div>
  );
}
