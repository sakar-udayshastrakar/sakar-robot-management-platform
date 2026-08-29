import { useMemo } from 'react';
import { generateTimeline } from '../../mocks/simulated';
import type { EventSeverity } from '../../types/domain';
import { Card } from '../../components/ui/Card';
import { SimulatedDataBanner } from '../../components/ui/SimulatedDataBanner';
import './timeline.css';

const SEVERITY_COLOR: Record<EventSeverity, string> = {
  INFO: 'var(--sakar-info)',
  WARNING: 'var(--sakar-warning)',
  ERROR: 'var(--sakar-danger)',
  CRITICAL: 'var(--sakar-danger)',
};

// A KRLog-style combined timeline. Backed by simulated data — no combined
// robot history endpoint exists yet (would need robot_events + robot_errors
// + commands + lock history joined server-side).
export function RobotTimeline({ robotId }: { robotId: string }) {
  const entries = useMemo(() => generateTimeline(robotId, 25), [robotId]);

  return (
    <Card title="Robot timeline">
      <SimulatedDataBanner label="No combined robot history endpoint exists — this timeline interleaves simulated application/event/error/command entries" />
      <ol className="sakar-timeline">
        {entries.map((entry) => (
          <li key={entry.id} className="sakar-timeline-item">
            <span className="sakar-timeline-dot" style={{ background: SEVERITY_COLOR[entry.severity] }} />
            <div className="sakar-timeline-body">
              <div className="sakar-timeline-meta">
                <span className="sakar-timeline-time">{new Date(entry.timestamp).toLocaleTimeString()}</span>
                <span className="sakar-timeline-source">{entry.sourceType}</span>
              </div>
              <div className="sakar-timeline-message">{entry.message}</div>
            </div>
          </li>
        ))}
      </ol>
    </Card>
  );
}
