import { describe, expect, it, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { RetentionAnalyticsPage } from './RetentionAnalyticsPage';
import * as dashboardApi from '../../api/dashboard';
import { ApiRequestError } from '../../api/client';
import type { RetentionRow } from '../../types/domain';

function renderPage() {
  return render(
    <MemoryRouter>
      <RetentionAnalyticsPage />
    </MemoryRouter>,
  );
}

const rows: RetentionRow[] = [
  { date: '2026-09-23', used3: 1, used7: 0, used15: 0, unused3: 0, unused7: 0, unused15: 0 },
];

describe('RetentionAnalyticsPage', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  it('renders real, computed retention counts in both tables', async () => {
    vi.spyOn(dashboardApi, 'getRetentionAnalytics').mockResolvedValue(rows);

    renderPage();

    await waitFor(() => expect(screen.getAllByText('2026-09-23').length).toBe(2));
    expect(screen.getByText('Number of continuously used stores')).toBeInTheDocument();
    expect(screen.getByText('Number of consecutive unused stores')).toBeInTheDocument();
  });

  it('shows an error state when the backend call fails', async () => {
    vi.spyOn(dashboardApi, 'getRetentionAnalytics').mockRejectedValue(new ApiRequestError('Service unavailable', 503, null));

    renderPage();

    await waitFor(() => expect(screen.getByText('Could not load retention analytics')).toBeInTheDocument());
  });
});
