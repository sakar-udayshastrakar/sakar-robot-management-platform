export interface HeartbeatDisplay {
  // Primary, visible text — "Just now" / "2 min ago" / "Never received", etc.
  relative: string;
  // Full formatted date/time, or null when no heartbeat has ever been
  // received. Intended as secondary/tooltip information only.
  absolute: string | null;
}

// Display-only formatting of Robot.lastSeenAt. This NEVER determines
// connectivity — connectionStatus from the backend (RobotConnectivityService)
// remains the sole source of truth for Online/Offline/Unknown. This function
// only makes an already-decided, backend-authoritative timestamp easier to
// read; it must not be used to compute or infer a connection state anywhere.
//
// `now` is an injectable parameter (defaults to Date.now()) purely so this
// stays unit-testable without faking global time.
export function formatHeartbeat(lastSeenAt: string | null, now: number = Date.now()): HeartbeatDisplay {
  if (!lastSeenAt) {
    return { relative: 'Never received', absolute: null };
  }

  const then = new Date(lastSeenAt);
  const absolute = then.toLocaleString();
  // Clamp negative deltas (clock skew) to zero rather than showing a
  // confusing negative duration.
  const diffSeconds = Math.max(0, Math.floor((now - then.getTime()) / 1000));

  if (diffSeconds < 45) {
    return { relative: 'Just now', absolute };
  }
  const diffMinutes = Math.floor(diffSeconds / 60);
  if (diffMinutes < 60) {
    return { relative: `${diffMinutes} min ago`, absolute };
  }
  const diffHours = Math.floor(diffMinutes / 60);
  if (diffHours < 24) {
    return { relative: `${diffHours} hr${diffHours === 1 ? '' : 's'} ago`, absolute };
  }
  const diffDays = Math.floor(diffHours / 24);
  return { relative: `${diffDays} day${diffDays === 1 ? '' : 's'} ago`, absolute };
}
