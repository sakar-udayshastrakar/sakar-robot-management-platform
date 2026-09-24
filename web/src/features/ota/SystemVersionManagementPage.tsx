import { useState, type FormEvent } from 'react';
import { createSoftwareVersion, listSoftwareVersions, pushSoftwareVersion } from '../../api/ota';
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
import { LoadingState, ErrorState } from '../../components/ui/States';
import type { SoftwareVersion } from '../../types/domain';

interface VersionFormState {
  packageName: string;
  wholeMachineSoftware: string;
  packageVersion: string;
  hardwareVersion: string;
  grayscale: boolean;
}

const EMPTY_FORM: VersionFormState = { packageName: '', wholeMachineSoftware: '', packageVersion: '', hardwareVersion: '', grayscale: false };

function AddVersionModal({ open, organizationId, onClose, onSaved }: { open: boolean; organizationId: string; onClose: () => void; onSaved: () => void }) {
  const toast = useToast();
  const [form, setForm] = useState<VersionFormState>(EMPTY_FORM);
  const [submitting, setSubmitting] = useState(false);

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setSubmitting(true);
    try {
      await createSoftwareVersion({
        organizationId,
        packageName: form.packageName,
        wholeMachineSoftware: form.wholeMachineSoftware || null,
        packageVersion: form.packageVersion,
        hardwareVersion: form.hardwareVersion || null,
        grayscale: form.grayscale,
      });
      toast.show('Version registered', 'success');
      onSaved();
      onClose();
      setForm(EMPTY_FORM);
    } catch (err) {
      toast.show(err instanceof ApiRequestError ? err.message : 'Failed to register version', 'error');
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <Modal open={open} title="Register Software Version" onClose={onClose}>
      <form onSubmit={handleSubmit}>
        <div className="sakar-field">
          <label htmlFor="version-package-name">Whole package name</label>
          <input id="version-package-name" required value={form.packageName} onChange={(e) => setForm((f) => ({ ...f, packageName: e.target.value }))} />
        </div>
        <div className="sakar-field">
          <label htmlFor="version-software">Whole machine software</label>
          <input id="version-software" value={form.wholeMachineSoftware} onChange={(e) => setForm((f) => ({ ...f, wholeMachineSoftware: e.target.value }))} />
        </div>
        <div className="sakar-field">
          <label htmlFor="version-package-version">Whole package version</label>
          <input id="version-package-version" required value={form.packageVersion} onChange={(e) => setForm((f) => ({ ...f, packageVersion: e.target.value }))} />
        </div>
        <div className="sakar-field">
          <label htmlFor="version-hardware">Whole machine hardware version</label>
          <input id="version-hardware" value={form.hardwareVersion} onChange={(e) => setForm((f) => ({ ...f, hardwareVersion: e.target.value }))} />
        </div>
        <div className="sakar-field">
          <label style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            <input type="checkbox" checked={form.grayscale} onChange={(e) => setForm((f) => ({ ...f, grayscale: e.target.checked }))} />
            Grayscale package
          </label>
        </div>
        <button type="submit" className="sakar-btn sakar-btn--primary" disabled={submitting}>
          {submitting ? 'Saving…' : 'Add'}
        </button>
      </form>
    </Modal>
  );
}

function PushModal({ version, onClose, onSaved }: { version: SoftwareVersion | null; onClose: () => void; onSaved: () => void }) {
  const toast = useToast();
  const { data: robots } = useApi(() => listRobots(0, 100), []);
  const [robotId, setRobotId] = useState('');
  const [oldVersionNumber, setOldVersionNumber] = useState('');
  const [submitting, setSubmitting] = useState(false);

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    if (!version || !robotId) return;
    setSubmitting(true);
    try {
      await pushSoftwareVersion(version.id, robotId, oldVersionNumber || null);
      toast.show('Push recorded', 'success');
      onSaved();
      onClose();
      setRobotId('');
      setOldVersionNumber('');
    } catch (err) {
      toast.show(err instanceof ApiRequestError ? err.message : 'Failed to record push', 'error');
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <Modal open={version !== null} title={version ? `Push ${version.packageVersion}` : 'Push'} onClose={onClose}>
      <p style={{ marginTop: 0, fontSize: 13, color: 'var(--sakar-text-faint)' }}>
        This records that you pushed this version to a robot. No OTA delivery channel exists yet, so this is a
        bookkeeping record only — it does not deliver or confirm anything on the robot itself.
      </p>
      <form onSubmit={handleSubmit}>
        <div className="sakar-field">
          <label htmlFor="push-robot">Robot</label>
          <select id="push-robot" required value={robotId} onChange={(e) => setRobotId(e.target.value)}>
            <option value="">Select a robot…</option>
            {(robots?.content ?? []).map((r) => <option key={r.id} value={r.id}>{r.name} ({r.serialNumber})</option>)}
          </select>
        </div>
        <div className="sakar-field">
          <label htmlFor="push-old-version">Old version number (optional)</label>
          <input id="push-old-version" value={oldVersionNumber} onChange={(e) => setOldVersionNumber(e.target.value)} />
        </div>
        <button type="submit" className="sakar-btn sakar-btn--primary" disabled={submitting || !robotId}>
          {submitting ? 'Pushing…' : 'Push'}
        </button>
      </form>
    </Modal>
  );
}

// OTA Management → System Version Management. Real CRUD + push-record
// (GET/POST /api/v1/ota/versions, POST /api/v1/ota/versions/{id}/push).
export function SystemVersionManagementPage() {
  const { user, hasPermission } = useAuth();
  const { data: versions, status, error, refetch } = useApi(() => listSoftwareVersions(), []);
  const [showAdd, setShowAdd] = useState(false);
  const [pushingVersion, setPushingVersion] = useState<SoftwareVersion | null>(null);
  const canConfigure = hasPermission('ROBOT_CONFIGURE');

  if (status === 'loading' || status === 'idle') {
    return <LoadingState title="Loading versions…" />;
  }
  if (status === 'error') {
    return (
      <ErrorState
        title="Could not load versions"
        detail={error ?? undefined}
        action={<button type="button" className="sakar-btn sakar-btn--secondary" onClick={refetch}>Retry</button>}
      />
    );
  }

  const rows = versions ?? [];

  return (
    <div>
      <Breadcrumb items={[{ label: 'OTA Management' }, { label: 'System Version Management' }]} />
      <PageHeader
        title="System Version Management"
        subtitle="Software/firmware package versions and their push history."
        actions={canConfigure && (
          <button type="button" className="sakar-btn sakar-btn--primary" onClick={() => setShowAdd(true)}>Add</button>
        )}
      />
      <Card title={`Whole package versions (${rows.length})`}>
        <DataTable
          rows={rows}
          rowKey={(v) => v.id}
          emptyTitle="No versions registered yet"
          columns={[
            { key: 'packageName', header: 'Whole package name', render: (v) => v.packageName },
            { key: 'software', header: 'Whole machine software', render: (v) => v.wholeMachineSoftware ?? '—' },
            { key: 'version', header: 'Whole package version', render: (v) => v.packageVersion },
            { key: 'hardware', header: 'Whole machine hardware version', render: (v) => v.hardwareVersion ?? '—' },
            { key: 'grayscale', header: 'Gray version or not', render: (v) => <Badge tone={v.grayscale ? 'warning' : 'neutral'}>{v.grayscale ? 'Yes' : 'No'}</Badge> },
            { key: 'size', header: 'Size', render: (v) => (v.sizeBytes != null ? `${(v.sizeBytes / 1_000_000).toFixed(1)} MB` : '—') },
            { key: 'creator', header: 'Creator', render: (v) => v.createdBy ?? '—' },
            { key: 'created', header: 'Creation time', render: (v) => new Date(v.createdAt).toLocaleString() },
            ...(canConfigure
              ? [{
                  key: 'actions',
                  header: 'Operate',
                  align: 'right' as const,
                  render: (v: SoftwareVersion) => (
                    <button type="button" className="sakar-btn sakar-btn--secondary sakar-btn--sm" onClick={() => setPushingVersion(v)}>Push</button>
                  ),
                }]
              : []),
          ]}
        />
      </Card>

      {user?.organizationId && (
        <AddVersionModal open={showAdd} organizationId={user.organizationId} onClose={() => setShowAdd(false)} onSaved={refetch} />
      )}
      <PushModal version={pushingVersion} onClose={() => setPushingVersion(null)} onSaved={refetch} />
    </div>
  );
}
