import { useEffect, useState } from 'react';
import { listSitesByOrganization } from '../../api/sites';
import type { Robot } from '../../types/domain';

// There is no GET /sites/{id} — only GET /sites?organizationId=. To show a
// real site name next to a robot (instead of a raw UUID), this resolves
// every distinct organizationId referenced by the given robots and builds
// a siteId -> name map from the real, existing list-by-organization calls.
export function useSiteNames(robots: Robot[] | null) {
  const [names, setNames] = useState<Map<string, string>>(new Map());

  useEffect(() => {
    if (!robots || robots.length === 0) {
      setNames(new Map());
      return;
    }
    let cancelled = false;
    const orgIds = Array.from(new Set(robots.map((r) => r.organizationId)));

    Promise.allSettled(orgIds.map((orgId) => listSitesByOrganization(orgId))).then((results) => {
      if (cancelled) {
        return;
      }
      const map = new Map<string, string>();
      results.forEach((result) => {
        if (result.status === 'fulfilled') {
          result.value.forEach((site) => map.set(site.id, site.name));
        }
      });
      setNames(map);
    });

    return () => {
      cancelled = true;
    };
  }, [robots]);

  return names;
}
