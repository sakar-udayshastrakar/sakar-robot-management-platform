import { useEffect, useState } from 'react';
import { getRobotStatus } from '../../api/robots';
import type { Robot } from '../../types/domain';

export interface ProbedStatus {
  online: boolean;
  mainState: string;
  observedAt: string;
}

// Bounded, on-demand live-status probe shared by the Dashboard's fleet
// table and KPI cards. Never polls continuously (Performance requirement)
// — runs once per robot-list change, capped, and a failed probe is
// recorded as "unavailable", never coerced into a fake reading.
export function useRobotStatusProbe(robots: Robot[] | null, limit = 12) {
  const [statuses, setStatuses] = useState<Map<string, ProbedStatus | 'unavailable'>>(new Map());
  const [probing, setProbing] = useState(false);

  useEffect(() => {
    if (!robots || robots.length === 0) {
      setStatuses(new Map());
      return;
    }
    let cancelled = false;
    setProbing(true);
    const sample = robots.slice(0, limit);

    Promise.allSettled(sample.map((r) => getRobotStatus(r.id))).then((results) => {
      if (cancelled) {
        return;
      }
      const next = new Map<string, ProbedStatus | 'unavailable'>();
      results.forEach((result, i) => {
        const robotId = sample[i].id;
        if (result.status === 'fulfilled') {
          next.set(robotId, { online: result.value.online, mainState: result.value.mainState, observedAt: result.value.observedAt });
        } else {
          next.set(robotId, 'unavailable');
        }
      });
      setStatuses(next);
      setProbing(false);
    });

    return () => {
      cancelled = true;
    };
  }, [robots, limit]);

  return { statuses, probing };
}
