import { useState } from 'react';
import { useRobotOptions } from '../shared/useRobotOptions';
import { RobotPicker } from '../shared/RobotPicker';
import { RobotTasksPanel } from './RobotTasksPanel';
import { PageHeader } from '../../components/ui/PageHeader';
import { EmptyState } from '../../components/ui/States';

// Real task orchestration (GET/POST /robots/{id}/tasks, lifecycle
// transitions on /tasks/{id}/*). Task orchestration is Sakar-side
// bookkeeping only — it does not itself dispatch a command to the robot
// (see the Commands tab on the Robot Detail page for that).
export function TasksPage() {
  const { robots } = useRobotOptions();
  const [robotId, setRobotId] = useState('');

  return (
    <div>
      <PageHeader title="Tasks" subtitle="Robot task orchestration." />
      <RobotPicker robots={robots} value={robotId} onChange={setRobotId} />
      {!robotId ? (
        <EmptyState title="Select a robot" detail="Choose a robot above to view and manage its tasks." />
      ) : (
        <RobotTasksPanel robotId={robotId} />
      )}
    </div>
  );
}
