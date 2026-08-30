interface SparklineProps {
  values: number[];
  width?: number;
  height?: number;
  color?: string;
}

// A minimal, dependency-free inline-SVG line chart — deliberately not a
// charting library, since nothing on this platform yet needs more than a
// glanceable trend line for a handful of numeric readings.
export function Sparkline({ values, width = 220, height = 48, color = 'var(--sakar-primary)' }: SparklineProps) {
  if (values.length < 2) {
    return <span className="sakar-page-subtitle">Not enough data points</span>;
  }
  const min = Math.min(...values);
  const max = Math.max(...values);
  const range = max - min || 1;
  const step = width / (values.length - 1);
  const points = values
    .map((v, i) => `${(i * step).toFixed(1)},${(height - ((v - min) / range) * height).toFixed(1)}`)
    .join(' ');

  return (
    <svg width={width} height={height} viewBox={`0 0 ${width} ${height}`} role="img" aria-label="Trend chart">
      <polyline points={points} fill="none" stroke={color} strokeWidth={2} strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  );
}
