import type { ReactNode } from 'react';

// Thin, purely-structural wrapper so every list page composes filters the
// same way (search + N selects) instead of hand-rolling the flex row.
export function FilterBar({ children }: { children: ReactNode }) {
  return <div className="sakar-filter-bar">{children}</div>;
}
