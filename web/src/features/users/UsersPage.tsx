import { useState, type FormEvent } from 'react';
import { activateUser, changeUserRole, createUser, listUsers, suspendUser, updateUser } from '../../api/users';
import { createDepartment, deleteDepartment, listDepartments, renameDepartment } from '../../api/departments';
import { useApi } from '../../hooks/useApi';
import { useAuth } from '../../features/auth/AuthContext';
import { useToast } from '../../components/ui/Toast';
import { ApiRequestError } from '../../api/client';
import { ROLE_NAMES, type RoleName } from '../../types/permissions';
import { PageHeader } from '../../components/ui/PageHeader';
import { Card } from '../../components/ui/Card';
import { DataTable } from '../../components/ui/DataTable';
import { Badge } from '../../components/ui/Badge';
import { SearchBar } from '../../components/ui/SearchBar';
import { FilterBar } from '../../components/ui/FilterBar';
import { Pagination } from '../../components/ui/Pagination';
import { Modal } from '../../components/ui/Modal';
import { ConfirmDialog } from '../../components/ui/ConfirmDialog';
import { LoadingState, ErrorState } from '../../components/ui/States';
import type { Department, PlatformUser, UserType } from '../../types/domain';

interface CreateUserModalProps {
  open: boolean;
  scope: UserType;
  departments: Department[];
  onClose: () => void;
  onCreated: () => void;
}

function CreateUserModal({ open, scope, departments, onClose, onCreated }: CreateUserModalProps) {
  const { user } = useAuth();
  const toast = useToast();
  const [organizationId, setOrganizationId] = useState(user?.organizationId ?? '');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [fullName, setFullName] = useState('');
  const [roleName, setRoleName] = useState<RoleName>('VIEWER');
  const [departmentId, setDepartmentId] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      await createUser({
        organizationId: organizationId || null,
        email,
        password,
        fullName,
        roleName,
        userType: scope,
        departmentId: scope === 'INTERNAL' ? departmentId || null : null,
      });
      toast.show('User created', 'success');
      setEmail('');
      setPassword('');
      setFullName('');
      setDepartmentId('');
      onCreated();
      onClose();
    } catch (err) {
      setError(err instanceof ApiRequestError ? err.message : 'Failed to create user');
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <Modal open={open} title={scope === 'INTERNAL' ? 'Create Internal User' : 'Create External User'} onClose={onClose}>
      <form onSubmit={handleSubmit}>
        <div className="sakar-field">
          <label htmlFor="user-org">Organization ID {user?.role !== 'SUPER_ADMIN' && '(your organization)'}</label>
          <input
            id="user-org"
            value={organizationId}
            onChange={(e) => setOrganizationId(e.target.value)}
            disabled={user?.role !== 'SUPER_ADMIN'}
            placeholder={user?.role === 'SUPER_ADMIN' ? 'Leave blank for cross-organization staff' : undefined}
          />
        </div>
        <div className="sakar-field">
          <label htmlFor="user-email">Email</label>
          <input id="user-email" type="email" required value={email} onChange={(e) => setEmail(e.target.value)} />
        </div>
        <div className="sakar-field">
          <label htmlFor="user-password">Password</label>
          <input id="user-password" type="password" required minLength={12} value={password} onChange={(e) => setPassword(e.target.value)} />
        </div>
        <div className="sakar-field">
          <label htmlFor="user-name">Full name</label>
          <input id="user-name" required value={fullName} onChange={(e) => setFullName(e.target.value)} />
        </div>
        <div className="sakar-field">
          <label htmlFor="user-role">Role</label>
          <select id="user-role" value={roleName} onChange={(e) => setRoleName(e.target.value as RoleName)}>
            {ROLE_NAMES.map((r) => <option key={r} value={r}>{r}</option>)}
          </select>
        </div>
        {scope === 'INTERNAL' && (
          <div className="sakar-field">
            <label htmlFor="user-department">Department</label>
            <select id="user-department" value={departmentId} onChange={(e) => setDepartmentId(e.target.value)}>
              <option value="">Ungrouped</option>
              {departments.map((d) => <option key={d.id} value={d.id}>{d.name}</option>)}
            </select>
          </div>
        )}
        {error && <div className="sakar-field-error" style={{ marginBottom: 12 }}>{error}</div>}
        <button type="submit" className="sakar-btn sakar-btn--primary" disabled={submitting}>
          {submitting ? 'Creating…' : 'Create user'}
        </button>
      </form>
    </Modal>
  );
}

function RoleSelect({ user, onChanged }: { user: PlatformUser; onChanged: () => void }) {
  const toast = useToast();
  const [busy, setBusy] = useState(false);

  async function handleChange(next: RoleName) {
    setBusy(true);
    try {
      await changeUserRole(user.id, next);
      toast.show('Role updated', 'success');
      onChanged();
    } catch (err) {
      toast.show(err instanceof ApiRequestError ? err.message : 'Failed to update role', 'error');
    } finally {
      setBusy(false);
    }
  }

  return (
    <select value={user.roleName} disabled={busy} onChange={(e) => handleChange(e.target.value as RoleName)} aria-label={`Role for ${user.email}`}>
      {ROLE_NAMES.map((r) => <option key={r} value={r}>{r}</option>)}
    </select>
  );
}

function DepartmentSelect({ user, departments, onChanged }: { user: PlatformUser; departments: Department[]; onChanged: () => void }) {
  const toast = useToast();
  const [busy, setBusy] = useState(false);

  async function handleChange(next: string) {
    setBusy(true);
    try {
      await updateUser(user.id, { fullName: user.fullName, departmentId: next || null });
      toast.show('Department updated', 'success');
      onChanged();
    } catch (err) {
      toast.show(err instanceof ApiRequestError ? err.message : 'Failed to update department', 'error');
    } finally {
      setBusy(false);
    }
  }

  return (
    <select
      value={user.departmentId ?? ''}
      disabled={busy}
      onChange={(e) => handleChange(e.target.value)}
      aria-label={`Department for ${user.email}`}
    >
      <option value="">Ungrouped</option>
      {departments.map((d) => <option key={d.id} value={d.id}>{d.name}</option>)}
    </select>
  );
}

// Managed inline in the Internal Users view, matching the reference
// product's "All departments" list — a flat CRUD sidebar-style card, not a
// separate route, since departments have no other page of their own.
function DepartmentsPanel({ departments, onChanged }: { departments: Department[]; onChanged: () => void }) {
  const toast = useToast();
  const [newName, setNewName] = useState('');
  const [editing, setEditing] = useState<Department | null>(null);
  const [editName, setEditName] = useState('');
  const [pendingDelete, setPendingDelete] = useState<Department | null>(null);
  const [busy, setBusy] = useState(false);

  async function handleCreate(e: FormEvent) {
    e.preventDefault();
    if (!newName.trim()) return;
    setBusy(true);
    try {
      await createDepartment(newName.trim());
      toast.show('Department created', 'success');
      setNewName('');
      onChanged();
    } catch (err) {
      toast.show(err instanceof ApiRequestError ? err.message : 'Failed to create department', 'error');
    } finally {
      setBusy(false);
    }
  }

  async function handleRename(e: FormEvent) {
    e.preventDefault();
    if (!editing || !editName.trim()) return;
    setBusy(true);
    try {
      await renameDepartment(editing.id, editName.trim());
      toast.show('Department renamed', 'success');
      setEditing(null);
      onChanged();
    } catch (err) {
      toast.show(err instanceof ApiRequestError ? err.message : 'Failed to rename department', 'error');
    } finally {
      setBusy(false);
    }
  }

  async function handleDelete() {
    if (!pendingDelete) return;
    setBusy(true);
    try {
      await deleteDepartment(pendingDelete.id);
      toast.show('Department deleted', 'success');
      setPendingDelete(null);
      onChanged();
    } catch (err) {
      toast.show(err instanceof ApiRequestError ? err.message : 'Failed to delete department', 'error');
      setPendingDelete(null);
    } finally {
      setBusy(false);
    }
  }

  return (
    <Card title={`Departments (${departments.length})`}>
      <ul style={{ listStyle: 'none', margin: 0, padding: 0, display: 'flex', flexDirection: 'column', gap: 6 }}>
        {departments.length === 0 && <li style={{ color: 'var(--sakar-text-faint)', fontSize: 13 }}>No departments yet</li>}
        {departments.map((d) => (
          <li key={d.id} style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 8 }}>
            <span>{d.name}</span>
            <span style={{ display: 'flex', gap: 6 }}>
              <button
                type="button"
                className="sakar-link-btn"
                onClick={() => { setEditing(d); setEditName(d.name); }}
              >
                Edit
              </button>
              <button type="button" className="sakar-link-btn" onClick={() => setPendingDelete(d)}>Delete</button>
            </span>
          </li>
        ))}
      </ul>
      <form onSubmit={handleCreate} style={{ display: 'flex', gap: 8, marginTop: 12 }}>
        <input
          value={newName}
          onChange={(e) => setNewName(e.target.value)}
          placeholder="New department name"
          aria-label="New department name"
        />
        <button type="submit" className="sakar-btn sakar-btn--secondary" disabled={busy || !newName.trim()}>New</button>
      </form>

      <Modal open={editing !== null} title="Rename Department" onClose={() => setEditing(null)}>
        <form onSubmit={handleRename}>
          <div className="sakar-field">
            <label htmlFor="dept-rename">Name</label>
            <input id="dept-rename" required value={editName} onChange={(e) => setEditName(e.target.value)} />
          </div>
          <button type="submit" className="sakar-btn sakar-btn--primary" disabled={busy}>Save</button>
        </form>
      </Modal>

      <ConfirmDialog
        open={pendingDelete !== null}
        title="Delete Department"
        message={`Delete "${pendingDelete?.name}"? This is rejected if any user is still assigned to it.`}
        confirmLabel="Delete"
        danger
        busy={busy}
        onConfirm={handleDelete}
        onCancel={() => setPendingDelete(null)}
      />
    </Card>
  );
}

interface UsersPageProps {
  // Account Permission Platform (Phase 1): the same real API/endpoints,
  // split into two routes by userType — see navConfig.ts's grouped nav
  // entry and App.tsx's /users/internal, /users/external routes.
  scope: UserType;
}

// Real user administration (GET/POST/PUT /api/v1/users/*, plus
// /api/v1/departments for the Internal view). Both routes are already
// gated on USER_MANAGE (App.tsx), matching the backend's own gate.
export function UsersPage({ scope }: UsersPageProps) {
  const toast = useToast();
  const [page, setPage] = useState(0);
  const [search, setSearch] = useState('');
  const [showCreate, setShowCreate] = useState(false);
  const [busyId, setBusyId] = useState<string | null>(null);
  const { data, status, error, refetch } = useApi(() => listUsers(page, 25, scope), [page, scope]);
  const { data: departments, refetch: refetchDepartments } = useApi(
    () => (scope === 'INTERNAL' ? listDepartments() : Promise.resolve([])),
    [scope],
  );

  async function handleSuspend(u: PlatformUser) {
    setBusyId(u.id);
    try {
      await suspendUser(u.id);
      toast.show(`${u.email} suspended`, 'success');
      refetch();
    } catch (err) {
      toast.show(err instanceof ApiRequestError ? err.message : 'Failed to suspend user', 'error');
    } finally {
      setBusyId(null);
    }
  }

  async function handleActivate(u: PlatformUser) {
    setBusyId(u.id);
    try {
      await activateUser(u.id);
      toast.show(`${u.email} activated`, 'success');
      refetch();
    } catch (err) {
      toast.show(err instanceof ApiRequestError ? err.message : 'Failed to activate user', 'error');
    } finally {
      setBusyId(null);
    }
  }

  if (status === 'loading' || status === 'idle') {
    return <LoadingState title="Loading users…" />;
  }
  if (status === 'error') {
    return (
      <ErrorState
        title="Could not load users"
        detail={error ?? undefined}
        action={<button type="button" className="sakar-btn sakar-btn--secondary" onClick={refetch}>Retry</button>}
      />
    );
  }

  const filtered = (data?.content ?? []).filter(
    (u) => !search.trim() || u.fullName.toLowerCase().includes(search.toLowerCase()) || u.email.toLowerCase().includes(search.toLowerCase()),
  );
  const departmentList = departments ?? [];
  const title = scope === 'INTERNAL' ? 'Internal Users' : 'External Users';

  const table = (
    <Card title={`${title} (${filtered.length} of ${data?.totalElements ?? 0} on this page)`}>
      <FilterBar>
        <SearchBar value={search} onChange={setSearch} ariaLabel="Search users" placeholder="Search by name or email…" />
      </FilterBar>
      <DataTable
        rows={filtered}
        rowKey={(u) => u.id}
        emptyTitle="No users found"
        columns={[
          { key: 'name', header: 'Name', render: (u) => u.fullName },
          { key: 'email', header: 'Email', render: (u) => u.email },
          { key: 'role', header: 'Role', render: (u) => <RoleSelect user={u} onChanged={refetch} /> },
          ...(scope === 'INTERNAL'
            ? [{ key: 'department', header: 'Department', render: (u: PlatformUser) => <DepartmentSelect user={u} departments={departmentList} onChanged={refetch} /> }]
            : [{ key: 'org', header: 'Organization', render: (u: PlatformUser) => (u.organizationId ? <span className="sakar-mono">{u.organizationId.slice(0, 8)}…</span> : 'Cross-organization') }]),
          { key: 'status', header: 'Status', render: (u) => <Badge tone={u.status === 'ACTIVE' ? 'success' : u.status === 'SUSPENDED' ? 'danger' : 'neutral'}>{u.status}</Badge> },
          { key: 'lastLogin', header: 'Last Login', render: (u) => (u.lastLoginAt ? new Date(u.lastLoginAt).toLocaleString() : 'Never') },
          {
            key: 'actions',
            header: 'Actions',
            render: (u) => (
              <div style={{ display: 'flex', gap: 6 }}>
                {u.status !== 'SUSPENDED' && (
                  <button type="button" className="sakar-btn sakar-btn--danger" disabled={busyId === u.id} onClick={() => handleSuspend(u)}>Suspend</button>
                )}
                {u.status !== 'ACTIVE' && (
                  <button type="button" className="sakar-btn sakar-btn--secondary" disabled={busyId === u.id} onClick={() => handleActivate(u)}>Activate</button>
                )}
              </div>
            ),
          },
        ]}
      />
      {data && <Pagination page={data.number} totalPages={data.totalPages} onChange={setPage} />}
    </Card>
  );

  return (
    <div>
      <PageHeader
        title={title}
        subtitle={scope === 'INTERNAL' ? 'Sakar staff, grouped by department.' : 'Customer/organization accounts, scoped to your organization.'}
        actions={<button type="button" className="sakar-btn sakar-btn--primary" onClick={() => setShowCreate(true)}>Create User</button>}
      />
      {scope === 'INTERNAL' ? (
        <div style={{ display: 'grid', gridTemplateColumns: '260px 1fr', gap: 16, alignItems: 'start' }}>
          <DepartmentsPanel departments={departmentList} onChanged={refetchDepartments} />
          {table}
        </div>
      ) : (
        table
      )}

      <CreateUserModal
        open={showCreate}
        scope={scope}
        departments={departmentList}
        onClose={() => setShowCreate(false)}
        onCreated={() => { refetch(); refetchDepartments(); }}
      />
    </div>
  );
}
