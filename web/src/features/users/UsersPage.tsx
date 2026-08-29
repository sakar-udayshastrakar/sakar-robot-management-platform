import { useMemo } from 'react';
import { generateUsers } from '../../mocks/simulated';
import { Card } from '../../components/ui/Card';
import { DataTable } from '../../components/ui/DataTable';
import { Badge } from '../../components/ui/Badge';
import { SimulatedDataBanner } from '../../components/ui/SimulatedDataBanner';

export function UsersPage() {
  const users = useMemo(() => generateUsers(10), []);

  return (
    <div>
      <div className="sakar-page-header">
        <div>
          <h1 className="sakar-page-title">Users</h1>
          <p className="sakar-page-subtitle">User management — preview only.</p>
        </div>
      </div>
      <SimulatedDataBanner label="User is a persisted entity but there is no /users REST controller — create/edit/deactivate are not wired to anything real" />
      <Card title="Users">
        <DataTable
          rows={users}
          rowKey={(u) => u.id}
          columns={[
            { key: 'name', header: 'Name', render: (u) => u.fullName },
            { key: 'email', header: 'Email', render: (u) => u.email },
            { key: 'role', header: 'Role', render: (u) => u.role },
            { key: 'active', header: 'Status', render: (u) => <Badge tone={u.active ? 'success' : 'neutral'}>{u.active ? 'Active' : 'Inactive'}</Badge> },
          ]}
        />
      </Card>
    </div>
  );
}
