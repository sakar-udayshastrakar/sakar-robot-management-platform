import type { ReactNode } from 'react';
import { Icon } from './Icon';

interface StateProps {
  title: string;
  detail?: string;
  action?: ReactNode;
}

export function LoadingState({ title = 'Loading…' }: { title?: string }) {
  return (
    <div className="sakar-state" role="status">
      <div className="sakar-state-icon">
        <Icon.refresh width={24} height={24} className="sakar-spin" />
      </div>
      <span className="sakar-state-title">{title}</span>
    </div>
  );
}

export function ErrorState({ title, detail, action }: StateProps) {
  return (
    <div className="sakar-state sakar-state--error" role="alert">
      <div className="sakar-state-icon">
        <Icon.alertOctagon width={26} height={26} />
      </div>
      <span className="sakar-state-title">{title}</span>
      {detail && <span className="sakar-state-detail">{detail}</span>}
      {action}
    </div>
  );
}

export function EmptyState({ title, detail, action }: StateProps) {
  return (
    <div className="sakar-state">
      <div className="sakar-state-icon">
        <Icon.fileText width={26} height={26} />
      </div>
      <span className="sakar-state-title">{title}</span>
      {detail && <span className="sakar-state-detail">{detail}</span>}
      {action}
    </div>
  );
}
