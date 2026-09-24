import { useMemo, useState, type FormEvent } from 'react';
import { listPhoneDevices, registerPhoneDevice } from '../../api/iot';
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
import type { Site } from '../../types/domain';

const NO_TELEMETRY_REASON = 'No phone/intercom vendor telemetry channel exists yet — this cannot be reported honestly, so it always shows Unknown.';

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
  const [networkingMode, setNetworkingMode] = useState('');
  const [submitting, setSubmitting] = useState(false);

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setSubmitting(true);
    try {
      await registerPhoneDevice({ organizationId, siteId, deviceId, deviceName: deviceName || null, networkingMode: networkingMode || null });
      toast.show('Phone device registered', 'success');
      onSaved();
      onClose();
      setSiteId(''); setDeviceId(''); setDeviceName(''); setNetworkingMode('');
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
          <label htmlFor="phone-store">Store</label>
          <select id="phone-store" required value={siteId} onChange={(e) => setSiteId(e.target.value)}>
            <option value="">Select a store…</option>
            {sites.map((s) => <option key={s.id} value={s.id}>{s.name}</option>)}
          </select>
        </div>
        <div className="sakar-field">
          <label htmlFor="phone-device-id">Device ID</label>
          <input id="phone-device-id" required value={deviceId} onChange={(e) => setDeviceId(e.target.value)} />
        </div>
        <div className="sakar-field">
          <label htmlFor="phone-device-name">Device name</label>
          <input id="phone-device-name" value={deviceName} onChange={(e) => setDeviceName(e.target.value)} />
        </div>
        <div className="sakar-field">
          <label htmlFor="phone-networking-mode">Networking mode</label>
          <input id="phone-networking-mode" value={networkingMode} onChange={(e) => setNetworkingMode(e.target.value)} />
        </div>
        <button type="submit" className="sakar-btn sakar-btn--primary" disabled={submitting || !siteId || !deviceId}>
          {submitting ? 'Saving…' : 'Device input'}
        </button>
      </form>
    </Modal>
  );
}

// IoT Platform → Phone Module → Device management. Real device registry
// (GET/POST /api/v1/iot/phone-devices) — "Online status" always shows
// "Unknown": no phone/intercom vendor telemetry channel exists here.
export function PhoneDeviceManagementPage() {
  const { user, hasPermission } = useAuth();
  const { data: devices, status, error, refetch } = useApi(() => listPhoneDevices(), []);
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
      return `${d.deviceId} ${d.deviceName ?? ''} ${site?.name ?? ''}`.toLowerCase().includes(term);
    });
  }, [devices, sitesById, search]);

  if (status === 'loading' || status === 'idle') {
    return <LoadingState title="Loading phone devices…" />;
  }
  if (status === 'error') {
    return (
      <ErrorState
        title="Could not load phone devices"
        detail={error ?? undefined}
        action={<button type="button" className="sakar-btn sakar-btn--secondary" onClick={refetch}>Retry</button>}
      />
    );
  }

  return (
    <div>
      <Breadcrumb items={[{ label: 'IoT Platform' }, { label: 'Phone device management' }]} />
      <PageHeader title="Phone device management" subtitle="Registered phone/intercom devices across the stores you can access." />

      <Card>
        <div style={{ display: 'flex', gap: 12, alignItems: 'flex-end', justifyContent: 'space-between', flexWrap: 'wrap' }}>
          <div className="sakar-field" style={{ marginBottom: 0, maxWidth: 320 }}>
            <label htmlFor="phone-search">Store / Device name</label>
            <input id="phone-search" value={search} onChange={(e) => setSearch(e.target.value)} placeholder="Search…" />
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
            { key: 'deviceId', header: 'Device ID', render: (d) => d.deviceId },
            { key: 'deviceName', header: 'Device name', render: (d) => d.deviceName ?? '—' },
            {
              key: 'online',
              header: 'Online status',
              render: () => (
                <span title={NO_TELEMETRY_REASON}>
                  <Badge tone="neutral" dot>Unknown</Badge>
                </span>
              ),
            },
            { key: 'store', header: 'Store', render: (d) => sitesById.get(d.siteId)?.name ?? '—' },
            { key: 'createdAt', header: 'Creation time', render: (d) => new Date(d.createdAt).toLocaleString() },
            { key: 'networkingMode', header: 'Networking mode', render: (d) => d.networkingMode ?? '—' },
          ]}
        />
      </Card>

      {user?.organizationId && (
        <DeviceInputModal open={showCreate} organizationId={user.organizationId} sites={sites ?? []} onClose={() => setShowCreate(false)} onSaved={refetch} />
      )}
    </div>
  );
}
