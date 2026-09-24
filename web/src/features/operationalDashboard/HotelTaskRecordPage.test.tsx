import { describe, expect, it, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { HotelTaskRecordPage } from './HotelTaskRecordPage';
import * as dashboardApi from '../../api/dashboard';
import * as sitesApi from '../../api/sites';
import * as robotsApi from '../../api/robots';
import { ApiRequestError } from '../../api/client';
import type { HotelTaskRecordResponse, Site } from '../../types/domain';

function renderPage() {
  return render(
    <MemoryRouter>
      <HotelTaskRecordPage />
    </MemoryRouter>,
  );
}

function pageOf<T>(content: T[]) {
  return { content, totalElements: content.length, totalPages: 1, number: 0, size: 200, first: true, last: true, empty: content.length === 0 };
}

const site: Site = {
  id: 'site-1', organizationId: 'org-1', name: 'Sakar robotics office', address: null, timezone: null,
  createdAt: '2026-01-01T00:00:00.000Z', updatedAt: '2026-01-01T00:00:00.000Z', area: 'INDIA',
  contactName: null, phone: null, email: null, sceneType: 'Hotel', chainBrand: false,
};

const response: HotelTaskRecordResponse = {
  totalVolumeOfTask: 12,
  cumulativeMileage: null,
  cumulativeDurationSeconds: 3660,
  numberOfRooms: null,
  dailyBreakdown: [{ date: '2026-09-23', count: 12 }],
  taskTypeBreakdown: [{ taskType: 'SWEEP', count: 12, percentage: 100 }],
};

describe('HotelTaskRecordPage', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
    vi.spyOn(sitesApi, 'listAllAccessibleSites').mockResolvedValue([site]);
    vi.spyOn(robotsApi, 'listRobots').mockResolvedValue(pageOf([]));
  });

  it('renders real task volume and formatted cumulative duration, with mileage/rooms always Not tracked', async () => {
    vi.spyOn(dashboardApi, 'getHotelTaskRecord').mockResolvedValue(response);

    renderPage();

    await waitFor(() => expect(screen.getAllByText('12').length).toBeGreaterThan(0));
    expect(screen.getByText('1h 1m')).toBeInTheDocument();
    expect(screen.getAllByText('Not tracked').length).toBe(2);
  });

  it('shows an error state when the backend call fails', async () => {
    vi.spyOn(dashboardApi, 'getHotelTaskRecord').mockRejectedValue(new ApiRequestError('Service unavailable', 503, null));

    renderPage();

    await waitFor(() => expect(screen.getByText('Could not load hotel task record')).toBeInTheDocument());
  });
});
