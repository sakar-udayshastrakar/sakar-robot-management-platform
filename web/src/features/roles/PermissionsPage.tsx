import { PERMISSION_CODES } from '../../types/permissions';
import { PageHeader } from '../../components/ui/PageHeader';
import { Card } from '../../components/ui/Card';
import { DataTable } from '../../components/ui/DataTable';

// The real, authoritative permission set — verbatim from
// backend/.../iam/PermissionCode.java. No simulated data on this page:
// every row is a real backend enum value with its documented purpose.
const DESCRIPTIONS: Record<string, string> = {
  ROBOT_VIEW: 'View robot identity, status, telemetry, and history',
  ROBOT_CONTROL: 'Issue non-task robot commands',
  ROBOT_TASK_CREATE: 'Create a robot task',
  ROBOT_TASK_CANCEL: 'Cancel/stop/pause an in-progress robot task',
  ROBOT_LOCK: "Remotely lock a robot's motors",
  ROBOT_UNLOCK: "Remotely unlock a robot's motors",
  ROBOT_CONFIGURE: 'Register/configure robots, sites, and organizations',
  ROBOT_DIAGNOSTICS: 'View diagnostic-level robot detail',
  ROBOT_LOG_VIEW: 'View the SRELS operational log timeline',
  AUDIT_VIEW: 'View the security/compliance audit trail',
  USER_MANAGE: 'Create/manage users within scope',
  ROLE_MANAGE: 'Manage roles/permission assignment',
  SYSTEM_ADMIN: 'Platform-level configuration',
};

export function PermissionsPage() {
  return (
    <div>
      <PageHeader title="Permissions" subtitle="The real backend permission set (iam/PermissionCode.java)." />
      <Card title={`Permissions (${PERMISSION_CODES.length})`}>
        <DataTable
          rows={PERMISSION_CODES.map((code) => ({ code }))}
          rowKey={(r) => r.code}
          columns={[
            { key: 'code', header: 'Permission', render: (r) => <span className="sakar-mono">{r.code}</span> },
            { key: 'description', header: 'Description', render: (r) => DESCRIPTIONS[r.code] ?? '—' },
          ]}
        />
      </Card>
    </div>
  );
}
