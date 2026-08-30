import { useState } from 'react';
import { getRobotStatus } from '../../api/robots';
import { ApiRequestError } from '../../api/client';
import { Card } from '../../components/ui/Card';
import { RobotStatusCard } from '../../components/ui/RobotStatusCard';

type ProbeState =
  | { kind: 'idle' }
  | { kind: 'loading' }
  | { kind: 'success'; mainState: string; online: boolean; observedAt: string }
  | { kind: 'error'; message: string };

export function RobotStatusPanel({ robotId }: { robotId: string }) {
  const [probe, setProbe] = useState<ProbeState>({ kind: 'idle' });

  async function checkStatus() {
    setProbe({ kind: 'loading' });
    try {
      const snapshot = await getRobotStatus(robotId);
      setProbe({ kind: 'success', mainState: snapshot.mainState, online: snapshot.online, observedAt: snapshot.observedAt });
    } catch (err) {
      setProbe({ kind: 'error', message: err instanceof ApiRequestError ? err.message : 'Status check failed' });
    }
  }

  return (
    <Card
      title="Live Status"
      actions={
        <button type="button" className="sakar-btn sakar-btn--secondary" onClick={checkStatus} disabled={probe.kind === 'loading'}>
          {probe.kind === 'loading' ? 'Checking…' : 'Check Live Status'}
        </button>
      }
    >
      {probe.kind === 'idle' && (
        <p className="sakar-page-subtitle">
          Calls the real <code>GET /robots/{'{id}'}/status</code> endpoint through a live robot adapter. Without a
          connected robot it commonly fails — a failure here means "unavailable", never "offline".
        </p>
      )}
      {probe.kind === 'error' && <p className="sakar-page-subtitle">Status unavailable: {probe.message}</p>}
      <RobotStatusCard
        online={probe.kind === 'success' ? probe.online : null}
        batteryPercent={null}
        charging={null}
        currentState={probe.kind === 'success' ? probe.mainState : null}
        lastHeartbeat={probe.kind === 'success' ? probe.observedAt : null}
        agentVersion={null}
        cloudConnected={null}
      />
    </Card>
  );
}
