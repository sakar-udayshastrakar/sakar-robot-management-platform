import { useState } from 'react';
import { getRobotBattery, getRobotStatus } from '../../api/robots';
import { ApiRequestError } from '../../api/client';
import { Card } from '../../components/ui/Card';
import { Badge } from '../../components/ui/Badge';
import { StatusBadge } from '../../components/ui/StatusBadge';
import type { RobotConnectionStatus } from '../../types/domain';

type StatusProbe =
  | { kind: 'idle' }
  | { kind: 'loading' }
  | { kind: 'success'; mainState: string; observedAt: string }
  | { kind: 'error'; message: string };

type BatteryProbe =
  | { kind: 'idle' }
  | { kind: 'loading' }
  | { kind: 'success'; percentage: number; charging: boolean }
  | { kind: 'error'; message: string };

// `connectionStatus`/`lastSeenAt` are the backend's authoritative connectivity
// verdict, passed down from the robot resource rather than probed here. The
// live probe below still drives Current state and Battery — genuine vendor
// readings — but it must never drive the connection badge: its `online` flag
// only means the vendor API answered, which is what let this panel show
// "Connected" for a robot with an open no-heartbeat alert.
export function RobotStatusPanel({
  robotId,
  connectionStatus,
  lastSeenAt,
}: {
  robotId: string;
  connectionStatus: RobotConnectionStatus;
  lastSeenAt: string | null;
}) {
  const [status, setStatus] = useState<StatusProbe>({ kind: 'idle' });
  const [battery, setBattery] = useState<BatteryProbe>({ kind: 'idle' });

  // Two independent adapter calls (GET_STATUS, GET_BATTERY) — fired together
  // but never allowed to block on each other: one failing (e.g.
  // UNSUPPORTED_CAPABILITY for a model that only supports one of the two)
  // must not hide a successful result from the other.
  async function checkStatus() {
    setStatus({ kind: 'loading' });
    setBattery({ kind: 'loading' });
    const [statusResult, batteryResult] = await Promise.allSettled([getRobotStatus(robotId), getRobotBattery(robotId)]);

    if (statusResult.status === 'fulfilled') {
      const snapshot = statusResult.value;
      setStatus({ kind: 'success', mainState: snapshot.mainState, observedAt: snapshot.observedAt });
    } else {
      setStatus({ kind: 'error', message: statusResult.reason instanceof ApiRequestError ? statusResult.reason.message : 'Status check failed' });
    }

    if (batteryResult.status === 'fulfilled') {
      setBattery({ kind: 'success', percentage: batteryResult.value.percentage, charging: batteryResult.value.charging });
    } else {
      setBattery({ kind: 'error', message: batteryResult.reason instanceof ApiRequestError ? batteryResult.reason.message : 'Battery check failed' });
    }
  }

  const loading = status.kind === 'loading' || battery.kind === 'loading';
  const errorMessage = status.kind === 'error' ? `Status unavailable: ${status.message}`
    : battery.kind === 'error' ? `Battery unavailable: ${battery.message}`
    : null;

  // A single compact "top summary" card — connection / current state /
  // battery / charging / last update rendered as a horizontal status
  // strip (Keenon-inspired device-console header), rather than two
  // separate full-width cards. Both fields still come from the one
  // Promise.allSettled probe above: a single "Check Live Status" action,
  // two independently-failable results.
  return (
    <Card
      title="Live Status"
      actions={
        <button type="button" className="sakar-btn sakar-btn--secondary" onClick={checkStatus} disabled={loading}>
          {loading ? 'Checking…' : 'Check Live Status'}
        </button>
      }
    >
      {status.kind === 'idle' && (
        <p className="sakar-page-subtitle" style={{ marginBottom: 12 }}>
          Calls the real <code>GET /robots/{'{id}'}/status</code> and <code>GET /robots/{'{id}'}/battery</code>{' '}
          endpoints through a live robot adapter — this checks whether <strong>Keenon</strong> answers, a separate
          concept from Sakar <strong>Connection</strong> below (heartbeat/telemetry freshness). Without a connected
          robot these commonly fail — a failure here means "unavailable", never "offline" or "0%", and a success
          here never changes Connection.
        </p>
      )}
      {errorMessage && <p className="sakar-page-subtitle" style={{ marginBottom: 12 }}>{errorMessage}</p>}

      <div className="sakar-status-strip">
        {/* Sakar connectivity — backend-authoritative, from the robot resource, never
            re-derived here. */}
        <div className="sakar-status-strip-item">
          <span className="sakar-status-strip-label">Connection</span>
          <StatusBadge status={connectionStatus} />
        </div>
        <div className="sakar-status-strip-item">
          <span className="sakar-status-strip-label">Last heartbeat</span>
          <span>{lastSeenAt ? new Date(lastSeenAt).toLocaleString() : 'Never received'}</span>
        </div>
        {/* Keenon vendor reachability — a DIFFERENT axis from Connection above, kept in
            its own cell with its own vocabulary (Reachable/Unreachable, not
            Online/Offline) precisely so a successful vendor probe can never read as
            "the robot is online". Plain Badge, not StatusBadge — StatusBadge's dot+label
            pairing is reserved for robot connectivity only. */}
        <div className="sakar-status-strip-item">
          <span className="sakar-status-strip-label">Keenon</span>
          <Badge tone={status.kind === 'success' ? 'info' : status.kind === 'error' ? 'warning' : 'neutral'}>
            {status.kind === 'success' ? 'Reachable'
              : status.kind === 'error' ? 'Unreachable'
              : status.kind === 'loading' ? 'Checking…'
              : 'Not checked'}
          </Badge>
        </div>
        <div className="sakar-status-strip-item">
          <span className="sakar-status-strip-label">Current state</span>
          <Badge tone="neutral">{status.kind === 'success' ? status.mainState : 'Not available'}</Badge>
        </div>
        <div className="sakar-status-strip-item">
          <span className="sakar-status-strip-label">Battery</span>
          {battery.kind === 'success' ? (
            <Badge tone={battery.percentage < 20 ? 'danger' : battery.percentage < 40 ? 'warning' : 'success'}>{battery.percentage}%</Badge>
          ) : (
            <Badge tone="neutral">Not available</Badge>
          )}
        </div>
        <div className="sakar-status-strip-item">
          <span className="sakar-status-strip-label">Charging</span>
          {battery.kind === 'success' ? (
            <Badge tone={battery.charging ? 'info' : 'neutral'}>{battery.charging ? 'Charging' : 'Not charging'}</Badge>
          ) : (
            <Badge tone="neutral">Not available</Badge>
          )}
        </div>
      </div>
    </Card>
  );
}
