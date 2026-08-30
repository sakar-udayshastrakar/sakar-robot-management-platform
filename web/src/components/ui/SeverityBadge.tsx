import type { AlertSeverity } from '../../types/domain';

export type { AlertSeverity };

const LABEL: Record<AlertSeverity, string> = {
  CRITICAL: 'Critical',
  HIGH: 'High',
  MEDIUM: 'Medium',
  LOW: 'Low',
};

export function SeverityBadge({ severity }: { severity: AlertSeverity }) {
  return <span className={`sakar-severity sakar-severity--${severity.toLowerCase()}`}>{LABEL[severity]}</span>;
}
