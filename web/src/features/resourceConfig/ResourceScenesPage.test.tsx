import { describe, expect, it, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { ResourceScenesPage } from './ResourceScenesPage';
import { AuthProvider } from '../auth/AuthContext';
import { ToastProvider } from '../../components/ui/Toast';
import { clearSession, setTokens } from '../auth/session';
import * as scenesApi from '../../api/scenes';
import * as robotsApi from '../../api/robots';
import * as sitesApi from '../../api/sites';
import { ApiRequestError } from '../../api/client';
import type { ResourceScene } from '../../types/domain';

function fakeToken(perms: string[]) {
  const header = btoa(JSON.stringify({ alg: 'HS384' }));
  const payload = btoa(
    JSON.stringify({
      sub: 'admin-1',
      email: 'admin@sakarrobotics.com',
      role: 'ORG_ADMIN',
      perms,
      org: 'org-1',
      exp: Math.floor(Date.now() / 1000) + 900,
    }),
  );
  return `${header}.${payload}.sig`;
}

function renderPage(perms: string[] = ['ROBOT_VIEW', 'ROBOT_CONFIGURE']) {
  setTokens(fakeToken(perms), 'refresh-token');
  return render(
    <MemoryRouter>
      <ToastProvider>
        <AuthProvider>
          <ResourceScenesPage />
        </AuthProvider>
      </ToastProvider>
    </MemoryRouter>,
  );
}

function mockPage() {
  return { content: [], totalElements: 0, totalPages: 0, number: 0, size: 100, first: true, last: true, empty: true };
}

const sampleScene: ResourceScene = {
  id: 'scene-1',
  organizationId: 'org-1',
  siteId: null,
  robotId: null,
  name: 'Office demo',
  resourcePackType: 'STANDARD',
  status: 'DRAFT',
  createdAt: '2026-01-01T00:00:00.000Z',
  updatedAt: '2026-01-01T00:00:00.000Z',
};

describe('ResourceScenesPage', () => {
  beforeEach(() => {
    clearSession();
    vi.restoreAllMocks();
    vi.spyOn(robotsApi, 'listRobots').mockResolvedValue(mockPage());
    vi.spyOn(sitesApi, 'listSitesByOrganization').mockResolvedValue([]);
  });

  it('renders scenes from the real GET /scenes shape', async () => {
    vi.spyOn(scenesApi, 'listScenes').mockResolvedValue([sampleScene]);

    renderPage();

    await waitFor(() => expect(screen.getByText('Office demo')).toBeInTheDocument());
    expect(screen.getByText('Draft')).toBeInTheDocument();
  });

  it('creates a scene through the real POST /scenes contract', async () => {
    vi.spyOn(scenesApi, 'listScenes').mockResolvedValue([]);
    const createSpy = vi.spyOn(scenesApi, 'createScene').mockResolvedValue(sampleScene);

    renderPage();
    await waitFor(() => expect(screen.getByText('Scenes (0)')).toBeInTheDocument());

    await userEvent.click(screen.getByRole('button', { name: 'Add Scene' }));
    await userEvent.type(screen.getByLabelText('Scene name'), 'Office demo');
    await userEvent.click(screen.getByRole('button', { name: 'Add scene' }));

    await waitFor(() =>
      expect(createSpy).toHaveBeenCalledWith(expect.objectContaining({ organizationId: 'org-1', name: 'Office demo' })),
    );
  });

  it('hides Add Scene / Edit / Delete for a caller without ROBOT_CONFIGURE', async () => {
    vi.spyOn(scenesApi, 'listScenes').mockResolvedValue([sampleScene]);

    renderPage(['ROBOT_VIEW']);

    await waitFor(() => expect(screen.getByText('Office demo')).toBeInTheDocument());
    expect(screen.queryByRole('button', { name: 'Add Scene' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Edit' })).not.toBeInTheDocument();
  });

  it('shows an error state when the backend call fails', async () => {
    vi.spyOn(scenesApi, 'listScenes').mockRejectedValue(new ApiRequestError('Service unavailable', 503, null));

    renderPage();

    await waitFor(() => expect(screen.getByText('Could not load scenes')).toBeInTheDocument());
  });
});
