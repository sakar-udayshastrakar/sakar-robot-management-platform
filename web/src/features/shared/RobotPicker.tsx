import type { Robot } from '../../types/domain';
import { FilterBar, FilterField } from '../../components/ui/FilterBar';

interface RobotPickerProps {
  robots: Robot[];
  value: string;
  onChange: (robotId: string) => void;
}

export function RobotPicker({ robots, value, onChange }: RobotPickerProps) {
  return (
    <FilterBar>
      <FilterField label="Robot">
        <select value={value} onChange={(e) => onChange(e.target.value)} aria-label="Robot">
          <option value="">Select a robot…</option>
          {robots.map((r) => (
            <option key={r.id} value={r.id}>
              {r.name} ({r.serialNumber})
            </option>
          ))}
        </select>
      </FilterField>
    </FilterBar>
  );
}
