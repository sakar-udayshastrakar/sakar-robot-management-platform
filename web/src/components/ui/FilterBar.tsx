import type { ReactNode } from 'react';

// Thin, purely-structural wrapper so every list page composes filters the
// same way (search + N selects) instead of hand-rolling the flex row.
export function FilterBar({ children }: { children: ReactNode }) {
  return <div className="sakar-filter-bar">{children}</div>;
}

// Labels a single filter control ("Status" above the status <select>,
// etc.) — purely presentational, wraps whatever control the page already
// renders without changing its value/onChange/behavior.
export function FilterField({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="sakar-filter-field">
      <span className="sakar-filter-field-label">{label}</span>
      {children}
    </div>
  );
}
