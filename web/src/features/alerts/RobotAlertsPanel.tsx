import { useState } from 'react';
import { acknowledgeAlert, listAlerts, resolveAlert } from '../../api/alerts';
import { useApi } from '../../hooks/useApi';
import { usePermissions } from '../../hooks/usePermissions';
import { useToast } from '../../components/ui/Toast';
import { ApiRequestError } from '../../api/client';
import { Card } from '../../components/ui/Card';
import { DataTable } from '../../components/ui/DataTable';
import { Badge } from '../../components/ui/Badge';
import { SeverityBadge } from '../../components/ui/SeverityBadge';
import { LoadingState, ErrorState } from '../../components/ui/States';
import type { RobotAlertStatus } from '../../types/domain';

const STATUS_TONE: Record<RobotAlertStatus, 'danger' | 'warning' | 'success'> = {
  OPEN: 'danger',
  ACKNOWLEDGED: 'warning',
  RESOLVED: 'success',
};

// Real alerts (GET /api/v1/alerts), filtered client-side to this robot — no
// per-robot alert endpoint exists, mirroring RobotAuditPanel's approach.
export function RobotAlertsPanel({ robotId }: { robotId: string }) {
  const { hasPermission } = usePermissions();
  const toast = useToast();
  const [busyId, setBusyId] = useState<string | null>(null);
  const { data, status, error, refetch } = useApi(() => listAlerts(0, 100), []);

  if (status === 'loading' || status === 'idle') {
    return <LoadingState title="Loading alerts…" />;
  }
  if (status === 'error') {
    return (
      <ErrorState
        title="Could not load alerts"
        detail={error ?? undefined}
        action={<button type="button" className="sakar-btn sakar-btn--secondary" onClick={refetch}>Retry</button>}
      />
    );
  }

  const rows = (data?.content ?? []).filter((a) => a.robotId === robotId);
  const canControl = hasPermission('ROBOT_CONTROL');

  async function handleAcknowledge(id: string) {
    setBusyId(id);
    try {
      await acknowledgeAlert(id);
      toast.show('Alert acknowledged', 'success');
      refetch();
    } catch (err) {
      toast.show(err instanceof ApiRequestError ? err.message : 'Failed to acknowledge alert', 'error');
    } finally {
      setBusyId(null);
    }
  }

  async function handleResolve(id: string) {
    setBusyId(id);
    try {
      await resolveAlert(id);
      toast.show('Alert resolved', 'success');
      refetch();
    } catch (err) {
      toast.show(err instanceof ApiRequestError ? err.message : 'Failed to resolve alert', 'error');
    } finally {
      setBusyId(null);
    }
  }

  return (
    <Card title="Alerts">
      <p className="sakar-page-subtitle" style={{ marginBottom: 12 }}>
        Filtered from the most recent 100 real alerts in your organization scope (no dedicated per-robot alert
        endpoint exists) — older matching entries may not appear here.
      </p>
      <DataTable
        rows={rows}
        rowKey={(a) => a.id}
        emptyTitle="No alerts for this robot"
        columns={[
          { key: 'severity', header: 'Severity', render: (a) => <SeverityBadge severity={a.severity} /> },
          { key: 'message', header: 'Alert', render: (a) => a.message },
          { key: 'status', header: 'Status', render: (a) => <Badge tone={STATUS_TONE[a.status]}>{a.status}</Badge> },
          { key: 'created', header: 'Created', render: (a) => new Date(a.createdAt).toLocaleString() },
          {
            key: 'actions',
            header: 'Actions',
            render: (a) => {
              if (!canControl) return <span className="sakar-page-subtitle">—</span>;
              if (a.status === 'OPEN') {
                return <button type="button" className="sakar-btn sakar-btn--secondary" disabled={busyId === a.id} onClick={() => handleAcknowledge(a.id)}>Acknowledge</button>;
              }
              if (a.status === 'ACKNOWLEDGED') {
                return <button type="button" className="sakar-btn sakar-btn--primary" disabled={busyId === a.id} onClick={() => handleResolve(a.id)}>Resolve</button>;
              }
              return <span className="sakar-page-subtitle">—</span>;
            },
          },
        ]}
      />
    </Card>
  );
}
