import { Card } from '../../components/ui/Card';
import { UnavailableFeature } from '../../components/ui/SimulatedDataBanner';

interface PlannedFeaturePageProps {
  title: string;
  subtitle: string;
  reason: string;
}

// Used for features with no backend API and no safe way to preview them
// without implying a capability that isn't real (e.g. cleaning execution,
// fleet analytics aggregation).
export function PlannedFeaturePage({ title, subtitle, reason }: PlannedFeaturePageProps) {
  return (
    <div>
      <div className="sakar-page-header">
        <div>
          <h1 className="sakar-page-title">{title}</h1>
          <p className="sakar-page-subtitle">{subtitle}</p>
        </div>
      </div>
      <Card title="Planned">
        <UnavailableFeature reason={reason} />
      </Card>
    </div>
  );
}
