import { useMemo, useState, type ReactNode } from 'react';
import { Link, useParams } from 'react-router-dom';
import { activateRobot, deactivateRobot, getRobot } from '../../api/robots';
import { useApi } from '../../hooks/useApi';
import { usePermissions } from '../../hooks/usePermissions';
import { useToast } from '../../components/ui/Toast';
import { useSiteNames } from '../shared/useSiteNames';
import { Card } from '../../components/ui/Card';
import { Badge } from '../../components/ui/Badge';
import { Icon } from '../../components/ui/Icon';
import { ConnectionStatusBadge } from '../../components/ui/ConnectionStatusBadge';
import { LoadingState, ErrorState, EmptyState } from '../../components/ui/States';
import { ConfirmDialog } from '../../components/ui/ConfirmDialog';
import { RobotStatusPanel } from './RobotStatusPanel';
import { RobotMapPanel } from './RobotMapPanel';
import { KeenonSceneConfigPanel } from './KeenonSceneConfigPanel';
import { MqttCredentialsPanel } from './MqttCredentialsPanel';
import { LockUnlockPanel } from './LockUnlockPanel';
import { RobotDiagnosticsPanel } from './RobotDiagnosticsPanel';
import { RobotAuditPanel } from './RobotAuditPanel';
import { RobotTimeline } from '../timeline/RobotTimeline';
import { RobotTasksPanel } from '../tasks/RobotTasksPanel';
import { RobotAlertsPanel } from '../alerts/RobotAlertsPanel';
import { RobotCleaningPanel } from '../cleaning/RobotCleaningPanel';
import { CommandsPanel } from './CommandsPanel';
import { RobotTelemetryPanel } from './RobotTelemetryPanel';
import { RobotEventsPanel } from './RobotEventsPanel';
import { RobotErrorsPanel } from './RobotErrorsPanel';
import { RobotLogsPanel } from './RobotLogsPanel';
import { ApiRequestError } from '../../api/client';
import './robots.css';

// The 7 Keenon-inspired tabs below "Overview" started as a visual shell only
// (SAKAR_KEENON_UI_AUDIT.md, Section O — first implementation slice). 'map'
// is wired to the real GET /robots/{id}/areas + map metadata/image endpoints
// (RobotMapPanel) — see that component for exactly what is and isn't
// available. 'configuration' is now wired to the real GET/PUT
// .../keenon/scene-config endpoints (KeenonSceneConfigPanel) — the
// prerequisite step for 'map' to resolve anything for a KEENON_CLOUD robot
// at all. The remaining 5 are still intentionally NOT wired to a backend
// endpoint: no such API exists today (recurring schedules, cleaning-run
// history/report generation, fleet statistics aggregation, or per-robot
// push-notification config are all real gaps tracked in the audit, Sections
// F/K/N). Rendering a mock table/chart here would misrepresent real robot
// capability, so each one shows a plain "not connected yet" empty state instead.
type PlannedTabKey = 'map' | 'taskManagement' | 'taskRecord' | 'statistics' | 'trialRunRecord' | 'configuration' | 'cleaningDailyReport';
type TabKey = 'overview' | PlannedTabKey | 'telemetry' | 'events' | 'errors' | 'alerts' | 'tasks' | 'cleaning' | 'commands' | 'logs' | 'timeline' | 'diagnostics' | 'audit';

const PLANNED_TABS: { key: PlannedTabKey; label: string }[] = [
  { key: 'map', label: 'Map' },
  { key: 'taskManagement', label: 'Task Management' },
  { key: 'taskRecord', label: 'Task Record' },
  { key: 'statistics', label: 'Statistics' },
  { key: 'trialRunRecord', label: 'Trial Run Record' },
  { key: 'configuration', label: 'Configuration' },
  { key: 'cleaningDailyReport', label: 'Cleaning Daily Report' },
];

const TABS: { key: TabKey; label: string }[] = [
  { key: 'overview', label: 'Overview' },
  ...PLANNED_TABS,
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

// Inline "Label: value" fact for the Overview tab's structured info panels
// (Basic Information / Location & Organization / Device Status) — verified
// against the live Keenon Cloud Robot Detail panels, which flow facts
// horizontally and wrap rather than stacking one bordered row per field.
function KvRow({ label, value, mono }: { label: string; value: ReactNode; mono?: boolean }) {
  return (
    <span className="sakar-kv-row">
      <span className="sakar-kv-label">{label}:</span>
      <span className={'sakar-kv-value' + (mono ? ' sakar-mono' : '')}>{value}</span>
    </span>
  );
}

function NotConnectedTab({ label }: { label: string }) {
  return (
    <Card title={label}>
      <EmptyState
        title="This feature is not connected yet."
        detail="Coming in a future implementation phase — no backend API exists for this view yet (see SAKAR_KEENON_UI_AUDIT.md for the tracked gap and roadmap). This tab intentionally shows no data rather than a fabricated example."
      />
    </Card>
  );
}

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

  const siteName = robot.siteId ? siteNames.get(robot.siteId) ?? robot.siteId : '—';

  return (
    <div>
      <nav className="sakar-breadcrumb" aria-label="Breadcrumb">
        <span>Fleet</span>
        <span aria-hidden="true">/</span>
        <Link to="/robots">Robots</Link>
        <span aria-hidden="true">/</span>
        <span className="sakar-breadcrumb-current">{robot.name}</span>
      </nav>
      <div className="sakar-page-header">
        <div>
          <h1 className="sakar-page-title">{robot.name}</h1>
          <p className="sakar-page-subtitle">
            {robot.serialNumber} · Sakar CleanBot 5000 Plus
          </p>
          <dl className="sakar-robot-header-meta">
            <div><dt>Robot ID</dt><dd className="sakar-mono">{robot.id}</dd></div>
            <div><dt>Model</dt><dd className="sakar-mono">{robot.robotModelId.slice(0, 8)}…</dd></div>
            <div><dt>Site</dt><dd>{siteName}</dd></div>
          </dl>
        </div>
        <div style={{ display: 'flex', gap: 10, alignItems: 'center', flexWrap: 'wrap' }}>
          {/* Backend-authoritative connectivity — identical to the value the Robots list
              renders and the value the offline-alert sweep acts on, so this page can never
              disagree with either. */}
          <ConnectionStatusBadge status={robot.connectionStatus} />
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
          <RobotStatusPanel robotId={robot.id} connectionStatus={robot.connectionStatus} lastSeenAt={robot.lastSeenAt} />

          <div className="sakar-info-grid">
            <Card title="Basic Information">
              <div className="sakar-fact-group">
                <KvRow label="Robot name" value={robot.name} />
                <KvRow label="Serial number" value={robot.serialNumber} />
                <KvRow label="Robot ID" value={robot.id} mono />
                <KvRow label="Model ID" value={robot.robotModelId} mono />
              </div>
            </Card>

            <Card title="Location & Organization">
              <div className="sakar-fact-group">
                <KvRow label="Organization" value={robot.organizationId} mono />
                <KvRow label="Site" value={siteName} />
              </div>
            </Card>

            <Card title="Device Status">
              <div className="sakar-fact-group">
                <KvRow
                  label="Registration state"
                  value={<Badge tone={robot.status === 'ACTIVE' ? 'success' : robot.status === 'DEACTIVATED' ? 'warning' : 'neutral'}>{robot.status}</Badge>}
                />
                <KvRow label="Registered" value={new Date(robot.createdAt).toLocaleString()} />
              </div>
            </Card>

            <Card title="Capabilities">
              {robot.capabilities.length === 0 ? (
                <span className="sakar-page-subtitle">No capabilities reported for this robot model.</span>
              ) : (
                <div style={{ display: 'flex', flexWrap: 'wrap', gap: 6 }}>
                  {robot.capabilities.map((c) => <Badge key={c} tone="primary">{c}</Badge>)}
                </div>
              )}
            </Card>
          </div>
        </div>
      )}

      {tab === 'map' && <RobotMapPanel robotId={robot.id} />}

      {tab === 'taskManagement' && <NotConnectedTab label="Task Management" />}

      {tab === 'taskRecord' && <NotConnectedTab label="Task Record" />}

      {tab === 'statistics' && <NotConnectedTab label="Statistics" />}

      {tab === 'trialRunRecord' && <NotConnectedTab label="Trial Run Record" />}

      {tab === 'configuration' && <KeenonSceneConfigPanel robotId={robot.id} />}

      {tab === 'cleaningDailyReport' && <NotConnectedTab label="Cleaning Daily Report" />}

      {tab === 'telemetry' && <RobotTelemetryPanel robotId={robot.id} />}

      {tab === 'events' && <RobotEventsPanel robotId={robot.id} />}

      {tab === 'errors' && <RobotErrorsPanel robotId={robot.id} />}

      {tab === 'alerts' && <RobotAlertsPanel robotId={robot.id} />}

      {tab === 'tasks' && <RobotTasksPanel robotId={robot.id} />}

      {tab === 'cleaning' && <RobotCleaningPanel robotId={robot.id} />}

      {tab === 'commands' && (
        <div style={{ display: 'grid', gap: 16 }}>
          <CommandsPanel robot={robot} />
          <MqttCredentialsPanel robotId={robot.id} />
          <LockUnlockPanel />
        </div>
      )}

      {tab === 'logs' && <RobotLogsPanel robotId={robot.id} />}

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
