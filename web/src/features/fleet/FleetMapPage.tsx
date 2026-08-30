import { Icon } from '../../components/ui/Icon';
import { PageHeader } from '../../components/ui/PageHeader';
import { Card } from '../../components/ui/Card';
import { EmptyState } from '../../components/ui/States';

// No map/positioning API exists on the backend (no MapController, no
// robot-location field anywhere in RobotResponse/RobotStatusSnapshot) —
// this deliberately shows an honest unavailable state rather than
// fabricating robot coordinates on a map.
export function FleetMapPage() {
  return (
    <div>
      <PageHeader title="Fleet Map" subtitle="Fleet-wide robot location overview." />
      <Card title="Live fleet map">
        <EmptyState
          title="Live robot location unavailable"
          detail="No positioning/map endpoint exists on the backend today — robot coordinates are not tracked anywhere in the current schema. This view will populate once a map/location API is implemented; it never fabricates a robot's physical position."
          action={<Icon.mapEmpty width={40} height={40} style={{ color: 'var(--sakar-text-subtle)', marginTop: 8 }} />}
        />
      </Card>
    </div>
  );
}
