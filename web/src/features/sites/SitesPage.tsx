import { useMemo, useState, type FormEvent } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { useApi } from '../../hooks/useApi';
import { usePermissions } from '../../hooks/usePermissions';
import { useAuth } from '../../features/auth/AuthContext';
import { listSitesByOrganization, createSite } from '../../api/sites';
import { getOrganization } from '../../api/organizations';
import { listRobots } from '../../api/robots';
import { useRobotStatusProbe } from '../shared/useRobotStatusProbe';
import { Breadcrumb } from '../../components/ui/Breadcrumb';
import { PageHeader } from '../../components/ui/PageHeader';
import { Card } from '../../components/ui/Card';
import { Badge } from '../../components/ui/Badge';
import { DataTable } from '../../components/ui/DataTable';
import { ErrorState, EmptyState } from '../../components/ui/States';
import { Icon } from '../../components/ui/Icon';
import { useToast } from '../../components/ui/Toast';
import { ApiRequestError } from '../../api/client';

// GET /sites requires an organizationId query param — there is no
// list-all-sites endpoint (SiteController.java only exposes
// listByOrganization). This page always operates against one organization
// at a time, defaulting to the signed-in user's own organization. The
// response is a plain array, not a page, so there is no pagination here.
export function SitesPage() {
  const { user } = useAuth();
  const { hasPermission } = usePermissions();
  const toast = useToast();
  const [params, setParams] = useSearchParams();
  const organizationId = params.get('organizationId') ?? user?.organizationId ?? '';

  const [orgIdInput, setOrgIdInput] = useState(organizationId);
  const [showCreate, setShowCreate] = useState(false);
  const [name, setName] = useState('');
  const [address, setAddress] = useState('');
  const [timezone, setTimezone] = useState('');
  const [createError, setCreateError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const { data: org } = useApi(() => (organizationId ? getOrganization(organizationId) : Promise.resolve(null)), [organizationId]);
  const { data: sites, status, error, refetch } = useApi(
    () => (organizationId ? listSitesByOrganization(organizationId) : Promise.resolve([])),
    [organizationId],
  );
  const { data: robotsPage } = useApi(() => listRobots(0, 100), []);
  const robotsForOrg = useMemo(
    () => (robotsPage?.content ?? []).filter((r) => r.organizationId === organizationId),
    [robotsPage, organizationId],
  );
  const { statuses } = useRobotStatusProbe(robotsForOrg);

  function handleSwitchOrg(e: FormEvent) {
    e.preventDefault();
    setParams({ organizationId: orgIdInput.trim() });
  }

  async function handleCreate(e: FormEvent) {
    e.preventDefault();
    setCreateError(null);
    setSubmitting(true);
    try {
      await createSite({ organizationId, name, address: address || null, timezone: timezone || null });
      setName('');
      setAddress('');
      setTimezone('');
      setShowCreate(false);
      refetch();
      toast.show('Site created', 'success');
    } catch (err) {
      setCreateError(err instanceof ApiRequestError ? err.message : 'Failed to create site');
    } finally {
      setSubmitting(false);
    }
  }

  const loading = status === 'loading' || status === 'idle';
  const failed = status === 'error';

  return (
    <div>
      <Breadcrumb items={[{ label: 'Administration' }, { label: 'Sites' }]} />
      <PageHeader
        title="Sites"
        subtitle={org ? `Sites in ${org.name}.` : 'Sites belonging to one organization at a time.'}
        actions={
          hasPermission('ROBOT_CONFIGURE') && organizationId && (
            <button type="button" className="sakar-btn sakar-btn--primary" onClick={() => setShowCreate((v) => !v)}>
              {showCreate ? 'Cancel' : 'Add site'}
            </button>
          )
        }
      />

      {showCreate && organizationId && (
        <Card title="New site">
          <form onSubmit={handleCreate}>
            <div className="sakar-form-row">
              <div className="sakar-field">
                <label htmlFor="site-name">Name</label>
                <input id="site-name" required value={name} onChange={(e) => setName(e.target.value)} />
              </div>
              <div className="sakar-field">
                <label htmlFor="site-address">Address</label>
                <input id="site-address" value={address} onChange={(e) => setAddress(e.target.value)} />
              </div>
              <div className="sakar-field">
                <label htmlFor="site-tz">Timezone</label>
                <input id="site-tz" value={timezone} onChange={(e) => setTimezone(e.target.value)} placeholder="Asia/Kolkata" />
              </div>
              <button type="submit" className="sakar-btn sakar-btn--primary" disabled={submitting}>
                {submitting ? 'Creating…' : 'Create'}
              </button>
            </div>
            {createError && <div className="sakar-field-error" style={{ marginTop: 10 }}>{createError}</div>}
          </form>
        </Card>
      )}

      <form className="sakar-filter-card" onSubmit={handleSwitchOrg}>
        <div className="sakar-filter-grid">
          <div className="sakar-filter-field">
            <span className="sakar-filter-field-label">Organization ID</span>
            <input
              id="site-org-id"
              value={orgIdInput}
              onChange={(e) => setOrgIdInput(e.target.value)}
              placeholder="UUID"
              aria-label="Organization ID"
              style={{ minWidth: 300 }}
            />
          </div>
        </div>
        <div className="sakar-filter-actions">
          <button type="submit" className="sakar-btn sakar-btn--primary" disabled={!orgIdInput.trim()}>
            Load sites
          </button>
        </div>
      </form>

      {!organizationId ? (
        <EmptyState title="No organization selected" detail="Enter an organization ID above to list its sites." />
      ) : (
        <>
          <div className="sakar-toolbar">
            <div className="sakar-toolbar-info">
              <span className="sakar-toolbar-count">
                {loading || failed
                  ? 'Sites'
                  : `${sites?.length ?? 0} site${(sites?.length ?? 0) === 1 ? '' : 's'}${org ? ` in ${org.name}` : ''}`}
              </span>
              <span className="sakar-toolbar-note">
                The sites API returns one organization at a time and is not paginated. Robot counts and online counts are
                tallied from the first 100 robots visible to you; a robot with no successful status probe is not counted
                as online.
              </span>
            </div>
            <div className="sakar-toolbar-actions">
              <button type="button" className="sakar-btn sakar-btn--secondary sakar-btn--sm" onClick={refetch} disabled={loading}>
                <Icon.refresh width={13} height={13} /> Refresh
              </button>
            </div>
          </div>

          <div className="sakar-card sakar-card--flush">
            <div className="sakar-card-body">
              {failed ? (
                <div style={{ padding: 'var(--sakar-sp-4)' }}>
                  <ErrorState
                    title="Could not load sites"
                    detail={error ?? undefined}
                    action={<button type="button" className="sakar-btn sakar-btn--secondary" onClick={refetch}>Retry</button>}
                  />
                </div>
              ) : (
                <DataTable
                  rows={sites ?? []}
                  rowKey={(s) => s.id}
                  loading={loading}
                  indexColumn
                  emptyTitle="No sites in this organization"
                  emptyDetail="Add a site to start grouping robots by location."
                  columns={[
                    { key: 'name', header: 'Site', render: (s) => <span className="sakar-nowrap">{s.name}</span> },
                    { key: 'address', header: 'Address', render: (s) => (s.address ? s.address : <span className="sakar-page-subtitle">—</span>) },
                    { key: 'timezone', header: 'Timezone', render: (s) => (s.timezone ? <span className="sakar-nowrap">{s.timezone}</span> : <span className="sakar-page-subtitle">—</span>) },
                    { key: 'org', header: 'Organization', render: () => <span className="sakar-nowrap">{org?.name ?? organizationId}</span> },
                    { key: 'robots', header: 'Robots', align: 'right', render: (s) => {
                        const count = robotsForOrg.filter((r) => r.siteId === s.id).length;
                        return <Link to={`/robots?siteId=${s.id}`}>{count}</Link>;
                      } },
                    { key: 'online', header: 'Online', align: 'right', render: (s) => {
                        const siteRobotIds = robotsForOrg.filter((r) => r.siteId === s.id).map((r) => r.id);
                        const probed = siteRobotIds.map((id) => statuses.get(id)).filter((v) => v && v !== 'unavailable') as { online: boolean }[];
                        if (probed.length === 0) return <span className="sakar-page-subtitle">—</span>;
                        return probed.filter((p) => p.online).length;
                      } },
                    { key: 'status', header: 'Status', render: (s) => {
                        const count = robotsForOrg.filter((r) => r.siteId === s.id).length;
                        return <Badge tone={count > 0 ? 'success' : 'neutral'}>{count > 0 ? 'Active' : 'No robots'}</Badge>;
                      } },
                  ]}
                />
              )}
            </div>
          </div>
        </>
      )}
    </div>
  );
}
