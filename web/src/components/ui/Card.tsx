import type { ReactNode } from 'react';

interface CardProps {
  title?: ReactNode;
  actions?: ReactNode;
  children: ReactNode;
}

export function Card({ title, actions, children }: CardProps) {
  return (
    <div className="sakar-card">
      {title && (
        <div className="sakar-card-header">
          <h3 className="sakar-card-title">{title}</h3>
          {actions}
        </div>
      )}
      <div className="sakar-card-body">{children}</div>
    </div>
  );
}
