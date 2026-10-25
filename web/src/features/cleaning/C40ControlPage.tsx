import { useState } from 'react';
import { useRobotOptions } from '../shared/useRobotOptions';
import { RobotPicker } from '../shared/RobotPicker';
import { RobotStatusPanel } from '../robots/RobotStatusPanel';
import { RobotTasksPanel } from '../tasks/RobotTasksPanel';
import { RobotCleaningPanel } from './RobotCleaningPanel';
import { PageHeader } from '../../components/ui/PageHeader';
import { Card } from '../../components/ui/Card';
import { EmptyState } from '../../components/ui/States';

// C40 cleaning modes recovered from the SDK/config evidence
// (research/.../ui/UI_ACTION_METHOD_MAPPING.md). These are the exact codes the
// Sakar backend's KeenonRobotAdapter maps to, shown here as a reference; the
// actual mode selector lives in the Tasks panel below.
const C40_MODES: { code: number; label: string }[] = [
  { code: 101, label: 'Sweep + Mop (sweep + wash)' },
  { code: 102, label: 'Water suction' },
  { code: 103, label: 'Sweep + Vacuum (sweep + suction)' },
  { code: 104, label: 'Sweep (push)' },
  { code: 105, label: 'Sweep' },
];

// C40 Cleaning Control — a focused operator console for one cleaning robot.
// It COMPOSES existing, backend-backed panels (live Status/Battery, task
// lifecycle with mode + area selection, and real cleaning history); it adds no
// new robot command path of its own and never talks to a robot directly — every
// action goes through the Sakar Cloud Backend, exactly as the underlying panels
// already do. Capabilities the backend does not expose (manual drive, actuator
// control, teach-path, water-mode, do-not-disturb) are intentionally absent
// rather than faked — see UI_ACTION_METHOD_MAPPING.md §"capability-bounded".
export function C40ControlPage() {
  const { robots } = useRobotOptions();
  const [robotId, setRobotId] = useState('');
  const robot = robots.find((r) => r.id === robotId);

  return (
    <div>
      <PageHeader
        title="C40 Cleaning Control"
        subtitle="Operator console for a cleaning robot — live status, cleaning tasks, and history, all through the Sakar backend."
      />
      <RobotPicker robots={robots} value={robotId} onChange={setRobotId} />

      {!robot ? (
        <EmptyState title="Select a robot" detail="Choose a robot above to open its cleaning control console." />
      ) : (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
          <RobotStatusPanel robotId={robot.id} connectionStatus={robot.connectionStatus} lastSeenAt={robot.lastSeenAt} />

          <Card title="C40 cleaning modes (reference)">
            <p className="sakar-page-subtitle" style={{ marginBottom: 12 }}>
              Static reference from reverse-engineering evidence — these are the C40 mode codes the backend maps to.
              Select a mode when creating a cleaning task below.
            </p>
            <ul style={{ listStyle: 'none', margin: 0, padding: 0, display: 'flex', flexWrap: 'wrap', gap: 8 }}>
              {C40_MODES.map((m) => (
                <li key={m.code} className="sakar-badge sakar-badge--neutral">
                  <strong>{m.code}</strong>&nbsp;{m.label}
                </li>
              ))}
            </ul>
          </Card>

          <RobotTasksPanel robotId={robot.id} />
          <RobotCleaningPanel robotId={robot.id} />
        </div>
      )}
    </div>
  );
}
