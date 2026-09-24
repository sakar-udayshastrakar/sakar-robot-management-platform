import { useMemo, useState, type FormEvent } from 'react';
import { listElevatorDevices, registerElevatorDevice } from '../../api/iot';
import { listAllAccessibleSites } from '../../api/sites';
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
import { LoadingState, ErrorState } from '../../components/ui/States';
import type { ElevatorDevice, Site } from '../../types/domain';

const NO_TELEMETRY_REASON = 'No elevator vendor telemetry channel exists yet — this cannot be reported honestly, so it always shows Unknown.';

function OnlineStatusBadge() {
  return (
    <span title={NO_TELEMETRY_REASON}>
      <Badge tone="neutral" dot>Unknown</Badge>
    </span>
  );
}

interface CreateModalProps {
  open: boolean;
  organizationId: string;
  sites: Site[];
  onClose: () => void;
  onSaved: () => void;
}

function DeviceInputModal({ open, organizationId, sites, onClose, onSaved }: CreateModalProps) {
  const toast = useToast();
  const [siteId, setSiteId] = useState('');
  const [deviceId, setDeviceId] = useState('');
  const [deviceName, setDeviceName] = useState('');
  const [building, setBuilding] = useState('');
  const [protocol, setProtocol] = useState('');
  const [networkingMode, setNetworkingMode] = useState('');
  const [communicationMode, setCommunicationMode] = useState('');
  const [submitting, setSubmitting] = useState(false);

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setSubmitting(true);
    try {
      await registerElevatorDevice({
        organizationId,
        siteId,
        deviceId,
        deviceName: deviceName || null,
        building: building || null,
        protocol: protocol || null,
        networkingMode: networkingMode || null,
        communicationMode: communicationMode || null,
      });
      toast.show('Elevator device registered', 'success');
      onSaved();
      onClose();
      setSiteId(''); setDeviceId(''); setDeviceName(''); setBuilding(''); setProtocol(''); setNetworkingMode(''); setCommunicationMode('');
    } catch (err) {
      toast.show(err instanceof ApiRequestError ? err.message : 'Failed to register device', 'error');
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <Modal open={open} title="Device Input" onClose={onClose}>
      <form onSubmit={handleSubmit}>
        <div className="sakar-field">
          <label htmlFor="elevator-store">Store</label>
          <select id="elevator-store" required value={siteId} onChange={(e) => setSiteId(e.target.value)}>
            <option value="">Select a store…</option>
            {sites.map((s) => <option key={s.id} value={s.id}>{s.name}</option>)}
          </select>
        </div>
        <div className="sakar-field">
          <label htmlFor="elevator-device-id">Device ID</label>
          <input id="elevator-device-id" required value={deviceId} onChange={(e) => setDeviceId(e.target.value)} />
        </div>
        <div className="sakar-field">
          <label htmlFor="elevator-device-name">Device name</label>
          <input id="elevator-device-name" value={deviceName} onChange={(e) => setDeviceName(e.target.value)} />
        </div>
        <div className="sakar-field">
          <label htmlFor="elevator-building">Elevator building</label>
          <input id="elevator-building" value={building} onChange={(e) => setBuilding(e.target.value)} />
        </div>
        <div className="sakar-field">
          <label htmlFor="elevator-protocol">Elevator protocol</label>
          <input id="elevator-protocol" value={protocol} onChange={(e) => setProtocol(e.target.value)} />
        </div>
        <div className="sakar-field">
          <label htmlFor="elevator-networking-mode">Networking mode</label>
          <input id="elevator-networking-mode" value={networkingMode} onChange={(e) => setNetworkingMode(e.target.value)} />
        </div>
        <div className="sakar-field">
          <label htmlFor="elevator-communication-mode">Communication mode</label>
          <input id="elevator-communication-mode" value={communicationMode} onChange={(e) => setCommunicationMode(e.target.value)} />
        </div>
        <button type="submit" className="sakar-btn sakar-btn--primary" disabled={submitting || !siteId || !deviceId}>
          {submitting ? 'Saving…' : 'Device input'}
        </button>
      </form>
    </Modal>
  );
}

// IoT Platform → Elevator Module → Elevator management. Real device
// registry (GET/POST /api/v1/iot/elevator-devices) — "Store" is the existing
// Site entity. "Online status"/"Slave online status" always show "Unknown":
// no elevator vendor telemetry channel exists in this codebase.
export function ElevatorManagementPage() {
  const { user, hasPermission } = useAuth();
  const { data: devices, status, error, refetch } = useApi(() => listElevatorDevices(), []);
  const { data: sites } = useApi(() => listAllAccessibleSites(), []);
  const [search, setSearch] = useState('');
  const [showCreate, setShowCreate] = useState(false);
  const canConfigure = hasPermission('ROBOT_CONFIGURE');

  const sitesById = useMemo(() => new Map((sites ?? []).map((s) => [s.id, s])), [sites]);

  const rows = useMemo(() => {
    const all = devices ?? [];
    const term = search.trim().toLowerCase();
    if (!term) return all;
    return all.filter((d) => {
      const site = sitesById.get(d.siteId);
      return `${d.deviceId} ${site?.name ?? ''}`.toLowerCase().includes(term);
    });
  }, [devices, sitesById, search]);

  if (status === 'loading' || status === 'idle') {
    return <LoadingState title="Loading elevator devices…" />;
  }
  if (status === 'error') {
    return (
      <ErrorState
        title="Could not load elevator devices"
        detail={error ?? undefined}
        action={<button type="button" className="sakar-btn sakar-btn--secondary" onClick={refetch}>Retry</button>}
      />
    );
  }

  return (
    <div>
      <Breadcrumb items={[{ label: 'IoT Platform' }, { label: 'Elevator management' }]} />
      <PageHeader title="Elevator management" subtitle="Registered elevator devices across the stores you can access." />

      <Card>
        <div style={{ display: 'flex', gap: 12, alignItems: 'flex-end', justifyContent: 'space-between', flexWrap: 'wrap' }}>
          <div className="sakar-field" style={{ marginBottom: 0, maxWidth: 320 }}>
            <label htmlFor="elevator-search">Store / Elevator ID / Device ID</label>
            <input id="elevator-search" value={search} onChange={(e) => setSearch(e.target.value)} placeholder="Search…" />
          </div>
          {canConfigure && (
            <button type="button" className="sakar-btn sakar-btn--primary" onClick={() => setShowCreate(true)}>Device input</button>
          )}
        </div>
      </Card>

      <Card title={`Devices (${rows.length})`}>
        <DataTable
          rows={rows}
          rowKey={(d) => d.id}
          emptyTitle="No Data"
          columns={[
            { key: 'elevatorId', header: 'Elevator ID', render: (d: ElevatorDevice) => <span className="sakar-mono" style={{ fontSize: 12 }}>{d.id.slice(0, 8)}…</span> },
            { key: 'deviceId', header: 'Device ID', render: (d: ElevatorDevice) => d.deviceId },
            { key: 'deviceName', header: 'Device name', render: (d: ElevatorDevice) => d.deviceName ?? '—' },
            { key: 'building', header: 'Elevator building', render: (d: ElevatorDevice) => d.building ?? '—' },
            { key: 'store', header: 'Store', render: (d: ElevatorDevice) => sitesById.get(d.siteId)?.name ?? '—' },
            { key: 'online', header: 'Online status', render: () => <OnlineStatusBadge /> },
            { key: 'slaveOnline', header: 'Slave online status', render: () => <OnlineStatusBadge /> },
            { key: 'protocol', header: 'Elevator protocol', render: (d: ElevatorDevice) => d.protocol ?? '—' },
            { key: 'networkingMode', header: 'Networking mode', render: (d: ElevatorDevice) => d.networkingMode ?? '—' },
            { key: 'communicationMode', header: 'Communication mode', render: (d: ElevatorDevice) => d.communicationMode ?? '—' },
          ]}
        />
      </Card>

      {user?.organizationId && (
        <DeviceInputModal open={showCreate} organizationId={user.organizationId} sites={sites ?? []} onClose={() => setShowCreate(false)} onSaved={refetch} />
      )}
    </div>
  );
}
