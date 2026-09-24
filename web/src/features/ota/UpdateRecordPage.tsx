import { useMemo } from 'react';
import { listDeploymentRecords, listSoftwareVersions } from '../../api/ota';
import { listRobots } from '../../api/robots';
import { useApi } from '../../hooks/useApi';
import { Breadcrumb } from '../../components/ui/Breadcrumb';
import { PageHeader } from '../../components/ui/PageHeader';
import { Card } from '../../components/ui/Card';
import { DataTable } from '../../components/ui/DataTable';
import { Badge } from '../../components/ui/Badge';
import { LoadingState, ErrorState } from '../../components/ui/States';

// OTA Management → Update record. Real, read-only, append-only push
// history (GET /api/v1/ota/deployment-records) — "Machine SN"/"Whole
// package name" are resolved from the existing robots/software-versions
// lookups, never duplicated fields.
export function UpdateRecordPage() {
  const { data: records, status, error, refetch } = useApi(() => listDeploymentRecords(), []);
  const { data: robots } = useApi(() => listRobots(0, 100), []);
  const { data: versions } = useApi(() => listSoftwareVersions(), []);

  const robotSerials = useMemo(() => new Map((robots?.content ?? []).map((r) => [r.id, r.serialNumber])), [robots]);
  const versionPackageNames = useMemo(() => new Map((versions ?? []).map((v) => [v.id, v.packageName])), [versions]);

  if (status === 'loading' || status === 'idle') {
    return <LoadingState title="Loading update records…" />;
  }
  if (status === 'error') {
    return (
      <ErrorState
        title="Could not load update records"
        detail={error ?? undefined}
        action={<button type="button" className="sakar-btn sakar-btn--secondary" onClick={refetch}>Retry</button>}
      />
    );
  }

  const rows = records ?? [];

  return (
    <div>
      <Breadcrumb items={[{ label: 'OTA Management' }, { label: 'Update record' }]} />
      <PageHeader title="Update record" subtitle={'Recorded pushes, newest first — "Recorded" means an operator logged the push, not that the robot confirmed it.'} />
      <Card title={`Records (${rows.length})`}>
        <DataTable
          rows={rows}
          rowKey={(r) => String(r.id)}
          emptyTitle="No update records yet"
          emptyDetail="Push a version from System Version Management to record one."
          columns={[
            { key: 'taskId', header: 'Task ID', render: (r) => <span className="sakar-mono" style={{ fontSize: 12 }}>{r.id}</span> },
            { key: 'machineSn', header: 'Machine SN', render: (r) => <span className="sakar-mono" style={{ fontSize: 12 }}>{robotSerials.get(r.robotId) ?? r.robotId}</span> },
            { key: 'packageName', header: 'Whole package name', render: (r) => versionPackageNames.get(r.softwareVersionId) ?? '—' },
            { key: 'oldVersion', header: 'Old version number', render: (r) => r.oldVersionNumber ?? '—' },
            { key: 'newVersion', header: 'New version number', render: (r) => r.newVersionNumber },
            { key: 'grayscale', header: 'Gray version or not', render: (r) => (r.grayscale ? 'Yes' : 'No') },
            {
              key: 'status',
              header: 'Status',
              render: (r) => (
                <Badge tone={r.status === 'FAILED' ? 'danger' : 'neutral'} dot>
                  {r.status === 'FAILED' ? 'Failed' : 'Recorded'}
                </Badge>
              ),
            },
            { key: 'error', header: 'Error message', render: (r) => r.errorMessage ?? '—' },
            { key: 'created', header: 'Upgrade time', render: (r) => new Date(r.createdAt).toLocaleString() },
          ]}
        />
      </Card>
    </div>
  );
}
