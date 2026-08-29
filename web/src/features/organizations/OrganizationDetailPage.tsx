import { useState, type FormEvent } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { useApi } from '../../hooks/useApi';
import { usePermissions } from '../../hooks/usePermissions';
import { getOrganization, getOrganizationChildren, createOrganization } from '../../api/organizations';
import type { OrganizationType } from '../../types/domain';
import { Card } from '../../components/ui/Card';
import { Badge } from '../../components/ui/Badge';
import { LoadingState, ErrorState } from '../../components/ui/States';
import { DataTable } from '../../components/ui/DataTable';
import { ApiRequestError } from '../../api/client';

const ORG_TYPES: OrganizationType[] = ['SAKAR_ROOT', 'INTERNAL', 'DISTRIBUTOR', 'SUB_DISTRIBUTOR', 'CLIENT', 'DIRECT_CLIENT'];

export function OrganizationDetailPage() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const { hasPermission } = usePermissions();
  const { data: org, status, error, refetch } = useApi(() => getOrganization(id!), [id]);
  const { data: children, refetch: refetchChildren } = useApi(() => getOrganizationChildren(id!), [id]);

  const [showCreate, setShowCreate] = useState(false);
  const [name, setName] = useState('');
  const [orgType, setOrgType] = useState<OrganizationType>('CLIENT');
  const [createError, setCreateError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  if (status === 'loading' || status === 'idle') {
    return <LoadingState title="Loading organization…" />;
  }
  if (status === 'error' || !org) {
    return (
      <ErrorState
        title="Could not load this organization"
        detail={error ?? 'Not found, or outside your access scope.'}
        action={
          <button type="button" className="sakar-btn sakar-btn--secondary" onClick={refetch}>
            Retry
          </button>
        }
      />
    );
  }

  async function handleCreateChild(e: FormEvent) {
    e.preventDefault();
    setCreateError(null);
    setSubmitting(true);
    try {
      await createOrganization({ name, orgType, parentOrganizationId: id });
      setName('');
      setShowCreate(false);
      refetchChildren();
    } catch (err) {
      setCreateError(err instanceof ApiRequestError ? err.message : 'Failed to create organization');
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div>
      <div className="sakar-page-header">
        <div>
          <h1 className="sakar-page-title">{org.name}</h1>
          <p className="sakar-page-subtitle">{org.orgType} · {org.id}</p>
        </div>
        <Badge tone={org.status === 'ACTIVE' ? 'success' : 'danger'}>{org.status}</Badge>
      </div>

      <Card title="Details">
        <dl style={{ display: 'grid', gridTemplateColumns: '160px 1fr', rowGap: 10 }}>
          <dt className="sakar-page-subtitle">Path</dt>
          <dd style={{ margin: 0, fontFamily: 'monospace', fontSize: 12.5 }}>{org.path}</dd>
          <dt className="sakar-page-subtitle">Parent</dt>
          <dd style={{ margin: 0 }}>
            {org.parentOrganizationId ? (
              <button type="button" className="sakar-btn sakar-btn--secondary" onClick={() => navigate(`/organizations/${org.parentOrganizationId}`)}>
                View parent
              </button>
            ) : (
              '— (root)'
            )}
          </dd>
          <dt className="sakar-page-subtitle">Created</dt>
          <dd style={{ margin: 0 }}>{new Date(org.createdAt).toLocaleString()}</dd>
        </dl>
      </Card>

      <div style={{ height: 16 }} />

      <Card
        title="Child organizations"
        actions={
          hasPermission('USER_MANAGE') && (
            <button type="button" className="sakar-btn sakar-btn--primary" onClick={() => setShowCreate((v) => !v)}>
              {showCreate ? 'Cancel' : 'Add child organization'}
            </button>
          )
        }
      >
        {showCreate && (
          <form onSubmit={handleCreateChild} style={{ marginBottom: 20, paddingBottom: 20, borderBottom: '1px solid var(--sakar-border)' }}>
            <div className="sakar-field">
              <label htmlFor="new-org-name">Name</label>
              <input id="new-org-name" required value={name} onChange={(e) => setName(e.target.value)} />
            </div>
            <div className="sakar-field">
              <label htmlFor="new-org-type">Type</label>
              <select id="new-org-type" value={orgType} onChange={(e) => setOrgType(e.target.value as OrganizationType)}>
                {ORG_TYPES.map((t) => (
                  <option key={t} value={t}>
                    {t}
                  </option>
                ))}
              </select>
            </div>
            {createError && <div className="sakar-field-error" style={{ marginBottom: 12 }}>{createError}</div>}
            <button type="submit" className="sakar-btn sakar-btn--primary" disabled={submitting}>
              {submitting ? 'Creating…' : 'Create'}
            </button>
          </form>
        )}

        <DataTable
          rows={children ?? []}
          rowKey={(c) => c.id}
          emptyTitle="No child organizations"
          columns={[
            { key: 'name', header: 'Name', render: (c) => (
                <button type="button" className="sakar-btn sakar-btn--secondary" onClick={() => navigate(`/organizations/${c.id}`)}>
                  {c.name}
                </button>
              ) },
            { key: 'type', header: 'Type', render: (c) => c.orgType },
            { key: 'status', header: 'Status', render: (c) => <Badge tone={c.status === 'ACTIVE' ? 'success' : 'danger'}>{c.status}</Badge> },
          ]}
        />
      </Card>

      <div style={{ height: 16 }} />

      <Card title="Sites in this organization">
        <button type="button" className="sakar-btn sakar-btn--secondary" onClick={() => navigate(`/sites?organizationId=${org.id}`)}>
          View sites
        </button>
      </Card>
    </div>
  );
}
