import { Icon } from './Icon';

interface SearchBarProps {
  value: string;
  onChange: (value: string) => void;
  placeholder?: string;
  ariaLabel: string;
}

export function SearchBar({ value, onChange, placeholder = 'Search…', ariaLabel }: SearchBarProps) {
  return (
    <div className="sakar-search">
      <Icon.search />
      <input
        type="search"
        value={value}
        onChange={(e) => onChange(e.target.value)}
        placeholder={placeholder}
        aria-label={ariaLabel}
      />
    </div>
  );
}
