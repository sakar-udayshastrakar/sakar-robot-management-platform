import { describe, expect, it, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { DashboardPage } from './DashboardPage';
import { AuthProvider } from '../auth/AuthContext';
import { ToastProvider } from '../../components/ui/Toast';
import * as dashboardApi from '../../api/dashboard';
import { trackVisit } from '../../hooks/useRecentlyUsed';
import type { HotelTaskRecordResponse } from '../../types/domain';

function renderPage() {
  return render(
    <MemoryRouter>
      <ToastProvider>
        <AuthProvider>
          <DashboardPage />
        </AuthProvider>
      </ToastProvider>
    </MemoryRouter>,
  );
}

const hotelTaskRecordResponse: HotelTaskRecordResponse = {
  totalVolumeOfTask: 7,
  cumulativeMileage: null,
  cumulativeDurationSeconds: 0,
  numberOfRooms: null,
  dailyBreakdown: [{ date: '2026-09-24', count: 7 }],
  taskTypeBreakdown: [{ taskType: 'SWEEP', count: 7, percentage: 100 }],
};

describe('DashboardPage', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
    localStorage.clear();
    vi.spyOn(dashboardApi, 'getHotelTaskRecord').mockResolvedValue(hotelTaskRecordResponse);
  });

  it('renders the hero carousel with its real slides, and clicking one navigates to its real page', async () => {
    renderPage();

    await waitFor(() => expect(screen.getByText('SAKAR ROBOT MANAGEMENT PLATFORM')).toBeInTheDocument());
    expect(screen.getByRole('region', { name: 'Dashboard highlights' })).toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: 'Next slide' }));
    expect(screen.getByText('IoT PLATFORM')).toBeInTheDocument();
  });

  it('shows an empty prompt in Recently Used when nothing has been visited yet', async () => {
    renderPage();

    await waitFor(() => expect(screen.getByText('Pages you visit will show up here.')).toBeInTheDocument());
  });

  it('lists a real, previously-visited page in Recently Used', async () => {
    trackVisit('/robots');

    renderPage();

    await waitFor(() => expect(screen.getByText('Robot List')).toBeInTheDocument());
  });

  it('shows the real seven-day task total and task-type distribution from the dashboard API', async () => {
    renderPage();

    await waitFor(() => expect(screen.getByText('tasks in the last 7 days')).toBeInTheDocument());
    const overviewCard = screen.getByText('Seven-day Overview').closest('.sakar-card');
    expect(overviewCard).toHaveTextContent('7');
    expect(screen.getByText('Task Distribution')).toBeInTheDocument();
  });

  it('shows Not-tracked mileage on the Task Statistics Mileage tab, never a fabricated number', async () => {
    renderPage();
    await waitFor(() => expect(screen.getByText('Task Statistics')).toBeInTheDocument());

    await userEvent.click(screen.getByRole('tab', { name: 'Mileage' }));

    expect(screen.getByText(/No distance\/odometer/)).toBeInTheDocument();
  });

  it('degrades to "No data available" instead of crashing when the backend response is missing the new fields', async () => {
    // Simulates an older backend build that predates taskTypeBreakdown/dailyBreakdown —
    // exactly what broke the page against a not-yet-updated remote backend.
    vi.spyOn(dashboardApi, 'getHotelTaskRecord').mockResolvedValue({
      totalVolumeOfTask: 3,
      cumulativeMileage: null,
      cumulativeDurationSeconds: 0,
      numberOfRooms: null,
    } as unknown as HotelTaskRecordResponse);

    renderPage();

    await waitFor(() => expect(screen.getByText('tasks in the last 7 days')).toBeInTheDocument());
    expect(screen.getAllByText('No data available').length).toBeGreaterThan(0);
  });
});
