import { useEffect, useMemo, useState, type FormEvent } from 'react';
import { createRepairRequest, listRepairRequests, updateRepairRequestStatus } from '../../api/repairRequests';
import { listRobots } from '../../api/robots';
import { listAllAccessibleSites } from '../../api/sites';
import { getOrganization } from '../../api/organizations';
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
import type { RepairRequest, RepairRequestStatus, Site } from '../../types/domain';

const STATUS_TONE: Record<RepairRequestStatus, BadgeTone> = {
  OPEN: 'warning',
  IN_PROGRESS: 'info',
  RESOLVED: 'success',
  CLOSED: 'neutral',
};

// Same pattern as StoreManagementPage's useAffiliatedAgentNames — resolves
// each distinct organizationId to its name via one batch of individual GETs.
function useOrganizationNames(organizationIds: string[]) {
  const [names, setNames] = useState<Map<string, string>>(new Map());

  useEffect(() => {
    if (organizationIds.length === 0) {
      setNames(new Map());
      return;
    }
    let cancelled = false;
    Promise.allSettled(organizationIds.map((id) => getOrganization(id))).then((results) => {
      if (cancelled) return;
      const map = new Map<string, string>();
      results.forEach((result, i) => {
        if (result.status === 'fulfilled') map.set(organizationIds[i], result.value.name);
      });
      setNames(map);
    });
    return () => { cancelled = true; };
  }, [organizationIds]);

  return names;
}

function toCsv(rows: RepairRequest[], storeNames: Map<string, string>, resellerNames: Map<string, string>): string {
  const header = ['Work Order No.', 'Store', 'Associated Reseller', 'Symptom', 'Reported At', 'Status'];
  const lines = rows.map((r) => [
    r.workOrderNumber,
    (r.siteId && storeNames.get(r.siteId)) ?? '',
    resellerNames.get(r.organizationId) ?? '',
    r.symptom.replace(/"/g, '""'),
    r.reportedAt,
    r.status,
  ].map((v) => `"${v}"`).join(','));
  return [header.join(','), ...lines].join('\n');
}

interface CreateModalProps {
  open: boolean;
  organizationId: string;
  sites: Site[];
  onClose: () => void;
  onSaved: () => void;
}

function CreateRepairRequestModal({ open, organizationId, sites, onClose, onSaved }: CreateModalProps) {
  const toast = useToast();
  const { data: robotsPage } = useApi(() => listRobots(0, 200), []);
  const [siteId, setSiteId] = useState('');
  const [robotId, setRobotId] = useState('');
  const [symptom, setSymptom] = useState('');
  const [submitting, setSubmitting] = useState(false);

  const robots = robotsPage?.content ?? [];
  const robotsForSite = siteId ? robots.filter((r) => r.siteId === siteId) : robots;

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setSubmitting(true);
    try {
      await createRepairRequest({
        organizationId,
        siteId: siteId || null,
        robotId: robotId || null,
        symptom,
      });
      toast.show('Repair request logged', 'success');
      onSaved();
      onClose();
      setSiteId('');
      setRobotId('');
      setSymptom('');
    } catch (err) {
      toast.show(err instanceof ApiRequestError ? err.message : 'Failed to log repair request', 'error');
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <Modal open={open} title="Construction Order On Behalf Of Others" onClose={onClose}>
      <p style={{ marginTop: 0, fontSize: 13, color: 'var(--sakar-text-faint)' }}>
        There is no customer self-report channel yet — every repair request is logged here by staff on the customer's behalf.
      </p>
      <form onSubmit={handleSubmit}>
        <div className="sakar-field">
          <label htmlFor="repair-store">Store</label>
          <select id="repair-store" value={siteId} onChange={(e) => { setSiteId(e.target.value); setRobotId(''); }}>
            <option value="">Select a store…</option>
            {sites.map((s) => <option key={s.id} value={s.id}>{s.name}</option>)}
          </select>
        </div>
        <div className="sakar-field">
          <label htmlFor="repair-robot">Robot</label>
          <select id="repair-robot" value={robotId} onChange={(e) => setRobotId(e.target.value)}>
            <option value="">Select a robot (optional)…</option>
            {robotsForSite.map((r) => <option key={r.id} value={r.id}>{r.name} ({r.serialNumber})</option>)}
          </select>
        </div>
        <div className="sakar-field">
          <label htmlFor="repair-symptom">Symptom</label>
          <textarea id="repair-symptom" required rows={3} value={symptom} onChange={(e) => setSymptom(e.target.value)} />
        </div>
        <button type="submit" className="sakar-btn sakar-btn--primary" disabled={submitting || !symptom.trim()}>
          {submitting ? 'Saving…' : 'Create'}
        </button>
      </form>
    </Modal>
  );
}

// Operation And Maintenance Platform → Customer Repair Requests. Real
// work-order log — "Associated Reseller" and "Store" resolve to the existing
// Organization/Site entities, never duplicated (see api/repairRequests.ts).
export function CustomerRepairRequestsPage() {
  const { user, hasPermission } = useAuth();
  const toast = useToast();
  const { data: requests, status, error, refetch } = useApi(() => listRepairRequests(), []);
  const { data: sites } = useApi(() => listAllAccessibleSites(), []);
  const [showCreate, setShowCreate] = useState(false);
  const [statusFilter, setStatusFilter] = useState('All');
  const canConfigure = hasPermission('ROBOT_CONFIGURE');

  const sitesById = useMemo(() => new Map((sites ?? []).map((s) => [s.id, s])), [sites]);
  const orgIds = useMemo(() => Array.from(new Set((requests ?? []).map((r) => r.organizationId))), [requests]);
  const resellerNames = useOrganizationNames(orgIds);
  const storeNames = useMemo(() => new Map((sites ?? []).map((s) => [s.id, s.name])), [sites]);

  const rows = useMemo(() => {
    const all = requests ?? [];
    return statusFilter === 'All' ? all : all.filter((r) => r.status === statusFilter);
  }, [requests, statusFilter]);

  async function handleStatusChange(request: RepairRequest, next: RepairRequestStatus) {
    try {
      await updateRepairRequestStatus(request.id, next);
      toast.show('Status updated', 'success');
      refetch();
    } catch (err) {
      toast.show(err instanceof ApiRequestError ? err.message : 'Failed to update status', 'error');
    }
  }

  function handleExport() {
    const csv = toCsv(rows, storeNames, resellerNames);
    const blob = new Blob([csv], { type: 'text/csv;charset=utf-8;' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = 'customer-repair-requests.csv';
    link.click();
    URL.revokeObjectURL(url);
  }

  if (status === 'loading' || status === 'idle') {
    return <LoadingState title="Loading repair requests…" />;
  }
  if (status === 'error') {
    return (
      <ErrorState
        title="Could not load repair requests"
        detail={error ?? undefined}
        action={<button type="button" className="sakar-btn sakar-btn--secondary" onClick={refetch}>Retry</button>}
      />
    );
  }

  return (
    <div>
      <Breadcrumb items={[{ label: 'Operation And Maintenance Platform' }, { label: 'Customer Repair Requests' }]} />
      <PageHeader
        title="Customer Repair Requests"
        subtitle="Real work-order log — staff-entered, since no customer self-report channel exists yet."
        actions={(
          <div style={{ display: 'flex', gap: 8 }}>
            <button type="button" className="sakar-btn sakar-btn--secondary" onClick={handleExport}>Export</button>
            {canConfigure && (
              <button type="button" className="sakar-btn sakar-btn--primary" onClick={() => setShowCreate(true)}>
                Construction order on behalf of others
              </button>
            )}
          </div>
        )}
      />

      <Card>
        <div className="sakar-field" style={{ marginBottom: 0, maxWidth: 240 }}>
          <label htmlFor="repair-status-filter">State</label>
          <select id="repair-status-filter" value={statusFilter} onChange={(e) => setStatusFilter(e.target.value)}>
            <option value="All">All</option>
            <option value="OPEN">Open</option>
            <option value="IN_PROGRESS">In Progress</option>
            <option value="RESOLVED">Resolved</option>
            <option value="CLOSED">Closed</option>
          </select>
        </div>
      </Card>

      <Card title={`Work Orders (${rows.length})`}>
        <DataTable
          rows={rows}
          rowKey={(r) => r.id}
          emptyTitle="No Data"
          columns={[
            { key: 'workOrder', header: 'Work Order No.', render: (r) => <span className="sakar-mono" style={{ fontSize: 12 }}>{r.workOrderNumber}</span> },
            { key: 'store', header: 'Store Name', render: (r) => (r.siteId && sitesById.get(r.siteId)?.name) ?? '—' },
            { key: 'reseller', header: 'Associated Reseller', render: (r) => resellerNames.get(r.organizationId) ?? '—' },
            { key: 'symptom', header: 'Symptom', render: (r) => r.symptom },
            { key: 'reportedAt', header: 'Reported At', render: (r) => new Date(r.reportedAt).toLocaleString() },
            { key: 'status', header: 'status', render: (r) => <Badge tone={STATUS_TONE[r.status]} dot>{r.status}</Badge> },
            ...(canConfigure
              ? [{
                  key: 'action',
                  header: 'Action',
                  align: 'right' as const,
                  render: (r: RepairRequest) => (
                    <select
                      aria-label={`Update status for ${r.workOrderNumber}`}
                      value={r.status}
                      onChange={(e) => handleStatusChange(r, e.target.value as RepairRequestStatus)}
                    >
                      <option value="OPEN">Open</option>
                      <option value="IN_PROGRESS">In Progress</option>
                      <option value="RESOLVED">Resolved</option>
                      <option value="CLOSED">Closed</option>
                    </select>
                  ),
                }]
              : []),
          ]}
        />
      </Card>

      {user?.organizationId && (
        <CreateRepairRequestModal
          open={showCreate}
          organizationId={user.organizationId}
          sites={sites ?? []}
          onClose={() => setShowCreate(false)}
          onSaved={refetch}
        />
      )}
    </div>
  );
}
