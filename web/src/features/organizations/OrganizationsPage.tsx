import { useEffect, useState, type FormEvent } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../../features/auth/AuthContext';
import { listRobots } from '../../api/robots';
import { getOrganization } from '../../api/organizations';
import { Breadcrumb } from '../../components/ui/Breadcrumb';
import { PageHeader } from '../../components/ui/PageHeader';
import { DataTable } from '../../components/ui/DataTable';
import { Badge } from '../../components/ui/Badge';
import { UnavailableFeature } from '../../components/ui/SimulatedDataBanner';
import { LoadingState } from '../../components/ui/States';
import type { Organization } from '../../types/domain';

interface DiscoveredOrg {
  id: string;
  // Resolved via the real GET /organizations/{id}; null when that call
  // failed or is still pending, in which case only the id is shown.
  org: Organization | null;
}

// There is no `GET /organizations` (list-all) endpoint in the backend today
// — only `GET /organizations/{id}` and `GET /organizations/{id}/children`
// (see OrganizationController.java). A user scoped to one organization is
// routed straight to it; a SUPER_ADMIN (no organizationId on their token)
// has no bounded starting point, so this page derives candidate roots from
// the organizations referenced by robots they can already see (a real,
// existing API), plus a manual "open by ID" fallback.
export function OrganizationsPage() {
  const { user } = useAuth();
  const navigate = useNavigate();
  const [discovered, setDiscovered] = useState<DiscoveredOrg[] | null>(null);
  const [manualId, setManualId] = useState('');

  useEffect(() => {
    if (user?.organizationId) {
      navigate(`/organizations/${user.organizationId}`, { replace: true });
      return;
    }
    let cancelled = false;
    listRobots(0, 100).then(async (page) => {
      const ids = Array.from(new Set(page.content.map((r) => r.organizationId)));
      if (cancelled) return;
      // Resolve each discovered id to its real organization record so the
      // table can show a name/type/status instead of a bare UUID. A failed
      // lookup degrades to the id alone — never a placeholder name.
      const resolved = await Promise.allSettled(ids.map((id) => getOrganization(id)));
      if (cancelled) return;
      setDiscovered(ids.map((id, i) => ({
        id,
        org: resolved[i].status === 'fulfilled' ? resolved[i].value : null,
      })));
    }).catch(() => {
      if (!cancelled) setDiscovered([]);
    });
    return () => {
      cancelled = true;
    };
  }, [user, navigate]);

  if (user?.organizationId) {
    return <LoadingState title="Opening your organization…" />;
  }

  function handleManualOpen(e: FormEvent) {
    e.preventDefault();
    if (manualId.trim()) {
      navigate(`/organizations/${manualId.trim()}`);
    }
  }

  const loading = discovered === null;

  return (
    <div>
      <Breadcrumb items={[{ label: 'Administration' }, { label: 'Organizations' }]} />
      <PageHeader title="Organizations" subtitle="Distributor / sub-distributor / client hierarchy." />

      <UnavailableFeature reason="No endpoint exists to list all organizations for a super-admin scope. This page shows organizations discovered via the robots you can already see, plus a manual lookup." />

      <form className="sakar-filter-card" onSubmit={handleManualOpen}>
        <div className="sakar-filter-grid">
          <div className="sakar-filter-field">
            <span className="sakar-filter-field-label">Organization ID</span>
            <input
              id="org-id"
              value={manualId}
              onChange={(e) => setManualId(e.target.value)}
              placeholder="UUID"
              aria-label="Organization ID"
              style={{ minWidth: 300 }}
            />
          </div>
        </div>
        <div className="sakar-filter-actions">
          <button type="submit" className="sakar-btn sakar-btn--primary" disabled={!manualId.trim()}>
            Open
          </button>
        </div>
      </form>

      <div className="sakar-toolbar">
        <div className="sakar-toolbar-info">
          <span className="sakar-toolbar-count">
            {loading ? 'Discovered organizations' : `${discovered.length} discovered organization${discovered.length === 1 ? '' : 's'}`}
          </span>
          <span className="sakar-toolbar-note">
            Derived from the organizations referenced by robots visible to you — not a complete tenant list.
          </span>
        </div>
      </div>

      <div className="sakar-card sakar-card--flush">
        <div className="sakar-card-body">
          <DataTable
            rows={discovered ?? []}
            rowKey={(d) => d.id}
            loading={loading}
            indexColumn
            emptyTitle="No organizations discoverable yet"
            emptyDetail="Register a robot first, or open an organization directly by its ID above."
            columns={[
              { key: 'name', header: 'Organization', render: (d) => (
                  <button type="button" className="sakar-link-btn sakar-nowrap" onClick={() => navigate(`/organizations/${d.id}`)}>
                    {d.org?.name ?? d.id}
                  </button>
                ) },
              { key: 'type', header: 'Type', render: (d) => (
                  d.org ? <span className="sakar-nowrap">{d.org.orgType}</span> : <span className="sakar-page-subtitle">—</span>
                ) },
              { key: 'status', header: 'Status', render: (d) => (
                  d.org ? <Badge tone={d.org.status === 'ACTIVE' ? 'success' : 'danger'}>{d.org.status}</Badge> : <span className="sakar-page-subtitle">—</span>
                ) },
              { key: 'id', header: 'Organization ID', render: (d) => <span className="sakar-mono sakar-nowrap" style={{ fontSize: 12 }}>{d.id}</span> },
              { key: 'actions', header: 'Actions', align: 'right', render: (d) => (
                  <button type="button" className="sakar-btn sakar-btn--secondary sakar-btn--sm" onClick={() => navigate(`/organizations/${d.id}`)}>
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
