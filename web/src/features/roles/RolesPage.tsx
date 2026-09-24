import { useState, type FormEvent } from 'react';
import { listRoles, updateRole } from '../../api/roles';
import { listPermissions } from '../../api/permissions';
import { useApi } from '../../hooks/useApi';
import { usePermissions } from '../../hooks/usePermissions';
import { useToast } from '../../components/ui/Toast';
import { ApiRequestError } from '../../api/client';
import { PageHeader } from '../../components/ui/PageHeader';
import { Card } from '../../components/ui/Card';
import { DataTable } from '../../components/ui/DataTable';
import { Modal } from '../../components/ui/Modal';
import { LoadingState, ErrorState } from '../../components/ui/States';
import type { RoleWithPermissions } from '../../types/domain';
import type { PermissionCode } from '../../types/permissions';

function EditRoleModal({ role, onClose, onSaved }: { role: RoleWithPermissions; onClose: () => void; onSaved: () => void }) {
  const toast = useToast();
  // Only fetches once the modal is actually being opened for a specific role
  // — not on every RolesPage view — because this component is now mounted
  // only while editing (see RolesPage's `editing &&` render guard below).
  // Previously it mounted unconditionally and fetched on page load; that
  // stray, easy-to-forget-to-mock request in tests could outlive its own
  // test and resolve later with a real 401 against the dev backend, firing
  // a global "session expired" event that corrupted an unrelated, later
  // test's auth state — a real bug this restructuring removes outright,
  // not just a test-mocking gap.
  const { data: catalog } = useApi(() => listPermissions(), []);
  const [description, setDescription] = useState(role.description ?? '');
  const [selected, setSelected] = useState<Set<string>>(new Set(role.permissions));
  const [submitting, setSubmitting] = useState(false);

  function toggle(code: string) {
    setSelected((prev) => {
      const next = new Set(prev);
      if (next.has(code)) next.delete(code);
      else next.add(code);
      return next;
    });
  }

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setSubmitting(true);
    try {
      // The checkbox list is sourced from the live GET /permissions catalog (real backend
      // enum values), so this is a safe assertion, not an unchecked cast of arbitrary input.
      await updateRole(role.id, description || null, Array.from(selected) as PermissionCode[]);
      toast.show(`${role.name} updated`, 'success');
      onSaved();
      onClose();
    } catch (err) {
      toast.show(err instanceof ApiRequestError ? err.message : 'Failed to update role', 'error');
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <Modal open title={`Edit ${role.name}`} onClose={onClose}>
      <form onSubmit={handleSubmit}>
        <div className="sakar-field">
          <label htmlFor="role-description">Description</label>
          <input id="role-description" value={description} onChange={(e) => setDescription(e.target.value)} />
        </div>
        <div className="sakar-field">
          <label>Permissions</label>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 4, maxHeight: 260, overflowY: 'auto' }}>
            {(catalog ?? []).map((p) => (
              <label key={p.id} style={{ display: 'flex', alignItems: 'center', gap: 8, fontWeight: 400 }}>
                <input type="checkbox" checked={selected.has(p.code)} onChange={() => toggle(p.code)} />
                <span className="sakar-mono">{p.code}</span>
                <span style={{ color: 'var(--sakar-text-faint)', fontSize: 12 }}>{p.description}</span>
              </label>
            ))}
          </div>
        </div>
        <button type="submit" className="sakar-btn sakar-btn--primary" disabled={submitting}>
          {submitting ? 'Saving…' : 'Save changes'}
        </button>
      </form>
    </Modal>
  );
}

// Real role → permission mapping (GET /api/v1/roles). The role NAME is
// fixed reference data (backend V9__seed_rbac.sql) and never editable — see
// docs on UpdateRoleRequest — but an existing role's description/permission
// set can be edited (PUT), gated on the stronger ROLE_MANAGE (Account
// Permission Platform, Phase 1).
export function RolesPage() {
  const { hasPermission } = usePermissions();
  const { data: roles, status, error, refetch } = useApi(() => listRoles(), []);
  const [editing, setEditing] = useState<RoleWithPermissions | null>(null);
  const canManage = hasPermission('ROLE_MANAGE');

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
      <PageHeader title="Roles" subtitle="Fixed role names, as seeded on the backend — descriptions and permissions can be edited." />
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
            ...(canManage
              ? [
                  {
                    key: 'actions',
                    header: 'Actions',
                    render: (r: RoleWithPermissions) => (
                      <button type="button" className="sakar-btn sakar-btn--secondary" onClick={() => setEditing(r)}>Edit</button>
                    ),
                  },
                ]
              : []),
          ]}
        />
      </Card>

      {canManage && editing && <EditRoleModal role={editing} onClose={() => setEditing(null)} onSaved={refetch} />}
    </div>
  );
}
