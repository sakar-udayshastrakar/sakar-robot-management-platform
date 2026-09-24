import { useEffect, useMemo, useState, type FormEvent } from 'react';
import {
  allocateRobot,
  listRobots,
  returnRobotToInventory,
  updateRobotInventory,
} from '../../api/robots';
import { listRobotModels } from '../../api/robotModels';
import { getOrganizationChildren } from '../../api/organizations';
import { listSitesByOrganization } from '../../api/sites';
import { useApi } from '../../hooks/useApi';
import { useAuth } from '../auth/AuthContext';
import { useToast } from '../../components/ui/Toast';
import { ApiRequestError } from '../../api/client';
import { useSiteNames } from '../shared/useSiteNames';
import { Breadcrumb } from '../../components/ui/Breadcrumb';
import { PageHeader } from '../../components/ui/PageHeader';
import { Card } from '../../components/ui/Card';
import { DataTable } from '../../components/ui/DataTable';
import { Modal } from '../../components/ui/Modal';
import { ConfirmDialog } from '../../components/ui/ConfirmDialog';
import { ErrorState } from '../../components/ui/States';
import type { Organization, Robot, Site } from '../../types/domain';

interface BindStoreModalProps {
  robot: Robot | null;
  onClose: () => void;
  onSaved: () => void;
}

function BindStoreModal({ robot, onClose, onSaved }: BindStoreModalProps) {
  const toast = useToast();
  const [sites, setSites] = useState<Site[]>([]);
  const [siteId, setSiteId] = useState('');
  const [warrantyStart, setWarrantyStart] = useState('');
  const [warrantyEnd, setWarrantyEnd] = useState('');
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    if (!robot) return;
    setSiteId(robot.siteId ?? '');
    setWarrantyStart(robot.warrantyStartDate ?? '');
    setWarrantyEnd(robot.warrantyEndDate ?? '');
    listSitesByOrganization(robot.organizationId).then(setSites).catch(() => setSites([]));
  }, [robot]);

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    if (!robot) return;
    setSubmitting(true);
    try {
      await updateRobotInventory(robot.id, {
        siteId: siteId || null,
        warrantyStartDate: warrantyStart || null,
        warrantyEndDate: warrantyEnd || null,
      });
      toast.show('Robot inventory updated', 'success');
      onSaved();
      onClose();
    } catch (err) {
      toast.show(err instanceof ApiRequestError ? err.message : 'Failed to update inventory', 'error');
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <Modal open={robot !== null} title={robot ? `Bind Store — ${robot.name}` : 'Bind Store'} onClose={onClose}>
      <form onSubmit={handleSubmit}>
        <div className="sakar-field">
          <label htmlFor="bind-site">Store (Site)</label>
          <select id="bind-site" value={siteId} onChange={(e) => setSiteId(e.target.value)}>
            <option value="">Unassigned</option>
            {sites.map((s) => <option key={s.id} value={s.id}>{s.name}</option>)}
          </select>
        </div>
        <div className="sakar-field">
          <label htmlFor="warranty-start">Warranty start date</label>
          <input id="warranty-start" type="date" value={warrantyStart} onChange={(e) => setWarrantyStart(e.target.value)} />
        </div>
        <div className="sakar-field">
          <label htmlFor="warranty-end">Warranty end date</label>
          <input id="warranty-end" type="date" value={warrantyEnd} onChange={(e) => setWarrantyEnd(e.target.value)} />
        </div>
        <button type="submit" className="sakar-btn sakar-btn--primary" disabled={submitting}>
          {submitting ? 'Saving…' : 'Save'}
        </button>
      </form>
    </Modal>
  );
}

interface AllocateModalProps {
  robot: Robot | null;
  onClose: () => void;
  onSaved: () => void;
}

function AllocateModal({ robot, onClose, onSaved }: AllocateModalProps) {
  const toast = useToast();
  const [children, setChildren] = useState<Organization[]>([]);
  const [targetOrgId, setTargetOrgId] = useState('');
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    if (!robot) return;
    setTargetOrgId('');
    getOrganizationChildren(robot.organizationId).then(setChildren).catch(() => setChildren([]));
  }, [robot]);

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    if (!robot || !targetOrgId) return;
    setSubmitting(true);
    try {
      await allocateRobot(robot.id, targetOrgId);
      toast.show('Robot allocated', 'success');
      onSaved();
      onClose();
    } catch (err) {
      toast.show(err instanceof ApiRequestError ? err.message : 'Failed to allocate robot', 'error');
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <Modal open={robot !== null} title={robot ? `Allocate to Lower-Level Agent — ${robot.name}` : 'Allocate'} onClose={onClose}>
      {children.length === 0 ? (
        <p style={{ margin: 0, fontSize: 14, color: 'var(--sakar-text-muted)' }}>
          This robot's organization has no child (sub-distributor/client) organizations to allocate to.
        </p>
      ) : (
        <form onSubmit={handleSubmit}>
          <div className="sakar-field">
            <label htmlFor="allocate-org">Target organization</label>
            <select id="allocate-org" value={targetOrgId} onChange={(e) => setTargetOrgId(e.target.value)} required>
              <option value="">Select an organization…</option>
              {children.map((c) => <option key={c.id} value={c.id}>{c.name} ({c.orgType})</option>)}
            </select>
          </div>
          <button type="submit" className="sakar-btn sakar-btn--primary" disabled={submitting || !targetOrgId}>
            {submitting ? 'Allocating…' : 'Allocate'}
          </button>
        </form>
      )}
    </Modal>
  );
}

interface RobotInventoryPageProps {
  // Robot Management (Phase 2): Inventory Control shows every robot the
  // caller can see; Sub-Agent Inventory narrows that to robots belonging to
  // a direct child (sub-distributor/client) organization of the caller's
  // own organization — one level down, matching "allocate to lower level
  // agent"'s own one-level semantics, not the whole descendant tree.
  scope: 'ALL' | 'SUB_AGENT';
}

export function RobotInventoryPage({ scope }: RobotInventoryPageProps) {
  const { user } = useAuth();
  const toast = useToast();
  const [busyId, setBusyId] = useState<string | null>(null);
  const [bindingRobot, setBindingRobot] = useState<Robot | null>(null);
  const [allocatingRobot, setAllocatingRobot] = useState<Robot | null>(null);
  const [returningRobot, setReturningRobot] = useState<Robot | null>(null);

  // Single page of up to 100 robots, no pagination UI — this view is a
  // management console for the whole accessible fleet at once, matching the
  // reference product's own Inventory Control table, not a paged browse list.
  const { data, status, error, refetch } = useApi(() => listRobots(0, 100), []);
  const { data: robotModels } = useApi(() => listRobotModels(), []);
  const { data: subAgentOrgIds } = useApi(
    () => (scope === 'SUB_AGENT' && user?.organizationId
      ? getOrganizationChildren(user.organizationId).then((children) => children.map((c) => c.id))
      : Promise.resolve<string[]>([])),
    [scope, user?.organizationId],
  );

  const modelNames = useMemo(() => {
    const map = new Map<string, string>();
    (robotModels ?? []).forEach((m) => map.set(m.id, m.sakarProductName || m.name));
    return map;
  }, [robotModels]);

  const robots = useMemo(() => {
    const all = data?.content ?? [];
    if (scope === 'ALL') return all;
    const childIds = new Set(subAgentOrgIds ?? []);
    return all.filter((r) => childIds.has(r.organizationId));
  }, [data, scope, subAgentOrgIds]);
  const siteNames = useSiteNames(robots);

  async function handleReturnToInventory() {
    if (!returningRobot) return;
    setBusyId(returningRobot.id);
    try {
      await returnRobotToInventory(returningRobot.id);
      toast.show(`${returningRobot.name} returned to inventory`, 'success');
      refetch();
    } catch (err) {
      toast.show(err instanceof ApiRequestError ? err.message : 'Failed to return robot to inventory', 'error');
    } finally {
      setBusyId(null);
      setReturningRobot(null);
    }
  }

  const loading = status === 'loading' || status === 'idle';
  const title = scope === 'ALL' ? 'Inventory Control' : 'Sub-Agent Inventory';

  if (status === 'error') {
    return (
      <ErrorState
        title="Could not load robot inventory"
        detail={error ?? undefined}
        action={<button type="button" className="sakar-btn sakar-btn--secondary" onClick={refetch}>Retry</button>}
      />
    );
  }

  return (
    <div>
      <Breadcrumb items={[{ label: 'Robot Management' }, { label: title }]} />
      <PageHeader
        title={title}
        subtitle={
          scope === 'ALL'
            ? 'Warranty, store assignment, and agent allocation for every robot you can see.'
            : "Robots allocated to a direct sub-agent (child organization) of your own organization."
        }
      />
      <Card title={`Robots (${robots.length})`}>
        <DataTable
          rows={robots}
          rowKey={(r) => r.id}
          loading={loading}
          emptyTitle={scope === 'ALL' ? 'No robots registered' : 'No robots allocated to a sub-agent yet'}
          columns={[
            { key: 'name', header: 'Robot name', render: (r) => r.name },
            { key: 'serial', header: 'Production code', render: (r) => <span className="sakar-mono" style={{ fontSize: 12 }}>{r.serialNumber}</span> },
            { key: 'vendorSerial', header: 'Robot SN', render: (r) => <span className="sakar-mono" style={{ fontSize: 12 }}>{r.vendorSerialNumber ?? '—'}</span> },
            { key: 'model', header: 'Model', render: (r) => modelNames.get(r.robotModelId) ?? '—' },
            { key: 'useType', header: 'Use type', render: (r) => (r.useType === 'PRODUCTION' ? 'Production' : 'Trial') },
            { key: 'store', header: 'Store', render: (r) => (r.siteId ? siteNames.get(r.siteId) ?? r.siteId : '—') },
            { key: 'warrantyStart', header: 'Warranty start', render: (r) => r.warrantyStartDate ?? '—' },
            { key: 'warrantyEnd', header: 'Warranty end', render: (r) => r.warrantyEndDate ?? '—' },
            {
              key: 'actions',
              header: 'Actions',
              align: 'right',
              render: (r) => (
                <div style={{ display: 'flex', gap: 6, justifyContent: 'flex-end' }}>
                  <button type="button" className="sakar-btn sakar-btn--secondary sakar-btn--sm" onClick={() => setBindingRobot(r)}>Bind Store</button>
                  <button type="button" className="sakar-btn sakar-btn--secondary sakar-btn--sm" onClick={() => setAllocatingRobot(r)}>Allocate</button>
                  <button type="button" className="sakar-btn sakar-btn--secondary sakar-btn--sm" disabled={busyId === r.id} onClick={() => setReturningRobot(r)}>
                    Return to Inventory
                  </button>
                </div>
              ),
            },
          ]}
        />
      </Card>

      <BindStoreModal robot={bindingRobot} onClose={() => setBindingRobot(null)} onSaved={refetch} />
      <AllocateModal robot={allocatingRobot} onClose={() => setAllocatingRobot(null)} onSaved={refetch} />
      <ConfirmDialog
        open={returningRobot !== null}
        title="Return to Inventory"
        message={`Unassign ${returningRobot?.name} from its store and reset it to REGISTERED?`}
        confirmLabel="Return to Inventory"
        busy={busyId === returningRobot?.id}
        onConfirm={handleReturnToInventory}
        onCancel={() => setReturningRobot(null)}
      />
    </div>
  );
}
