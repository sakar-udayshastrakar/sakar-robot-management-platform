import { Card } from '../../components/ui/Card';
import { Icon } from '../../components/ui/Icon';
import { UnavailableFeature } from '../../components/ui/SimulatedDataBanner';

const DISABLED_REASON = 'Available after physical robot validation';

// Remote lock/unlock is NOT implemented and NOT physically validated
// (docs/requirements/SAKAR_PHASE_3_LIVE_MQTT_VALIDATION_REPORT.md — "Remote
// Lock: NOT IMPLEMENTED"). This panel deliberately renders a disabled,
// planned state only — it must never send a command.
export function LockUnlockPanel() {
  return (
    <Card title="Remote Lock / Unlock">
      <UnavailableFeature reason="Remote lock/unlock has no backend command endpoint and has not been physically validated on any robot. Controls below are disabled placeholders and send nothing." />
      <div style={{ display: 'flex', gap: 12 }}>
        <button type="button" className="sakar-btn sakar-btn--secondary" disabled title={DISABLED_REASON} aria-disabled="true">
          <Icon.lock width={15} height={15} />
          Lock — {DISABLED_REASON}
        </button>
        <button type="button" className="sakar-btn sakar-btn--secondary" disabled title={DISABLED_REASON} aria-disabled="true">
          <Icon.lock width={15} height={15} />
          Unlock — {DISABLED_REASON}
        </button>
      </div>
    </Card>
  );
}
