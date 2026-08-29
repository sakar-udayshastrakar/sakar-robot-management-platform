// Simulated/test data generators — used ONLY for pages where no backend REST
// API exists yet (telemetry, events, errors, alerts, tasks, users, roles,
// application logs). Never called on a page backed by a real endpoint. Every
// consumer of this module must render <SimulatedDataBanner /> alongside it
// so a viewer can never mistake this for real robot data. See the Phase 4
// implementation report's "Implementation Gap List".

import type {
  AlertStatus,
  ApplicationLogRecord,
  ErrorStatus,
  EventCategory,
  EventSeverity,
  LogLevel,
  PlatformRoleRecord,
  PlatformUserRecord,
  RobotAlertRecord,
  RobotErrorRecord,
  RobotEventRecord,
  RobotTaskRecord,
  TaskStatus,
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

export function generateAlerts(robotId: string, count = 6): RobotAlertRecord[] {
  return Array.from({ length: count }, (_, i) => ({
    id: `sim-alert-${robotId}-${i}`,
    robotId,
    severity: pick(SEVERITIES),
    status: pick<AlertStatus>(['ACTIVE', 'ACKNOWLEDGED', 'RESOLVED']),
    message: pick(['Low battery — return to dock recommended', 'Offline for over 15 minutes', 'Repeated navigation errors']),
    createdAt: minutesAgo(i * 19),
  }));
}

export function generateTasks(robotId: string, count = 5): RobotTaskRecord[] {
  return Array.from({ length: count }, (_, i) => ({
    id: `sim-task-${robotId}-${i}`,
    robotId,
    taskType: pick(['ZONE_CLEAN', 'RETURN_TO_DOCK', 'SPOT_CLEAN', 'SCHEDULED_CLEAN']),
    status: pick<TaskStatus>(['PLANNED', 'RUNNING', 'PAUSED', 'COMPLETED', 'CANCELLED', 'FAILED']),
    createdAt: minutesAgo(i * 40),
  }));
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

export function generateUsers(count = 8): PlatformUserRecord[] {
  return Array.from({ length: count }, (_, i) => ({
    id: `sim-user-${i}`,
    email: `user${i}@sakarrobotics.com`,
    fullName: pick(['A. Sharma', 'R. Iyer', 'K. Patel', 'M. Rao', 'S. Nair']),
    role: pick(['ORG_ADMIN', 'SITE_ADMIN', 'OPERATOR', 'TECHNICIAN', 'VIEWER']),
    organizationId: null,
    active: Math.random() > 0.15,
  }));
}

export function generateRoles(): PlatformRoleRecord[] {
  return [
    { name: 'SUPER_ADMIN', description: 'Full cross-organization access', permissions: ['SYSTEM_ADMIN'] },
    { name: 'ORG_ADMIN', description: 'Full access within one organization tree', permissions: ['ROBOT_CONFIGURE', 'USER_MANAGE'] },
    { name: 'SITE_ADMIN', description: 'Manage robots/users at one site', permissions: ['ROBOT_CONFIGURE'] },
    { name: 'OPERATOR', description: 'Operate robots, view fleet state', permissions: ['ROBOT_CONTROL', 'ROBOT_VIEW'] },
    { name: 'TECHNICIAN', description: 'Diagnostics and maintenance', permissions: ['ROBOT_DIAGNOSTICS', 'ROBOT_VIEW'] },
    { name: 'VIEWER', description: 'Read-only fleet visibility', permissions: ['ROBOT_VIEW'] },
  ];
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
