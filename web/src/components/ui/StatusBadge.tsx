// The canonical robot-status vocabulary used consistently across the
// dashboard, robot list, robot detail, and Sites: ONLINE -> success
// (positive), OFFLINE -> danger (destructive — a robot with no recent
// heartbeat/telemetry is a real fault condition, not a neutral one),
// UNKNOWN -> neutral (no heartbeat ever received, nothing to alarm on yet),
// CHARGING -> info, WARNING -> warning, ERROR/CRITICAL -> danger.
//
// ONLINE/OFFLINE/UNKNOWN here map 1:1 onto the backend's authoritative
// `RobotConnectionStatus` (see `Robot.connectionStatus` in types/domain.ts) —
// this is the ONLY component that should ever render a robot connectivity
// badge. Never introduce a second one (e.g. a raw "Connected"/"Disconnected"
// indicator driven by a live vendor probe): a vendor call succeeding is not
// evidence of Sakar connectivity, and text/color must not imply otherwise.
export type RobotStatusValue = 'ONLINE' | 'OFFLINE' | 'CHARGING' | 'WARNING' | 'ERROR' | 'CRITICAL' | 'UNKNOWN';

const CONFIG: Record<RobotStatusValue, { label: string; dot: string; badge: string }> = {
  ONLINE: { label: 'Online', dot: 'sakar-status-dot--online', badge: 'sakar-badge--success' },
  OFFLINE: { label: 'Offline', dot: 'sakar-status-dot--offline', badge: 'sakar-badge--danger' },
  CHARGING: { label: 'Charging', dot: 'sakar-status-dot--charging', badge: 'sakar-badge--info' },
  WARNING: { label: 'Warning', dot: 'sakar-status-dot--warning', badge: 'sakar-badge--warning' },
  ERROR: { label: 'Error', dot: 'sakar-status-dot--error', badge: 'sakar-badge--danger' },
  CRITICAL: { label: 'Critical', dot: 'sakar-status-dot--error', badge: 'sakar-badge--danger' },
  UNKNOWN: { label: 'Unknown', dot: 'sakar-status-dot--unknown', badge: 'sakar-badge--neutral' },
};

// Status/color is never the only signal — cfg.label (or an explicit
// override) always renders as visible text alongside the dot.
export function StatusBadge({ status, label }: { status: RobotStatusValue; label?: string }) {
  const cfg = CONFIG[status];
  return (
    <span className={`sakar-badge ${cfg.badge}`}>
      <span className={`sakar-status-dot ${cfg.dot}`} aria-hidden="true" />
      {label ?? cfg.label}
    </span>
  );
}
