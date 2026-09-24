import { describe, expect, it, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { OperationRankingPage } from './OperationRankingPage';
import * as dashboardApi from '../../api/dashboard';
import { ApiRequestError } from '../../api/client';
import type { OperationRankingResponse } from '../../types/domain';

function renderPage() {
  return render(
    <MemoryRouter>
      <OperationRankingPage />
    </MemoryRouter>,
  );
}

const response: OperationRankingResponse = {
  totalTasks: 130,
  totalMileage: null,
  totalCalls: null,
  storeRankingsByTasks: [{ id: 'site-1', label: 'Sakar robotics office', count: 130 }],
  robotRankingsByTasks: [{ id: 'robot-1', label: 'A8:B5:8E:B5:E3:E7', count: 130 }],
  storeRankingsByMileage: null,
  robotRankingsByMileage: null,
};

describe('OperationRankingPage', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  it('renders real task-count totals and rankings, with mileage/calls always Not tracked', async () => {
    vi.spyOn(dashboardApi, 'getOperationRanking').mockResolvedValue(response);

    renderPage();

    await waitFor(() => expect(screen.getAllByText('130').length).toBeGreaterThan(0));
    expect(screen.getAllByText('Not tracked').length).toBe(2);
    expect(screen.getByText('Sakar robotics office')).toBeInTheDocument();
    expect(screen.getByText('A8:B5:8E:B5:E3:E7')).toBeInTheDocument();
  });

  it('shows an unavailable-feature notice on the Mileage tab instead of fabricated data', async () => {
    vi.spyOn(dashboardApi, 'getOperationRanking').mockResolvedValue(response);

    renderPage();
    await waitFor(() => expect(screen.getByText('Sakar robotics office')).toBeInTheDocument());

    await userEvent.click(screen.getByRole('tab', { name: 'Mileage' }));

    expect(screen.getAllByText(/No distance\/odometer/).length).toBeGreaterThan(0);
  });

  it('shows an error state when the backend call fails', async () => {
    vi.spyOn(dashboardApi, 'getOperationRanking').mockRejectedValue(new ApiRequestError('Service unavailable', 503, null));

    renderPage();

    await waitFor(() => expect(screen.getByText('Could not load operation ranking')).toBeInTheDocument());
  });
});
