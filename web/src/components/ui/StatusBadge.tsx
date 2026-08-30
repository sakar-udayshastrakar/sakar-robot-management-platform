import { Icon } from './Icon';

// The canonical robot-status vocabulary used consistently across the
// dashboard, robot list, and robot detail: ONLINE -> success, OFFLINE ->
// neutral, CHARGING -> info, WARNING -> warning, ERROR/CRITICAL -> danger.
export type RobotStatusValue = 'ONLINE' | 'OFFLINE' | 'CHARGING' | 'WARNING' | 'ERROR' | 'CRITICAL' | 'UNKNOWN';

const CONFIG: Record<RobotStatusValue, { label: string; dot: string; badge: string }> = {
  ONLINE: { label: 'Online', dot: 'sakar-status-dot--online', badge: 'sakar-badge--success' },
  OFFLINE: { label: 'Offline', dot: 'sakar-status-dot--offline', badge: 'sakar-badge--neutral' },
  CHARGING: { label: 'Charging', dot: 'sakar-status-dot--charging', badge: 'sakar-badge--info' },
  WARNING: { label: 'Warning', dot: 'sakar-status-dot--warning', badge: 'sakar-badge--warning' },
  ERROR: { label: 'Error', dot: 'sakar-status-dot--error', badge: 'sakar-badge--danger' },
  CRITICAL: { label: 'Critical', dot: 'sakar-status-dot--error', badge: 'sakar-badge--danger' },
  UNKNOWN: { label: 'Unknown', dot: 'sakar-status-dot--unknown', badge: 'sakar-badge--neutral' },
};

export function StatusBadge({ status, label }: { status: RobotStatusValue; label?: string }) {
  const cfg = CONFIG[status];
  return (
    <span className={`sakar-badge ${cfg.badge}`}>
      <span className={`sakar-status-dot ${cfg.dot}`} aria-hidden="true" />
      {label ?? cfg.label}
    </span>
  );
}

export function ConnectionIndicator({ connected }: { connected: boolean | null }) {
  if (connected === null) {
    return <StatusBadge status="UNKNOWN" label="Unknown" />;
  }
  return (
    <span className={`sakar-badge ${connected ? 'sakar-badge--success' : 'sakar-badge--neutral'}`}>
      <Icon.wifi width={13} height={13} />
      {connected ? 'Connected' : 'Disconnected'}
    </span>
  );
}
