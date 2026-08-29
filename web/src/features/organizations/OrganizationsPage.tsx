import { useEffect, useState, type FormEvent } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../../features/auth/AuthContext';
import { listRobots } from '../../api/robots';
import { Card } from '../../components/ui/Card';
import { UnavailableFeature } from '../../components/ui/SimulatedDataBanner';
import { LoadingState } from '../../components/ui/States';

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
  const [candidateOrgIds, setCandidateOrgIds] = useState<string[] | null>(null);
  const [manualId, setManualId] = useState('');

  useEffect(() => {
    if (user?.organizationId) {
      navigate(`/organizations/${user.organizationId}`, { replace: true });
      return;
    }
    listRobots(0, 100).then((page) => {
      const ids = Array.from(new Set(page.content.map((r) => r.organizationId)));
      setCandidateOrgIds(ids);
    });
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

  return (
    <div>
      <div className="sakar-page-header">
        <div>
          <h1 className="sakar-page-title">Organizations</h1>
          <p className="sakar-page-subtitle">Distributor / sub-distributor / client hierarchy.</p>
        </div>
      </div>

      <UnavailableFeature reason="No endpoint exists to list all organizations for a super-admin scope. Showing organizations discovered via the robots you can already see, plus a manual lookup." />

      <Card title="Organizations discovered via visible robots">
        {candidateOrgIds === null ? (
          <LoadingState />
        ) : candidateOrgIds.length === 0 ? (
          <p className="sakar-page-subtitle">No organizations discoverable yet — register a robot first, or open one by ID below.</p>
        ) : (
          <ul style={{ listStyle: 'none', margin: 0, padding: 0 }}>
            {candidateOrgIds.map((id) => (
              <li key={id} style={{ padding: '8px 0', borderBottom: '1px solid var(--sakar-border)' }}>
                <button type="button" className="sakar-btn sakar-btn--secondary" onClick={() => navigate(`/organizations/${id}`)}>
                  {id}
                </button>
              </li>
            ))}
          </ul>
        )}
      </Card>

      <div style={{ height: 16 }} />

      <Card title="Open organization by ID">
        <form onSubmit={handleManualOpen} style={{ display: 'flex', gap: 10, alignItems: 'flex-end' }}>
          <div className="sakar-field" style={{ marginBottom: 0, flex: 1 }}>
            <label htmlFor="org-id">Organization ID</label>
            <input id="org-id" value={manualId} onChange={(e) => setManualId(e.target.value)} placeholder="UUID" />
          </div>
          <button type="submit" className="sakar-btn sakar-btn--primary">
            Open
          </button>
        </form>
      </Card>
    </div>
  );
}
