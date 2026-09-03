import { useState } from 'react';
import { getRobotBattery, getRobotStatus } from '../../api/robots';
import { ApiRequestError } from '../../api/client';
import { Card } from '../../components/ui/Card';
import { Badge } from '../../components/ui/Badge';
import { ConnectionIndicator } from '../../components/ui/StatusBadge';

type StatusProbe =
  | { kind: 'idle' }
  | { kind: 'loading' }
  | { kind: 'success'; mainState: string; online: boolean; observedAt: string }
  | { kind: 'error'; message: string };

type BatteryProbe =
  | { kind: 'idle' }
  | { kind: 'loading' }
  | { kind: 'success'; percentage: number; charging: boolean }
  | { kind: 'error'; message: string };

export function RobotStatusPanel({ robotId }: { robotId: string }) {
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
      setStatus({ kind: 'success', mainState: snapshot.mainState, online: snapshot.online, observedAt: snapshot.observedAt });
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
          endpoints through a live robot adapter. Without a connected robot these commonly fail — a failure here
          means "unavailable", never "offline" or "0%".
        </p>
      )}
      {errorMessage && <p className="sakar-page-subtitle" style={{ marginBottom: 12 }}>{errorMessage}</p>}

      <div className="sakar-status-strip">
        <div className="sakar-status-strip-item">
          <span className="sakar-status-strip-label">Connection</span>
          <ConnectionIndicator connected={status.kind === 'success' ? status.online : null} />
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
        <div className="sakar-status-strip-item">
          <span className="sakar-status-strip-label">Last update</span>
          <span>{status.kind === 'success' ? new Date(status.observedAt).toLocaleString() : 'Not available'}</span>
        </div>
      </div>
    </Card>
  );
}
