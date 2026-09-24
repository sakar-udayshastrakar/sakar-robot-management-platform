import { describe, expect, it, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { StoreRealtimeStatsPage } from './StoreRealtimeStatsPage';
import * as dashboardApi from '../../api/dashboard';
import * as sitesApi from '../../api/sites';
import { ApiRequestError } from '../../api/client';
import type { Site, StoreRealtimeStatsResponse } from '../../types/domain';

function renderPage() {
  return render(
    <MemoryRouter>
      <StoreRealtimeStatsPage />
    </MemoryRouter>,
  );
}

const site: Site = {
  id: 'site-1', organizationId: 'org-1', name: 'Sakar robotics office', address: null, timezone: null,
  createdAt: '2026-01-01T00:00:00.000Z', updatedAt: '2026-01-01T00:00:00.000Z', area: 'INDIA',
  contactName: null, phone: null, email: null, sceneType: 'Hotel', chainBrand: false,
};

const response: StoreRealtimeStatsResponse = {
  tasksToday: 4,
  taskModeProportionToday: [{ taskType: 'SWEEP', count: 4, percentage: 100 }],
  callsToday: null,
  activeMachines: 2,
  mileageToday: null,
  averageSpeedMetersPerSecond: null,
};

describe('StoreRealtimeStatsPage', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
    vi.spyOn(sitesApi, 'listAllAccessibleSites').mockResolvedValue([site]);
  });

  it('renders real today-task counts and active machines, with calls/mileage/speed always Not tracked', async () => {
    vi.spyOn(dashboardApi, 'getStoreRealtimeStats').mockResolvedValue(response);

    renderPage();

    await waitFor(() => expect(screen.getAllByText('4').length).toBeGreaterThan(0));
    expect(screen.getByText('2 active')).toBeInTheDocument();
    expect(screen.getByText('Not tracked')).toBeInTheDocument();
  });

  it('shows an error state when the backend call fails', async () => {
    vi.spyOn(dashboardApi, 'getStoreRealtimeStats').mockRejectedValue(new ApiRequestError('Service unavailable', 503, null));

    renderPage();

    await waitFor(() => expect(screen.getByText('Could not load store real-time data')).toBeInTheDocument());
  });
});
