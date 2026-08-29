import { useMemo } from 'react';
import { generateRoles } from '../../mocks/simulated';
import { Card } from '../../components/ui/Card';
import { DataTable } from '../../components/ui/DataTable';
import { SimulatedDataBanner } from '../../components/ui/SimulatedDataBanner';

// Role/Permission content shown here is the REAL RoleName/PermissionCode
// enum values (backend/.../iam/RoleName.java, PermissionCode.java) — only
// the "which permissions belong to which role" assignment view is
// simulated, since there is no GET /roles endpoint to read it from.
export function RolesPage() {
  const roles = useMemo(() => generateRoles(), []);

  return (
    <div>
      <div className="sakar-page-header">
        <div>
          <h1 className="sakar-page-title">Roles</h1>
          <p className="sakar-page-subtitle">Role → permission mapping — preview only.</p>
        </div>
      </div>
      <SimulatedDataBanner label="No GET /roles endpoint exists — role names and permission codes below are the real backend enum values, but the mapping shown is illustrative" />
      <Card title="Roles">
        <DataTable
          rows={roles}
          rowKey={(r) => r.name}
          columns={[
            { key: 'name', header: 'Role', render: (r) => r.name },
            { key: 'description', header: 'Description', render: (r) => r.description },
            { key: 'permissions', header: 'Example permissions', render: (r) => r.permissions.join(', ') },
          ]}
        />
      </Card>
    </div>
  );
}
