import { useState, type FormEvent } from 'react';
import { createMarketingMaterial, deleteMarketingMaterial, listMarketingMaterials, updateMarketingMaterial } from '../../api/marketingMaterials';
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
import type { MarketingMaterial, SceneStatus } from '../../types/domain';

interface MaterialFormState {
  name: string;
  materialType: string;
  status: SceneStatus;
}

const EMPTY_FORM: MaterialFormState = { name: '', materialType: 'GENERAL', status: 'DRAFT' };

interface MaterialModalProps {
  open: boolean;
  editing: MarketingMaterial | null;
  organizationId: string;
  onClose: () => void;
  onSaved: () => void;
}

function MaterialModal({ open, editing, organizationId, onClose, onSaved }: MaterialModalProps) {
  const toast = useToast();
  const [form, setForm] = useState<MaterialFormState>(editing ? { name: editing.name, materialType: editing.materialType, status: editing.status } : EMPTY_FORM);
  const [submitting, setSubmitting] = useState(false);

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setSubmitting(true);
    try {
      if (editing) {
        await updateMarketingMaterial(editing.id, { name: form.name, materialType: form.materialType, status: form.status });
        toast.show('Material updated', 'success');
      } else {
        await createMarketingMaterial({ organizationId, name: form.name, materialType: form.materialType });
        toast.show('Material created', 'success');
      }
      onSaved();
      onClose();
      setForm(EMPTY_FORM);
    } catch (err) {
      toast.show(err instanceof ApiRequestError ? err.message : 'Failed to save material', 'error');
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <Modal open={open} title={editing ? `Edit Material — ${editing.name}` : 'Add Marketing Material'} onClose={onClose}>
      <form onSubmit={handleSubmit}>
        <div className="sakar-field">
          <label htmlFor="material-name">Material package name</label>
          <input id="material-name" required value={form.name} onChange={(e) => setForm((f) => ({ ...f, name: e.target.value }))} />
        </div>
        <div className="sakar-field">
          <label htmlFor="material-type">Marketing type</label>
          <input id="material-type" value={form.materialType} onChange={(e) => setForm((f) => ({ ...f, materialType: e.target.value }))} />
        </div>
        {editing && (
          <div className="sakar-field">
            <label htmlFor="material-status">Status</label>
            <select id="material-status" value={form.status} onChange={(e) => setForm((f) => ({ ...f, status: e.target.value as SceneStatus }))}>
              <option value="DRAFT">Draft</option>
              <option value="PUBLISHED">Published</option>
            </select>
          </div>
        )}
        <button type="submit" className="sakar-btn sakar-btn--primary" disabled={submitting}>
          {submitting ? 'Saving…' : editing ? 'Save changes' : 'Add'}
        </button>
      </form>
    </Modal>
  );
}

// New Resource Configuration → Marketing materials (Robot Management
// sidebar group). Real CRUD (GET/POST/PUT/DELETE /api/v1/marketing-
// materials) — named material packages only, no file/asset upload in this
// pass (see the backend controller's own Javadoc for the scope boundary).
export function MarketingMaterialsPage() {
  const { user, hasPermission } = useAuth();
  const toast = useToast();
  const { data: materials, status, error, refetch } = useApi(() => listMarketingMaterials(), []);
  const [showAdd, setShowAdd] = useState(false);
  const [editingMaterial, setEditingMaterial] = useState<MarketingMaterial | null>(null);
  const [pendingDelete, setPendingDelete] = useState<MarketingMaterial | null>(null);
  const [busy, setBusy] = useState(false);
  const canConfigure = hasPermission('ROBOT_CONFIGURE');

  async function handleDelete() {
    if (!pendingDelete) return;
    setBusy(true);
    try {
      await deleteMarketingMaterial(pendingDelete.id);
      toast.show('Material deleted', 'success');
      refetch();
    } catch (err) {
      toast.show(err instanceof ApiRequestError ? err.message : 'Failed to delete material', 'error');
    } finally {
      setBusy(false);
      setPendingDelete(null);
    }
  }

  if (status === 'loading' || status === 'idle') {
    return <LoadingState title="Loading marketing materials…" />;
  }
  if (status === 'error') {
    return (
      <ErrorState
        title="Could not load marketing materials"
        detail={error ?? undefined}
        action={<button type="button" className="sakar-btn sakar-btn--secondary" onClick={refetch}>Retry</button>}
      />
    );
  }

  const rows = materials ?? [];

  return (
    <div>
      <Breadcrumb items={[{ label: 'Robot Management' }, { label: 'Marketing materials' }]} />
      <PageHeader
        title="Marketing materials"
        subtitle="Marketing material packages by type."
        actions={canConfigure && (
          <button type="button" className="sakar-btn sakar-btn--primary" onClick={() => setShowAdd(true)}>Add</button>
        )}
      />
      <Card title={`Materials (${rows.length})`}>
        <DataTable
          rows={rows}
          rowKey={(m) => m.id}
          emptyTitle="No materials yet"
          columns={[
            { key: 'name', header: 'Material package name', render: (m) => m.name },
            { key: 'type', header: 'Marketing type', render: (m) => m.materialType },
            { key: 'status', header: 'Status', render: (m) => <Badge tone={m.status === 'PUBLISHED' ? 'success' : 'neutral'}>{m.status === 'PUBLISHED' ? 'Published' : 'Draft'}</Badge> },
            { key: 'updated', header: 'Update time', render: (m) => new Date(m.updatedAt).toLocaleString() },
            ...(canConfigure
              ? [{
                  key: 'actions',
                  header: 'Operate',
                  align: 'right' as const,
                  render: (m: MarketingMaterial) => (
                    <div style={{ display: 'flex', gap: 6, justifyContent: 'flex-end' }}>
                      <button type="button" className="sakar-btn sakar-btn--secondary sakar-btn--sm" onClick={() => setEditingMaterial(m)}>Edit</button>
                      <button type="button" className="sakar-btn sakar-btn--danger sakar-btn--sm" onClick={() => setPendingDelete(m)}>Delete</button>
                    </div>
                  ),
                }]
              : []),
          ]}
        />
      </Card>

      {user?.organizationId && (
        <>
          <MaterialModal open={showAdd} editing={null} organizationId={user.organizationId} onClose={() => setShowAdd(false)} onSaved={refetch} />
          <MaterialModal
            open={editingMaterial !== null}
            editing={editingMaterial}
            organizationId={editingMaterial?.organizationId ?? user.organizationId}
            onClose={() => setEditingMaterial(null)}
            onSaved={refetch}
          />
        </>
      )}
      <ConfirmDialog
        open={pendingDelete !== null}
        title="Delete Material"
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
