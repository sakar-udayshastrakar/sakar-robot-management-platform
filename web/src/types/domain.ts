// Types in this file that mirror a real backend entity are annotated with
// the source file. Types with no such annotation have NO backing REST API
// today (see docs/requirements/SAKAR_PHASE_4_WEB_IMPLEMENTATION_REPORT.md,
// "Implementation Gap List") and exist only to describe the shape of
// simulated/mock data used to build the corresponding UI ahead of the API.

// --- Real, backend-verified types -----------------------------------------

// backend/.../org/Organization.java
export type OrganizationType =
  | 'SAKAR_ROOT'
  | 'INTERNAL'
  | 'DISTRIBUTOR'
  | 'SUB_DISTRIBUTOR'
  | 'CLIENT'
  | 'DIRECT_CLIENT';

export type OrganizationStatus = 'ACTIVE' | 'SUSPENDED';

export interface Organization {
  id: string;
  parentOrganizationId: string | null;
  name: string;
  orgType: OrganizationType;
  status: OrganizationStatus;
  path: string;
  createdAt: string;
  updatedAt: string;
}

// backend/.../org/Site.java
export interface Site {
  id: string;
  organizationId: string;
  name: string;
  address: string | null;
  timezone: string | null;
  createdAt: string;
  updatedAt: string;
}

// backend/.../robot/registry/RobotLifecycleStatus.java
export type RobotLifecycleStatus = 'REGISTERED' | 'ACTIVE' | 'DEACTIVATED';

// backend/.../robot/registry/RobotCapabilityType.java
export type RobotCapabilityType =
  | 'GET_STATUS'
  | 'GET_BATTERY'
  | 'GET_TELEMETRY'
  | 'GET_EVENTS'
  | 'START_TASK'
  | 'STOP_TASK'
  | 'PAUSE_TASK'
  | 'RESUME_TASK'
  | 'RETURN_TO_DOCK'
  | 'LOCK'
  | 'UNLOCK'
  | 'GET_MAP'
  | 'GET_AREAS'
  | 'CLEANING';

// backend/.../robot/registry/dto/RobotResponse.java
export interface Robot {
  id: string;
  organizationId: string;
  siteId: string | null;
  robotModelId: string;
  name: string;
  serialNumber: string;
  status: RobotLifecycleStatus;
  capabilities: RobotCapabilityType[];
  createdAt: string;
}

// backend/.../robot/adapter/dto/RobotStatusSnapshot.java
export interface RobotStatusSnapshot {
  mainState: string;
  subState: string | null;
  online: boolean;
  observedAt: string;
  raw: unknown;
}

// backend/.../robot/registry/dto/RobotMqttCredentialResponse.java
export interface RobotMqttCredentialResponse {
  robotId: string;
  mqttUsername: string;
  mqttPassword: string;
  issuedAt: string;
}

// backend/.../audit/AuditLog.java
export interface AuditLog {
  id: string;
  userId: string | null;
  organizationId: string | null;
  robotId: string | null;
  action: string;
  result: string;
  reason: string | null;
  ipAddress: string | null;
  device: string | null;
  requestId: string | null;
  createdAt: string;
}

// --- Simulated-only types (no backend REST API exists for these yet) ------
// See RobotTelemetry / RobotAlert / RobotTask / TaskEvent / robot_events /
// robot_errors / application_logs / User / Role entities in the backend —
// all persisted, none exposed over REST.

export type TelemetrySource = 'MQTT_AGENT' | 'SIMULATED';

export interface TelemetryReading {
  id: string;
  robotId: string;
  recordedAt: string;
  metricType: string;
  valueNumeric: number | null;
  valueText: string | null;
  source: TelemetrySource;
}

export type EventSeverity = 'INFO' | 'WARNING' | 'ERROR' | 'CRITICAL';
export type EventCategory =
  | 'SECURITY'
  | 'NAVIGATION'
  | 'BATTERY'
  | 'CHARGING'
  | 'SDK'
  | 'SYSTEM';

export interface RobotEventRecord {
  id: string;
  robotId: string;
  recordedAt: string;
  severity: EventSeverity;
  category: EventCategory;
  source: TelemetrySource;
  message: string;
}

export type ErrorStatus = 'OPEN' | 'ACKNOWLEDGED' | 'RESOLVED';

export interface RobotErrorRecord {
  id: string;
  robotId: string;
  errorCode: string;
  severity: EventSeverity;
  source: TelemetrySource;
  message: string;
  recordedAt: string;
  status: ErrorStatus;
  resolvedAt: string | null;
  resolvedBy: string | null;
}

export type AlertStatus = 'ACTIVE' | 'ACKNOWLEDGED' | 'RESOLVED';

export interface RobotAlertRecord {
  id: string;
  robotId: string;
  severity: EventSeverity;
  status: AlertStatus;
  message: string;
  createdAt: string;
}

export type TimelineSourceType =
  | 'APPLICATION'
  | 'EVENT'
  | 'ERROR'
  | 'COMMAND'
  | 'SECURITY'
  | 'TELEMETRY';

export interface TimelineEntry {
  id: string;
  robotId: string;
  timestamp: string;
  sourceType: TimelineSourceType;
  severity: EventSeverity;
  message: string;
  dataSource: TelemetrySource;
}

export type TaskStatus =
  | 'PLANNED'
  | 'RUNNING'
  | 'PAUSED'
  | 'COMPLETED'
  | 'CANCELLED'
  | 'FAILED';

export interface RobotTaskRecord {
  id: string;
  robotId: string;
  taskType: string;
  status: TaskStatus;
  createdAt: string;
}

export type LogLevel = 'INFO' | 'WARN' | 'ERROR' | 'CRITICAL';

export interface ApplicationLogRecord {
  id: string;
  level: LogLevel;
  source: string;
  robotId: string | null;
  message: string;
  recordedAt: string;
}

export interface PlatformUserRecord {
  id: string;
  email: string;
  fullName: string;
  role: RoleNameLike;
  organizationId: string | null;
  active: boolean;
}

export interface PlatformRoleRecord {
  name: RoleNameLike;
  description: string;
  permissions: string[];
}

// Kept as a loose string here (rather than importing RoleName) since these
// records are simulated and must not be confused with the real JWT-derived
// role claim used for actual authorization.
export type RoleNameLike = string;
