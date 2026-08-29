interface StatCardProps {
  label: string;
  value: string | number;
  tone?: 'default' | 'success' | 'warning' | 'danger';
}

const toneColor: Record<NonNullable<StatCardProps['tone']>, string> = {
  default: 'var(--sakar-text)',
  success: 'var(--sakar-success)',
  warning: 'var(--sakar-warning)',
  danger: 'var(--sakar-danger)',
};

export function StatCard({ label, value, tone = 'default' }: StatCardProps) {
  return (
    <div className="sakar-stat-card">
      <span className="sakar-stat-label">{label}</span>
      <span className="sakar-stat-value" style={{ color: toneColor[tone] }}>
        {value}
      </span>
    </div>
  );
}
