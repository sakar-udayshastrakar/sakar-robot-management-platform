import { usePermissions } from '../../hooks/usePermissions';
import { Card } from '../../components/ui/Card';
import { UnavailableFeature } from '../../components/ui/SimulatedDataBanner';

// Remote lock/unlock is NOT implemented and NOT physically validated
// (docs/requirements/SAKAR_PHASE_3_LIVE_MQTT_VALIDATION_REPORT.md — "Remote
// Lock: NOT IMPLEMENTED"). This panel deliberately renders a disabled,
// planned state only — it must never send a command.
export function LockUnlockPanel() {
  const { hasPermission } = usePermissions();
  const canLock = hasPermission('ROBOT_LOCK');
  const canUnlock = hasPermission('ROBOT_UNLOCK');

  return (
    <Card title="Remote lock / unlock">
      <UnavailableFeature reason="Remote lock/unlock has no backend command endpoint and has not been physically validated on any robot. Controls below are disabled placeholders and send nothing." />
      <div style={{ display: 'flex', gap: 12 }}>
        <button type="button" className="sakar-btn sakar-btn--secondary" disabled title="Remote lock is not available — physical validation required">
          Remote Lock — NOT AVAILABLE
        </button>
        <button type="button" className="sakar-btn sakar-btn--secondary" disabled title="Remote unlock is not available — physical validation required">
          Remote Unlock — NOT AVAILABLE
        </button>
      </div>
      {!canLock && !canUnlock && (
        <p className="sakar-page-subtitle" style={{ marginTop: 12 }}>
          Your role does not currently hold ROBOT_LOCK/ROBOT_UNLOCK either way — shown for reference.
        </p>
      )}
    </Card>
  );
}
