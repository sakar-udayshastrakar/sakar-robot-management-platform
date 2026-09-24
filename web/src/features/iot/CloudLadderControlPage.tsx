import { useMemo, useState, type FormEvent } from 'react';
import {
  createLadderControlBinding,
  listElevatorConfigurationDeliveries,
  listElevatorConfigurations,
  listElevatorDevices,
  listLadderControlBindings,
} from '../../api/iot';
import { listAllAccessibleSites } from '../../api/sites';
import { listRobots } from '../../api/robots';
import { useApi } from '../../hooks/useApi';
import { useAuth } from '../auth/AuthContext';
import { useToast } from '../../components/ui/Toast';
import { ApiRequestError } from '../../api/client';
import { Breadcrumb } from '../../components/ui/Breadcrumb';
import { PageHeader } from '../../components/ui/PageHeader';
import { Card } from '../../components/ui/Card';
import { DataTable } from '../../components/ui/DataTable';
import { Badge } from '../../components/ui/Badge';
import { Modal } from '../../components/ui/Modal';
import { Tabs } from '../../components/ui/Tabs';
import { LoadingState, ErrorState } from '../../components/ui/States';
import type { LadderControlStoreBinding, Site } from '../../types/domain';

interface CreateModalProps {
  open: boolean;
  organizationId: string;
  sites: Site[];
  boundSiteIds: Set<string>;
  onClose: () => void;
  onSaved: () => void;
}

function NewStoreBindingModal({ open, organizationId, sites, boundSiteIds, onClose, onSaved }: CreateModalProps) {
  const toast = useToast();
  const [siteId, setSiteId] = useState('');
  const [manufacturer, setManufacturer] = useState('');
  const [buildingId, setBuildingId] = useState('');
  const [clientId, setClientId] = useState('');
  const [submitting, setSubmitting] = useState(false);

  const availableSites = sites.filter((s) => !boundSiteIds.has(s.id));

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setSubmitting(true);
    try {
      await createLadderControlBinding({
        organizationId,
        siteId,
        manufacturer,
        buildingId: buildingId || null,
        clientId: clientId || null,
      });
      toast.show('Store binding created', 'success');
      onSaved();
      onClose();
      setSiteId(''); setManufacturer(''); setBuildingId(''); setClientId('');
    } catch (err) {
      toast.show(err instanceof ApiRequestError ? err.message : 'Failed to create store binding', 'error');
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <Modal open={open} title="New Store Binding" onClose={onClose}>
      <form onSubmit={handleSubmit}>
        <div className="sakar-field">
          <label htmlFor="binding-store">Store</label>
          <select id="binding-store" required value={siteId} onChange={(e) => setSiteId(e.target.value)}>
            <option value="">Select a store…</option>
            {availableSites.map((s) => <option key={s.id} value={s.id}>{s.name}</option>)}
          </select>
        </div>
        <div className="sakar-field">
          <label htmlFor="binding-manufacturer">Ladder control manufacturer</label>
          <input id="binding-manufacturer" required value={manufacturer} onChange={(e) => setManufacturer(e.target.value)} />
        </div>
        <div className="sakar-field">
          <label htmlFor="binding-building-id">Ladder control building id</label>
          <input id="binding-building-id" value={buildingId} onChange={(e) => setBuildingId(e.target.value)} />
        </div>
        <div className="sakar-field">
          <label htmlFor="binding-client-id">Ladder control clientId</label>
          <input id="binding-client-id" value={clientId} onChange={(e) => setClientId(e.target.value)} />
        </div>
        <button type="submit" className="sakar-btn sakar-btn--primary" disabled={submitting || !siteId || !manufacturer}>
          {submitting ? 'Saving…' : 'Create'}
        </button>
      </form>
    </Modal>
  );
}

type TabKey = 'binding' | 'equipment' | 'delivered';

// IoT Platform → Elevator Module → Cloud ladder control configuration.
// "Store binding" is real CRUD (GET/POST /api/v1/iot/ladder-control-bindings).
// "Tripartite ladder control equipment management" is a derived, real view —
// the existing elevator device registry filtered to bound stores, not a
// second copy of that data. "The elevator configuration is delivered" is the
// real, read-only bookkeeping list from Elevator configuration's own
// "Deliver" action.
export function CloudLadderControlPage() {
  const { user, hasPermission } = useAuth();
  const [tab, setTab] = useState<TabKey>('binding');
  const [showCreate, setShowCreate] = useState(false);
  const canConfigure = hasPermission('ROBOT_CONFIGURE');

  const { data: bindings, status: bindingsStatus, error: bindingsError, refetch: refetchBindings } = useApi(() => listLadderControlBindings(), []);
  const { data: sites } = useApi(() => listAllAccessibleSites(), []);
  const { data: devices, status: devicesStatus, error: devicesError } = useApi(() => listElevatorDevices(), []);
  const { data: deliveries, status: deliveriesStatus, error: deliveriesError } = useApi(() => listElevatorConfigurationDeliveries(), []);
  const { data: configurations } = useApi(() => listElevatorConfigurations(), []);
  const { data: robotsPage } = useApi(() => listRobots(0, 200), []);

  const sitesById = useMemo(() => new Map((sites ?? []).map((s) => [s.id, s])), [sites]);
  const boundSiteIds = useMemo(() => new Set((bindings ?? []).map((b) => b.siteId)), [bindings]);
  const configurationsById = useMemo(() => new Map((configurations ?? []).map((c) => [c.id, c])), [configurations]);
  const robotsById = useMemo(() => new Map((robotsPage?.content ?? []).map((r) => [r.id, r])), [robotsPage]);

  const equipmentRows = useMemo(() => (devices ?? []).filter((d) => boundSiteIds.has(d.siteId)), [devices, boundSiteIds]);

  if (bindingsStatus === 'loading' || bindingsStatus === 'idle') {
    return <LoadingState title="Loading cloud ladder control configuration…" />;
  }
  if (bindingsStatus === 'error') {
    return (
      <ErrorState
        title="Could not load store bindings"
        detail={bindingsError ?? undefined}
        action={<button type="button" className="sakar-btn sakar-btn--secondary" onClick={refetchBindings}>Retry</button>}
      />
    );
  }

  return (
    <div>
      <Breadcrumb items={[{ label: 'IoT Platform' }, { label: 'Cloud ladder control configuration' }]} />
      <PageHeader
        title="Cloud ladder control configuration"
        subtitle={'Third-party ("tripartite") ladder-control vendor bindings per store.'}
        actions={tab === 'binding' && canConfigure && (
          <button type="button" className="sakar-btn sakar-btn--primary" onClick={() => setShowCreate(true)}>New store binding</button>
        )}
      />

      <Card>
        <Tabs
          ariaLabel="Cloud ladder control configuration views"
          tabs={[
            { key: 'binding', label: 'Store binding' },
            { key: 'equipment', label: 'Tripartite ladder control equipment management' },
            { key: 'delivered', label: 'The elevator configuration is delivered' },
          ]}
          active={tab}
          onChange={setTab}
        />
      </Card>

      {tab === 'binding' && (
        <Card title={`Bindings (${(bindings ?? []).length})`}>
          <DataTable
            rows={bindings ?? []}
            rowKey={(b) => b.id}
            emptyTitle="No Data"
            columns={[
              { key: 'store', header: 'Store name', render: (b: LadderControlStoreBinding) => sitesById.get(b.siteId)?.name ?? '—' },
              { key: 'storeId', header: 'Store ID', render: (b: LadderControlStoreBinding) => <span className="sakar-mono" style={{ fontSize: 12 }}>{b.siteId.slice(0, 8)}…</span> },
              { key: 'manufacturer', header: 'Ladder control manufacturer', render: (b: LadderControlStoreBinding) => b.manufacturer },
              { key: 'buildingId', header: 'Ladder control building id', render: (b: LadderControlStoreBinding) => b.buildingId ?? '—' },
              { key: 'clientId', header: 'Ladder control clientId', render: (b: LadderControlStoreBinding) => b.clientId ?? '—' },
            ]}
          />
        </Card>
      )}

      {tab === 'equipment' && (
        devicesStatus === 'error' ? (
          <ErrorState title="Could not load equipment" detail={devicesError ?? undefined} />
        ) : (
          <Card title={`Equipment (${equipmentRows.length})`}>
            <DataTable
              rows={equipmentRows}
              rowKey={(d) => d.id}
              emptyTitle="No Data"
              columns={[
                { key: 'deviceId', header: 'Device ID', render: (d) => d.deviceId },
                { key: 'deviceName', header: 'Device name', render: (d) => d.deviceName ?? '—' },
                { key: 'store', header: 'Store', render: (d) => sitesById.get(d.siteId)?.name ?? '—' },
                { key: 'manufacturer', header: 'Ladder control manufacturer', render: (d) => bindings?.find((b) => b.siteId === d.siteId)?.manufacturer ?? '—' },
              ]}
            />
          </Card>
        )
      )}

      {tab === 'delivered' && (
        deliveriesStatus === 'error' ? (
          <ErrorState title="Could not load delivery records" detail={deliveriesError ?? undefined} />
        ) : (
          <Card title={`Deliveries (${(deliveries ?? []).length})`}>
            <DataTable
              rows={deliveries ?? []}
              rowKey={(d) => String(d.id)}
              emptyTitle="No Data"
              columns={[
                { key: 'configuration', header: 'Configuration name', render: (d) => configurationsById.get(d.elevatorConfigurationId)?.name ?? '—' },
                { key: 'robotSn', header: 'Robot SN', render: (d) => robotsById.get(d.robotId)?.serialNumber ?? d.robotId.slice(0, 8) },
                { key: 'deliveredBy', header: 'Delivered by', render: (d) => d.deliveredBy ?? '—' },
                { key: 'status', header: 'Status', render: (d) => <Badge tone={d.status === 'FAILED' ? 'danger' : 'neutral'} dot>{d.status === 'FAILED' ? 'Failed' : 'Recorded'}</Badge> },
                { key: 'createdAt', header: 'Delivery time', render: (d) => new Date(d.createdAt).toLocaleString() },
              ]}
            />
          </Card>
        )
      )}

      {user?.organizationId && (
        <NewStoreBindingModal
          open={showCreate}
          organizationId={user.organizationId}
          sites={sites ?? []}
          boundSiteIds={boundSiteIds}
          onClose={() => setShowCreate(false)}
          onSaved={refetchBindings}
        />
      )}
    </div>
  );
}
