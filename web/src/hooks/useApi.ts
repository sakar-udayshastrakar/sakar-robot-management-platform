import { useCallback, useEffect, useState } from 'react';
import { ApiRequestError } from '../api/client';

export type AsyncStatus = 'idle' | 'loading' | 'success' | 'error';

interface UseApiResult<T> {
  data: T | null;
  status: AsyncStatus;
  error: string | null;
  errorStatus: number | undefined;
  refetch: () => void;
}

// Small fetch hook shared by every list/detail page: gives every caller a
// consistent loading / error / empty rendering contract instead of each
// page re-inventing it.
export function useApi<T>(fetcher: () => Promise<T>, deps: unknown[]): UseApiResult<T> {
  const [data, setData] = useState<T | null>(null);
  const [status, setStatus] = useState<AsyncStatus>('idle');
  const [error, setError] = useState<string | null>(null);
  const [errorStatus, setErrorStatus] = useState<number | undefined>(undefined);
  const [tick, setTick] = useState(0);

  const refetch = useCallback(() => setTick((t) => t + 1), []);

  useEffect(() => {
    let cancelled = false;
    setStatus('loading');
    setError(null);
    setErrorStatus(undefined);

    fetcher()
      .then((result) => {
        if (!cancelled) {
          setData(result);
          setStatus('success');
        }
      })
      .catch((err: unknown) => {
        if (cancelled) {
          return;
        }
        if (err instanceof ApiRequestError) {
          setError(err.message);
          setErrorStatus(err.status);
        } else {
          setError(err instanceof Error ? err.message : 'Unexpected error');
        }
        setStatus('error');
      });

    return () => {
      cancelled = true;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [...deps, tick]);

  return { data, status, error, errorStatus, refetch };
}
