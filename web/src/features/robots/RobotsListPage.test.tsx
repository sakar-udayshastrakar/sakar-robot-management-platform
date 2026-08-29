import { describe, expect, it, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { RobotsListPage } from './RobotsListPage';
import { AuthProvider } from '../auth/AuthContext';
import * as robotsApi from '../../api/robots';
import { ApiRequestError } from '../../api/client';
import type { Robot } from '../../types/domain';

function renderPage() {
  return render(
    <MemoryRouter>
      <AuthProvider>
        <RobotsListPage />
      </AuthProvider>
    </MemoryRouter>,
  );
}

const sampleRobot: Robot = {
  id: 'robot-1',
  organizationId: 'org-1',
  siteId: 'site-1',
  robotModelId: 'model-1',
  name: 'CleanBot Alpha',
  serialNumber: 'SN-001',
  status: 'ACTIVE',
  capabilities: ['GET_STATUS'],
  createdAt: new Date().toISOString(),
};

describe('RobotsListPage', () => {
  beforeEach(() => vi.restoreAllMocks());

  it('renders robots returned by the real GET /robots shape', async () => {
    vi.spyOn(robotsApi, 'listRobots').mockResolvedValue({
      content: [sampleRobot],
      totalElements: 1,
      totalPages: 1,
      number: 0,
      size: 25,
      first: true,
      last: true,
      empty: false,
    });

    renderPage();

    await waitFor(() => expect(screen.getByText('CleanBot Alpha')).toBeInTheDocument());
    expect(screen.getByText('SN-001')).toBeInTheDocument();
  });

  it('shows an empty state when the fleet has no robots', async () => {
    vi.spyOn(robotsApi, 'listRobots').mockResolvedValue({
      content: [], totalElements: 0, totalPages: 0, number: 0, size: 25, first: true, last: true, empty: true,
    });

    renderPage();

    await waitFor(() => expect(screen.getByText('No robots registered')).toBeInTheDocument());
  });

  it('shows an error state when the backend call fails', async () => {
    vi.spyOn(robotsApi, 'listRobots').mockRejectedValue(new ApiRequestError('Service unavailable', 503, null));

    renderPage();

    await waitFor(() => expect(screen.getByText('Could not load robots')).toBeInTheDocument());
    expect(screen.getByText('Service unavailable')).toBeInTheDocument();
  });
});
