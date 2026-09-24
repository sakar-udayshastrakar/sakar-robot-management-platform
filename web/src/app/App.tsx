import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom';
import { AuthProvider } from '../features/auth/AuthContext';
import { ProtectedRoute } from '../features/auth/ProtectedRoute';
import { LoginPage } from '../features/auth/LoginPage';
import { AppShell } from '../components/layout/AppShell';
import { ToastProvider } from '../components/ui/Toast';
import { DashboardPage } from '../features/dashboard/DashboardPage';
import { FleetMapPage } from '../features/fleet/FleetMapPage';
import { OrganizationsPage } from '../features/organizations/OrganizationsPage';
import { OrganizationDetailPage } from '../features/organizations/OrganizationDetailPage';
import { SitesPage } from '../features/sites/SitesPage';
import { RobotsListPage } from '../features/robots/RobotsListPage';
import { RobotDetailPage } from '../features/robots/RobotDetailPage';
import { RobotInventoryPage } from '../features/robots/RobotInventoryPage';
import { ResourceScenesPage } from '../features/resourceConfig/ResourceScenesPage';
import { MarketingMaterialsPage } from '../features/resourceConfig/MarketingMaterialsPage';
import { StoreManagementPage } from '../features/stores/StoreManagementPage';
import { SystemVersionManagementPage } from '../features/ota/SystemVersionManagementPage';
import { UpdateRecordPage } from '../features/ota/UpdateRecordPage';
import { MissionLogPage } from '../features/operations/MissionLogPage';
import { CustomerRepairRequestsPage } from '../features/operations/CustomerRepairRequestsPage';
import { DeviceManagementPage } from '../features/operations/DeviceManagementPage';
import { RemoteDeploymentPage } from '../features/operations/RemoteDeploymentPage';
import { ElevatorManagementPage } from '../features/iot/ElevatorManagementPage';
import { ElevatorConfigurationPage } from '../features/iot/ElevatorConfigurationPage';
import { CloudLadderControlPage } from '../features/iot/CloudLadderControlPage';
import { PhoneDeviceManagementPage } from '../features/iot/PhoneDeviceManagementPage';
import { CustomerRegistrationPage } from '../features/openPlatform/CustomerRegistrationPage';
import { ApplicationManagementPage } from '../features/openPlatform/ApplicationManagementPage';
import { FileDownloadPage } from '../features/openPlatform/FileDownloadPage';
import { OperationalDashboardHomePage } from '../features/operationalDashboard/OperationalDashboardHomePage';
import { OperationRankingPage } from '../features/operationalDashboard/OperationRankingPage';
import { StoreRealtimeStatsPage } from '../features/operationalDashboard/StoreRealtimeStatsPage';
import { RetentionAnalyticsPage } from '../features/operationalDashboard/RetentionAnalyticsPage';
import { HotelTaskRecordPage } from '../features/operationalDashboard/HotelTaskRecordPage';
import { TelemetryPage } from '../features/telemetry/TelemetryPage';
import { EventsPage } from '../features/events/EventsPage';
import { ErrorsPage } from '../features/errors/ErrorsPage';
import { AlertsPage } from '../features/alerts/AlertsPage';
import { TasksPage } from '../features/tasks/TasksPage';
import { CleaningPage } from '../features/cleaning/CleaningPage';
import { LogsPage } from '../features/logs/LogsPage';
import { UsersPage } from '../features/users/UsersPage';
import { RolesPage } from '../features/roles/RolesPage';
import { PermissionsPage } from '../features/roles/PermissionsPage';
import { AuditLogPage } from '../features/audit/AuditLogPage';
import { SettingsPage } from '../features/settings/SettingsPage';
import { PlannedFeaturePage } from '../features/placeholder/PlannedFeaturePage';
import { ForbiddenPage, NotFoundPage } from '../features/misc/StatusPages';

// Learning Center Platform (matching the reference product's full sidebar
// structure, at the user's explicit request) has no Sakar backend behind it
// yet — it is an honest placeholder, not fabricated data, same convention as
// the pre-existing Analytics route above. Building it out for real is
// separately scoped, later work.
const PLACEHOLDER_ROUTES: { path: string; title: string; subtitle: string }[] = [
  { path: '/resource-configuration/language', title: 'Language configuration management', subtitle: 'Per-business-type translation templates and configured language files.' },
  { path: '/operations/remote-log', title: 'Remote log', subtitle: 'On-demand remote log capture from a robot.' },
  { path: '/learning-center', title: 'Learning Center Platform', subtitle: 'Product documentation and training materials.' },
];

export function App() {
  return (
    <BrowserRouter>
      <ToastProvider>
      <AuthProvider>
        <Routes>
          <Route path="/login" element={<LoginPage />} />
          <Route path="/forbidden" element={<ForbiddenPage />} />

          <Route
            element={
              <ProtectedRoute>
                <AppShell />
              </ProtectedRoute>
            }
          >
            <Route path="/" element={<Navigate to="/dashboard" replace />} />
            <Route path="/dashboard" element={<DashboardPage />} />

            <Route path="/organizations" element={<OrganizationsPage />} />
            <Route path="/organizations/:id" element={<OrganizationDetailPage />} />
            <Route path="/sites" element={<SitesPage />} />

            <Route path="/robots" element={<RobotsListPage />} />
            <Route path="/robots/inventory" element={<RobotInventoryPage scope="ALL" />} />
            <Route path="/robots/sub-agent-inventory" element={<RobotInventoryPage scope="SUB_AGENT" />} />
            <Route
              path="/robots/running-statistics"
              element={
                <PlannedFeaturePage
                  title="Running Statistics"
                  subtitle="Fleet-wide runtime/utilization statistics."
                  reason="No fleet-wide run-statistics aggregation endpoint exists on the backend — aggregating simulated numbers here would misrepresent fleet performance, so this page is left as a placeholder."
                />
              }
            />
            <Route
              path="/robots/running-record"
              element={
                <PlannedFeaturePage
                  title="Running Record"
                  subtitle="Historical run-by-run record across the fleet."
                  reason="No fleet-wide run-history endpoint exists on the backend (per-robot task/cleaning history exists on the Robot Detail page, but no combined fleet-wide record) — this page is left as a placeholder rather than fabricating one."
                />
              }
            />
            {/* Must come after the more specific /robots/* routes above — React Router
                matches in declaration order and :id would otherwise swallow them. */}
            <Route path="/robots/:id" element={<RobotDetailPage />} />
            <Route path="/fleet" element={<FleetMapPage />} />

            <Route path="/resource-configuration" element={<ResourceScenesPage />} />
            <Route path="/resource-configuration/marketing-materials" element={<MarketingMaterialsPage />} />
            <Route path="/stores" element={<StoreManagementPage />} />

            <Route path="/ota/versions" element={<SystemVersionManagementPage />} />
            <Route path="/ota/records" element={<UpdateRecordPage />} />

            <Route path="/operations/mission-log" element={<MissionLogPage />} />
            <Route path="/operations/repair-requests" element={<CustomerRepairRequestsPage />} />
            <Route path="/operations/device-management" element={<DeviceManagementPage />} />
            <Route path="/operations/remote-deployment" element={<RemoteDeploymentPage />} />

            <Route path="/iot/elevator-management" element={<ElevatorManagementPage />} />
            <Route path="/iot/elevator-configuration" element={<ElevatorConfigurationPage />} />
            <Route path="/iot/cloud-ladder-control" element={<CloudLadderControlPage />} />
            <Route path="/iot/phone-device-management" element={<PhoneDeviceManagementPage />} />

            <Route path="/open-platform/customer-registration" element={<CustomerRegistrationPage />} />
            <Route path="/open-platform/applications" element={<ApplicationManagementPage />} />
            <Route path="/open-platform/file-download" element={<FileDownloadPage />} />

            <Route path="/operational-dashboard" element={<OperationalDashboardHomePage />} />
            <Route path="/operational-dashboard/operation-ranking" element={<OperationRankingPage />} />
            <Route path="/operational-dashboard/store-realtime" element={<StoreRealtimeStatsPage />} />
            <Route path="/operational-dashboard/retention" element={<RetentionAnalyticsPage />} />
            <Route path="/operational-dashboard/hotel-task-record" element={<HotelTaskRecordPage />} />

            <Route path="/tasks" element={<TasksPage />} />
            <Route path="/cleaning" element={<CleaningPage />} />
            <Route
              path="/telemetry"
              element={
                <ProtectedRoute requirePermission="ROBOT_LOG_VIEW">
                  <TelemetryPage />
                </ProtectedRoute>
              }
            />
            <Route path="/alerts" element={<AlertsPage />} />
            <Route
              path="/events"
              element={
                <ProtectedRoute requirePermission="ROBOT_LOG_VIEW">
                  <EventsPage />
                </ProtectedRoute>
              }
            />
            <Route
              path="/errors"
              element={
                <ProtectedRoute requirePermission="ROBOT_LOG_VIEW">
                  <ErrorsPage />
                </ProtectedRoute>
              }
            />

            <Route
              path="/logs"
              element={
                <ProtectedRoute requirePermission="ROBOT_LOG_VIEW">
                  <LogsPage />
                </ProtectedRoute>
              }
            />
            <Route
              path="/analytics"
              element={
                <PlannedFeaturePage
                  title="Analytics"
                  subtitle="Utilization, error frequency, and downtime aggregation."
                  reason="No analytics API exists on the backend (GET /analytics/* is unbuilt) — aggregating simulated numbers here would misrepresent fleet performance, so this page is left as a placeholder."
                />
              }
            />

            {/* /users redirects to /external — same UsersPage component, split by userType
                (Account Permission Platform, Phase 1) — see navConfig.ts's grouped nav entry. */}
            <Route path="/users" element={<Navigate to="/users/external" replace />} />
            <Route
              path="/users/internal"
              element={
                <ProtectedRoute requirePermission="USER_MANAGE">
                  <UsersPage scope="INTERNAL" />
                </ProtectedRoute>
              }
            />
            <Route
              path="/users/external"
              element={
                <ProtectedRoute requirePermission="USER_MANAGE">
                  <UsersPage scope="EXTERNAL" />
                </ProtectedRoute>
              }
            />
            <Route
              path="/roles"
              element={
                // Matches the backend's real gate: RoleController requires
                // USER_MANAGE (its only consumer today is the user-role
                // picker), not ROLE_MANAGE, which the RBAC seed only grants
                // to SUPER_ADMIN — gating this route on ROLE_MANAGE would
                // block ORG_ADMIN from a page the backend actually lets
                // them load.
                <ProtectedRoute requirePermission="USER_MANAGE">
                  <RolesPage />
                </ProtectedRoute>
              }
            />
            <Route
              path="/permissions"
              element={
                <ProtectedRoute requirePermission="ROLE_MANAGE">
                  <PermissionsPage />
                </ProtectedRoute>
              }
            />
            {PLACEHOLDER_ROUTES.map(({ path, title, subtitle }) => (
              <Route
                key={path}
                path={path}
                element={
                  <PlannedFeaturePage
                    title={title}
                    subtitle={subtitle}
                    reason="No Sakar backend exists for this section yet — this page is a structural placeholder, not fabricated data. See the Robot Management / Account Permissions sections for the parts of this platform that are real today."
                  />
                }
              />
            ))}

            <Route
              path="/audit"
              element={
                <ProtectedRoute requirePermission="AUDIT_VIEW">
                  <AuditLogPage />
                </ProtectedRoute>
              }
            />
            <Route path="/settings" element={<SettingsPage />} />
          </Route>

          <Route path="*" element={<NotFoundPage />} />
        </Routes>
      </AuthProvider>
      </ToastProvider>
    </BrowserRouter>
  );
}
