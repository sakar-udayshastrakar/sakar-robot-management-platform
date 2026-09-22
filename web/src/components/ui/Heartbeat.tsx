import { formatHeartbeat } from '../../utils/formatHeartbeat';

// Renders Robot.lastSeenAt as relative text ("2 min ago") with the full
// absolute timestamp available as a native tooltip (title) — never the
// reverse, and never used to decide Online/Offline/Unknown (that stays
// StatusBadge + Robot.connectionStatus only; see ConnectionStatusBadge).
export function Heartbeat({ lastSeenAt }: { lastSeenAt: string | null }) {
  const { relative, absolute } = formatHeartbeat(lastSeenAt);
  if (!absolute) {
    return <span className="sakar-page-subtitle sakar-nowrap">{relative}</span>;
  }
  return (
    <span className="sakar-nowrap" title={absolute}>
      {relative}
    </span>
  );
}
