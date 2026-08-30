import { useState } from 'react';
import { issueCommand, listCommands } from '../../api/commands';
import { useApi } from '../../hooks/useApi';
import { usePermissions } from '../../hooks/usePermissions';
import { useToast } from '../../components/ui/Toast';
import { ApiRequestError } from '../../api/client';
import { Card } from '../../components/ui/Card';
import { DataTable } from '../../components/ui/DataTable';
import { Badge } from '../../components/ui/Badge';
import { Icon } from '../../components/ui/Icon';
import { LoadingState, ErrorState } from '../../components/ui/States';
import { UnavailableFeature } from '../../components/ui/SimulatedDataBanner';
import type { NonLockCommandType, Robot, RobotCapabilityType } from '../../types/domain';

// Every entry here is a REAL command the backend can issue
// (command/NonLockCommandType.java) — LOCK/UNLOCK are not, and cannot be,
// offered from this panel: there is no code path that would let this UI
// construct a request RobotCommandController would accept for either.
const COMMANDS: { type: NonLockCommandType; label: string; icon: keyof typeof Icon; capability: RobotCapabilityType }[] = [
  { type: 'START_TASK', label: 'Start Task', icon: 'play', capability: 'START_TASK' },
  { type: 'PAUSE_TASK', label: 'Pause', icon: 'pause', capability: 'PAUSE_TASK' },
  { type: 'RESUME_TASK', label: 'Resume', icon: 'play', capability: 'RESUME_TASK' },
  { type: 'STOP_TASK', label: 'Stop', icon: 'stop', capability: 'STOP_TASK' },
  { type: 'RETURN_TO_DOCK', label: 'Return to Dock', icon: 'dock', capability: 'RETURN_TO_DOCK' },
];

export function CommandsPanel({ robot }: { robot: Robot }) {
  const { hasPermission } = usePermissions();
  const toast = useToast();
  const [busyType, setBusyType] = useState<NonLockCommandType | null>(null);
  const { data, status, error, refetch } = useApi(() => listCommands(robot.id, 0, 10), [robot.id]);

  async function handleIssue(commandType: NonLockCommandType) {
    setBusyType(commandType);
    try {
      const command = await issueCommand(robot.id, { commandType });
      if (command.dispatched) {
        toast.show(`${commandType} sent — ${command.dispatchNote ?? 'delivery not confirmed'}`, 'success');
      } else {
        toast.show(`${commandType} not dispatched — ${command.dispatchNote ?? 'MQTT unavailable'}`, 'error');
      }
      refetch();
    } catch (err) {
      toast.show(err instanceof ApiRequestError ? err.message : `Failed to issue ${commandType}`, 'error');
    } finally {
      setBusyType(null);
    }
  }

  const canControl = hasPermission('ROBOT_CONTROL');

  return (
    <div style={{ display: 'grid', gap: 16 }}>
      <Card title="Issue Command">
        <UnavailableFeature reason="A dispatched command is a real, best-effort MQTT publish attempt — SakarC40Agent has no command-consuming code yet, so a command's status never advances past AUTHORIZED/SENT and delivery/execution is never confirmed here. Lock/Unlock are not offered — no backend endpoint accepts either." />
        {!canControl ? (
          <p className="sakar-page-subtitle">You do not have permission to issue commands to this robot.</p>
        ) : (
          <div style={{ display: 'flex', gap: 10, flexWrap: 'wrap' }}>
            {COMMANDS.map((cmd) => {
              const supported = robot.capabilities.includes(cmd.capability);
              const IconCmp = Icon[cmd.icon];
              return (
                <button
                  key={cmd.type}
                  type="button"
                  className="sakar-btn sakar-btn--secondary"
                  disabled={!supported || busyType !== null}
                  title={supported ? undefined : 'This robot model does not report this capability'}
                  onClick={() => handleIssue(cmd.type)}
                >
                  <IconCmp width={14} height={14} /> {busyType === cmd.type ? 'Sending…' : cmd.label}
                </button>
              );
            })}
          </div>
        )}
      </Card>

      <Card title="Command History">
        {status === 'loading' || status === 'idle' ? (
          <LoadingState title="Loading commands…" />
        ) : status === 'error' ? (
          <ErrorState
            title="Could not load command history"
            detail={error ?? undefined}
            action={<button type="button" className="sakar-btn sakar-btn--secondary" onClick={refetch}>Retry</button>}
          />
        ) : (
          <DataTable
            rows={data?.content ?? []}
            rowKey={(c) => c.id}
            emptyTitle="No commands issued to this robot yet"
            columns={[
              { key: 'type', header: 'Command', render: (c) => c.commandType },
              { key: 'status', header: 'Status', render: (c) => <Badge tone={c.status === 'SENT' ? 'info' : 'neutral'}>{c.status}</Badge> },
              { key: 'dispatched', header: 'Dispatched', render: (c) => <Badge tone={c.dispatched ? 'success' : 'warning'}>{c.dispatched ? 'Yes' : 'No'}</Badge> },
              { key: 'note', header: 'Note', render: (c) => c.dispatchNote ?? '—' },
              { key: 'created', header: 'Issued', render: (c) => new Date(c.createdAt).toLocaleString() },
            ]}
          />
        )}
      </Card>
    </div>
  );
}
