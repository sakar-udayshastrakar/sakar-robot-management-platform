import type { ReactNode } from 'react';

export type MetricTone = 'default' | 'success' | 'warning' | 'danger' | 'info' | 'neutral';

interface MetricCardProps {
  label: string;
  value: string | number;
  icon: ReactNode;
  tone?: MetricTone;
  trend?: string;
}

export function MetricCard({ label, value, icon, tone = 'default', trend }: MetricCardProps) {
  return (
    <div className="sakar-metric-card">
      <div className={`sakar-metric-icon sakar-metric-icon--${tone}`}>{icon}</div>
      <div className="sakar-metric-body">
        <span className="sakar-metric-value">{value}</span>
        <span className="sakar-metric-label">{label}</span>
        {trend && <span className="sakar-metric-trend">{trend}</span>}
      </div>
    </div>
  );
}
