import { useMemo, useState, type FormEvent } from 'react';
import { createApplication, listApplications, updateApplication } from '../../api/openPlatform';
import { useApi } from '../../hooks/useApi';
import { useAuth } from '../auth/AuthContext';
import { useToast } from '../../components/ui/Toast';
import { ApiRequestError } from '../../api/client';
import { Breadcrumb } from '../../components/ui/Breadcrumb';
import { PageHeader } from '../../components/ui/PageHeader';
import { Card } from '../../components/ui/Card';
import { DataTable } from '../../components/ui/DataTable';
import { Modal } from '../../components/ui/Modal';
import { LoadingState, ErrorState } from '../../components/ui/States';
import type { OpenPlatformApplication } from '../../types/domain';

function NewApplicationModal({ open, organizationId, onClose, onSaved }: {
  open: boolean;
  organizationId: string;
  onClose: () => void;
  onSaved: () => void;
}) {
  const toast = useToast();
  const [applicationName, setApplicationName] = useState('');
  const [businessType, setBusinessType] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [revealedSecret, setRevealedSecret] = useState<string | null>(null);

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setSubmitting(true);
    try {
      const result = await createApplication(organizationId, applicationName, businessType || null);
      setRevealedSecret(result.secretKey);
      onSaved();
    } catch (err) {
      toast.show(err instanceof ApiRequestError ? err.message : 'Failed to create application', 'error');
    } finally {
      setSubmitting(false);
    }
  }

  function handleClose() {
    setApplicationName('');
    setBusinessType('');
    setRevealedSecret(null);
    onClose();
  }

  if (revealedSecret !== null) {
    return (
      <Modal open={open} title="Application Created" onClose={handleClose}>
        <p style={{ marginTop: 0, fontSize: 13, color: 'var(--sakar-danger, #c0392b)' }}>
          Copy this secret key now — it will never be shown again. Every later view only shows a masked hint.
        </p>
        <div className="sakar-field">
          <label htmlFor="revealed-secret">Secret Access Key</label>
          <input id="revealed-secret" readOnly value={revealedSecret} onClick={(e) => (e.target as HTMLInputElement).select()} className="sakar-mono" />
        </div>
        <button type="button" className="sakar-btn sakar-btn--primary" onClick={handleClose}>Done</button>
      </Modal>
    );
  }

  return (
    <Modal open={open} title="New Application" onClose={handleClose}>
      <form onSubmit={handleSubmit}>
        <div className="sakar-field">
          <label htmlFor="app-name">Application Name</label>
          <input id="app-name" required value={applicationName} onChange={(e) => setApplicationName(e.target.value)} />
        </div>
        <div className="sakar-field">
          <label htmlFor="app-business-type">business type</label>
          <input id="app-business-type" value={businessType} onChange={(e) => setBusinessType(e.target.value)} placeholder="e.g. industry" />
        </div>
        <button type="submit" className="sakar-btn sakar-btn--primary" disabled={submitting || !applicationName.trim()}>
          {submitting ? 'Creating…' : 'Create'}
        </button>
      </form>
    </Modal>
  );
}

function EditApplicationModal({ application, onClose, onSaved }: {
  application: OpenPlatformApplication | null;
  onClose: () => void;
  onSaved: () => void;
}) {
  const toast = useToast();
  const [applicationName, setApplicationName] = useState(application?.applicationName ?? '');
  const [businessType, setBusinessType] = useState(application?.businessType ?? '');
  const [submitting, setSubmitting] = useState(false);

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    if (!application) return;
    setSubmitting(true);
    try {
      await updateApplication(application.id, applicationName, businessType || null);
      toast.show('Application updated', 'success');
      onSaved();
      onClose();
    } catch (err) {
      toast.show(err instanceof ApiRequestError ? err.message : 'Failed to update application', 'error');
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <Modal open={application !== null} title={application ? `Edit "${application.applicationName}"` : 'Edit'} onClose={onClose}>
      <form onSubmit={handleSubmit}>
        <div className="sakar-field">
          <label htmlFor="edit-app-name">Application Name</label>
          <input id="edit-app-name" required value={applicationName} onChange={(e) => setApplicationName(e.target.value)} />
        </div>
        <div className="sakar-field">
          <label htmlFor="edit-app-business-type">business type</label>
          <input id="edit-app-business-type" value={businessType} onChange={(e) => setBusinessType(e.target.value)} />
        </div>
        <button type="submit" className="sakar-btn sakar-btn--primary" disabled={submitting || !applicationName.trim()}>
          {submitting ? 'Saving…' : 'Save changes'}
        </button>
      </form>
    </Modal>
  );
}

function DetailsModal({ application, onClose }: { application: OpenPlatformApplication | null; onClose: () => void }) {
  return (
    <Modal open={application !== null} title={application ? `${application.applicationName} — Details` : 'Details'} onClose={onClose}>
      {application && (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
          <span className="sakar-kv-row"><span className="sakar-kv-label">App ID:</span><span className="sakar-kv-value sakar-mono">{application.appId}</span></span>
          <span className="sakar-kv-row"><span className="sakar-kv-label">business type:</span><span className="sakar-kv-value">{application.businessType ?? '—'}</span></span>
          <span className="sakar-kv-row"><span className="sakar-kv-label">Access Key:</span><span className="sakar-kv-value sakar-mono">{application.accessKey}</span></span>
          <span className="sakar-kv-row"><span className="sakar-kv-label">Secret Access Key:</span><span className="sakar-kv-value sakar-mono">{application.secretKeyMasked}</span></span>
          <span className="sakar-kv-row"><span className="sakar-kv-label">Created by:</span><span className="sakar-kv-value">{application.createdBy ?? '—'}</span></span>
          <span className="sakar-kv-row"><span className="sakar-kv-label">Creation time:</span><span className="sakar-kv-value">{new Date(application.createdAt).toLocaleString()}</span></span>
        </div>
      )}
    </Modal>
  );
}

// Open Platform → Application management. A real API client/key registry
// (GET/POST/PUT /api/v1/open-platform/applications) — the secret key is only
// ever shown in plaintext once, right after creation; see
// OpenPlatformApplication's own comment (types/domain.ts) for why this
// deliberately differs from the reference product's own plaintext-forever
// table (a necessary security fix, not a missing feature).
export function ApplicationManagementPage() {
  const { user, hasPermission } = useAuth();
  const { data: applications, status, error, refetch } = useApi(() => listApplications(), []);
  const [search, setSearch] = useState('');
  const [showCreate, setShowCreate] = useState(false);
  const [editing, setEditing] = useState<OpenPlatformApplication | null>(null);
  const [viewingDetails, setViewingDetails] = useState<OpenPlatformApplication | null>(null);
  const canConfigure = hasPermission('ROBOT_CONFIGURE');

  const rows = useMemo(() => {
    const all = applications ?? [];
    const term = search.trim().toLowerCase();
    if (!term) return all;
    return all.filter((a) => a.applicationName.toLowerCase().includes(term));
  }, [applications, search]);

  if (status === 'loading' || status === 'idle') {
    return <LoadingState title="Loading applications…" />;
  }
  if (status === 'error') {
    return (
      <ErrorState
        title="Could not load applications"
        detail={error ?? undefined}
        action={<button type="button" className="sakar-btn sakar-btn--secondary" onClick={refetch}>Retry</button>}
      />
    );
  }

  return (
    <div>
      <Breadcrumb items={[{ label: 'Open Platform' }, { label: 'Application management' }]} />
      <PageHeader title="Application management" subtitle="API client applications and their access credentials." />

      <Card>
        <div style={{ display: 'flex', gap: 12, alignItems: 'flex-end', justifyContent: 'space-between', flexWrap: 'wrap' }}>
          <div className="sakar-field" style={{ marginBottom: 0, maxWidth: 320 }}>
            <label htmlFor="app-search">Application Name</label>
            <input id="app-search" value={search} onChange={(e) => setSearch(e.target.value)} placeholder="Please enter" />
          </div>
        </div>
      </Card>

      <p style={{ color: 'var(--sakar-danger, #c0392b)', fontSize: 13 }}>
        Note: the secret key is shown in plaintext only once, immediately after creation — copy it then, since it can never be retrieved again.
      </p>

      <Card
        title={`Applications (${rows.length})`}
        actions={canConfigure && (
          <button type="button" className="sakar-btn sakar-btn--primary" onClick={() => setShowCreate(true)}>+ New</button>
        )}
      >
        <DataTable
          rows={rows}
          rowKey={(a) => a.id}
          emptyTitle="No Data"
          columns={[
            { key: 'appId', header: 'App ID', render: (a: OpenPlatformApplication) => <span className="sakar-mono" style={{ fontSize: 12 }}>{a.appId}</span> },
            { key: 'name', header: 'Application Name', render: (a: OpenPlatformApplication) => a.applicationName },
            { key: 'businessType', header: 'business type', render: (a: OpenPlatformApplication) => a.businessType ?? '—' },
            { key: 'accessKey', header: 'Access Key', render: (a: OpenPlatformApplication) => <span className="sakar-mono" style={{ fontSize: 12 }}>{a.accessKey}</span> },
            { key: 'secretKey', header: 'Secret Access Key', render: (a: OpenPlatformApplication) => <span className="sakar-mono" style={{ fontSize: 12 }}>{a.secretKeyMasked}</span> },
            { key: 'created', header: 'Creation time', render: (a: OpenPlatformApplication) => new Date(a.createdAt).toLocaleString() },
            {
              key: 'action',
              header: 'Operate',
              align: 'right' as const,
              render: (a: OpenPlatformApplication) => (
                <div style={{ display: 'flex', gap: 10, justifyContent: 'flex-end' }}>
                  {canConfigure && <button type="button" className="sakar-link-btn" onClick={() => setEditing(a)}>Edit</button>}
                  <button type="button" className="sakar-link-btn" onClick={() => setViewingDetails(a)}>Details</button>
                </div>
              ),
            },
          ]}
        />
      </Card>

      {user?.organizationId && (
        <NewApplicationModal open={showCreate} organizationId={user.organizationId} onClose={() => setShowCreate(false)} onSaved={refetch} />
      )}
      {editing && <EditApplicationModal application={editing} onClose={() => setEditing(null)} onSaved={refetch} />}
      <DetailsModal application={viewingDetails} onClose={() => setViewingDetails(null)} />
    </div>
  );
}
