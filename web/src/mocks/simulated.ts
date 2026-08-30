// Simulated/test data generators — used ONLY for pages where no backend REST
// API exists yet (telemetry, events, errors, application logs, timeline).
// Alerts, Tasks, Users, and Roles moved to real backend APIs in Phase 6 —
// see api/alerts.ts, api/tasks.ts, api/users.ts, api/roles.ts — and their
// generators were removed from here; do not re-add them. Never call this
// module from a page backed by a real endpoint. Every consumer of what
// remains here must render <SimulatedDataBanner /> alongside it so a viewer
// can never mistake this for real robot data.

import type {
  ApplicationLogRecord,
  ErrorStatus,
  EventCategory,
  EventSeverity,
  LogLevel,
  RobotErrorRecord,
  RobotEventRecord,
  TelemetryReading,
  TimelineEntry,
  TimelineSourceType,
} from '../types/domain';

function pick<T>(items: readonly T[]): T {
  return items[Math.floor(Math.random() * items.length)];
}

function minutesAgo(n: number): string {
  return new Date(Date.now() - n * 60_000).toISOString();
}

const METRIC_TYPES = ['battery_percent', 'work_mode', 'sync_status', 'robot_ip', 'total_odo'] as const;
const SEVERITIES: readonly EventSeverity[] = ['INFO', 'WARNING', 'ERROR', 'CRITICAL'];
const CATEGORIES: readonly EventCategory[] = ['SECURITY', 'NAVIGATION', 'BATTERY', 'CHARGING', 'SDK', 'SYSTEM'];

export function generateTelemetry(robotId: string, count = 20): TelemetryReading[] {
  return Array.from({ length: count }, (_, i) => {
    const metricType = pick(METRIC_TYPES);
    const isNumeric = metricType === 'battery_percent' || metricType === 'total_odo';
    return {
      id: `sim-telemetry-${robotId}-${i}`,
      robotId,
      recordedAt: minutesAgo(i * 4),
      metricType,
      valueNumeric: isNumeric ? Math.round(Math.random() * (metricType === 'battery_percent' ? 100 : 5000)) : null,
      valueText: isNumeric ? null : pick(['idle', 'cleaning', 'charging', 'ok', '192.168.1.42']),
      source: 'SIMULATED',
    };
  });
}

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

export function generateErrors(robotId: string, count = 8): RobotErrorRecord[] {
  return Array.from({ length: count }, (_, i) => {
    const status: ErrorStatus = pick(['OPEN', 'ACKNOWLEDGED', 'RESOLVED']);
    return {
      id: `sim-error-${robotId}-${i}`,
      robotId,
      errorCode: `E-${1000 + i}`,
      severity: pick(SEVERITIES),
      source: 'SIMULATED',
      message: pick([
        'Motor stall detected on drive wheel',
        'Lidar signal degraded',
        'Unexpected disconnect from charging dock',
        'Sensor calibration drift detected',
      ]),
      recordedAt: minutesAgo(i * 27),
      status,
      resolvedAt: status === 'RESOLVED' ? minutesAgo(i * 10) : null,
      resolvedBy: status === 'RESOLVED' ? 'ops@sakarrobotics.com' : null,
    };
  });
}

export function generateLogs(count = 30): ApplicationLogRecord[] {
  return Array.from({ length: count }, (_, i) => ({
    id: `sim-log-${i}`,
    level: pick<LogLevel>(['INFO', 'WARN', 'ERROR', 'CRITICAL']),
    source: pick(['mqtt', 'auth', 'robot-registry', 'websocket']),
    robotId: Math.random() > 0.4 ? `sim-robot-${Math.floor(Math.random() * 3)}` : null,
    message: pick([
      'HEARTBEAT_ACCEPTED',
      'TELEMETRY_ACCEPTED',
      'RATE_LIMITED_REJECTED',
      'Login succeeded',
      'MQTT_RECONNECTED',
    ]),
    recordedAt: minutesAgo(i * 3),
  }));
}

// Same shape as generateLogs, but every row is pinned to the given real
// robotId — used by the Robot Detail "Logs" tab so it never shows another
// robot's simulated entries alongside this one's.
export function generateRobotLogs(robotId: string, count = 15): ApplicationLogRecord[] {
  return Array.from({ length: count }, (_, i) => ({
    id: `sim-log-${robotId}-${i}`,
    level: pick<LogLevel>(['INFO', 'WARN', 'ERROR', 'CRITICAL']),
    source: pick(['mqtt', 'robot-registry', 'websocket']),
    robotId,
    message: pick(['HEARTBEAT_ACCEPTED', 'TELEMETRY_ACCEPTED', 'RATE_LIMITED_REJECTED', 'MQTT_RECONNECTED']),
    recordedAt: minutesAgo(i * 5),
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
