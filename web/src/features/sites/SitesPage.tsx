import { useState, type FormEvent } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { useApi } from '../../hooks/useApi';
import { usePermissions } from '../../hooks/usePermissions';
import { useAuth } from '../../features/auth/AuthContext';
import { listSitesByOrganization, createSite } from '../../api/sites';
import { Card } from '../../components/ui/Card';
import { DataTable } from '../../components/ui/DataTable';
import { LoadingState, ErrorState, EmptyState } from '../../components/ui/States';
import { ApiRequestError } from '../../api/client';

// GET /sites requires an organizationId query param — there is no
// list-all-sites endpoint (SiteController.java only exposes
// listByOrganization). This page always operates against one organization
// at a time, defaulting to the signed-in user's own organization.
export function SitesPage() {
  const { user } = useAuth();
  const { hasPermission } = usePermissions();
  const [params, setParams] = useSearchParams();
  const organizationId = params.get('organizationId') ?? user?.organizationId ?? '';

  const [orgIdInput, setOrgIdInput] = useState(organizationId);
  const [showCreate, setShowCreate] = useState(false);
  const [name, setName] = useState('');
  const [address, setAddress] = useState('');
  const [timezone, setTimezone] = useState('');
  const [createError, setCreateError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const { data: sites, status, error, refetch } = useApi(
    () => (organizationId ? listSitesByOrganization(organizationId) : Promise.resolve([])),
    [organizationId],
  );

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
    } catch (err) {
      setCreateError(err instanceof ApiRequestError ? err.message : 'Failed to create site');
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div>
      <div className="sakar-page-header">
        <div>
          <h1 className="sakar-page-title">Sites</h1>
          <p className="sakar-page-subtitle">Sites belonging to one organization at a time.</p>
        </div>
      </div>

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
          title={`Sites (${sites?.length ?? 0})`}
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
              { key: 'name', header: 'Name', render: (s) => s.name },
              { key: 'address', header: 'Address', render: (s) => s.address ?? '—' },
              { key: 'timezone', header: 'Timezone', render: (s) => s.timezone ?? '—' },
              {
                key: 'robots',
                header: 'Robots',
                render: (s) => (
                  <Link to={`/robots?siteId=${s.id}`}>View robots</Link>
                ),
              },
            ]}
          />
        </Card>
      )}
    </div>
  );
}
