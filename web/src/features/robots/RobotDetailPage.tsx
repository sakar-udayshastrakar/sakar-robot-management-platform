import { useMemo, useState } from 'react';
import { useParams } from 'react-router-dom';
import { activateRobot, deactivateRobot, getRobot } from '../../api/robots';
import { useApi } from '../../hooks/useApi';
import { usePermissions } from '../../hooks/usePermissions';
import { useToast } from '../../components/ui/Toast';
import { useSiteNames } from '../shared/useSiteNames';
import { useRobotStatusProbe } from '../shared/useRobotStatusProbe';
import { Card } from '../../components/ui/Card';
import { Badge } from '../../components/ui/Badge';
import { Icon } from '../../components/ui/Icon';
import { StatusBadge } from '../../components/ui/StatusBadge';
import { LoadingState, ErrorState } from '../../components/ui/States';
import { ConfirmDialog } from '../../components/ui/ConfirmDialog';
import { RobotStatusPanel } from './RobotStatusPanel';
import { MqttCredentialsPanel } from './MqttCredentialsPanel';
import { LockUnlockPanel } from './LockUnlockPanel';
import { RobotDiagnosticsPanel } from './RobotDiagnosticsPanel';
import { RobotAuditPanel } from './RobotAuditPanel';
import { RobotTimeline } from '../timeline/RobotTimeline';
import { RobotTasksPanel } from '../tasks/RobotTasksPanel';
import { RobotAlertsPanel } from '../alerts/RobotAlertsPanel';
import { RobotCleaningPanel } from '../cleaning/RobotCleaningPanel';
import { CommandsPanel } from './CommandsPanel';
import { generateTelemetry, generateEvents, generateErrors, generateRobotLogs } from '../../mocks/simulated';
import { SimulatedDataBanner } from '../../components/ui/SimulatedDataBanner';
import { DataTable } from '../../components/ui/DataTable';
import { ApiRequestError } from '../../api/client';
import './robots.css';

type TabKey = 'overview' | 'telemetry' | 'events' | 'errors' | 'alerts' | 'tasks' | 'cleaning' | 'commands' | 'logs' | 'timeline' | 'diagnostics' | 'audit';

const TABS: { key: TabKey; label: string }[] = [
  { key: 'overview', label: 'Overview' },
  { key: 'telemetry', label: 'Telemetry' },
  { key: 'events', label: 'Events' },
  { key: 'errors', label: 'Errors' },
  { key: 'alerts', label: 'Alerts' },
  { key: 'tasks', label: 'Tasks' },
  { key: 'cleaning', label: 'Cleaning' },
  { key: 'commands', label: 'Commands' },
  { key: 'logs', label: 'Logs' },
  { key: 'timeline', label: 'Timeline' },
  { key: 'diagnostics', label: 'Diagnostics' },
  { key: 'audit', label: 'Audit' },
];

export function RobotDetailPage() {
  const { id } = useParams<{ id: string }>();
  const { hasPermission } = usePermissions();
  const toast = useToast();
  const { data: robot, status, error, refetch } = useApi(() => getRobot(id!), [id]);
  const [tab, setTab] = useState<TabKey>('overview');
  const [confirmDeactivate, setConfirmDeactivate] = useState(false);
  const [busy, setBusy] = useState(false);

  const robotArray = useMemo(() => (robot ? [robot] : null), [robot]);
  const siteNames = useSiteNames(robotArray);
  const { statuses } = useRobotStatusProbe(robotArray, 1);
  const headerStatus = robot ? statuses.get(robot.id) : undefined;

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
    try {
      await activateRobot(robot!.id);
      toast.show('Robot activated', 'success');
      refetch();
    } catch (err) {
      toast.show(err instanceof ApiRequestError ? err.message : 'Action failed', 'error');
    } finally {
      setBusy(false);
    }
  }

  async function handleDeactivate() {
    setBusy(true);
    try {
      await deactivateRobot(robot!.id);
      toast.show('Robot deactivated', 'success');
      refetch();
    } catch (err) {
      toast.show(err instanceof ApiRequestError ? err.message : 'Action failed', 'error');
    } finally {
      setBusy(false);
      setConfirmDeactivate(false);
    }
  }

  const telemetry = generateTelemetry(robot.id, 10);
  const events = generateEvents(robot.id, 10);
  const errors = generateErrors(robot.id, 10);
  const logs = generateRobotLogs(robot.id, 12);
  const siteName = robot.siteId ? siteNames.get(robot.siteId) ?? robot.siteId : '—';

  return (
    <div>
      <div className="sakar-page-header">
        <div>
          <h1 className="sakar-page-title">{robot.name}</h1>
          <p className="sakar-page-subtitle">Sakar Robot — Sakar CleanBot 5000 Plus</p>
          <dl className="sakar-robot-header-meta">
            <div><dt>Robot ID</dt><dd className="sakar-mono">{robot.id}</dd></div>
            <div><dt>Model</dt><dd className="sakar-mono">{robot.robotModelId.slice(0, 8)}…</dd></div>
            <div><dt>Site</dt><dd>{siteName}</dd></div>
          </dl>
        </div>
        <div style={{ display: 'flex', gap: 10, alignItems: 'center', flexWrap: 'wrap' }}>
          {headerStatus && headerStatus !== 'unavailable' ? (
            <StatusBadge status={headerStatus.online ? 'ONLINE' : 'OFFLINE'} />
          ) : (
            <StatusBadge status="UNKNOWN" />
          )}
          <Badge tone={robot.status === 'ACTIVE' ? 'success' : robot.status === 'DEACTIVATED' ? 'warning' : 'neutral'}>{robot.status}</Badge>
        </div>
      </div>

      <div className="sakar-actions-row">
        <button type="button" className="sakar-btn sakar-btn--secondary" onClick={() => setTab('overview')}>
          <Icon.dashboard width={14} height={14} /> View
        </button>
        <button type="button" className="sakar-btn sakar-btn--secondary" onClick={() => setTab('diagnostics')}>
          <Icon.gauge width={14} height={14} /> Diagnostics
        </button>
        <button type="button" className="sakar-btn sakar-btn--secondary" onClick={() => setTab('logs')}>
          <Icon.fileText width={14} height={14} /> Logs
        </button>
        {hasPermission('ROBOT_CONFIGURE') && (
          <>
            {robot.status !== 'ACTIVE' && (
              <button type="button" className="sakar-btn sakar-btn--primary" disabled={busy} onClick={handleActivate}>Activate</button>
            )}
            {robot.status !== 'DEACTIVATED' && (
              <button type="button" className="sakar-btn sakar-btn--danger" disabled={busy} onClick={() => setConfirmDeactivate(true)}>Deactivate</button>
            )}
          </>
        )}
      </div>

      <div className="sakar-tabs sakar-scroll-x">
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
          <Card title="Registry Detail">
            <dl style={{ display: 'grid', gridTemplateColumns: '160px 1fr', rowGap: 10 }}>
              <dt className="sakar-page-subtitle">Robot ID</dt>
              <dd style={{ margin: 0, fontFamily: 'var(--sakar-font-mono)', fontSize: 12.5 }}>{robot.id}</dd>
              <dt className="sakar-page-subtitle">Serial Number</dt>
              <dd style={{ margin: 0 }}>{robot.serialNumber}</dd>
              <dt className="sakar-page-subtitle">Organization</dt>
              <dd style={{ margin: 0 }}>{robot.organizationId}</dd>
              <dt className="sakar-page-subtitle">Site</dt>
              <dd style={{ margin: 0 }}>{siteName}</dd>
              <dt className="sakar-page-subtitle">Model ID</dt>
              <dd style={{ margin: 0 }}>{robot.robotModelId}</dd>
              <dt className="sakar-page-subtitle">Capabilities</dt>
              <dd style={{ margin: 0 }}>{robot.capabilities.join(', ') || 'None reported'}</dd>
              <dt className="sakar-page-subtitle">Registered</dt>
              <dd style={{ margin: 0 }}>{new Date(robot.createdAt).toLocaleString()}</dd>
            </dl>
          </Card>
          <RobotStatusPanel robotId={robot.id} />
          <MqttCredentialsPanel robotId={robot.id} />
          <LockUnlockPanel />
        </div>
      )}

      {tab === 'telemetry' && (
        <Card title="Recent Telemetry">
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
        <Card title="Recent Events">
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
        <Card title="Recent Errors">
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

      {tab === 'alerts' && <RobotAlertsPanel robotId={robot.id} />}

      {tab === 'tasks' && <RobotTasksPanel robotId={robot.id} />}

      {tab === 'cleaning' && <RobotCleaningPanel robotId={robot.id} />}

      {tab === 'commands' && <CommandsPanel robot={robot} />}

      {tab === 'logs' && (
        <Card title="Application Logs">
          <SimulatedDataBanner label="application_logs is written by the backend but has no per-robot GET endpoint yet" />
          <DataTable
            rows={logs}
            rowKey={(l) => l.id}
            columns={[
              { key: 'time', header: 'Timestamp', render: (l) => new Date(l.recordedAt).toLocaleString() },
              { key: 'level', header: 'Level', render: (l) => l.level },
              { key: 'source', header: 'Source', render: (l) => <span className="sakar-mono">{l.source}</span> },
              { key: 'message', header: 'Message', render: (l) => <span className="sakar-mono">{l.message}</span> },
            ]}
          />
        </Card>
      )}

      {tab === 'timeline' && <RobotTimeline robotId={robot.id} />}

      {tab === 'diagnostics' && <RobotDiagnosticsPanel robot={robot} />}

      {tab === 'audit' && <RobotAuditPanel robotId={robot.id} />}

      <ConfirmDialog
        open={confirmDeactivate}
        title="Deactivate robot"
        message={`Deactivate ${robot.name}? It will stop being treated as an active fleet member until reactivated.`}
        confirmLabel="Deactivate"
        danger
        busy={busy}
        onConfirm={handleDeactivate}
        onCancel={() => setConfirmDeactivate(false)}
      />
    </div>
  );
}
