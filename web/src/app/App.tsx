import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom';
import { AuthProvider } from '../features/auth/AuthContext';
import { ProtectedRoute } from '../features/auth/ProtectedRoute';
import { LoginPage } from '../features/auth/LoginPage';
import { AppShell } from '../components/layout/AppShell';
import { DashboardPage } from '../features/dashboard/DashboardPage';
import { OrganizationsPage } from '../features/organizations/OrganizationsPage';
import { OrganizationDetailPage } from '../features/organizations/OrganizationDetailPage';
import { SitesPage } from '../features/sites/SitesPage';
import { RobotsListPage } from '../features/robots/RobotsListPage';
import { RobotDetailPage } from '../features/robots/RobotDetailPage';
import { TelemetryPage } from '../features/telemetry/TelemetryPage';
import { EventsPage } from '../features/events/EventsPage';
import { ErrorsPage } from '../features/errors/ErrorsPage';
import { AlertsPage } from '../features/alerts/AlertsPage';
import { TasksPage } from '../features/tasks/TasksPage';
import { LogsPage } from '../features/logs/LogsPage';
import { UsersPage } from '../features/users/UsersPage';
import { RolesPage } from '../features/roles/RolesPage';
import { AuditLogPage } from '../features/audit/AuditLogPage';
import { SettingsPage } from '../features/settings/SettingsPage';
import { PlannedFeaturePage } from '../features/placeholder/PlannedFeaturePage';
import { ForbiddenPage, NotFoundPage } from '../features/misc/StatusPages';

export function App() {
  return (
    <BrowserRouter>
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
            <Route path="/robots/:id" element={<RobotDetailPage />} />

            <Route path="/tasks" element={<TasksPage />} />
            <Route
              path="/cleaning"
              element={
                <PlannedFeaturePage
                  title="Cleaning"
                  subtitle="Scheduled/immediate cleaning task control."
                  reason="No cleaning API exists on the backend (POST /robots/{id}/cleaning/* is unbuilt). This page intentionally does not simulate task execution."
                />
              }
            />
            <Route path="/telemetry" element={<TelemetryPage />} />
            <Route path="/alerts" element={<AlertsPage />} />
            <Route path="/events" element={<EventsPage />} />
            <Route path="/errors" element={<ErrorsPage />} />

            <Route path="/logs" element={<LogsPage />} />
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

            <Route
              path="/users"
              element={
                <ProtectedRoute requirePermission="USER_MANAGE">
                  <UsersPage />
                </ProtectedRoute>
              }
            />
            <Route
              path="/roles"
              element={
                <ProtectedRoute requirePermission="ROLE_MANAGE">
                  <RolesPage />
                </ProtectedRoute>
              }
            />
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
    </BrowserRouter>
  );
}
