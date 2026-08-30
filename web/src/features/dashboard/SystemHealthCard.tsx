import { useEffect, useState } from 'react';
import { Card } from '../../components/ui/Card';
import { Badge } from '../../components/ui/Badge';
import { getBackendHealth, type HealthResponse } from '../../api/health';
import { isRealtimeEnabled } from '../../websocket/stompClient';

type CheckState = 'checking' | 'up' | 'down' | 'unexposed';

function HealthRow({ name, state, detail }: { name: string; state: CheckState; detail?: string }) {
  const tone = state === 'up' ? 'success' : state === 'down' ? 'danger' : state === 'checking' ? 'neutral' : 'neutral';
  const label = state === 'up' ? 'Up' : state === 'down' ? 'Down' : state === 'checking' ? 'Checking…' : 'Not exposed';
  return (
    <div className="sakar-health-row">
      <span className="sakar-health-name">{name}</span>
      <span>
        <Badge tone={tone}>{label}</Badge>
        {detail && <span className="sakar-page-subtitle" style={{ marginLeft: 8 }}>{detail}</span>}
      </span>
    </div>
  );
}

// Real backend check via GET /actuator/health. Database/MQTT rows only
// show a real status when the backend's health response actually includes
// that component (Spring Boot only auto-populates ones with a bean
// present, and there is no custom MQTT HealthIndicator in the codebase —
// see api/health.ts) — otherwise "Not exposed", never a fabricated
// green check.
export function SystemHealthCard() {
  const [health, setHealth] = useState<HealthResponse | null>(null);
  const [state, setState] = useState<CheckState>('checking');

  useEffect(() => {
    let cancelled = false;
    getBackendHealth()
      .then((res) => {
        if (cancelled) return;
        setHealth(res);
        setState(res.status === 'UP' ? 'up' : 'down');
      })
      .catch(() => !cancelled && setState('down'));
    return () => {
      cancelled = true;
    };
  }, []);

  const dbState: CheckState = health?.components?.db
    ? health.components.db.status === 'UP' ? 'up' : 'down'
    : state === 'checking' ? 'checking' : 'unexposed';
  const redisState: CheckState = health?.components?.redis
    ? health.components.redis.status === 'UP' ? 'up' : 'down'
    : state === 'checking' ? 'checking' : 'unexposed';

  return (
    <Card title="System Health">
      <HealthRow name="Backend API" state={state} />
      <HealthRow name="Database" state={dbState} detail={dbState === 'unexposed' ? '(health endpoint does not report a db component)' : undefined} />
      <HealthRow name="MQTT" state="unexposed" detail="(no MQTT health indicator exists on the backend)" />
      <HealthRow
        name="WebSocket"
        state={isRealtimeEnabled() ? 'unexposed' : 'unexposed'}
        detail={isRealtimeEnabled() ? '(feature enabled — live SUBSCRIBE behavior still unvalidated)' : '(feature flag OFF by default)'}
      />
      {redisState !== 'unexposed' && <HealthRow name="Redis (rate limiting)" state={redisState} />}
    </Card>
  );
}
