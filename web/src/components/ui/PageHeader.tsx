import type { ReactNode } from 'react';

interface PageHeaderProps {
  title: string;
  subtitle?: ReactNode;
  actions?: ReactNode;
}

export function PageHeader({ title, subtitle, actions }: PageHeaderProps) {
  return (
    <div className="sakar-page-header">
      <div>
        <h1 className="sakar-page-title">{title}</h1>
        {subtitle && <p className="sakar-page-subtitle">{subtitle}</p>}
      </div>
      {actions}
    </div>
  );
}
