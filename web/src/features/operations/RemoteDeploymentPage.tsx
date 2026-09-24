import { useMemo, useState, type FormEvent } from 'react';
import { createRemoteDeployment, listRemoteDeployments, updateRemoteDeploymentStatus } from '../../api/remoteDeployments';
import { listRobots } from '../../api/robots';
import { listAllAccessibleSites } from '../../api/sites';
import { useApi } from '../../hooks/useApi';
import { useAuth } from '../auth/AuthContext';
import { useToast } from '../../components/ui/Toast';
import { ApiRequestError } from '../../api/client';
import { Breadcrumb } from '../../components/ui/Breadcrumb';
import { PageHeader } from '../../components/ui/PageHeader';
import { Card } from '../../components/ui/Card';
import { DataTable } from '../../components/ui/DataTable';
import { Badge, type BadgeTone } from '../../components/ui/Badge';
import { Modal } from '../../components/ui/Modal';
import { LoadingState, ErrorState } from '../../components/ui/States';
import type { RemoteDeploymentRecord, RemoteDeploymentRecordStatus } from '../../types/domain';

const STATUS_TONE: Record<RemoteDeploymentRecordStatus, BadgeTone> = {
  RECORDED: 'neutral',
  COMPLETED: 'success',
  FAILED: 'danger',
};

interface CreateModalProps {
  open: boolean;
  onClose: () => void;
  onSaved: () => void;
}

function CreateDeploymentModal({ open, onClose, onSaved }: CreateModalProps) {
  const toast = useToast();
  const { data: robotsPage } = useApi(() => listRobots(0, 200), []);
  const [robotId, setRobotId] = useState('');
  const [notes, setNotes] = useState('');
  const [submitting, setSubmitting] = useState(false);

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    if (!robotId) return;
    setSubmitting(true);
    try {
      await createRemoteDeployment(robotId, notes || null);
      toast.show('Deployment recorded', 'success');
      onSaved();
      onClose();
      setRobotId('');
      setNotes('');
    } catch (err) {
      toast.show(err instanceof ApiRequestError ? err.message : 'Failed to record deployment', 'error');
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <Modal open={open} title="Record Remote Deployment" onClose={onClose}>
      <p style={{ marginTop: 0, fontSize: 13, color: 'var(--sakar-text-faint)' }}>
        This records that you deployed a configuration to a robot. No remote-configuration-push channel exists yet,
        so this is a bookkeeping record only — mark it Completed or Failed yourself once you've finished.
      </p>
      <form onSubmit={handleSubmit}>
        <div className="sakar-field">
          <label htmlFor="deploy-robot">Robot</label>
          <select id="deploy-robot" required value={robotId} onChange={(e) => setRobotId(e.target.value)}>
            <option value="">Select a robot…</option>
            {(robotsPage?.content ?? []).map((r) => <option key={r.id} value={r.id}>{r.name} ({r.serialNumber})</option>)}
          </select>
        </div>
        <div className="sakar-field">
          <label htmlFor="deploy-notes">Notes (optional)</label>
          <textarea id="deploy-notes" rows={3} value={notes} onChange={(e) => setNotes(e.target.value)} />
        </div>
        <button type="submit" className="sakar-btn sakar-btn--primary" disabled={submitting || !robotId}>
          {submitting ? 'Saving…' : 'Create'}
        </button>
      </form>
    </Modal>
  );
}

// Operation And Maintenance Platform → Remote Deployment. Bookkeeping only —
// see RemoteDeploymentRecord's own comment (types/domain.ts) for why
// COMPLETED/FAILED are only ever set by the operator's own follow-up action.
export function RemoteDeploymentPage() {
  const { hasPermission } = useAuth();
  const toast = useToast();
  const { data: records, status, error, refetch } = useApi(() => listRemoteDeployments(), []);
  const { data: robotsPage } = useApi(() => listRobots(0, 200), []);
  const { data: sites } = useApi(() => listAllAccessibleSites(), []);
  const [showCreate, setShowCreate] = useState(false);
  const canConfigure = hasPermission('ROBOT_CONFIGURE');

  const robotsById = useMemo(() => new Map((robotsPage?.content ?? []).map((r) => [r.id, r])), [robotsPage]);
  const sitesById = useMemo(() => new Map((sites ?? []).map((s) => [s.id, s])), [sites]);
  const rows = records ?? [];

  async function handleStatusChange(record: RemoteDeploymentRecord, next: RemoteDeploymentRecordStatus) {
    try {
      await updateRemoteDeploymentStatus(record.id, next);
      toast.show('Status updated', 'success');
      refetch();
    } catch (err) {
      toast.show(err instanceof ApiRequestError ? err.message : 'Failed to update status', 'error');
    }
  }

  if (status === 'loading' || status === 'idle') {
    return <LoadingState title="Loading remote deployments…" />;
  }
  if (status === 'error') {
    return (
      <ErrorState
        title="Could not load remote deployments"
        detail={error ?? undefined}
        action={<button type="button" className="sakar-btn sakar-btn--secondary" onClick={refetch}>Retry</button>}
      />
    );
  }

  return (
    <div>
      <Breadcrumb items={[{ label: 'Operation And Maintenance Platform' }, { label: 'Remote Deployment' }]} />
      <PageHeader
        title="Remote Deployment"
        subtitle="Bookkeeping record of manual configuration deployments — no remote-push channel exists yet."
        actions={canConfigure && (
          <button type="button" className="sakar-btn sakar-btn--primary" onClick={() => setShowCreate(true)}>+ Create</button>
        )}
      />
      <Card title={`Deployments (${rows.length})`}>
        <DataTable
          rows={rows}
          rowKey={(r) => r.id}
          emptyTitle="No Data"
          columns={[
            { key: 'taskId', header: 'Deploy task ID', render: (r) => <span className="sakar-mono" style={{ fontSize: 12 }}>{r.id.slice(0, 8)}…</span> },
            { key: 'storeId', header: 'Store ID', render: (r) => (r.siteId ? <span className="sakar-mono" style={{ fontSize: 12 }}>{r.siteId.slice(0, 8)}…</span> : '—') },
            { key: 'storeName', header: 'Store name', render: (r) => (r.siteId && sitesById.get(r.siteId)?.name) ?? '—' },
            { key: 'robotSn', header: 'Robots SN', render: (r) => robotsById.get(r.robotId)?.serialNumber ?? r.robotId.slice(0, 8) },
            { key: 'deployer', header: 'Deployer', render: (r) => r.deployedBy ?? '—' },
            { key: 'created', header: 'Create deployment time', render: (r) => new Date(r.createdAt).toLocaleString() },
            { key: 'completed', header: 'End deployment time', render: (r) => (r.completedAt ? new Date(r.completedAt).toLocaleString() : '—') },
            { key: 'updated', header: 'Update time', render: (r) => new Date(r.updatedAt).toLocaleString() },
            {
              key: 'status',
              header: 'Status',
              render: (r) => canConfigure ? (
                <select
                  aria-label={`Update status for deployment ${r.id}`}
                  value={r.status}
                  onChange={(e) => handleStatusChange(r, e.target.value as RemoteDeploymentRecordStatus)}
                >
                  <option value="RECORDED">Recorded</option>
                  <option value="COMPLETED">Completed</option>
                  <option value="FAILED">Failed</option>
                </select>
              ) : (
                <Badge tone={STATUS_TONE[r.status]} dot>{r.status}</Badge>
              ),
            },
          ]}
        />
      </Card>

      <CreateDeploymentModal open={showCreate} onClose={() => setShowCreate(false)} onSaved={refetch} />
    </div>
  );
}
