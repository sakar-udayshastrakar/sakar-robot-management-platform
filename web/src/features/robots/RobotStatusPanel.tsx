import { useState } from 'react';
import { getRobotStatus } from '../../api/robots';
import { ApiRequestError } from '../../api/client';
import { Card } from '../../components/ui/Card';
import { Badge } from '../../components/ui/Badge';

type ProbeState =
  | { kind: 'idle' }
  | { kind: 'loading' }
  | { kind: 'success'; mainState: string; subState: string | null; online: boolean; observedAt: string }
  | { kind: 'error'; message: string };

export function RobotStatusPanel({ robotId }: { robotId: string }) {
  const [probe, setProbe] = useState<ProbeState>({ kind: 'idle' });

  async function checkStatus() {
    setProbe({ kind: 'loading' });
    try {
      const snapshot = await getRobotStatus(robotId);
      setProbe({
        kind: 'success',
        mainState: snapshot.mainState,
        subState: snapshot.subState,
        online: snapshot.online,
        observedAt: snapshot.observedAt,
      });
    } catch (err) {
      setProbe({
        kind: 'error',
        message: err instanceof ApiRequestError ? err.message : 'Status check failed',
      });
    }
  }

  return (
    <Card
      title="Live status"
      actions={
        <button type="button" className="sakar-btn sakar-btn--secondary" onClick={checkStatus} disabled={probe.kind === 'loading'}>
          {probe.kind === 'loading' ? 'Checking…' : 'Check live status'}
        </button>
      }
    >
      {probe.kind === 'idle' && (
        <p className="sakar-page-subtitle">
          Not checked yet — this calls the real <code>GET /robots/{'{id}'}/status</code> endpoint, which reads
          through a live robot adapter. Without a connected robot it commonly fails; a failure here means
          "unavailable", never "offline".
        </p>
      )}
      {probe.kind === 'error' && (
        <div>
          <Badge tone="neutral">Status unavailable</Badge>
          <p className="sakar-page-subtitle" style={{ marginTop: 8 }}>{probe.message}</p>
        </div>
      )}
      {probe.kind === 'success' && (
        <dl style={{ display: 'grid', gridTemplateColumns: '160px 1fr', rowGap: 10 }}>
          <dt className="sakar-page-subtitle">Online</dt>
          <dd style={{ margin: 0 }}>
            <Badge tone={probe.online ? 'success' : 'danger'}>{probe.online ? 'Online' : 'Offline'}</Badge>
          </dd>
          <dt className="sakar-page-subtitle">Main state</dt>
          <dd style={{ margin: 0 }}>{probe.mainState}</dd>
          <dt className="sakar-page-subtitle">Sub state</dt>
          <dd style={{ margin: 0 }}>{probe.subState ?? '—'}</dd>
          <dt className="sakar-page-subtitle">Observed at</dt>
          <dd style={{ margin: 0 }}>{new Date(probe.observedAt).toLocaleString()}</dd>
        </dl>
      )}
    </Card>
  );
}
