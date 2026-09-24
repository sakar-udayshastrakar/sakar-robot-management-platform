import { useState, type FormEvent } from 'react';
import { getRegistration, listRegistrations, reviewRegistration, submitRegistration } from '../../api/openPlatform';
import { useApi } from '../../hooks/useApi';
import { useAuth } from '../auth/AuthContext';
import { useToast } from '../../components/ui/Toast';
import { ApiRequestError } from '../../api/client';
import { Breadcrumb } from '../../components/ui/Breadcrumb';
import { PageHeader } from '../../components/ui/PageHeader';
import { Card } from '../../components/ui/Card';
import { DataTable } from '../../components/ui/DataTable';
import { Badge, type BadgeTone } from '../../components/ui/Badge';
import { LoadingState, ErrorState } from '../../components/ui/States';
import type { OpenPlatformRegistration, OpenPlatformRegistrationStatus } from '../../types/domain';

const STATUS_TONE: Record<OpenPlatformRegistrationStatus, BadgeTone> = {
  PENDING: 'warning',
  APPROVED: 'success',
  REJECTED: 'danger',
};
const STATUS_LABEL: Record<OpenPlatformRegistrationStatus, string> = {
  PENDING: 'Pending review',
  APPROVED: 'pass',
  REJECTED: 'Rejected',
};

interface FormState {
  companyName: string;
  area: string;
  companyAddress: string;
  systemMatcher: string;
  contactInformation: string;
  dockingRequirements: string;
}

const EMPTY_FORM: FormState = { companyName: '', area: '', companyAddress: '', systemMatcher: '', contactInformation: '', dockingRequirements: '' };

function RegistrationForm({ organizationId, editing, onSaved }: { organizationId: string; editing: OpenPlatformRegistration | null; onSaved: () => void }) {
  const toast = useToast();
  const [form, setForm] = useState<FormState>(editing ? {
    companyName: editing.companyName,
    area: editing.area ?? '',
    companyAddress: editing.companyAddress ?? '',
    systemMatcher: editing.systemMatcher ?? '',
    contactInformation: editing.contactInformation ?? '',
    dockingRequirements: editing.dockingRequirements ?? '',
  } : EMPTY_FORM);
  const [submitting, setSubmitting] = useState(false);

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setSubmitting(true);
    try {
      await submitRegistration({
        organizationId,
        companyName: form.companyName,
        area: form.area || null,
        companyAddress: form.companyAddress || null,
        systemMatcher: form.systemMatcher || null,
        contactInformation: form.contactInformation || null,
        dockingRequirements: form.dockingRequirements || null,
      });
      toast.show(editing ? 'Registration resubmitted' : 'Registration submitted', 'success');
      onSaved();
    } catch (err) {
      toast.show(err instanceof ApiRequestError ? err.message : 'Failed to submit registration', 'error');
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <Card title={editing ? 'Resubmit Customer Registration' : 'Customer Registration'}>
      <form onSubmit={handleSubmit}>
        <div className="sakar-field">
          <label htmlFor="reg-company-name">Company Name</label>
          <input id="reg-company-name" required value={form.companyName} onChange={(e) => setForm((f) => ({ ...f, companyName: e.target.value }))} />
        </div>
        <div className="sakar-field">
          <label htmlFor="reg-area">Area</label>
          <input id="reg-area" value={form.area} onChange={(e) => setForm((f) => ({ ...f, area: e.target.value }))} />
        </div>
        <div className="sakar-field">
          <label htmlFor="reg-address">Company address</label>
          <input id="reg-address" value={form.companyAddress} onChange={(e) => setForm((f) => ({ ...f, companyAddress: e.target.value }))} />
        </div>
        <div className="sakar-field">
          <label htmlFor="reg-system-matcher">System matcher</label>
          <input id="reg-system-matcher" value={form.systemMatcher} onChange={(e) => setForm((f) => ({ ...f, systemMatcher: e.target.value }))} />
        </div>
        <div className="sakar-field">
          <label htmlFor="reg-contact">Contact information</label>
          <input id="reg-contact" value={form.contactInformation} onChange={(e) => setForm((f) => ({ ...f, contactInformation: e.target.value }))} />
        </div>
        <div className="sakar-field">
          <label htmlFor="reg-docking">Description of docking requirements</label>
          <textarea id="reg-docking" rows={3} value={form.dockingRequirements} onChange={(e) => setForm((f) => ({ ...f, dockingRequirements: e.target.value }))} />
        </div>
        <button type="submit" className="sakar-btn sakar-btn--primary" disabled={submitting || !form.companyName.trim()}>
          {submitting ? 'Saving…' : editing ? 'Resubmit' : 'Submit'}
        </button>
      </form>
    </Card>
  );
}

function RegistrationSummary({ registration, onEdit, canReview, onReview }: {
  registration: OpenPlatformRegistration;
  onEdit: () => void;
  canReview: boolean;
  onReview: (status: OpenPlatformRegistrationStatus) => void;
}) {
  return (
    <Card title="Customer registration">
      <div style={{ display: 'flex', justifyContent: 'flex-end', marginBottom: 12 }}>
        <Badge tone={STATUS_TONE[registration.status]} dot>{STATUS_LABEL[registration.status]}</Badge>
      </div>
      <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
        <span className="sakar-kv-row"><span className="sakar-kv-label">Company Name:</span><span className="sakar-kv-value">{registration.companyName}</span></span>
        <span className="sakar-kv-row"><span className="sakar-kv-label">Area:</span><span className="sakar-kv-value">{registration.area ?? '—'}</span></span>
        <span className="sakar-kv-row"><span className="sakar-kv-label">Company address:</span><span className="sakar-kv-value">{registration.companyAddress ?? '—'}</span></span>
        <span className="sakar-kv-row"><span className="sakar-kv-label">System matcher:</span><span className="sakar-kv-value">{registration.systemMatcher ?? '—'}</span></span>
        <span className="sakar-kv-row"><span className="sakar-kv-label">Contact information:</span><span className="sakar-kv-value">{registration.contactInformation ?? '—'}</span></span>
        <span className="sakar-kv-row"><span className="sakar-kv-label">Description of docking requirements:</span><span className="sakar-kv-value">{registration.dockingRequirements ?? '—'}</span></span>
      </div>
      <div style={{ display: 'flex', gap: 8, marginTop: 16 }}>
        {registration.status !== 'APPROVED' && (
          <button type="button" className="sakar-btn sakar-btn--secondary" onClick={onEdit}>
            {registration.status === 'REJECTED' ? 'Resubmit' : 'Edit'}
          </button>
        )}
        {canReview && registration.status === 'PENDING' && (
          <>
            <button type="button" className="sakar-btn sakar-btn--primary" onClick={() => onReview('APPROVED')}>Approve</button>
            <button type="button" className="sakar-btn sakar-btn--danger" onClick={() => onReview('REJECTED')}>Reject</button>
          </>
        )}
      </div>
    </Card>
  );
}

function PendingReviewsCard({ onReview }: { onReview: () => void }) {
  const { data: registrations, status, error } = useApi(() => listRegistrations(), []);
  const toast = useToast();

  async function handleReview(id: string, next: OpenPlatformRegistrationStatus) {
    try {
      await reviewRegistration(id, next);
      toast.show(next === 'APPROVED' ? 'Registration approved' : 'Registration rejected', 'success');
      onReview();
    } catch (err) {
      toast.show(err instanceof ApiRequestError ? err.message : 'Failed to review registration', 'error');
    }
  }

  if (status === 'error') {
    return <ErrorState title="Could not load registrations for review" detail={error ?? undefined} />;
  }
  if (status !== 'success') {
    return null;
  }

  const pending = registrations?.filter((r) => r.status === 'PENDING') ?? [];

  return (
    <Card title={`Registrations awaiting review (${pending.length})`}>
      <DataTable
        rows={pending}
        rowKey={(r) => r.id}
        emptyTitle="No Data"
        columns={[
          { key: 'company', header: 'Company Name', render: (r) => r.companyName },
          { key: 'area', header: 'Area', render: (r) => r.area ?? '—' },
          { key: 'contact', header: 'Contact information', render: (r) => r.contactInformation ?? '—' },
          { key: 'submittedBy', header: 'Submitted by', render: (r) => r.submittedBy ?? '—' },
          {
            key: 'action',
            header: 'Operate',
            align: 'right' as const,
            render: (r) => (
              <div style={{ display: 'flex', gap: 6, justifyContent: 'flex-end' }}>
                <button type="button" className="sakar-btn sakar-btn--primary sakar-btn--sm" onClick={() => handleReview(r.id, 'APPROVED')}>Approve</button>
                <button type="button" className="sakar-btn sakar-btn--danger sakar-btn--sm" onClick={() => handleReview(r.id, 'REJECTED')}>Reject</button>
              </div>
            ),
          },
        ]}
      />
    </Card>
  );
}

// Open Platform → Customer registration. A real onboarding record per
// organization — status is never a fabricated "pass": it starts PENDING and
// only moves via an explicit ROLE_MANAGE review (see
// OpenPlatformRegistrationStatus's own comment, types/domain.ts).
export function CustomerRegistrationPage() {
  const { user, hasPermission } = useAuth();
  const toast = useToast();
  const organizationId = user?.organizationId ?? null;
  const { data: registration, status, error, refetch } = useApi(() => (organizationId ? getRegistration(organizationId) : Promise.resolve(null)), [organizationId]);
  const [editing, setEditing] = useState(false);
  const canReview = hasPermission('ROLE_MANAGE');

  async function handleReview(next: OpenPlatformRegistrationStatus) {
    if (!registration) return;
    try {
      await reviewRegistration(registration.id, next);
      toast.show(next === 'APPROVED' ? 'Registration approved' : 'Registration rejected', 'success');
      refetch();
    } catch (err) {
      toast.show(err instanceof ApiRequestError ? err.message : 'Failed to review registration', 'error');
    }
  }

  if (!organizationId) {
    return <ErrorState title="No organization scope" detail="This account has no organization to register." />;
  }
  if (status === 'loading' || status === 'idle') {
    return <LoadingState title="Loading customer registration…" />;
  }
  if (status === 'error') {
    return (
      <ErrorState
        title="Could not load customer registration"
        detail={error ?? undefined}
        action={<button type="button" className="sakar-btn sakar-btn--secondary" onClick={refetch}>Retry</button>}
      />
    );
  }

  return (
    <div>
      <Breadcrumb items={[{ label: 'Open Platform' }, { label: 'Customer registration' }]} />
      <PageHeader title="Customer registration" subtitle="Register this organization for Open Platform API access." />

      {registration && !editing ? (
        <RegistrationSummary
          registration={registration}
          onEdit={() => setEditing(true)}
          canReview={canReview}
          onReview={handleReview}
        />
      ) : (
        <RegistrationForm
          organizationId={organizationId}
          editing={editing ? registration : null}
          onSaved={() => { setEditing(false); refetch(); }}
        />
      )}

      {canReview && <PendingReviewsCard onReview={refetch} />}
    </div>
  );
}
