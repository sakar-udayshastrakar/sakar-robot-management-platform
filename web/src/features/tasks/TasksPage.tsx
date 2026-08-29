import { useMemo, useState } from 'react';
import { useRobotOptions } from '../shared/useRobotOptions';
import { RobotPicker } from '../shared/RobotPicker';
import { generateTasks } from '../../mocks/simulated';
import { Card } from '../../components/ui/Card';
import { DataTable } from '../../components/ui/DataTable';
import { SimulatedDataBanner } from '../../components/ui/SimulatedDataBanner';
import { EmptyState } from '../../components/ui/States';

// Read-only task history preview only. No task creation/start/pause/cancel
// control is offered here — RobotTask/TaskEvent are persisted entities with
// no REST controller, and Step 20 explicitly forbids fabricating robot task
// execution.
export function TasksPage() {
  const { robots } = useRobotOptions();
  const [robotId, setRobotId] = useState('');
  const tasks = useMemo(() => (robotId ? generateTasks(robotId, 10) : []), [robotId]);

  return (
    <div>
      <div className="sakar-page-header">
        <div>
          <h1 className="sakar-page-title">Tasks</h1>
          <p className="sakar-page-subtitle">Task history — read-only preview.</p>
        </div>
      </div>
      <SimulatedDataBanner label="No task API exists (GET/POST /robots/{id}/tasks are unbuilt) — task creation and control are not available from this page" />
      <RobotPicker robots={robots} value={robotId} onChange={setRobotId} />
      <Card title="Tasks">
        {!robotId ? (
          <EmptyState title="Select a robot" detail="Choose a robot above to preview its task history." />
        ) : (
          <DataTable
            rows={tasks}
            rowKey={(t) => t.id}
            columns={[
              { key: 'type', header: 'Task type', render: (t) => t.taskType },
              { key: 'status', header: 'Status', render: (t) => t.status },
              { key: 'created', header: 'Created', render: (t) => new Date(t.createdAt).toLocaleString() },
            ]}
          />
        )}
      </Card>
    </div>
  );
}
