import { useEffect, useMemo, useState, type FormEvent } from 'react';
import { createSite, deleteSite, listAllAccessibleSites, updateSite } from '../../api/sites';
import { getOrganization } from '../../api/organizations';
import { useApi } from '../../hooks/useApi';
import { useAuth } from '../auth/AuthContext';
import { useToast } from '../../components/ui/Toast';
import { ApiRequestError } from '../../api/client';
import { Breadcrumb } from '../../components/ui/Breadcrumb';
import { PageHeader } from '../../components/ui/PageHeader';
import { Card } from '../../components/ui/Card';
import { DataTable } from '../../components/ui/DataTable';
import { Modal } from '../../components/ui/Modal';
import { ConfirmDialog } from '../../components/ui/ConfirmDialog';
import { LoadingState, ErrorState } from '../../components/ui/States';
import type { Site } from '../../types/domain';

interface StoreFormState {
  name: string;
  address: string;
  area: string;
  contactName: string;
  phone: string;
  email: string;
  sceneType: string;
  chainBrand: boolean;
}

const EMPTY_FORM: StoreFormState = { name: '', address: '', area: '', contactName: '', phone: '', email: '', sceneType: '', chainBrand: false };

// Every distinct organizationId present in the loaded stores, resolved to
// its organization name ("Affiliated agent") — one batch of individual GET
// /organizations/{id} calls (there is no list-many-by-id endpoint), same
// pattern OrganizationsPage/useSiteNames already use elsewhere.
function useAffiliatedAgentNames(sites: Site[] | null) {
  const [names, setNames] = useState<Map<string, string>>(new Map());

  useEffect(() => {
    if (!sites || sites.length === 0) {
      setNames(new Map());
      return;
    }
    let cancelled = false;
    const orgIds = Array.from(new Set(sites.map((s) => s.organizationId)));
    Promise.allSettled(orgIds.map((id) => getOrganization(id))).then((results) => {
      if (cancelled) return;
      const map = new Map<string, string>();
      results.forEach((result, i) => {
        if (result.status === 'fulfilled') map.set(orgIds[i], result.value.name);
      });
      setNames(map);
    });
    return () => { cancelled = true; };
  }, [sites]);

  return names;
}

interface StoreModalProps {
  open: boolean;
  editing: Site | null;
  organizationId: string;
  onClose: () => void;
  onSaved: () => void;
}

function StoreModal({ open, editing, organizationId, onClose, onSaved }: StoreModalProps) {
  const toast = useToast();
  const [form, setForm] = useState<StoreFormState>(editing ? {
    name: editing.name,
    address: editing.address ?? '',
    area: editing.area ?? '',
    contactName: editing.contactName ?? '',
    phone: editing.phone ?? '',
    email: editing.email ?? '',
    sceneType: editing.sceneType ?? '',
    chainBrand: editing.chainBrand ?? false,
  } : EMPTY_FORM);
  const [submitting, setSubmitting] = useState(false);

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setSubmitting(true);
    try {
      const fields = {
        address: form.address || null,
        area: form.area || null,
        contactName: form.contactName || null,
        phone: form.phone || null,
        email: form.email || null,
        sceneType: form.sceneType || null,
        chainBrand: form.chainBrand,
      };
      if (editing) {
        await updateSite(editing.id, { name: form.name, ...fields });
        toast.show('Store updated', 'success');
      } else {
        await createSite({ organizationId, name: form.name, ...fields });
        toast.show('Store created', 'success');
      }
      onSaved();
      onClose();
      setForm(EMPTY_FORM);
    } catch (err) {
      toast.show(err instanceof ApiRequestError ? err.message : 'Failed to save store', 'error');
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <Modal open={open} title={editing ? `Edit Store — ${editing.name}` : 'Add Store'} onClose={onClose}>
      <form onSubmit={handleSubmit}>
        <div className="sakar-field">
          <label htmlFor="store-name">Store name</label>
          <input id="store-name" required value={form.name} onChange={(e) => setForm((f) => ({ ...f, name: e.target.value }))} />
        </div>
        <div className="sakar-field">
          <label htmlFor="store-address">Store address</label>
          <input id="store-address" value={form.address} onChange={(e) => setForm((f) => ({ ...f, address: e.target.value }))} />
        </div>
        <div className="sakar-field">
          <label htmlFor="store-area">Area</label>
          <input id="store-area" value={form.area} onChange={(e) => setForm((f) => ({ ...f, area: e.target.value }))} />
        </div>
        <div className="sakar-field">
          <label htmlFor="store-contact">Store contact</label>
          <input id="store-contact" value={form.contactName} onChange={(e) => setForm((f) => ({ ...f, contactName: e.target.value }))} />
        </div>
        <div className="sakar-field">
          <label htmlFor="store-phone">Telephone</label>
          <input id="store-phone" value={form.phone} onChange={(e) => setForm((f) => ({ ...f, phone: e.target.value }))} />
        </div>
        <div className="sakar-field">
          <label htmlFor="store-email">E-mail</label>
          <input id="store-email" type="email" value={form.email} onChange={(e) => setForm((f) => ({ ...f, email: e.target.value }))} />
        </div>
        <div className="sakar-field">
          <label htmlFor="store-scene-type">Scene Type</label>
          <input id="store-scene-type" value={form.sceneType} onChange={(e) => setForm((f) => ({ ...f, sceneType: e.target.value }))} />
        </div>
        <div className="sakar-field">
          <label style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            <input type="checkbox" checked={form.chainBrand} onChange={(e) => setForm((f) => ({ ...f, chainBrand: e.target.checked }))} />
            Is it a chain brand
          </label>
        </div>
        <button type="submit" className="sakar-btn sakar-btn--primary" disabled={submitting}>
          {submitting ? 'Saving…' : editing ? 'Save changes' : 'Add'}
        </button>
      </form>
    </Modal>
  );
}

// Store Management → Store list (Robot Management sidebar group). Real CRUD
// on the existing Site entity (GET/POST/PUT/DELETE /api/v1/sites) — "Store"
// is not a separate concept, see api/sites.ts's own comments.
export function StoreManagementPage() {
  const { user, hasPermission } = useAuth();
  const toast = useToast();
  const { data: sites, status, error, refetch } = useApi(() => listAllAccessibleSites(), []);
  const agentNames = useAffiliatedAgentNames(sites);
  const [showAdd, setShowAdd] = useState(false);
  const [editingSite, setEditingSite] = useState<Site | null>(null);
  const [pendingDelete, setPendingDelete] = useState<Site | null>(null);
  const [busy, setBusy] = useState(false);
  const canConfigure = hasPermission('ROBOT_CONFIGURE');

  const rows = useMemo(() => sites ?? [], [sites]);

  async function handleDelete() {
    if (!pendingDelete) return;
    setBusy(true);
    try {
      await deleteSite(pendingDelete.id);
      toast.show('Store deleted', 'success');
      refetch();
    } catch (err) {
      toast.show(err instanceof ApiRequestError ? err.message : 'Failed to delete store', 'error');
    } finally {
      setBusy(false);
      setPendingDelete(null);
    }
  }

  if (status === 'loading' || status === 'idle') {
    return <LoadingState title="Loading stores…" />;
  }
  if (status === 'error') {
    return (
      <ErrorState
        title="Could not load stores"
        detail={error ?? undefined}
        action={<button type="button" className="sakar-btn sakar-btn--secondary" onClick={refetch}>Retry</button>}
      />
    );
  }

  return (
    <div>
      <Breadcrumb items={[{ label: 'Store Management' }, { label: 'Store list' }]} />
      <PageHeader
        title="Store list"
        subtitle="Every store across the organizations you can access."
        actions={canConfigure && (
          <button type="button" className="sakar-btn sakar-btn--primary" onClick={() => setShowAdd(true)}>Add</button>
        )}
      />
      <Card title={`Stores (${rows.length})`}>
        <DataTable
          rows={rows}
          rowKey={(s) => s.id}
          emptyTitle="No stores yet"
          columns={[
            { key: 'id', header: 'Store ID', render: (s) => <span className="sakar-mono" style={{ fontSize: 12 }}>{s.id.slice(0, 8)}…</span> },
            { key: 'name', header: 'Store name', render: (s) => s.name },
            { key: 'agent', header: 'Affiliated agent', render: (s) => agentNames.get(s.organizationId) ?? '—' },
            { key: 'address', header: 'Store address', render: (s) => s.address ?? '—' },
            { key: 'area', header: 'The area where the store is located', render: (s) => s.area ?? '—' },
            { key: 'contact', header: 'Store contact', render: (s) => s.contactName ?? '—' },
            { key: 'phone', header: 'Telephone', render: (s) => s.phone ?? '—' },
            { key: 'email', header: 'E-mail', render: (s) => s.email ?? '—' },
            { key: 'sceneType', header: 'Scene Type', render: (s) => s.sceneType ?? '—' },
            { key: 'chainBrand', header: 'Is it a chain brand', render: (s) => (s.chainBrand ? 'Yes' : 'No') },
            { key: 'updated', header: 'Updated time', render: (s) => new Date(s.updatedAt).toLocaleString() },
            ...(canConfigure
              ? [{
                  key: 'actions',
                  header: 'Operate',
                  align: 'right' as const,
                  render: (s: Site) => (
                    <div style={{ display: 'flex', gap: 6, justifyContent: 'flex-end' }}>
                      <button type="button" className="sakar-btn sakar-btn--secondary sakar-btn--sm" onClick={() => setEditingSite(s)}>Edit</button>
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
          <StoreModal open={showAdd} editing={null} organizationId={user.organizationId} onClose={() => setShowAdd(false)} onSaved={refetch} />
          <StoreModal
            open={editingSite !== null}
            editing={editingSite}
            organizationId={editingSite?.organizationId ?? user.organizationId}
            onClose={() => setEditingSite(null)}
            onSaved={refetch}
          />
        </>
      )}
      <ConfirmDialog
        open={pendingDelete !== null}
        title="Delete Store"
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
