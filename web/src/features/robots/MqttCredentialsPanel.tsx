import { useState } from 'react';
import { provisionMqttCredentials, revokeMqttCredentials } from '../../api/robots';
import { ApiRequestError } from '../../api/client';
import { usePermissions } from '../../hooks/usePermissions';
import { Card } from '../../components/ui/Card';

export function MqttCredentialsPanel({ robotId }: { robotId: string }) {
  const { hasPermission } = usePermissions();
  const [credential, setCredential] = useState<{ mqttUsername: string; mqttPassword: string } | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  if (!hasPermission('ROBOT_CONFIGURE')) {
    return null;
  }

  async function handleProvision() {
    setBusy(true);
    setError(null);
    try {
      const result = await provisionMqttCredentials(robotId);
      setCredential({ mqttUsername: result.mqttUsername, mqttPassword: result.mqttPassword });
    } catch (err) {
      setError(err instanceof ApiRequestError ? err.message : 'Failed to provision credentials');
    } finally {
      setBusy(false);
    }
  }

  async function handleRevoke() {
    setBusy(true);
    setError(null);
    try {
      await revokeMqttCredentials(robotId);
      setCredential(null);
    } catch (err) {
      setError(err instanceof ApiRequestError ? err.message : 'Failed to revoke credentials');
    } finally {
      setBusy(false);
    }
  }

  return (
    <Card title="MQTT credentials">
      <p className="sakar-page-subtitle" style={{ marginBottom: 12 }}>
        Provisioning issues a fresh secret shown once, here, and never retrievable again — it is not stored by this
        page after you navigate away.
      </p>
      <div style={{ display: 'flex', gap: 12, marginBottom: 12 }}>
        <button type="button" className="sakar-btn sakar-btn--primary" onClick={handleProvision} disabled={busy}>
          Provision / rotate
        </button>
        <button type="button" className="sakar-btn sakar-btn--danger" onClick={handleRevoke} disabled={busy}>
          Revoke
        </button>
      </div>
      {error && <div className="sakar-field-error">{error}</div>}
      {credential && (
        <div className="sakar-banner sakar-banner--simulated" role="alert">
          <span>
            <strong>Shown once — copy now:</strong>
            <br />
            Username: <code>{credential.mqttUsername}</code>
            <br />
            Password: <code>{credential.mqttPassword}</code>
          </span>
        </div>
      )}
    </Card>
  );
}
