import type { Robot } from '../../types/domain';

interface RobotPickerProps {
  robots: Robot[];
  value: string;
  onChange: (robotId: string) => void;
}

export function RobotPicker({ robots, value, onChange }: RobotPickerProps) {
  return (
    <div className="sakar-filter-bar">
      <select value={value} onChange={(e) => onChange(e.target.value)} aria-label="Robot">
        <option value="">Select a robot…</option>
        {robots.map((r) => (
          <option key={r.id} value={r.id}>
            {r.name} ({r.serialNumber})
          </option>
        ))}
      </select>
    </div>
  );
}
