import { useState, type FormEvent } from 'react';
import { activateUser, changeUserRole, createUser, listUsers, suspendUser } from '../../api/users';
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
import { LoadingState, ErrorState } from '../../components/ui/States';
import type { PlatformUser } from '../../types/domain';

function CreateUserModal({ open, onClose, onCreated }: { open: boolean; onClose: () => void; onCreated: () => void }) {
  const { user } = useAuth();
  const toast = useToast();
  const [organizationId, setOrganizationId] = useState(user?.organizationId ?? '');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [fullName, setFullName] = useState('');
  const [roleName, setRoleName] = useState<RoleName>('VIEWER');
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      await createUser({ organizationId: organizationId || null, email, password, fullName, roleName });
      toast.show('User created', 'success');
      setEmail('');
      setPassword('');
      setFullName('');
      onCreated();
      onClose();
    } catch (err) {
      setError(err instanceof ApiRequestError ? err.message : 'Failed to create user');
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <Modal open={open} title="Create User" onClose={onClose}>
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

// Real user administration (GET/POST /api/v1/users/*). This route is
// already gated on USER_MANAGE (App.tsx), matching the backend's own gate.
export function UsersPage() {
  const toast = useToast();
  const [page, setPage] = useState(0);
  const [search, setSearch] = useState('');
  const [showCreate, setShowCreate] = useState(false);
  const [busyId, setBusyId] = useState<string | null>(null);
  const { data, status, error, refetch } = useApi(() => listUsers(page, 25), [page]);

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

  return (
    <div>
      <PageHeader
        title="Users"
        subtitle="User administration, scoped to your organization."
        actions={<button type="button" className="sakar-btn sakar-btn--primary" onClick={() => setShowCreate(true)}>Create User</button>}
      />
      <Card title={`Users (${filtered.length} of ${data?.totalElements ?? 0} on this page)`}>
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
            { key: 'org', header: 'Organization', render: (u) => (u.organizationId ? <span className="sakar-mono">{u.organizationId.slice(0, 8)}…</span> : 'Cross-organization') },
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

      <CreateUserModal open={showCreate} onClose={() => setShowCreate(false)} onCreated={refetch} />
    </div>
  );
}
