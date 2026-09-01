import { useState } from 'react';
import { getRobotBattery, getRobotStatus } from '../../api/robots';
import { ApiRequestError } from '../../api/client';
import { Card } from '../../components/ui/Card';
import { RobotStatusCard } from '../../components/ui/RobotStatusCard';

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
        <p className="sakar-page-subtitle">
          Calls the real <code>GET /robots/{'{id}'}/status</code> and <code>GET /robots/{'{id}'}/battery</code>{' '}
          endpoints through a live robot adapter. Without a connected robot these commonly fail — a failure here
          means "unavailable", never "offline" or "0%".
        </p>
      )}
      {status.kind === 'error' && <p className="sakar-page-subtitle">Status unavailable: {status.message}</p>}
      {battery.kind === 'error' && status.kind !== 'error' && (
        <p className="sakar-page-subtitle">Battery unavailable: {battery.message}</p>
      )}
      <RobotStatusCard
        online={status.kind === 'success' ? status.online : null}
        batteryPercent={battery.kind === 'success' ? battery.percentage : null}
        charging={battery.kind === 'success' ? battery.charging : null}
        currentState={status.kind === 'success' ? status.mainState : null}
        lastHeartbeat={status.kind === 'success' ? status.observedAt : null}
        agentVersion={null}
        cloudConnected={null}
      />
    </Card>
  );
}
