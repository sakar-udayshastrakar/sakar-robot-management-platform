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

// backend/.../org/Site.java — also this platform's "Store" (Store
// Management, Robot Management sidebar group). The 6 store-specific fields
// are typed optional (rather than required), same convention as
// Robot.vendorSerialNumber, so the existing Site fixtures across the test
// suite that predate them don't need to change as a side effect of adding
// them — the real backend response always includes the keys.
export interface Site {
  id: string;
  organizationId: string;
  name: string;
  address: string | null;
  timezone: string | null;
  createdAt: string;
  updatedAt: string;
  area?: string | null;
  contactName?: string | null;
  phone?: string | null;
  email?: string | null;
  sceneType?: string | null;
  chainBrand?: boolean;
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
  // The vendor's own manufacturer serial (e.g. Keenon mftCode) — distinct
  // from serialNumber, which is Sakar's own generated identity. Nullable,
  // and typed optional (rather than required) purely so the many existing
  // Robot test fixtures across the suite that predate this field don't need
  // to change as a side effect of adding it — the real backend response
  // always includes the key, same convention as RobotArea.sakarAreaId.
  vendorSerialNumber?: string | null;
  status: RobotLifecycleStatus;
  capabilities: RobotCapabilityType[];
  // The backend's single authoritative connectivity verdict
  // (RobotConnectivityService): derived from the robot's own
  // heartbeat/telemetry against the configured offline threshold. Render it
  // as-is — never re-derive connectivity in the browser from lastSeenAt,
  // which is display-only and would reintroduce the clock-skew and
  // stale-badge problems this field exists to remove.
  connectionStatus: RobotConnectionStatus;
  lastSeenAt: string | null;
  createdAt: string;
  // backend/.../robot/registry/RobotUseType.java (Robot Management, Phase 2)
  // — commercial terms, independent of `status`'s deployment lifecycle.
  // Optional for the same reason as vendorSerialNumber above.
  useType?: RobotUseType;
  warrantyStartDate?: string | null;
  warrantyEndDate?: string | null;
}

export type RobotUseType = 'TRIAL' | 'PRODUCTION';

// backend/.../telemetry/RobotConnectionStatus.java
export type RobotConnectionStatus = 'ONLINE' | 'OFFLINE' | 'UNKNOWN';

// backend/.../robot/registry/dto/RobotModelResponse.java — previously
// nothing exposed this, so the frontend could only show a raw, truncated
// robotModelId UUID.
export interface RobotModel {
  id: string;
  manufacturerName: string | null;
  name: string;
  sakarProductName: string | null;
}

// backend/.../robot/adapter/dto/RobotStatusSnapshot.java
export interface RobotStatusSnapshot {
  mainState: string;
  subState: string | null;
  // CAUTION: this is a vendor-reachability flag — true means the adapter's
  // status call returned data, NOT that the robot is connected. Use
  // Robot.connectionStatus for connectivity; this field must never drive a
  // connection badge.
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
// GET_AREAS capability). The adapter does not receive or expose polygon
// geometry, navigation points, or a charging-point position from the
// vendor, so none of that is modeled here — see SAKAR_KEENON_UI_AUDIT.md /
// the Map implementation report for exactly what is and isn't available.
//
// sakarAreaId mirrors AreaInfo.java's third field: the Sakar-owned
// KeenonAreaMapping row id, null when the vendor reports an area with no
// synced Sakar mapping yet (never fabricated to fill the gap), and the
// value a CLEANING task's parameters.areaIds must use — see
// RobotTaskService.parseCleaningStartParams and RobotTasksPanel's
// CreateTaskForm. Typed optional here (rather than required) only so an
// existing, unrelated RobotMapPanel test fixture that predates this field
// does not need to change as a side effect of adding it; the real backend
// response always includes the key.
export interface RobotArea {
  vendorAreaId: string | null;
  displayName: string | null;
  sakarAreaId?: string | null;
}

// backend/.../map/RobotMapResponse.java — vendorMapId/name/width/height/
// mapMd5/updatedAt only, mirroring the backend DTO exactly. Deliberately no
// image URL/path field here: the backend response never includes one (see
// RobotMapResponse's own Javadoc) — the PNG itself is fetched separately via
// getRobotMapImage, never a client-constructed path.
export interface RobotMapMetadata {
  vendorMapId: string | null;
  name: string | null;
  width: number | null;
  height: number | null;
  mapMd5: string | null;
  updatedAt: string;
}

// backend/.../integration/keenon/dto/SceneConfigResponse.java — Sakar-owned
// per-robot Keenon sceneCode configuration, never a live vendor read (see
// KeenonMapMetadataSyncService's backend Javadoc for why no Keenon endpoint
// can supply this for C-series robots on this account).
export interface SceneConfig {
  sceneCode: string;
  sceneName: string | null;
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

// backend/.../iam/UserType.java — Sakar-internal staff vs. customer/external
// account, independent of organizationId nullability.
export type UserType = 'INTERNAL' | 'EXTERNAL';

// backend/.../iam/Department.java — a flat, Sakar-wide list used only to
// group INTERNAL users, never organization-scoped.
export interface Department {
  id: string;
  name: string;
  createdAt: string;
  updatedAt: string;
}

// backend/.../iam/dto/UserResponse.java — organizationId is null only for a
// SUPER_ADMIN-scoped, cross-organization Sakar staff account. departmentId
// is only meaningful for userType === 'INTERNAL'; departmentName is
// resolved server-side (null when departmentId is null).
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
  userType: UserType;
  departmentId: string | null;
  departmentName: string | null;
}

// backend/.../iam/dto/RoleResponse.java — the role NAME is fixed reference
// data (V9__seed_rbac.sql) and never editable; description/permissions can
// now be edited via PUT /api/v1/roles/{id} (Account Permission Platform,
// Phase 1) — see api/roles.ts's updateRole.
export interface RoleWithPermissions {
  id: string;
  name: RoleNameLike;
  description: string | null;
  permissions: string[];
}

// backend/.../iam/dto/PermissionResponse.java — the live source for the
// role-permission editor; types/permissions.ts's PERMISSION_CODES remains
// the hardcoded mirror used for UI-gating elsewhere in the app.
export interface PermissionCatalogEntry {
  id: string;
  code: string;
  description: string | null;
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

// backend/.../resourceconfig/{ResourceScene,SceneStatus}.java — New Resource
// Configuration → Scene list (Robot Management sidebar group). siteId
// ("store") and robotId are both optional bindings, reusing the existing
// Site/Robot entities — no separate "Store" concept was invented.
export type SceneStatus = 'DRAFT' | 'PUBLISHED';

export interface ResourceScene {
  id: string;
  organizationId: string;
  siteId: string | null;
  robotId: string | null;
  name: string;
  resourcePackType: string;
  status: SceneStatus;
  createdAt: string;
  updatedAt: string;
}

// backend/.../resourceconfig/MarketingMaterial.java — New Resource
// Configuration → Marketing materials. Named material packages only — no
// file/asset storage in this pass (see the backend controller's own
// Javadoc for the scope boundary).
export interface MarketingMaterial {
  id: string;
  organizationId: string;
  name: string;
  materialType: string;
  status: SceneStatus;
  createdAt: string;
  updatedAt: string;
}

// backend/.../ota/SoftwareVersion.java — OTA Management → System Version
// Management (governance doc Module B, approved for a real build).
export interface SoftwareVersion {
  id: string;
  organizationId: string;
  packageName: string;
  wholeMachineSoftware: string | null;
  packageVersion: string;
  hardwareVersion: string | null;
  grayscale: boolean;
  sizeBytes: number | null;
  createdBy: string | null;
  notes: string | null;
  createdAt: string;
  updatedAt: string;
}

// backend/.../ota/DeploymentStatus.java — RECORDED means an operator logged
// the push, never that a robot actually received/applied it (no OTA
// delivery channel exists). FAILED is for an operator-recorded failure.
export type DeploymentStatus = 'RECORDED' | 'FAILED';

// backend/.../ota/DeploymentRecord.java — OTA Management → Update record.
// Append-only.
export interface DeploymentRecord {
  id: number;
  organizationId: string;
  robotId: string;
  softwareVersionId: string;
  oldVersionNumber: string | null;
  newVersionNumber: string;
  status: DeploymentStatus;
  errorMessage: string | null;
  grayscale: boolean;
  createdAt: string;
}

// backend/.../repair/RepairRequest.java — Operation And Maintenance
// Platform → Customer Repair Requests. "Associated Reseller" is resolved
// from organizationId by the caller, same convention as Site's own
// "affiliated agent".
export type RepairRequestStatus = 'OPEN' | 'IN_PROGRESS' | 'RESOLVED' | 'CLOSED';

export interface RepairRequest {
  id: string;
  organizationId: string;
  siteId: string | null;
  robotId: string | null;
  workOrderNumber: string;
  symptom: string;
  status: RepairRequestStatus;
  reportedBy: string | null;
  reportedAt: string;
  resolvedAt: string | null;
  notes: string | null;
  createdAt: string;
  updatedAt: string;
}

// backend/.../remotedeployment/RemoteDeploymentRecord.java — Operation And
// Maintenance Platform → Remote Deployment. RECORDED/COMPLETED/FAILED are
// only ever set by the operator's own action — see the backend enum's own
// Javadoc for why this never claims a real remote-push confirmation.
export type RemoteDeploymentRecordStatus = 'RECORDED' | 'COMPLETED' | 'FAILED';

export interface RemoteDeploymentRecord {
  id: string;
  organizationId: string;
  siteId: string | null;
  robotId: string;
  deployedBy: string | null;
  status: RemoteDeploymentRecordStatus;
  notes: string | null;
  completedAt: string | null;
  createdAt: string;
  updatedAt: string;
}

// backend/.../iot/ElevatorDevice.java — IoT Platform → Elevator Module →
// Elevator management. There is deliberately no "online status" field: no
// elevator vendor telemetry channel exists in this codebase — render an
// honest "Unknown", never a fabricated live status.
export interface ElevatorDevice {
  id: string;
  organizationId: string;
  siteId: string;
  deviceId: string;
  deviceName: string | null;
  building: string | null;
  protocol: string | null;
  networkingMode: string | null;
  communicationMode: string | null;
  createdAt: string;
  updatedAt: string;
}

// backend/.../iot/ElevatorConfiguration.java — binds an ElevatorDevice to a
// Robot for a Site ("Elevator configuration").
export interface ElevatorConfiguration {
  id: string;
  organizationId: string;
  siteId: string;
  elevatorDeviceId: string;
  robotId: string;
  name: string;
  notes: string | null;
  modifiedBy: string | null;
  createdAt: string;
  updatedAt: string;
}

// backend/.../iot/ElevatorConfigurationEvent.java — "set record", an
// AppendOnlyEntity with a Long id, so this is a JSON number, not a UUID.
export interface ElevatorConfigurationEvent {
  id: number;
  elevatorConfigurationId: string;
  eventType: string;
  detail: string | null;
  createdAt: string;
}

// backend/.../iot/ElevatorDeliveryStatus.java — RECORDED means an operator
// logged the delivery, never a real elevator-controller confirmation.
export type ElevatorDeliveryStatus = 'RECORDED' | 'FAILED';

// backend/.../iot/ElevatorConfigurationDelivery.java — "The elevator
// configuration is delivered". Append-only.
export interface ElevatorConfigurationDelivery {
  id: number;
  organizationId: string;
  elevatorConfigurationId: string;
  robotId: string;
  deliveredBy: string | null;
  status: ElevatorDeliveryStatus;
  createdAt: string;
}

// backend/.../iot/LadderControlStoreBinding.java — Cloud ladder control
// configuration → Store binding. One binding per Site.
export interface LadderControlStoreBinding {
  id: string;
  organizationId: string;
  siteId: string;
  manufacturer: string;
  buildingId: string | null;
  clientId: string | null;
  createdAt: string;
  updatedAt: string;
}

// backend/.../iot/PhoneDevice.java — IoT Platform → Phone Module → Device
// management. Same "no fabricated online status" convention as ElevatorDevice.
export interface PhoneDevice {
  id: string;
  organizationId: string;
  siteId: string;
  deviceId: string;
  deviceName: string | null;
  networkingMode: string | null;
  createdAt: string;
  updatedAt: string;
}

// backend/.../openplatform/OpenPlatformRegistrationStatus.java — never a
// fabricated "pass": a registration starts PENDING and only ever moves via
// an explicit ROLE_MANAGE-gated review action.
export type OpenPlatformRegistrationStatus = 'PENDING' | 'APPROVED' | 'REJECTED';

// backend/.../openplatform/dto/OpenPlatformRegistrationResponse.java — Open
// Platform → Customer registration. One per organization.
export interface OpenPlatformRegistration {
  id: string;
  organizationId: string;
  companyName: string;
  area: string | null;
  companyAddress: string | null;
  systemMatcher: string | null;
  contactInformation: string | null;
  dockingRequirements: string | null;
  status: OpenPlatformRegistrationStatus;
  submittedBy: string | null;
  reviewedBy: string | null;
  reviewedAt: string | null;
  createdAt: string;
  updatedAt: string;
}

// backend/.../openplatform/dto/OpenPlatformApplicationResponse.java — Open
// Platform → Application management. `secretKeyMasked` is the only trace of
// the secret key ever shown again after creation — see api/openPlatform.ts's
// own comment for why.
export interface OpenPlatformApplication {
  id: string;
  organizationId: string;
  appId: string;
  applicationName: string;
  businessType: string | null;
  accessKey: string;
  secretKeyMasked: string;
  createdBy: string | null;
  createdAt: string;
  updatedAt: string;
}

// backend/.../dashboard/dto/RankingEntry.java
export interface RankingEntry {
  id: string;
  label: string;
  count: number;
}

// backend/.../dashboard/dto/OperationRankingResponse.java — Operational
// Dashboard → Operation Ranking. totalMileage/totalCalls (and their ranking
// lists) are always null — no distance/odometer or call/summon concept
// exists anywhere in this codebase, so this never reports a fabricated
// number for them. Render "Not tracked", never "0".
export interface OperationRankingResponse {
  totalTasks: number;
  totalMileage: number | null;
  totalCalls: number | null;
  storeRankingsByTasks: RankingEntry[];
  robotRankingsByTasks: RankingEntry[];
  storeRankingsByMileage: RankingEntry[] | null;
  robotRankingsByMileage: RankingEntry[] | null;
}

// backend/.../dashboard/dto/TaskTypeShare.java
export interface TaskTypeShare {
  taskType: string;
  count: number;
  percentage: number;
}

// backend/.../dashboard/dto/StoreRealtimeStatsResponse.java — Operational
// Dashboard → Store Real-Time Data Statistics. Same null-means-not-tracked
// convention as OperationRankingResponse.
export interface StoreRealtimeStatsResponse {
  tasksToday: number;
  taskModeProportionToday: TaskTypeShare[];
  callsToday: number | null;
  activeMachines: number;
  mileageToday: number | null;
  averageSpeedMetersPerSecond: number | null;
}

// backend/.../dashboard/dto/RetentionRow.java — one day's real, computed
// retention snapshot (never simulated) for Use Retention Analytics.
export interface RetentionRow {
  date: string;
  used3: number;
  used7: number;
  used15: number;
  unused3: number;
  unused7: number;
  unused15: number;
}

// backend/.../dashboard/dto/DailyTaskCount.java
export interface DailyTaskCount {
  date: string;
  count: number;
}

// backend/.../dashboard/dto/HotelTaskRecordResponse.java — Operational
// Dashboard → Hotel Task Record; also reused by the main Dashboard's
// "Seven-day Overview" / "Task Data Details" cards. Same
// null-means-not-tracked convention.
export interface HotelTaskRecordResponse {
  totalVolumeOfTask: number;
  cumulativeMileage: number | null;
  cumulativeDurationSeconds: number;
  numberOfRooms: number | null;
  dailyBreakdown: DailyTaskCount[];
  taskTypeBreakdown: TaskTypeShare[];
}

// backend/.../command/dto/CommandResultResponse.java — one append-only
// command_results lifecycle-history row. `result` is always a CommandStatus
// name (never fabricated); `detail`/`durationMs` are nullable, exactly as
// the backend record models them.
export interface CommandResult {
  commandId: string;
  result: string;
  detail: string | null;
  durationMs: number | null;
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
