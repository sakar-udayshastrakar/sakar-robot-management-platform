import { useEffect, useState } from 'react';
import { getRobotMapImage } from '../../api/robots';
import { ApiRequestError } from '../../api/client';

export type RobotMapImageStatus = 'loading' | 'success' | 'not-found' | 'error';

interface UseRobotMapImageResult {
  status: RobotMapImageStatus;
  imageUrl: string | null;
  error: string | null;
  refetch: () => void;
}

// Authenticated map-image loading (GET /robots/{id}/map/image). The browser
// cannot attach our Authorization header to a plain <img src="..."> request,
// so this fetches the PNG bytes through the same authenticated apiClient as
// everything else (see getRobotMapImage), then exposes a local `blob:`
// object URL for <img> to use instead — the JWT never appears in any URL.
//
// Object URL lifecycle: each effect run creates at most one object URL and
// that same run's own cleanup function is what revokes it — never a shared
// ref — so a robotId change, a refetch(), or unmounting all correctly revoke
// exactly the URL that run created, before (or instead of) a new one is
// created. This is what prevents a leak across robot switches, since React
// Router reuses this component across a route param change rather than
// unmounting it.
export function useRobotMapImage(robotId: string): UseRobotMapImageResult {
  const [status, setStatus] = useState<RobotMapImageStatus>('loading');
  const [imageUrl, setImageUrl] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [tick, setTick] = useState(0);

  const refetch = () => setTick((t) => t + 1);

  useEffect(() => {
    let cancelled = false;
    let createdUrl: string | null = null;
    setStatus('loading');
    setError(null);

    getRobotMapImage(robotId)
      .then((blob) => {
        if (cancelled) {
          return;
        }
        // The backend only ever writes a validated PNG (RobotMapImageService
        // checks the PNG magic bytes before ever storing one) — this is a
        // defensive check against an unexpected content type, not a
        // reimplementation of that validation.
        if (blob.type && !blob.type.startsWith('image/')) {
          setStatus('error');
          setError('Map image response was not an image');
          return;
        }
        createdUrl = URL.createObjectURL(blob);
        setImageUrl(createdUrl);
        setStatus('success');
      })
      .catch((err: unknown) => {
        if (cancelled) {
          return;
        }
        if (err instanceof ApiRequestError && err.status === 404) {
          setStatus('not-found');
          setError(null);
          return;
        }
        setStatus('error');
        setError(err instanceof Error ? err.message : 'Unexpected error');
      });

    return () => {
      cancelled = true;
      if (createdUrl) {
        URL.revokeObjectURL(createdUrl);
      }
    };
  }, [robotId, tick]);

  return { status, imageUrl, error, refetch };
}
