import { useEffect, useMemo, useState, type FormEvent } from 'react';
import {
  createElevatorConfiguration,
  deliverElevatorConfiguration,
  listElevatorConfigurationEvents,
  listElevatorConfigurations,
  listElevatorDevices,
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
import { Modal } from '../../components/ui/Modal';
import { Tabs } from '../../components/ui/Tabs';
import { LoadingState, ErrorState } from '../../components/ui/States';
import type { ElevatorConfiguration, ElevatorConfigurationEvent, ElevatorDevice, Robot, Site } from '../../types/domain';

interface CreateModalProps {
  open: boolean;
  organizationId: string;
  sites: Site[];
  devices: ElevatorDevice[];
  robots: Robot[];
  onClose: () => void;
  onSaved: () => void;
}

function CreateConfigurationModal({ open, organizationId, sites, devices, robots, onClose, onSaved }: CreateModalProps) {
  const toast = useToast();
  const [siteId, setSiteId] = useState('');
  const [elevatorDeviceId, setElevatorDeviceId] = useState('');
  const [robotId, setRobotId] = useState('');
  const [name, setName] = useState('');
  const [submitting, setSubmitting] = useState(false);

  const devicesForSite = siteId ? devices.filter((d) => d.siteId === siteId) : devices;
  const robotsForSite = siteId ? robots.filter((r) => r.siteId === siteId) : robots;

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setSubmitting(true);
    try {
      await createElevatorConfiguration({ organizationId, siteId, elevatorDeviceId, robotId, name });
      toast.show('Elevator configuration created', 'success');
      onSaved();
      onClose();
      setSiteId(''); setElevatorDeviceId(''); setRobotId(''); setName('');
    } catch (err) {
      toast.show(err instanceof ApiRequestError ? err.message : 'Failed to create configuration', 'error');
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <Modal open={open} title="Create Elevator Configuration" onClose={onClose}>
      <form onSubmit={handleSubmit}>
        <div className="sakar-field">
          <label htmlFor="config-name">Configuration name</label>
          <input id="config-name" required value={name} onChange={(e) => setName(e.target.value)} />
        </div>
        <div className="sakar-field">
          <label htmlFor="config-store">Store</label>
          <select id="config-store" required value={siteId} onChange={(e) => { setSiteId(e.target.value); setElevatorDeviceId(''); setRobotId(''); }}>
            <option value="">Select a store…</option>
            {sites.map((s) => <option key={s.id} value={s.id}>{s.name}</option>)}
          </select>
        </div>
        <div className="sakar-field">
          <label htmlFor="config-elevator">Elevator</label>
          <select id="config-elevator" required value={elevatorDeviceId} onChange={(e) => setElevatorDeviceId(e.target.value)}>
            <option value="">Select an elevator…</option>
            {devicesForSite.map((d) => <option key={d.id} value={d.id}>{d.deviceName ?? d.deviceId}</option>)}
          </select>
        </div>
        <div className="sakar-field">
          <label htmlFor="config-robot">Robot SN</label>
          <select id="config-robot" required value={robotId} onChange={(e) => setRobotId(e.target.value)}>
            <option value="">Select a robot…</option>
            {robotsForSite.map((r) => <option key={r.id} value={r.id}>{r.name} ({r.serialNumber})</option>)}
          </select>
        </div>
        <button type="submit" className="sakar-btn sakar-btn--primary" disabled={submitting || !siteId || !elevatorDeviceId || !robotId || !name}>
          {submitting ? 'Saving…' : 'Create'}
        </button>
      </form>
    </Modal>
  );
}

function DeliverModal({ configuration, robots, onClose, onSaved }: {
  configuration: ElevatorConfiguration | null;
  robots: Robot[];
  onClose: () => void;
  onSaved: () => void;
}) {
  const toast = useToast();
  const [robotId, setRobotId] = useState('');
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    if (configuration) setRobotId(configuration.robotId);
  }, [configuration]);

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    if (!configuration || !robotId) return;
    setSubmitting(true);
    try {
      await deliverElevatorConfiguration(configuration.id, robotId);
      toast.show('Delivery recorded', 'success');
      onSaved();
      onClose();
    } catch (err) {
      toast.show(err instanceof ApiRequestError ? err.message : 'Failed to record delivery', 'error');
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <Modal open={configuration !== null} title={configuration ? `Deliver "${configuration.name}"` : 'Deliver'} onClose={onClose}>
      <p style={{ marginTop: 0, fontSize: 13, color: 'var(--sakar-text-faint)' }}>
        This records that you delivered this configuration to a robot. No delivery channel to a physical elevator
        controller exists yet, so this is a bookkeeping record only.
      </p>
      <form onSubmit={handleSubmit}>
        <div className="sakar-field">
          <label htmlFor="deliver-robot">Robot</label>
          <select id="deliver-robot" required value={robotId} onChange={(e) => setRobotId(e.target.value)}>
            <option value="">Select a robot…</option>
            {robots.map((r) => <option key={r.id} value={r.id}>{r.name} ({r.serialNumber})</option>)}
          </select>
        </div>
        <button type="submit" className="sakar-btn sakar-btn--primary" disabled={submitting || !robotId}>
          {submitting ? 'Recording…' : 'Deliver'}
        </button>
      </form>
    </Modal>
  );
}

function SetRecordTab({ configurations }: { configurations: ElevatorConfiguration[] }) {
  const [events, setEvents] = useState<(ElevatorConfigurationEvent & { configurationName: string })[] | null>(null);

  useEffect(() => {
    let cancelled = false;
    if (configurations.length === 0) {
      setEvents([]);
      return;
    }
    Promise.allSettled(configurations.map((c) => listElevatorConfigurationEvents(c.id))).then((results) => {
      if (cancelled) return;
      const merged = results.flatMap((result, i) =>
        result.status === 'fulfilled' ? result.value.map((e) => ({ ...e, configurationName: configurations[i].name })) : [],
      );
      merged.sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime());
      setEvents(merged);
    });
    return () => { cancelled = true; };
  }, [configurations]);

  if (events === null) {
    return <LoadingState title="Loading set record…" />;
  }

  return (
    <Card title={`set record (${events.length})`}>
      <DataTable
        rows={events}
        rowKey={(e) => `${e.configurationName}-${e.id}`}
        emptyTitle="No Data"
        columns={[
          { key: 'configuration', header: 'Configuration name', render: (e) => e.configurationName },
          { key: 'eventType', header: 'Change', render: (e) => e.eventType },
          { key: 'detail', header: 'Detail', render: (e) => e.detail ?? '—' },
          { key: 'createdAt', header: 'Update time', render: (e) => new Date(e.createdAt).toLocaleString() },
        ]}
      />
    </Card>
  );
}

// IoT Platform → Elevator Module → Elevator configuration. Real CRUD binding
// an existing ElevatorDevice + Robot for a Site (GET/POST/PUT
// /api/v1/iot/elevator-configurations); "set record" is that configuration's
// own real change history (GET .../events), merged fleet-wide here.
export function ElevatorConfigurationPage() {
  const { user, hasPermission } = useAuth();
  const { data: configurations, status, error, refetch } = useApi(() => listElevatorConfigurations(), []);
  const { data: sites } = useApi(() => listAllAccessibleSites(), []);
  const { data: devices } = useApi(() => listElevatorDevices(), []);
  const { data: robotsPage } = useApi(() => listRobots(0, 200), []);
  const [tab, setTab] = useState<'configuration' | 'record'>('configuration');
  const [showCreate, setShowCreate] = useState(false);
  const [delivering, setDelivering] = useState<ElevatorConfiguration | null>(null);
  const canConfigure = hasPermission('ROBOT_CONFIGURE');

  const sitesById = useMemo(() => new Map((sites ?? []).map((s) => [s.id, s])), [sites]);
  const devicesById = useMemo(() => new Map((devices ?? []).map((d) => [d.id, d])), [devices]);
  const robotsById = useMemo(() => new Map((robotsPage?.content ?? []).map((r) => [r.id, r])), [robotsPage]);

  const rows = configurations ?? [];

  if (status === 'loading' || status === 'idle') {
    return <LoadingState title="Loading elevator configurations…" />;
  }
  if (status === 'error') {
    return (
      <ErrorState
        title="Could not load elevator configurations"
        detail={error ?? undefined}
        action={<button type="button" className="sakar-btn sakar-btn--secondary" onClick={refetch}>Retry</button>}
      />
    );
  }

  return (
    <div>
      <Breadcrumb items={[{ label: 'IoT Platform' }, { label: 'Elevator configuration' }]} />
      <PageHeader
        title="Elevator configuration"
        subtitle="Binds a robot to an elevator device for a store, so the robot can call it."
        actions={tab === 'configuration' && canConfigure && (
          <button type="button" className="sakar-btn sakar-btn--primary" onClick={() => setShowCreate(true)}>Create elevator configuration</button>
        )}
      />

      <Card>
        <Tabs
          ariaLabel="Elevator configuration views"
          tabs={[{ key: 'configuration', label: 'Elevator configuration' }, { key: 'record', label: 'set record' }]}
          active={tab}
          onChange={setTab}
        />
      </Card>

      {tab === 'configuration' ? (
        <Card title={`Configurations (${rows.length})`}>
          <DataTable
            rows={rows}
            rowKey={(c) => c.id}
            emptyTitle="No Data"
            columns={[
              { key: 'name', header: 'Configuration name', render: (c: ElevatorConfiguration) => c.name },
              { key: 'store', header: 'Store', render: (c: ElevatorConfiguration) => sitesById.get(c.siteId)?.name ?? '—' },
              { key: 'elevator', header: 'Elevator', render: (c: ElevatorConfiguration) => devicesById.get(c.elevatorDeviceId)?.deviceName ?? devicesById.get(c.elevatorDeviceId)?.deviceId ?? '—' },
              { key: 'robotSn', header: 'Robot SN', render: (c: ElevatorConfiguration) => robotsById.get(c.robotId)?.serialNumber ?? c.robotId.slice(0, 8) },
              { key: 'modifiedBy', header: 'Modified by', render: (c: ElevatorConfiguration) => c.modifiedBy ?? '—' },
              { key: 'updated', header: 'Update time', render: (c: ElevatorConfiguration) => new Date(c.updatedAt).toLocaleString() },
              ...(canConfigure
                ? [{
                    key: 'action',
                    header: 'Operate',
                    align: 'right' as const,
                    render: (c: ElevatorConfiguration) => (
                      <button type="button" className="sakar-btn sakar-btn--secondary sakar-btn--sm" onClick={() => setDelivering(c)}>Deliver</button>
                    ),
                  }]
                : []),
            ]}
          />
        </Card>
      ) : (
        <SetRecordTab configurations={rows} />
      )}

      {user?.organizationId && (
        <CreateConfigurationModal
          open={showCreate}
          organizationId={user.organizationId}
          sites={sites ?? []}
          devices={devices ?? []}
          robots={robotsPage?.content ?? []}
          onClose={() => setShowCreate(false)}
          onSaved={refetch}
        />
      )}
      <DeliverModal configuration={delivering} robots={robotsPage?.content ?? []} onClose={() => setDelivering(null)} onSaved={refetch} />
    </div>
  );
}
