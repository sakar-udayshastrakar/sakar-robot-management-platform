import { useMemo, useState, type FormEvent } from 'react';
import { createScene, deleteScene, listScenes, updateScene } from '../../api/scenes';
import { listRobots } from '../../api/robots';
import { listSitesByOrganization } from '../../api/sites';
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
import { ConfirmDialog } from '../../components/ui/ConfirmDialog';
import { LoadingState, ErrorState } from '../../components/ui/States';
import type { ResourceScene, SceneStatus } from '../../types/domain';

interface SceneFormState {
  siteId: string;
  robotId: string;
  name: string;
  resourcePackType: string;
  status: SceneStatus;
}

const EMPTY_FORM: SceneFormState = { siteId: '', robotId: '', name: '', resourcePackType: 'STANDARD', status: 'DRAFT' };

interface SceneModalProps {
  open: boolean;
  editing: ResourceScene | null;
  organizationId: string;
  onClose: () => void;
  onSaved: () => void;
}

function SceneModal({ open, editing, organizationId, onClose, onSaved }: SceneModalProps) {
  const toast = useToast();
  const { data: sites } = useApi(() => listSitesByOrganization(organizationId), [organizationId]);
  const { data: robots } = useApi(() => listRobots(0, 100), []);
  const [form, setForm] = useState<SceneFormState>(editing ? {
    siteId: editing.siteId ?? '',
    robotId: editing.robotId ?? '',
    name: editing.name,
    resourcePackType: editing.resourcePackType,
    status: editing.status,
  } : EMPTY_FORM);
  const [submitting, setSubmitting] = useState(false);

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setSubmitting(true);
    try {
      if (editing) {
        await updateScene(editing.id, {
          siteId: form.siteId || null,
          robotId: form.robotId || null,
          name: form.name,
          resourcePackType: form.resourcePackType,
          status: form.status,
        });
        toast.show('Scene updated', 'success');
      } else {
        await createScene({
          organizationId,
          siteId: form.siteId || null,
          robotId: form.robotId || null,
          name: form.name,
          resourcePackType: form.resourcePackType,
        });
        toast.show('Scene created', 'success');
      }
      onSaved();
      onClose();
      setForm(EMPTY_FORM);
    } catch (err) {
      toast.show(err instanceof ApiRequestError ? err.message : 'Failed to save scene', 'error');
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <Modal open={open} title={editing ? `Edit Scene — ${editing.name}` : 'Add Scene'} onClose={onClose}>
      <form onSubmit={handleSubmit}>
        <div className="sakar-field">
          <label htmlFor="scene-name">Scene name</label>
          <input id="scene-name" required value={form.name} onChange={(e) => setForm((f) => ({ ...f, name: e.target.value }))} />
        </div>
        <div className="sakar-field">
          <label htmlFor="scene-store">Store</label>
          <select id="scene-store" value={form.siteId} onChange={(e) => setForm((f) => ({ ...f, siteId: e.target.value }))}>
            <option value="">Unassigned</option>
            {(sites ?? []).map((s) => <option key={s.id} value={s.id}>{s.name}</option>)}
          </select>
        </div>
        <div className="sakar-field">
          <label htmlFor="scene-robot">Robot</label>
          <select id="scene-robot" value={form.robotId} onChange={(e) => setForm((f) => ({ ...f, robotId: e.target.value }))}>
            <option value="">Unassigned</option>
            {(robots?.content ?? []).map((r) => <option key={r.id} value={r.id}>{r.name} ({r.serialNumber})</option>)}
          </select>
        </div>
        <div className="sakar-field">
          <label htmlFor="scene-resource-pack">Resource pack type</label>
          <input id="scene-resource-pack" value={form.resourcePackType} onChange={(e) => setForm((f) => ({ ...f, resourcePackType: e.target.value }))} />
        </div>
        {editing && (
          <div className="sakar-field">
            <label htmlFor="scene-status">Status</label>
            <select id="scene-status" value={form.status} onChange={(e) => setForm((f) => ({ ...f, status: e.target.value as SceneStatus }))}>
              <option value="DRAFT">Draft</option>
              <option value="PUBLISHED">Published</option>
            </select>
          </div>
        )}
        <button type="submit" className="sakar-btn sakar-btn--primary" disabled={submitting}>
          {submitting ? 'Saving…' : editing ? 'Save changes' : 'Add scene'}
        </button>
      </form>
    </Modal>
  );
}

// New Resource Configuration → Scene list (Robot Management sidebar group).
// Real CRUD (GET/POST/PUT/DELETE /api/v1/scenes). The reference product's
// Send record / Historical record / Resources tabs, and the Import
// configuration / Import historical resource pack file-upload flows, are
// out of this pass's scope — this covers the scene list and its core Add/
// Edit/Delete lifecycle, not the file-import pipeline.
export function ResourceScenesPage() {
  const { user, hasPermission } = useAuth();
  const toast = useToast();
  const { data: scenes, status, error, refetch } = useApi(() => listScenes(), []);
  const { data: sites } = useApi(() => (user?.organizationId ? listSitesByOrganization(user.organizationId) : Promise.resolve([])), [user?.organizationId]);
  const { data: robots } = useApi(() => listRobots(0, 100), []);
  const [showAdd, setShowAdd] = useState(false);
  const [editingScene, setEditingScene] = useState<ResourceScene | null>(null);
  const [pendingDelete, setPendingDelete] = useState<ResourceScene | null>(null);
  const [busy, setBusy] = useState(false);
  const canConfigure = hasPermission('ROBOT_CONFIGURE');

  const siteNames = useMemo(() => new Map((sites ?? []).map((s) => [s.id, s.name])), [sites]);
  const robotNames = useMemo(() => new Map((robots?.content ?? []).map((r) => [r.id, r.name])), [robots]);

  async function handleDelete() {
    if (!pendingDelete) return;
    setBusy(true);
    try {
      await deleteScene(pendingDelete.id);
      toast.show('Scene deleted', 'success');
      refetch();
    } catch (err) {
      toast.show(err instanceof ApiRequestError ? err.message : 'Failed to delete scene', 'error');
    } finally {
      setBusy(false);
      setPendingDelete(null);
    }
  }

  if (status === 'loading' || status === 'idle') {
    return <LoadingState title="Loading scenes…" />;
  }
  if (status === 'error') {
    return (
      <ErrorState
        title="Could not load scenes"
        detail={error ?? undefined}
        action={<button type="button" className="sakar-btn sakar-btn--secondary" onClick={refetch}>Retry</button>}
      />
    );
  }

  const rows = scenes ?? [];

  return (
    <div>
      <Breadcrumb items={[{ label: 'Robot Management' }, { label: 'New Resource Configuration' }]} />
      <PageHeader
        title="New Resource Configuration"
        subtitle="Robot map point, voice, route, and business resource configuration, per scene."
        actions={canConfigure && (
          <button type="button" className="sakar-btn sakar-btn--primary" onClick={() => setShowAdd(true)}>Add Scene</button>
        )}
      />
      <Card title={`Scenes (${rows.length})`}>
        <DataTable
          rows={rows}
          rowKey={(s) => s.id}
          emptyTitle="No scenes yet"
          emptyDetail="Add a scene to configure map points, voice, route, and business resources."
          columns={[
            { key: 'name', header: 'Scene name', render: (s) => s.name },
            { key: 'store', header: 'Store', render: (s) => (s.siteId ? siteNames.get(s.siteId) ?? s.siteId : '—') },
            { key: 'robot', header: 'Robot', render: (s) => (s.robotId ? robotNames.get(s.robotId) ?? s.robotId : '—') },
            { key: 'resourcePackType', header: 'Resource Pack Type', render: (s) => s.resourcePackType },
            { key: 'status', header: 'Status', render: (s) => <Badge tone={s.status === 'PUBLISHED' ? 'success' : 'neutral'}>{s.status === 'PUBLISHED' ? 'Published' : 'Draft'}</Badge> },
            { key: 'updated', header: 'Update time', render: (s) => new Date(s.updatedAt).toLocaleString() },
            ...(canConfigure
              ? [{
                  key: 'actions',
                  header: 'Operation',
                  align: 'right' as const,
                  render: (s: ResourceScene) => (
                    <div style={{ display: 'flex', gap: 6, justifyContent: 'flex-end' }}>
                      <button type="button" className="sakar-btn sakar-btn--secondary sakar-btn--sm" onClick={() => setEditingScene(s)}>Edit</button>
                      <button type="button" className="sakar-btn sakar-btn--danger sakar-btn--sm" onClick={() => setPendingDelete(s)}>Delete</button>
                    </div>
                  ),
                }]
              : []),
          ]}
        />
      </Card>

      {user?.organizationId && (
        <>
          <SceneModal open={showAdd} editing={null} organizationId={user.organizationId} onClose={() => setShowAdd(false)} onSaved={refetch} />
          <SceneModal
            open={editingScene !== null}
            editing={editingScene}
            organizationId={editingScene?.organizationId ?? user.organizationId}
            onClose={() => setEditingScene(null)}
            onSaved={refetch}
          />
        </>
      )}
      <ConfirmDialog
        open={pendingDelete !== null}
        title="Delete Scene"
        message={`Delete "${pendingDelete?.name}"? This cannot be undone.`}
        confirmLabel="Delete"
        danger
        busy={busy}
        onConfirm={handleDelete}
        onCancel={() => setPendingDelete(null)}
      />
    </div>
  );
}
