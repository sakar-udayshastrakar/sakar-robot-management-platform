import { useState, type FormEvent } from 'react';
import { cancelTask, createTask, listRobotTasks, pauseTask, resumeTask, startTask, stopTask } from '../../api/tasks';
import { getRobotAreas } from '../../api/robots';
import { useApi } from '../../hooks/useApi';
import { usePermissions } from '../../hooks/usePermissions';
import { useToast } from '../../components/ui/Toast';
import { ApiRequestError } from '../../api/client';
import { Card } from '../../components/ui/Card';
import { DataTable } from '../../components/ui/DataTable';
import { Pagination } from '../../components/ui/Pagination';
import { Badge } from '../../components/ui/Badge';
import { LoadingState, ErrorState } from '../../components/ui/States';
import type { RobotArea, RobotTask, TaskStatus } from '../../types/domain';

// Sakar's generic `mode` values — mirrors the literal switch in
// KeenonRobotAdapter.cleanModelIdFor (backend has no "list cleaning modes"
// endpoint yet, so this is hardcoded client-side, the same convention
// CommandsPanel.tsx already uses for NonLockCommandType) plus the labels
// used across the rest of this codebase's cleaning-related UI.
const CLEANING_MODES: { value: string; label: string }[] = [
  { value: 'SWEEP', label: 'Sweep' },
  { value: 'SWEEP_MOP', label: 'Sweep + Mop' },
  { value: 'SWEEP_VACUUM', label: 'Sweep + Vacuum' },
  { value: 'SWEEP_PUSH', label: 'Sweep (push)' },
  { value: 'WATER_SUCTION', label: 'Water suction' },
];

const STATUS_TONE: Record<TaskStatus, 'neutral' | 'info' | 'warning' | 'success' | 'danger'> = {
  CREATED: 'neutral',
  RUNNING: 'info',
  PAUSED: 'warning',
  COMPLETED: 'success',
  CANCELLED: 'neutral',
  FAILED: 'danger',
};

// Sakar-side task bookkeeping only — these buttons record a lifecycle
// transition (RobotTaskService.transition), they do not by themselves
// dispatch anything to the robot. Real robot command dispatch is the
// separate Commands tab (RobotCommandController) on the Robot Detail page.
function TaskActions({ task, onChanged }: { task: RobotTask; onChanged: () => void }) {
  const { hasPermission } = usePermissions();
  const toast = useToast();
  const [busy, setBusy] = useState(false);

  async function run(action: (id: string) => Promise<RobotTask>, label: string) {
    setBusy(true);
    try {
      await action(task.id);
      toast.show(`Task ${label.toLowerCase()}`, 'success');
      onChanged();
    } catch (err) {
      toast.show(err instanceof ApiRequestError ? err.message : `Failed to ${label.toLowerCase()} task`, 'error');
    } finally {
      setBusy(false);
    }
  }

  const canControl = hasPermission('ROBOT_CONTROL');
  const canCancel = hasPermission('ROBOT_TASK_CANCEL');

  return (
    <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap' }}>
      {canControl && task.status === 'CREATED' && (
        <button type="button" className="sakar-btn sakar-btn--secondary" disabled={busy} onClick={() => run(startTask, 'Started')}>Start</button>
      )}
      {canControl && task.status === 'RUNNING' && (
        <button type="button" className="sakar-btn sakar-btn--secondary" disabled={busy} onClick={() => run(pauseTask, 'Paused')}>Pause</button>
      )}
      {canControl && task.status === 'PAUSED' && (
        <button type="button" className="sakar-btn sakar-btn--secondary" disabled={busy} onClick={() => run(resumeTask, 'Resumed')}>Resume</button>
      )}
      {canControl && (task.status === 'RUNNING' || task.status === 'PAUSED') && (
        <button type="button" className="sakar-btn sakar-btn--secondary" disabled={busy} onClick={() => run(stopTask, 'Stopped')}>Stop</button>
      )}
      {canCancel && (task.status === 'CREATED' || task.status === 'RUNNING' || task.status === 'PAUSED') && (
        <button type="button" className="sakar-btn sakar-btn--danger" disabled={busy} onClick={() => run(cancelTask, 'Cancelled')}>Cancel</button>
      )}
      {!canControl && !canCancel && <span className="sakar-page-subtitle">No control permission</span>}
    </div>
  );
}

function CreateTaskForm({ robotId, onCreated }: { robotId: string; onCreated: () => void }) {
  const toast = useToast();
  const [taskType, setTaskType] = useState('CLEANING');
  const [mode, setMode] = useState('');
  const [selectedAreaIds, setSelectedAreaIds] = useState<string[]>([]);
  const [repeatCount, setRepeatCount] = useState(1);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // Every area the vendor currently reports, but only ones with a synced
  // Sakar mapping (sakarAreaId) are ever selectable — an unsynced area has
  // no id a CLEANING task's parameters could legally reference (see
  // AreaInfo.java's Javadoc), so it is filtered out entirely rather than
  // shown disabled or with a fabricated id.
  const { data: areas, status: areasStatus, error: areasError } = useApi(() => getRobotAreas(robotId), [robotId]);
  const selectableAreas = (areas ?? []).filter(
    (a): a is RobotArea & { sakarAreaId: string } => Boolean(a.sakarAreaId),
  );

  function toggleArea(sakarAreaId: string) {
    setSelectedAreaIds((prev) =>
      prev.includes(sakarAreaId) ? prev.filter((id) => id !== sakarAreaId) : [...prev, sakarAreaId],
    );
  }

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setError(null);

    if (taskType === 'CLEANING') {
      if (!mode) {
        setError('Cleaning mode is required.');
        return;
      }
      if (selectedAreaIds.length === 0) {
        setError('Select at least one area.');
        return;
      }
    }

    setSubmitting(true);
    try {
      const input =
        taskType === 'CLEANING'
          ? { taskType, parameters: JSON.stringify({ mode, areaIds: selectedAreaIds, repeatCount }) }
          : { taskType };
      await createTask(robotId, input);
      toast.show('Task created', 'success');
      onCreated();
    } catch (err) {
      setError(err instanceof ApiRequestError ? err.message : 'Failed to create task');
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <form onSubmit={handleSubmit} style={{ display: 'flex', gap: 10, alignItems: 'flex-end', marginBottom: 16, flexWrap: 'wrap' }}>
      <div className="sakar-field" style={{ marginBottom: 0 }}>
        <label htmlFor="task-type">Task type</label>
        <select id="task-type" value={taskType} onChange={(e) => setTaskType(e.target.value)}>
          <option value="CLEANING">Cleaning</option>
          <option value="RETURN_TO_DOCK">Return to dock</option>
          <option value="SPOT_CLEAN">Spot clean</option>
        </select>
      </div>

      {taskType === 'CLEANING' && (
        <>
          <div className="sakar-field" style={{ marginBottom: 0 }}>
            <label htmlFor="cleaning-mode">Cleaning mode</label>
            <select id="cleaning-mode" value={mode} onChange={(e) => setMode(e.target.value)}>
              <option value="">Select a mode…</option>
              {CLEANING_MODES.map((m) => (
                <option key={m.value} value={m.value}>{m.label}</option>
              ))}
            </select>
          </div>

          <div className="sakar-field" style={{ marginBottom: 0 }}>
            <label htmlFor="repeat-count">Repeat count</label>
            <input
              id="repeat-count"
              type="number"
              min={1}
              value={repeatCount}
              onChange={(e) => setRepeatCount(Math.max(1, Number(e.target.value) || 1))}
              style={{ width: 70 }}
            />
          </div>

          <div style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
            {areasStatus === 'loading' || areasStatus === 'idle' ? (
              <span>Loading areas…</span>
            ) : areasStatus === 'error' ? (
              <span className="sakar-field-error">Could not load areas: {areasError}</span>
            ) : selectableAreas.length === 0 ? (
              <span>No synced areas available for this robot.</span>
            ) : (
              selectableAreas.map((area) => (
                <label key={area.sakarAreaId} style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
                  <input
                    type="checkbox"
                    checked={selectedAreaIds.includes(area.sakarAreaId)}
                    onChange={() => toggleArea(area.sakarAreaId)}
                  />
                  {area.displayName ?? area.vendorAreaId}
                </label>
              ))
            )}
          </div>
        </>
      )}

      <button type="submit" className="sakar-btn sakar-btn--primary" disabled={submitting}>
        {submitting ? 'Creating…' : 'Create task'}
      </button>
      {error && <div className="sakar-field-error">{error}</div>}
    </form>
  );
}

export function RobotTasksPanel({ robotId }: { robotId: string }) {
  const { hasPermission } = usePermissions();
  const [page, setPage] = useState(0);
  const { data, status, error, refetch } = useApi(() => listRobotTasks(robotId, page, 10), [robotId, page]);

  if (status === 'loading' || status === 'idle') {
    return <LoadingState title="Loading tasks…" />;
  }
  if (status === 'error') {
    return (
      <ErrorState
        title="Could not load tasks"
        detail={error ?? undefined}
        action={<button type="button" className="sakar-btn sakar-btn--secondary" onClick={refetch}>Retry</button>}
      />
    );
  }

  return (
    <Card title={`Tasks (${data?.totalElements ?? 0})`}>
      {hasPermission('ROBOT_TASK_CREATE') && <CreateTaskForm robotId={robotId} onCreated={refetch} />}
      <DataTable
        rows={data?.content ?? []}
        rowKey={(t) => t.id}
        emptyTitle="No tasks yet for this robot"
        columns={[
          { key: 'type', header: 'Task type', render: (t) => t.taskType },
          { key: 'status', header: 'Status', render: (t) => <Badge tone={STATUS_TONE[t.status]}>{t.status}</Badge> },
          { key: 'created', header: 'Created', render: (t) => new Date(t.createdAt).toLocaleString() },
          { key: 'updated', header: 'Updated', render: (t) => new Date(t.updatedAt).toLocaleString() },
          { key: 'actions', header: 'Actions', render: (t) => <TaskActions task={t} onChanged={refetch} /> },
        ]}
      />
      {data && <Pagination page={data.number} totalPages={data.totalPages} onChange={setPage} />}
    </Card>
  );
}
