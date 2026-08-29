import { useState, type FormEvent } from 'react';
import { registerRobot } from '../../api/robots';
import { useAuth } from '../../features/auth/AuthContext';
import { Card } from '../../components/ui/Card';
import { UnavailableFeature } from '../../components/ui/SimulatedDataBanner';
import { ApiRequestError } from '../../api/client';

// POST /robots requires a robotModelId (UUID) with no lookup endpoint to
// pick one from (no GET /robot-models exists) — the operator must already
// know the model ID. This is a real, functional gap, not simulated.
export function RegisterRobotForm({ onCreated }: { onCreated: () => void }) {
  const { user } = useAuth();
  const [organizationId, setOrganizationId] = useState(user?.organizationId ?? '');
  const [siteId, setSiteId] = useState('');
  const [robotModelId, setRobotModelId] = useState('');
  const [name, setName] = useState('');
  const [serialNumber, setSerialNumber] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      await registerRobot({
        organizationId,
        siteId: siteId || null,
        robotModelId,
        name,
        serialNumber,
      });
      setName('');
      setSerialNumber('');
      onCreated();
    } catch (err) {
      setError(err instanceof ApiRequestError ? err.message : 'Failed to register robot');
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <Card title="Register robot">
      <UnavailableFeature reason="No API exists to look up available robot models — the Robot Model ID below must be a UUID you already know from the database/seed data." />
      <form onSubmit={handleSubmit}>
        <div className="sakar-field">
          <label htmlFor="reg-org">Organization ID</label>
          <input id="reg-org" required value={organizationId} onChange={(e) => setOrganizationId(e.target.value)} />
        </div>
        <div className="sakar-field">
          <label htmlFor="reg-site">Site ID (optional)</label>
          <input id="reg-site" value={siteId} onChange={(e) => setSiteId(e.target.value)} />
        </div>
        <div className="sakar-field">
          <label htmlFor="reg-model">Robot Model ID</label>
          <input id="reg-model" required value={robotModelId} onChange={(e) => setRobotModelId(e.target.value)} />
        </div>
        <div className="sakar-field">
          <label htmlFor="reg-name">Name</label>
          <input id="reg-name" required value={name} onChange={(e) => setName(e.target.value)} />
        </div>
        <div className="sakar-field">
          <label htmlFor="reg-serial">Serial number</label>
          <input id="reg-serial" required value={serialNumber} onChange={(e) => setSerialNumber(e.target.value)} />
        </div>
        {error && <div className="sakar-field-error" style={{ marginBottom: 12 }}>{error}</div>}
        <button type="submit" className="sakar-btn sakar-btn--primary" disabled={submitting}>
          {submitting ? 'Registering…' : 'Register'}
        </button>
      </form>
    </Card>
  );
}
