interface StateProps {
  title: string;
  detail?: string;
  action?: React.ReactNode;
}

export function LoadingState({ title = 'Loading…' }: { title?: string }) {
  return (
    <div className="sakar-state" role="status">
      <span className="sakar-state-title">{title}</span>
    </div>
  );
}

export function ErrorState({ title, detail, action }: StateProps) {
  return (
    <div className="sakar-state" role="alert">
      <span className="sakar-state-title">{title}</span>
      {detail && <span className="sakar-state-detail">{detail}</span>}
      {action}
    </div>
  );
}

export function EmptyState({ title, detail, action }: StateProps) {
  return (
    <div className="sakar-state">
      <span className="sakar-state-title">{title}</span>
      {detail && <span className="sakar-state-detail">{detail}</span>}
      {action}
    </div>
  );
}
