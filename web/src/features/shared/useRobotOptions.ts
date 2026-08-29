import { useEffect, useState } from 'react';
import { listRobots } from '../../api/robots';
import type { Robot } from '../../types/domain';

// Small shared helper for pages that need "pick a robot" context
// (telemetry/events/errors/alerts/timeline) — backed by the real
// GET /robots endpoint, capped to a reasonable page size for a picker.
export function useRobotOptions() {
  const [robots, setRobots] = useState<Robot[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let cancelled = false;
    listRobots(0, 100)
      .then((page) => {
        if (!cancelled) {
          setRobots(page.content);
        }
      })
      .finally(() => {
        if (!cancelled) {
          setLoading(false);
        }
      });
    return () => {
      cancelled = true;
    };
  }, []);

  return { robots, loading };
}
