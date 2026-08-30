import { useMemo, useState, type FormEvent } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { useApi } from '../../hooks/useApi';
import { usePermissions } from '../../hooks/usePermissions';
import { useAuth } from '../../features/auth/AuthContext';
import { listSitesByOrganization, createSite } from '../../api/sites';
import { getOrganization } from '../../api/organizations';
import { listRobots } from '../../api/robots';
import { useRobotStatusProbe } from '../shared/useRobotStatusProbe';
import { PageHeader } from '../../components/ui/PageHeader';
import { Card } from '../../components/ui/Card';
import { Badge } from '../../components/ui/Badge';
import { DataTable } from '../../components/ui/DataTable';
import { LoadingState, ErrorState, EmptyState } from '../../components/ui/States';
import { useToast } from '../../components/ui/Toast';
import { ApiRequestError } from '../../api/client';

// GET /sites requires an organizationId query param — there is no
// list-all-sites endpoint (SiteController.java only exposes
// listByOrganization). This page always operates against one organization
// at a time, defaulting to the signed-in user's own organization.
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

  return (
    <div>
      <PageHeader title="Sites" subtitle="Sites belonging to one organization at a time." />

      <Card title="Organization scope">
        <form onSubmit={handleSwitchOrg} style={{ display: 'flex', gap: 10, alignItems: 'flex-end' }}>
          <div className="sakar-field" style={{ marginBottom: 0, flex: 1 }}>
            <label htmlFor="site-org-id">Organization ID</label>
            <input id="site-org-id" value={orgIdInput} onChange={(e) => setOrgIdInput(e.target.value)} placeholder="UUID" />
          </div>
          <button type="submit" className="sakar-btn sakar-btn--primary">
            Load sites
          </button>
        </form>
      </Card>

      <div style={{ height: 16 }} />

      {!organizationId ? (
        <EmptyState title="No organization selected" detail="Enter an organization ID above to list its sites." />
      ) : status === 'loading' || status === 'idle' ? (
        <LoadingState title="Loading sites…" />
      ) : status === 'error' ? (
        <ErrorState title="Could not load sites" detail={error ?? undefined} action={<button type="button" className="sakar-btn sakar-btn--secondary" onClick={refetch}>Retry</button>} />
      ) : (
        <Card
          title={`Sites (${sites?.length ?? 0})${org ? ` — ${org.name}` : ''}`}
          actions={
            hasPermission('ROBOT_CONFIGURE') && (
              <button type="button" className="sakar-btn sakar-btn--primary" onClick={() => setShowCreate((v) => !v)}>
                {showCreate ? 'Cancel' : 'Add site'}
              </button>
            )
          }
        >
          {showCreate && (
            <form onSubmit={handleCreate} style={{ marginBottom: 20, paddingBottom: 20, borderBottom: '1px solid var(--sakar-border)' }}>
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
              {createError && <div className="sakar-field-error" style={{ marginBottom: 12 }}>{createError}</div>}
              <button type="submit" className="sakar-btn sakar-btn--primary" disabled={submitting}>
                {submitting ? 'Creating…' : 'Create'}
              </button>
            </form>
          )}

          <DataTable
            rows={sites ?? []}
            rowKey={(s) => s.id}
            emptyTitle="No sites in this organization"
            columns={[
              { key: 'name', header: 'Site', render: (s) => s.name },
              { key: 'org', header: 'Organization', render: () => org?.name ?? organizationId },
              { key: 'robots', header: 'Robots', render: (s) => {
                  const count = robotsForOrg.filter((r) => r.siteId === s.id).length;
                  return <Link to={`/robots?siteId=${s.id}`}>{count}</Link>;
                } },
              { key: 'online', header: 'Online', render: (s) => {
                  const siteRobotIds = robotsForOrg.filter((r) => r.siteId === s.id).map((r) => r.id);
                  const probed = siteRobotIds.map((id) => statuses.get(id)).filter((v) => v && v !== 'unavailable') as { online: boolean }[];
                  if (probed.length === 0) return <span className="sakar-page-subtitle">—</span>;
                  return probed.filter((p) => p.online).length;
                } },
              { key: 'alerts', header: 'Alerts', render: () => <span className="sakar-page-subtitle">Simulated</span> },
              { key: 'status', header: 'Status', render: (s) => {
                  const count = robotsForOrg.filter((r) => r.siteId === s.id).length;
                  return <Badge tone={count > 0 ? 'success' : 'neutral'}>{count > 0 ? 'Active' : 'No robots'}</Badge>;
                } },
            ]}
          />
        </Card>
      )}
    </div>
  );
}
