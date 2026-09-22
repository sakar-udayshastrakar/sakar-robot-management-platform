import { StatusBadge } from './StatusBadge';
import type { RobotConnectionStatus } from '../../types/domain';

// Explanatory sentence shown as a tooltip alongside the badge's own visible
// label — this does NOT change StatusBadge's label/color contract (pinned by
// StatusBadge.test.tsx), it only adds context so UNKNOWN in particular reads
// as "no heartbeat has ever been received" rather than an ambiguous gray dot.
const DESCRIPTION: Record<RobotConnectionStatus, string> = {
  ONLINE: 'Robot is connected — heartbeat/telemetry received within the configured offline threshold.',
  OFFLINE: 'No recent heartbeat — the robot has not reported within the configured offline threshold.',
  UNKNOWN: 'No heartbeat has been received from this robot yet.',
};

// Thin wrapper around StatusBadge for robot connectivity specifically.
// `status` must always come straight from the backend's authoritative
// Robot.connectionStatus — never re-derived, never defaulted here.
export function ConnectionStatusBadge({ status }: { status: RobotConnectionStatus }) {
  return (
    <span title={DESCRIPTION[status]}>
      <StatusBadge status={status} />
    </span>
  );
}
