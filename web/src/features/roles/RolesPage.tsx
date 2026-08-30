import { listRoles } from '../../api/roles';
import { useApi } from '../../hooks/useApi';
import { PageHeader } from '../../components/ui/PageHeader';
import { Card } from '../../components/ui/Card';
import { DataTable } from '../../components/ui/DataTable';
import { LoadingState, ErrorState } from '../../components/ui/States';

// Real, read-only role → permission mapping (GET /api/v1/roles). Roles and
// permissions are fixed reference data (backend V9__seed_rbac.sql) — there
// is no create/edit endpoint, and none is invented here.
export function RolesPage() {
  const { data: roles, status, error, refetch } = useApi(() => listRoles(), []);

  if (status === 'loading' || status === 'idle') {
    return <LoadingState title="Loading roles…" />;
  }
  if (status === 'error') {
    return (
      <ErrorState
        title="Could not load roles"
        detail={error ?? undefined}
        action={<button type="button" className="sakar-btn sakar-btn--secondary" onClick={refetch}>Retry</button>}
      />
    );
  }

  const rows = roles ?? [];

  return (
    <div>
      <PageHeader title="Roles" subtitle="Fixed role → permission mapping, as seeded on the backend." />
      <Card title={`Roles (${rows.length})`}>
        <DataTable
          rows={rows}
          rowKey={(r) => r.id}
          emptyTitle="No roles found"
          columns={[
            { key: 'name', header: 'Role', render: (r) => <span className="sakar-mono">{r.name}</span> },
            { key: 'description', header: 'Description', render: (r) => r.description ?? '—' },
            {
              key: 'permissions',
              header: 'Permissions',
              render: (r) => (r.permissions.length ? r.permissions.join(', ') : 'None'),
            },
          ]}
        />
      </Card>
    </div>
  );
}
