import { describe, expect, it } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import { useApi } from './useApi';
import { ApiRequestError } from '../api/client';

function Probe({ fetcher }: { fetcher: () => Promise<string> }) {
  const { data, status, error } = useApi(fetcher, []);
  return (
    <div>
      <span data-testid="status">{status}</span>
      <span data-testid="data">{data ?? ''}</span>
      <span data-testid="error">{error ?? ''}</span>
    </div>
  );
}

describe('useApi', () => {
  it('transitions loading -> success and exposes the resolved data', async () => {
    render(<Probe fetcher={() => Promise.resolve('hello')} />);
    await waitFor(() => expect(screen.getByTestId('status')).toHaveTextContent('success'));
    expect(screen.getByTestId('data')).toHaveTextContent('hello');
  });

  it('transitions loading -> error and exposes the ApiRequestError message', async () => {
    render(
      <Probe
        fetcher={() => Promise.reject(new ApiRequestError('Forbidden', 403, null))}
      />,
    );
    await waitFor(() => expect(screen.getByTestId('status')).toHaveTextContent('error'));
    expect(screen.getByTestId('error')).toHaveTextContent('Forbidden');
  });
});
