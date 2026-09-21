import { useEffect, useState, type FormEvent } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { useApi } from '../../hooks/useApi';
import { usePermissions } from '../../hooks/usePermissions';
import { getOrganization, getOrganizationChildren, createOrganization } from '../../api/organizations';
import { listSitesByOrganization } from '../../api/sites';
import { listRobots } from '../../api/robots';
import type { Organization, OrganizationType } from '../../types/domain';
import { Breadcrumb } from '../../components/ui/Breadcrumb';
import { PageHeader } from '../../components/ui/PageHeader';
import { Card } from '../../components/ui/Card';
import { Badge } from '../../components/ui/Badge';
import { LoadingState, ErrorState } from '../../components/ui/States';
import { DataTable } from '../../components/ui/DataTable';
import { ApiRequestError } from '../../api/client';

// Real counts, resolved client-side: no endpoint returns
// sites-count/robots-count per organization directly, so this fetches the
// real sites list per child org and the real (capped) robots list once,
// then tallies both — never a fabricated number.
function useOrgCounts(children: Organization[] | null) {
  const [sites, setSites] = useState<Map<string, number>>(new Map());
  const [robots, setRobots] = useState<Map<string, number>>(new Map());

  useEffect(() => {
    if (!children || children.length === 0) {
      setSites(new Map());
      return;
    }
    let cancelled = false;
    Promise.allSettled(children.map((c) => listSitesByOrganization(c.id))).then((results) => {
      if (cancelled) return;
      const map = new Map<string, number>();
      results.forEach((r, i) => map.set(children[i].id, r.status === 'fulfilled' ? r.value.length : 0));
      setSites(map);
    });
    return () => {
      cancelled = true;
    };
  }, [children]);

  useEffect(() => {
    if (!children || children.length === 0) {
      setRobots(new Map());
      return;
    }
    let cancelled = false;
    listRobots(0, 100).then((page) => {
      if (cancelled) return;
      const map = new Map<string, number>();
      children.forEach((c) => map.set(c.id, page.content.filter((r) => r.organizationId === c.id).length));
      setRobots(map);
    }).catch(() => {});
    return () => {
      cancelled = true;
    };
  }, [children]);

  return { sites, robots };
}

const ORG_TYPES: OrganizationType[] = ['SAKAR_ROOT', 'INTERNAL', 'DISTRIBUTOR', 'SUB_DISTRIBUTOR', 'CLIENT', 'DIRECT_CLIENT'];

export function OrganizationDetailPage() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const { hasPermission } = usePermissions();
  const { data: org, status, error, refetch } = useApi(() => getOrganization(id!), [id]);
  const { data: children, refetch: refetchChildren } = useApi(() => getOrganizationChildren(id!), [id]);
  const { sites: siteCounts, robots: robotCounts } = useOrgCounts(children);

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
      <Breadcrumb
        items={[
          { label: 'Administration' },
          { label: 'Organizations', to: '/organizations' },
          { label: org.name },
        ]}
      />
      <PageHeader
        title={org.name}
        subtitle={`${org.orgType} · ${org.id}`}
        actions={
          <div style={{ display: 'flex', gap: 8, alignItems: 'center', flexWrap: 'wrap' }}>
            <Badge tone={org.status === 'ACTIVE' ? 'success' : 'danger'}>{org.status}</Badge>
            <button type="button" className="sakar-btn sakar-btn--secondary" onClick={() => navigate(`/sites?organizationId=${org.id}`)}>
              View sites
            </button>
            {hasPermission('USER_MANAGE') && (
              <button type="button" className="sakar-btn sakar-btn--primary" onClick={() => setShowCreate((v) => !v)}>
                {showCreate ? 'Cancel' : 'Add child organization'}
              </button>
            )}
          </div>
        }
      />

      {showCreate && (
        <Card title="New child organization">
          <form onSubmit={handleCreateChild}>
            <div className="sakar-form-row">
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
              <button type="submit" className="sakar-btn sakar-btn--primary" disabled={submitting}>
                {submitting ? 'Creating…' : 'Create'}
              </button>
            </div>
            {createError && <div className="sakar-field-error" style={{ marginTop: 10 }}>{createError}</div>}
          </form>
        </Card>
      )}

      <Card title="Details">
        <div className="sakar-fact-group">
          <span className="sakar-kv-row">
            <span className="sakar-kv-label">Path:</span>
            <span className="sakar-kv-value sakar-mono">{org.path}</span>
          </span>
          <span className="sakar-kv-row">
            <span className="sakar-kv-label">Parent:</span>
            <span className="sakar-kv-value">
              {org.parentOrganizationId ? (
                <button type="button" className="sakar-link-btn" onClick={() => navigate(`/organizations/${org.parentOrganizationId}`)}>
                  View parent
                </button>
              ) : (
                '— (root)'
              )}
            </span>
          </span>
          <span className="sakar-kv-row">
            <span className="sakar-kv-label">Created:</span>
            <span className="sakar-kv-value">{new Date(org.createdAt).toLocaleString()}</span>
          </span>
        </div>
      </Card>

      <div className="sakar-toolbar" style={{ marginTop: 'var(--sakar-sp-4)' }}>
        <div className="sakar-toolbar-info">
          <span className="sakar-toolbar-count">
            {children === null ? 'Child organizations' : `${children.length} child organization${children.length === 1 ? '' : 's'}`}
          </span>
          <span className="sakar-toolbar-note">
            Site and robot counts are tallied client-side from the real sites and robots APIs; the robot tally reads the
            first 100 robots visible to you.
          </span>
        </div>
      </div>

      <div className="sakar-card sakar-card--flush">
        <div className="sakar-card-body">
          <DataTable
            rows={children ?? []}
            rowKey={(c) => c.id}
            loading={children === null}
            indexColumn
            emptyTitle="No child organizations"
            emptyDetail="This organization has no children in the tenant hierarchy."
            columns={[
              { key: 'name', header: 'Organization', render: (c) => (
                  <button type="button" className="sakar-link-btn sakar-nowrap" onClick={() => navigate(`/organizations/${c.id}`)}>
                    {c.name}
                  </button>
                ) },
              { key: 'type', header: 'Type', render: (c) => <span className="sakar-nowrap">{c.orgType}</span> },
              { key: 'sites', header: 'Sites', align: 'right', render: (c) => siteCounts.get(c.id) ?? '—' },
              { key: 'robots', header: 'Robots', align: 'right', render: (c) => robotCounts.get(c.id) ?? '—' },
              { key: 'status', header: 'Status', render: (c) => <Badge tone={c.status === 'ACTIVE' ? 'success' : 'danger'}>{c.status}</Badge> },
              { key: 'actions', header: 'Actions', align: 'right', render: (c) => (
                  <button type="button" className="sakar-btn sakar-btn--secondary sakar-btn--sm" onClick={() => navigate(`/organizations/${c.id}`)}>
                    Open
                  </button>
                ) },
            ]}
          />
        </div>
      </div>
    </div>
  );
}
