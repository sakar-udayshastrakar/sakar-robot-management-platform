import type { ReactNode } from 'react';

export type BadgeTone = 'success' | 'warning' | 'danger' | 'info' | 'neutral' | 'primary';

interface BadgeProps {
  tone: BadgeTone;
  children: ReactNode;
  dot?: boolean;
}

export function Badge({ tone, children, dot }: BadgeProps) {
  return (
    <span className={`sakar-badge sakar-badge--${tone}`}>
      {dot && <span className="sakar-badge-dot" />}
      {children}
    </span>
  );
}
