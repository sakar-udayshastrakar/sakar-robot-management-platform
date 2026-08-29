import { useMemo, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { listRobots } from '../../api/robots';
import { useApi } from '../../hooks/useApi';
import { usePermissions } from '../../hooks/usePermissions';
import { Card } from '../../components/ui/Card';
import { DataTable } from '../../components/ui/DataTable';
import { Badge } from '../../components/ui/Badge';
import { Pagination } from '../../components/ui/Pagination';
import { LoadingState, ErrorState } from '../../components/ui/States';
import type { RobotLifecycleStatus } from '../../types/domain';
import { RegisterRobotForm } from './RegisterRobotForm';

const STATUS_TONE: Record<RobotLifecycleStatus, 'success' | 'neutral' | 'warning'> = {
  ACTIVE: 'success',
  REGISTERED: 'neutral',
  DEACTIVATED: 'warning',
};

export function RobotsListPage() {
  const navigate = useNavigate();
  const { hasPermission } = usePermissions();
  const [params] = useSearchParams();
  const siteIdFilter = params.get('siteId');

  const [page, setPage] = useState(0);
  const [showRegister, setShowRegister] = useState(false);
  const { data, status, error, refetch } = useApi(() => listRobots(page, 25), [page]);

  const rows = useMemo(() => {
    const content = data?.content ?? [];
    return siteIdFilter ? content.filter((r) => r.siteId === siteIdFilter) : content;
  }, [data, siteIdFilter]);

  return (
    <div>
      <div className="sakar-page-header">
        <div>
          <h1 className="sakar-page-title">Robots</h1>
          <p className="sakar-page-subtitle">
            Sakar Robot / CleanBot 5000 Plus registry{siteIdFilter ? ' — filtered by site' : ''}.
          </p>
        </div>
        {hasPermission('ROBOT_CONFIGURE') && (
          <button type="button" className="sakar-btn sakar-btn--primary" onClick={() => setShowRegister((v) => !v)}>
            {showRegister ? 'Cancel' : 'Register robot'}
          </button>
        )}
      </div>

      {showRegister && (
        <div style={{ marginBottom: 20 }}>
          <RegisterRobotForm
            onCreated={() => {
              setShowRegister(false);
              refetch();
            }}
          />
        </div>
      )}

      {status === 'loading' || status === 'idle' ? (
        <LoadingState title="Loading robots…" />
      ) : status === 'error' ? (
        <ErrorState
          title="Could not load robots"
          detail={error ?? undefined}
          action={<button type="button" className="sakar-btn sakar-btn--secondary" onClick={refetch}>Retry</button>}
        />
      ) : (
        <Card title={`Robots (${data?.totalElements ?? 0})`}>
          <p className="sakar-page-subtitle" style={{ marginBottom: 12 }}>
            Online/offline, battery, and agent version are not part of the robot registry list response — open a
            robot to probe its live status.
          </p>
          <DataTable
            rows={rows}
            rowKey={(r) => r.id}
            emptyTitle="No robots registered"
            columns={[
              { key: 'name', header: 'Name', render: (r) => (
                  <button type="button" className="sakar-btn sakar-btn--secondary" onClick={() => navigate(`/robots/${r.id}`)}>
                    {r.name}
                  </button>
                ) },
              { key: 'serial', header: 'Serial number', render: (r) => r.serialNumber },
              { key: 'status', header: 'Lifecycle status', render: (r) => <Badge tone={STATUS_TONE[r.status]}>{r.status}</Badge> },
              { key: 'capabilities', header: 'Capabilities', render: (r) => r.capabilities.length ? r.capabilities.length : '—' },
              { key: 'created', header: 'Registered', render: (r) => new Date(r.createdAt).toLocaleDateString() },
            ]}
          />
          {data && <Pagination page={data.number} totalPages={data.totalPages} onChange={setPage} />}
        </Card>
      )}
    </div>
  );
}
