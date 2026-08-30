import { useState } from 'react';
import { useRobotOptions } from '../shared/useRobotOptions';
import { RobotPicker } from '../shared/RobotPicker';
import { RobotCleaningPanel } from './RobotCleaningPanel';
import { PageHeader } from '../../components/ui/PageHeader';
import { EmptyState } from '../../components/ui/States';

export function CleaningPage() {
  const { robots } = useRobotOptions();
  const [robotId, setRobotId] = useState('');

  return (
    <div>
      <PageHeader title="Cleaning" subtitle="Real cleaning session history, per robot." />
      <RobotPicker robots={robots} value={robotId} onChange={setRobotId} />
      {!robotId ? (
        <EmptyState title="Select a robot" detail="Choose a robot above to view its cleaning history." />
      ) : (
        <RobotCleaningPanel robotId={robotId} />
      )}
    </div>
  );
}
