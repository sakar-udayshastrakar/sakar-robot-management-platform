import { useState } from 'react';
import { useParams } from 'react-router-dom';
import { activateRobot, deactivateRobot, getRobot } from '../../api/robots';
import { useApi } from '../../hooks/useApi';
import { usePermissions } from '../../hooks/usePermissions';
import { Card } from '../../components/ui/Card';
import { Badge } from '../../components/ui/Badge';
import { LoadingState, ErrorState } from '../../components/ui/States';
import { RobotStatusPanel } from './RobotStatusPanel';
import { MqttCredentialsPanel } from './MqttCredentialsPanel';
import { LockUnlockPanel } from './LockUnlockPanel';
import { RobotTimeline } from '../timeline/RobotTimeline';
import { generateTelemetry, generateEvents, generateErrors } from '../../mocks/simulated';
import { SimulatedDataBanner } from '../../components/ui/SimulatedDataBanner';
import { DataTable } from '../../components/ui/DataTable';
import { ApiRequestError } from '../../api/client';
import './robots.css';

type TabKey = 'overview' | 'telemetry' | 'events' | 'errors' | 'timeline' | 'security';

const TABS: { key: TabKey; label: string }[] = [
  { key: 'overview', label: 'Overview' },
  { key: 'telemetry', label: 'Telemetry' },
  { key: 'events', label: 'Events' },
  { key: 'errors', label: 'Errors' },
  { key: 'timeline', label: 'Timeline' },
  { key: 'security', label: 'Security' },
];

export function RobotDetailPage() {
  const { id } = useParams<{ id: string }>();
  const { hasPermission } = usePermissions();
  const { data: robot, status, error, refetch } = useApi(() => getRobot(id!), [id]);
  const [tab, setTab] = useState<TabKey>('overview');
  const [actionError, setActionError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  if (status === 'loading' || status === 'idle') {
    return <LoadingState title="Loading robot…" />;
  }
  if (status === 'error' || !robot) {
    return (
      <ErrorState
        title="Could not load this robot"
        detail={error ?? 'Not found, or outside your access scope.'}
        action={<button type="button" className="sakar-btn sakar-btn--secondary" onClick={refetch}>Retry</button>}
      />
    );
  }

  async function handleActivate() {
    setBusy(true);
    setActionError(null);
    try {
      await activateRobot(robot!.id);
      refetch();
    } catch (err) {
      setActionError(err instanceof ApiRequestError ? err.message : 'Action failed');
    } finally {
      setBusy(false);
    }
  }

  async function handleDeactivate() {
    setBusy(true);
    setActionError(null);
    try {
      await deactivateRobot(robot!.id);
      refetch();
    } catch (err) {
      setActionError(err instanceof ApiRequestError ? err.message : 'Action failed');
    } finally {
      setBusy(false);
    }
  }

  const telemetry = generateTelemetry(robot.id, 10);
  const events = generateEvents(robot.id, 10);
  const errors = generateErrors(robot.id, 10);

  return (
    <div>
      <div className="sakar-page-header">
        <div>
          <h1 className="sakar-page-title">{robot.name}</h1>
          <p className="sakar-page-subtitle">Sakar Robot / CleanBot 5000 Plus · Serial {robot.serialNumber}</p>
        </div>
        <div style={{ display: 'flex', gap: 10, alignItems: 'center' }}>
          <Badge tone={robot.status === 'ACTIVE' ? 'success' : robot.status === 'DEACTIVATED' ? 'warning' : 'neutral'}>
            {robot.status}
          </Badge>
          {hasPermission('ROBOT_CONFIGURE') && (
            <>
              {robot.status !== 'ACTIVE' && (
                <button type="button" className="sakar-btn sakar-btn--primary" disabled={busy} onClick={handleActivate}>
                  Activate
                </button>
              )}
              {robot.status !== 'DEACTIVATED' && (
                <button type="button" className="sakar-btn sakar-btn--danger" disabled={busy} onClick={handleDeactivate}>
                  Deactivate
                </button>
              )}
            </>
          )}
        </div>
      </div>
      {actionError && <div className="sakar-field-error" style={{ marginBottom: 12 }}>{actionError}</div>}

      <div className="sakar-tabs">
        {TABS.map((t) => (
          <button
            key={t.key}
            type="button"
            className={'sakar-tab' + (tab === t.key ? ' sakar-tab--active' : '')}
            onClick={() => setTab(t.key)}
          >
            {t.label}
          </button>
        ))}
      </div>

      {tab === 'overview' && (
        <div style={{ display: 'grid', gap: 16 }}>
          <Card title="Registry detail">
            <dl style={{ display: 'grid', gridTemplateColumns: '160px 1fr', rowGap: 10 }}>
              <dt className="sakar-page-subtitle">Robot ID</dt>
              <dd style={{ margin: 0, fontFamily: 'monospace', fontSize: 12.5 }}>{robot.id}</dd>
              <dt className="sakar-page-subtitle">Organization</dt>
              <dd style={{ margin: 0 }}>{robot.organizationId}</dd>
              <dt className="sakar-page-subtitle">Site</dt>
              <dd style={{ margin: 0 }}>{robot.siteId ?? '—'}</dd>
              <dt className="sakar-page-subtitle">Model ID</dt>
              <dd style={{ margin: 0 }}>{robot.robotModelId}</dd>
              <dt className="sakar-page-subtitle">Capabilities</dt>
              <dd style={{ margin: 0 }}>{robot.capabilities.join(', ') || 'None reported'}</dd>
              <dt className="sakar-page-subtitle">Registered</dt>
              <dd style={{ margin: 0 }}>{new Date(robot.createdAt).toLocaleString()}</dd>
            </dl>
          </Card>
          <RobotStatusPanel robotId={robot.id} />
        </div>
      )}

      {tab === 'telemetry' && (
        <Card title="Recent telemetry">
          <SimulatedDataBanner />
          <DataTable
            rows={telemetry}
            rowKey={(r) => r.id}
            columns={[
              { key: 'time', header: 'Timestamp', render: (r) => new Date(r.recordedAt).toLocaleString() },
              { key: 'metric', header: 'Metric', render: (r) => r.metricType },
              { key: 'value', header: 'Value', render: (r) => r.valueNumeric ?? r.valueText ?? '—' },
            ]}
          />
        </Card>
      )}

      {tab === 'events' && (
        <Card title="Recent events">
          <SimulatedDataBanner />
          <DataTable
            rows={events}
            rowKey={(e) => e.id}
            columns={[
              { key: 'time', header: 'Timestamp', render: (e) => new Date(e.recordedAt).toLocaleString() },
              { key: 'severity', header: 'Severity', render: (e) => e.severity },
              { key: 'message', header: 'Message', render: (e) => e.message },
            ]}
          />
        </Card>
      )}

      {tab === 'errors' && (
        <Card title="Recent errors">
          <SimulatedDataBanner />
          <DataTable
            rows={errors}
            rowKey={(e) => e.id}
            columns={[
              { key: 'code', header: 'Code', render: (e) => e.errorCode },
              { key: 'message', header: 'Message', render: (e) => e.message },
              { key: 'status', header: 'Status', render: (e) => e.status },
            ]}
          />
        </Card>
      )}

      {tab === 'timeline' && <RobotTimeline robotId={robot.id} />}

      {tab === 'security' && (
        <div style={{ display: 'grid', gap: 16 }}>
          <MqttCredentialsPanel robotId={robot.id} />
          <LockUnlockPanel />
        </div>
      )}
    </div>
  );
}
