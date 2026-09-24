import { useMemo, useState } from 'react';
import { listRobots } from '../../api/robots';
import { listAllAccessibleSites } from '../../api/sites';
import { listRobotModels } from '../../api/robotModels';
import { useApi } from '../../hooks/useApi';
import { Breadcrumb } from '../../components/ui/Breadcrumb';
import { PageHeader } from '../../components/ui/PageHeader';
import { Card } from '../../components/ui/Card';
import { DataTable } from '../../components/ui/DataTable';
import { Badge } from '../../components/ui/Badge';
import { LoadingState, ErrorState } from '../../components/ui/States';
import type { Robot } from '../../types/domain';

const STREAMING_DISABLED_REASON = 'No device-streaming channel exists yet — this action cannot do anything real.';

// Operation And Maintenance Platform → Device Management. Reuses the
// existing Robot/Site/RobotModel data (Robot List, Store Management, Robot
// Models) end to end — no new backend, no duplicated fields. The reference
// product's "remote desktop / Monitor / Playback" actions are disabled: no
// device video/streaming channel exists in this codebase, and faking a
// working button would misrepresent what this platform can actually do.
export function DeviceManagementPage() {
  const { data: robotsPage, status, error, refetch } = useApi(() => listRobots(0, 200), []);
  const { data: sites } = useApi(() => listAllAccessibleSites(), []);
  const { data: models } = useApi(() => listRobotModels(), []);
  const [search, setSearch] = useState('');

  const sitesById = useMemo(() => new Map((sites ?? []).map((s) => [s.id, s])), [sites]);
  const modelsById = useMemo(() => new Map((models ?? []).map((m) => [m.id, m])), [models]);

  const rows = useMemo(() => {
    const robots = robotsPage?.content ?? [];
    const term = search.trim().toLowerCase();
    if (!term) return robots;
    return robots.filter((r) => {
      const site = r.siteId ? sitesById.get(r.siteId) : undefined;
      const haystack = `${r.serialNumber} ${r.vendorSerialNumber ?? ''} ${site?.name ?? ''}`.toLowerCase();
      return haystack.includes(term);
    });
  }, [robotsPage, sitesById, search]);

  if (status === 'loading' || status === 'idle') {
    return <LoadingState title="Loading devices…" />;
  }
  if (status === 'error') {
    return (
      <ErrorState
        title="Could not load devices"
        detail={error ?? undefined}
        action={<button type="button" className="sakar-btn sakar-btn--secondary" onClick={refetch}>Retry</button>}
      />
    );
  }

  return (
    <div>
      <Breadcrumb items={[{ label: 'Operation And Maintenance Platform' }, { label: 'Device Management' }]} />
      <PageHeader title="Device Management" subtitle="Every registered device you can access — the same registry as Robot List, in the reference product's own column layout." />

      <Card>
        <div className="sakar-field" style={{ marginBottom: 0, maxWidth: 320 }}>
          <label htmlFor="device-search">Production No. / Machine SN / Store Name</label>
          <input id="device-search" value={search} onChange={(e) => setSearch(e.target.value)} placeholder="Search…" />
        </div>
      </Card>

      <Card title={`Devices (${rows.length})`}>
        <DataTable
          rows={rows}
          rowKey={(r) => r.id}
          emptyTitle="No Data"
          columns={[
            { key: 'machineSn', header: 'Machine SN', render: (r: Robot) => <span className="sakar-mono" style={{ fontSize: 12 }}>{r.serialNumber}</span> },
            { key: 'store', header: 'Store Name', render: (r: Robot) => (r.siteId && sitesById.get(r.siteId)?.name) ?? '—' },
            { key: 'productionNo', header: 'Production No.', render: (r: Robot) => r.vendorSerialNumber ?? '—' },
            { key: 'model', header: 'model', render: (r: Robot) => modelsById.get(r.robotModelId)?.sakarProductName ?? modelsById.get(r.robotModelId)?.name ?? '—' },
            {
              key: 'network',
              header: 'network',
              render: (r: Robot) => (
                <Badge tone={r.connectionStatus === 'ONLINE' ? 'success' : r.connectionStatus === 'OFFLINE' ? 'neutral' : 'warning'} dot>
                  {r.connectionStatus === 'ONLINE' ? 'Online' : r.connectionStatus === 'OFFLINE' ? 'Wi-Fi Offline' : 'Unknown'}
                </Badge>
              ),
            },
            { key: 'lastOnline', header: 'Last online time', render: (r: Robot) => (r.lastSeenAt ? new Date(r.lastSeenAt).toLocaleString() : '—') },
            { key: 'warrantyEnd', header: 'Warranty end time', render: (r: Robot) => r.warrantyEndDate ?? '—' },
            {
              key: 'action',
              header: 'Action',
              align: 'right' as const,
              render: () => (
                <div style={{ display: 'flex', gap: 6, justifyContent: 'flex-end' }}>
                  <button type="button" className="sakar-btn sakar-btn--secondary sakar-btn--sm" disabled title={STREAMING_DISABLED_REASON}>remote desktop</button>
                  <button type="button" className="sakar-btn sakar-btn--secondary sakar-btn--sm" disabled title={STREAMING_DISABLED_REASON}>Monitor</button>
                  <button type="button" className="sakar-btn sakar-btn--secondary sakar-btn--sm" disabled title={STREAMING_DISABLED_REASON}>Playback</button>
                </div>
              ),
            },
          ]}
        />
      </Card>
    </div>
  );
}
