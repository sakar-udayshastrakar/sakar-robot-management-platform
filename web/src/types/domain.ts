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

// backend/.../robot/adapter/dto/BatteryInfo.java — same live-adapter-call
// pattern as RobotStatusSnapshot (GET /robots/{id}/battery, GET_BATTERY
// capability). `charging` is hardcoded false by KeenonRobotAdapter today
// (the vendor battery-level endpoint it calls does not report charging
// state) — real, not fabricated, just an incomplete upstream field.
export interface RobotBatteryInfo {
  percentage: number;
  charging: boolean;
  observedAt: string;
}

// backend/.../robot/adapter/dto/AreaInfo.java — same live-adapter-call
// pattern as RobotStatusSnapshot/BatteryInfo (GET /robots/{id}/areas,
// GET_AREAS capability). Deliberately only these two fields: the adapter
// does not receive or expose polygon geometry, navigation points, or a
// charging-point position from the vendor, so none of that is modeled
// here — see SAKAR_KEENON_UI_AUDIT.md / the Map implementation report for
// exactly what is and isn't available.
export interface RobotArea {
  vendorAreaId: string | null;
  displayName: string | null;
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

// backend/.../iam/UserStatus.java
export type UserStatus = 'ACTIVE' | 'SUSPENDED' | 'INVITED';

// backend/.../iam/dto/UserResponse.java — organizationId is null only for a
// SUPER_ADMIN-scoped, cross-organization Sakar staff account.
export interface PlatformUser {
  id: string;
  organizationId: string | null;
  email: string;
  fullName: string;
  roleName: RoleNameLike;
  status: UserStatus;
  mfaEnabled: boolean;
  lastLoginAt: string | null;
  createdAt: string;
}

// backend/.../iam/dto/RoleResponse.java — roles/permissions are fixed
// reference data (V9__seed_rbac.sql); this is a read-only list, not an
// editable resource.
export interface RoleWithPermissions {
  id: string;
  name: RoleNameLike;
  description: string | null;
  permissions: string[];
}

// backend/.../task/TaskLifecycleStatus.java
export type TaskStatus = 'CREATED' | 'RUNNING' | 'PAUSED' | 'COMPLETED' | 'CANCELLED' | 'FAILED';

// backend/.../task/dto/TaskResponse.java
export interface RobotTask {
  id: string;
  robotId: string;
  organizationId: string;
  createdBy: string | null;
  taskType: string;
  parameters: string | null;
  status: TaskStatus;
  createdAt: string;
  updatedAt: string;
}

// backend/.../task/dto/TaskEventResponse.java — TaskEvent is an
// AppendOnlyEntity with a Long id, so this is a JSON number, not a UUID.
export interface TaskEvent {
  id: number;
  eventType: string;
  detail: string | null;
  createdAt: string;
}

// backend/.../task/dto/TaskDetailResponse.java
export interface TaskDetail {
  task: RobotTask;
  events: TaskEvent[];
}

// backend/.../alert/AlertType.java — the only two alert-generation rules
// this backend implements; no other alert type is fabricated on the frontend.
export type RobotAlertType = 'LOW_BATTERY' | 'OFFLINE';

// backend/.../alert/AlertStatus.java — real vocabulary (OPEN, not ACTIVE).
export type RobotAlertStatus = 'OPEN' | 'ACKNOWLEDGED' | 'RESOLVED';

// backend/.../alert/dto/AlertResponse.java
export interface RobotAlert {
  id: string;
  robotId: string;
  organizationId: string;
  alertType: RobotAlertType;
  severity: AlertSeverity;
  message: string;
  status: RobotAlertStatus;
  acknowledgedBy: string | null;
  acknowledgedAt: string | null;
  createdAt: string;
}

// backend/.../cleaning/dto/CleaningSessionResponse.java — vendorReference is
// deliberately omitted here: the backend itself never populates it with a
// real value yet (see CleaningSession's own Javadoc), so it is not surfaced.
export interface CleaningSession {
  id: string;
  robotId: string;
  siteId: string | null;
  taskId: string | null;
  startedAt: string;
  endedAt: string | null;
  durationSeconds: number | null;
  areaSqMeters: number | null;
  efficiency: number | null;
  result: string | null;
  failureReason: string | null;
}

// backend/.../command/NonLockCommandType.java — LOCK/UNLOCK deliberately do
// not exist in this union; no UI control anywhere may offer them.
export type NonLockCommandType = 'START_TASK' | 'STOP_TASK' | 'PAUSE_TASK' | 'RESUME_TASK' | 'RETURN_TO_DOCK';

// backend/.../command/CommandStatus.java — the full lifecycle enum. Only
// AUTHORIZED and SENT are actually reachable via RobotCommandService today
// (no agent-side consumer exists yet to advance a command any further).
export type CommandStatus =
  | 'REQUESTED'
  | 'AUTHORIZED'
  | 'SIGNED'
  | 'SENT'
  | 'COMMAND_RECEIVED'
  | 'RUNNING'
  | 'COMMAND_SUCCESS'
  | 'COMMAND_FAILED'
  | 'COMMAND_TIMEOUT'
  | 'CANCELLED';

// backend/.../command/dto/CommandResponse.java — dispatched/dispatchNote are
// the honest bridge between "accepted" and "executed"; status alone never
// implies physical execution (see the backend DTO's own Javadoc).
export interface RobotCommand {
  id: string;
  robotId: string;
  commandType: NonLockCommandType;
  status: CommandStatus;
  nonce: string;
  expiresAt: string;
  sentAt: string | null;
  dispatched: boolean;
  dispatchNote: string | null;
  createdAt: string;
}

// --- Real telemetry/diagnostics history (Roadmap Phase 9 web-platform gap
// analysis STEP 2) — mirror backend/.../telemetry/dto/RobotTelemetryResponse
// and .../srels/dto/{RobotEventResponse,RobotErrorResponse,ApplicationLogResponse}
// field-for-field. Do not add a field the backend does not actually send.

export interface RobotTelemetryEntry {
  id: number;
  robotId: string;
  metric: string;
  valueNumeric: number | null;
  valueText: string | null;
  recordedAt: string;
  createdAt: string;
}

export interface RobotEventEntry {
  id: number;
  robotId: string;
  eventType: string;
  severity: string;
  payload: string | null;
  occurredAt: string;
  createdAt: string;
}

export interface RobotErrorEntry {
  id: string;
  robotId: string;
  errorCode: string;
  severity: string;
  source: string | null;
  message: string | null;
  sdkApi: string | null;
  status: string;
  occurredAt: string;
  resolvedAt: string | null;
  resolvedBy: string | null;
  createdAt: string;
}

export interface ApplicationLogEntry {
  id: number;
  source: string;
  robotId: string | null;
  level: string;
  message: string;
  context: string | null;
  createdAt: string;
}

// --- Simulated-only types (no backend REST API exists for these yet) ------
// Telemetry/events/errors/application-logs moved to real APIs above (Phase 9
// web-platform gap analysis STEP 2) — only the Timeline tab (which merges
// all four into one synthesized view) remains simulated; see RobotTimeline.

export type TelemetrySource = 'MQTT_AGENT' | 'SIMULATED';

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

// Alert-severity vocabulary (Critical/High/Medium/Low) — distinct from the
// Info/Warning/Error/Critical event-severity scale used by RobotEventRecord
// / RobotErrorRecord above; both are real, separate taxonomies. Also used by
// the real RobotAlert type above (backend/.../alert/AlertSeverity.java) —
// the values match exactly, so this single definition serves both.
export type AlertSeverity = 'CRITICAL' | 'HIGH' | 'MEDIUM' | 'LOW';

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


// Kept as a loose string here (rather than importing RoleName from
// types/permissions) purely to avoid a circular/needless coupling between
// this file and the JWT-claim permission types — PlatformUser.roleName and
// RoleWithPermissions.name above are both real, backend-sourced strings from
// the RoleName enum, just not narrowed to that union in this file.
export type RoleNameLike = string;
