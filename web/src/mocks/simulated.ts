// Simulated/test data generator — used ONLY for pages where no backend REST
// API exists yet (the Dashboard's "Recent Robot Events" card, and the Robot
// Detail Timeline tab). Telemetry, Events, Errors, and Application Logs
// pages moved to real backend APIs in the Phase 9 web-platform gap analysis
// STEP 2 — see api/telemetry.ts, api/diagnostics.ts — and their generators
// were removed from here; do not re-add them. Never call this module from a
// page backed by a real endpoint. Every consumer of what remains here must
// render <SimulatedDataBanner /> alongside it so a viewer can never mistake
// this for real robot data.

import type {
  EventCategory,
  EventSeverity,
  RobotEventRecord,
  TimelineEntry,
  TimelineSourceType,
} from '../types/domain';

function pick<T>(items: readonly T[]): T {
  return items[Math.floor(Math.random() * items.length)];
}

function minutesAgo(n: number): string {
  return new Date(Date.now() - n * 60_000).toISOString();
}

const SEVERITIES: readonly EventSeverity[] = ['INFO', 'WARNING', 'ERROR', 'CRITICAL'];
const CATEGORIES: readonly EventCategory[] = ['SECURITY', 'NAVIGATION', 'BATTERY', 'CHARGING', 'SDK', 'SYSTEM'];

export function generateEvents(robotId: string, count = 15): RobotEventRecord[] {
  return Array.from({ length: count }, (_, i) => ({
    id: `sim-event-${robotId}-${i}`,
    robotId,
    recordedAt: minutesAgo(i * 11),
    severity: pick(SEVERITIES),
    category: pick(CATEGORIES),
    source: 'SIMULATED',
    message: pick([
      'Navigation replanned due to obstacle',
      'Battery crossed low-charge threshold',
      'Docking sequence completed',
      'SDK heartbeat callback received',
      'Charging session started',
      'Operator acknowledged status change',
    ]),
  }));
}

const TIMELINE_SOURCES: readonly TimelineSourceType[] = ['APPLICATION', 'EVENT', 'ERROR', 'COMMAND', 'SECURITY', 'TELEMETRY'];

export function generateTimeline(robotId: string, count = 20): TimelineEntry[] {
  const entries: TimelineEntry[] = Array.from({ length: count }, (_, i) => ({
    id: `sim-timeline-${robotId}-${i}`,
    robotId,
    timestamp: minutesAgo(i * 6),
    sourceType: pick(TIMELINE_SOURCES),
    severity: pick(SEVERITIES),
    message: pick([
      'Robot online',
      'MQTT connected',
      'Telemetry received',
      'Navigation warning',
      'Heartbeat accepted',
      'Operator command issued',
      'Error cleared',
    ]),
    dataSource: 'SIMULATED',
  }));
  return entries.sort((a, b) => (a.timestamp < b.timestamp ? 1 : -1));
}
