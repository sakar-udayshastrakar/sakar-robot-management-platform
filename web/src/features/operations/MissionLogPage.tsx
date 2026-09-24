import { useMemo, useState } from 'react';
import { listAllAccessibleTasks } from '../../api/tasks';
import { listRobots } from '../../api/robots';
import { listAllAccessibleSites } from '../../api/sites';
import { useApi } from '../../hooks/useApi';
import { Breadcrumb } from '../../components/ui/Breadcrumb';
import { PageHeader } from '../../components/ui/PageHeader';
import { Card } from '../../components/ui/Card';
import { DataTable } from '../../components/ui/DataTable';
import { Badge, type BadgeTone } from '../../components/ui/Badge';
import { LoadingState, ErrorState } from '../../components/ui/States';
import type { RobotTask, TaskStatus } from '../../types/domain';
import { useNavigate } from 'react-router-dom';

const STATUS_TONE: Record<TaskStatus, BadgeTone> = {
  CREATED: 'neutral',
  RUNNING: 'info',
  PAUSED: 'warning',
  COMPLETED: 'success',
  CANCELLED: 'neutral',
  FAILED: 'danger',
};

function destinationOf(parameters: string | null): string {
  if (!parameters) return '—';
  try {
    const parsed = JSON.parse(parameters) as { areaIds?: unknown; destinationId?: unknown };
    if (Array.isArray(parsed.areaIds) && parsed.areaIds.length > 0) return parsed.areaIds.join(', ');
    if (parsed.destinationId != null) return String(parsed.destinationId);
  } catch {
    // Not JSON, or a shape without areaIds/destinationId — fall through to '—' rather than guessing.
  }
  return '—';
}

// Operation And Maintenance Platform → Mission Log. Real fleet-wide task
// history (GET /api/v1/tasks) — reuses the existing robot_tasks table, never
// a separate "mission" concept. "Business Line" is the affiliated store's
// own scene_type (Store Management), "Destination" is read straight out of
// the task's own parameters JSON when present — never invented.
export function MissionLogPage() {
  const navigate = useNavigate();
  const { data: tasksPage, status, error, refetch } = useApi(() => listAllAccessibleTasks(0, 200), []);
  const { data: robotsPage } = useApi(() => listRobots(0, 200), []);
  const { data: sites } = useApi(() => listAllAccessibleSites(), []);

  const [businessLine, setBusinessLine] = useState('All');
  const [search, setSearch] = useState('');
  const [resultFilter, setResultFilter] = useState('All');

  const robotsById = useMemo(() => new Map((robotsPage?.content ?? []).map((r) => [r.id, r])), [robotsPage]);
  const sitesById = useMemo(() => new Map((sites ?? []).map((s) => [s.id, s])), [sites]);

  const businessLines = useMemo(() => {
    const values = new Set<string>();
    (sites ?? []).forEach((s) => { if (s.sceneType) values.add(s.sceneType); });
    return ['All', ...Array.from(values).sort()];
  }, [sites]);

  const rows = useMemo(() => {
    const tasks = tasksPage?.content ?? [];
    const term = search.trim().toLowerCase();
    return tasks.filter((t) => {
      const robot = robotsById.get(t.robotId);
      const site = robot?.siteId ? sitesById.get(robot.siteId) : undefined;
      if (businessLine !== 'All' && site?.sceneType !== businessLine) return false;
      if (resultFilter !== 'All' && t.status !== resultFilter) return false;
      if (term) {
        const haystack = `${robot?.serialNumber ?? ''} ${t.id} ${t.taskType}`.toLowerCase();
        if (!haystack.includes(term)) return false;
      }
      return true;
    });
  }, [tasksPage, robotsById, sitesById, businessLine, resultFilter, search]);

  if (status === 'loading' || status === 'idle') {
    return <LoadingState title="Loading mission log…" />;
  }
  if (status === 'error') {
    return (
      <ErrorState
        title="Could not load mission log"
        detail={error ?? undefined}
        action={<button type="button" className="sakar-btn sakar-btn--secondary" onClick={refetch}>Retry</button>}
      />
    );
  }

  return (
    <div>
      <Breadcrumb items={[{ label: 'Operation And Maintenance Platform' }, { label: 'Mission Log' }]} />
      <PageHeader title="Mission Log" subtitle="Fleet-wide task history across every robot you can access." />

      <Card>
        <div style={{ display: 'flex', gap: 8, marginBottom: 12, flexWrap: 'wrap' }}>
          {businessLines.map((line) => (
            <button
              key={line}
              type="button"
              className={`sakar-btn sakar-btn--sm ${businessLine === line ? 'sakar-btn--primary' : 'sakar-btn--secondary'}`}
              onClick={() => setBusinessLine(line)}
            >
              {line}
            </button>
          ))}
        </div>
        <div style={{ display: 'flex', gap: 12, marginBottom: 12, flexWrap: 'wrap', alignItems: 'flex-end' }}>
          <div className="sakar-field" style={{ marginBottom: 0 }}>
            <label htmlFor="mission-log-search">Robot SN / Task ID / Task Type</label>
            <input id="mission-log-search" value={search} onChange={(e) => setSearch(e.target.value)} placeholder="Search…" />
          </div>
          <div className="sakar-field" style={{ marginBottom: 0 }}>
            <label htmlFor="mission-log-result">Task Result</label>
            <select id="mission-log-result" value={resultFilter} onChange={(e) => setResultFilter(e.target.value)}>
              <option value="All">All</option>
              {(['CREATED', 'RUNNING', 'PAUSED', 'COMPLETED', 'CANCELLED', 'FAILED'] as TaskStatus[]).map((s) => (
                <option key={s} value={s}>{s}</option>
              ))}
            </select>
          </div>
        </div>
      </Card>

      <Card title={`Main Task Records (${rows.length})`}>
        <DataTable
          rows={rows}
          rowKey={(t) => t.id}
          emptyTitle="No Data"
          columns={[
            {
              key: 'store',
              header: 'Store',
              render: (t: RobotTask) => {
                const robot = robotsById.get(t.robotId);
                const site = robot?.siteId ? sitesById.get(robot.siteId) : undefined;
                return site?.name ?? '—';
              },
            },
            { key: 'robotSn', header: 'Robot SN', render: (t: RobotTask) => robotsById.get(t.robotId)?.serialNumber ?? t.robotId.slice(0, 8) },
            { key: 'productionNo', header: 'Production No.', render: (t: RobotTask) => robotsById.get(t.robotId)?.vendorSerialNumber ?? '—' },
            {
              key: 'storeId',
              header: 'Store ID',
              render: (t: RobotTask) => {
                const robot = robotsById.get(t.robotId);
                return robot?.siteId ? <span className="sakar-mono" style={{ fontSize: 12 }}>{robot.siteId.slice(0, 8)}…</span> : '—';
              },
            },
            { key: 'start', header: 'Task Start Time', render: (t: RobotTask) => new Date(t.createdAt).toLocaleString() },
            {
              key: 'end',
              header: 'Task End Time',
              render: (t: RobotTask) => (['COMPLETED', 'CANCELLED', 'FAILED'] as TaskStatus[]).includes(t.status)
                ? new Date(t.updatedAt).toLocaleString()
                : '—',
            },
            { key: 'taskType', header: 'Task Type', render: (t: RobotTask) => t.taskType },
            { key: 'destination', header: 'Destination', render: (t: RobotTask) => destinationOf(t.parameters) },
            { key: 'result', header: 'Task Result', render: (t: RobotTask) => <Badge tone={STATUS_TONE[t.status]} dot>{t.status}</Badge> },
            {
              key: 'action',
              header: 'Action',
              align: 'right' as const,
              render: (t: RobotTask) => (
                <button type="button" className="sakar-link-btn" onClick={() => navigate(`/robots/${t.robotId}`)}>View robot</button>
              ),
            },
          ]}
        />
      </Card>
    </div>
  );
}
