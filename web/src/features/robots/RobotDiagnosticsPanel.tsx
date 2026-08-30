import { useState } from 'react';
import { getRobotStatus } from '../../api/robots';
import { ApiRequestError } from '../../api/client';
import { Card } from '../../components/ui/Card';
import { Badge } from '../../components/ui/Badge';
import type { Robot } from '../../types/domain';

// Real diagnostic detail only: the robot's own registered capabilities
// (RobotResponse.capabilities, real) plus the raw adapter payload from a
// live status probe when one succeeds (RobotStatusSnapshot.raw — its own
// doc comment calls this "for audit/debugging", which is exactly this
// tab's purpose). No simulated fields are shown here.
export function RobotDiagnosticsPanel({ robot }: { robot: Robot }) {
  const [raw, setRaw] = useState<unknown>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function runProbe() {
    setBusy(true);
    setError(null);
    try {
      const snapshot = await getRobotStatus(robot.id);
      setRaw(snapshot.raw);
    } catch (err) {
      setError(err instanceof ApiRequestError ? err.message : 'Diagnostics probe failed');
    } finally {
      setBusy(false);
    }
  }

  return (
    <div style={{ display: 'grid', gap: 16 }}>
      <Card title="Registered Capabilities">
        <p className="sakar-page-subtitle" style={{ marginBottom: 10 }}>
          From the robot model's real capability flags (RobotCapabilityType) — gates which operations the backend
          will attempt against this robot.
        </p>
        <div style={{ display: 'flex', flexWrap: 'wrap', gap: 6 }}>
          {robot.capabilities.length === 0 ? (
            <span className="sakar-page-subtitle">No capabilities reported for this robot model.</span>
          ) : (
            robot.capabilities.map((c) => <Badge key={c} tone="primary">{c}</Badge>)
          )}
        </div>
      </Card>

      <Card
        title="Adapter Diagnostics"
        actions={
          <button type="button" className="sakar-btn sakar-btn--secondary" onClick={runProbe} disabled={busy}>
            {busy ? 'Running…' : 'Run Diagnostics Probe'}
          </button>
        }
      >
        <p className="sakar-page-subtitle" style={{ marginBottom: 10 }}>
          Calls the real <code>GET /robots/{'{id}'}/status</code> endpoint and shows the raw adapter payload
          returned — untranslated vendor detail, useful for debugging a failed status read.
        </p>
        {error && <p className="sakar-page-subtitle">Probe failed: {error}</p>}
        {raw !== null ? (
          <pre className="sakar-mono" style={{ background: 'var(--sakar-surface-sunken)', padding: 12, borderRadius: 'var(--sakar-radius-sm)', fontSize: 12, overflowX: 'auto' }}>
            {JSON.stringify(raw, null, 2)}
          </pre>
        ) : (
          <span className="sakar-page-subtitle">No probe run yet.</span>
        )}
      </Card>
    </div>
  );
}
