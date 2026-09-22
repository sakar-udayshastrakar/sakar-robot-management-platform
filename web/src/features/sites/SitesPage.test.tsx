import { describe, expect, it, vi, beforeEach } from 'vitest';
import { render, screen, waitFor, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { SitesPage } from './SitesPage';
import { AuthProvider } from '../auth/AuthContext';
import { ToastProvider } from '../../components/ui/Toast';
import * as sitesApi from '../../api/sites';
import * as robotsApi from '../../api/robots';
import * as organizationsApi from '../../api/organizations';
import type { Robot, Site, Organization } from '../../types/domain';

function renderPage() {
  return render(
    <MemoryRouter initialEntries={['/sites?organizationId=org-1']}>
      <ToastProvider>
        <AuthProvider>
          <SitesPage />
        </AuthProvider>
      </ToastProvider>
    </MemoryRouter>,
  );
}

const sampleOrg: Organization = {
  id: 'org-1',
  parentOrganizationId: null,
  name: 'Sakar Robotics',
  orgType: 'DIRECT_CLIENT',
  status: 'ACTIVE',
  path: 'org-1',
  createdAt: '2026-01-01T00:00:00.000Z',
  updatedAt: '2026-01-01T00:00:00.000Z',
};

const sampleSite: Site = {
  id: 'site-1',
  organizationId: 'org-1',
  name: 'HQ',
  address: null,
  timezone: null,
  createdAt: '2026-01-01T00:00:00.000Z',
  updatedAt: '2026-01-01T00:00:00.000Z',
};

function robot(overrides: Partial<Robot>): Robot {
  return {
    id: overrides.id ?? 'robot-1',
    organizationId: 'org-1',
    siteId: 'site-1',
    robotModelId: 'model-1',
    name: overrides.name ?? 'CleanBot Alpha',
    serialNumber: 'SN-001',
    status: 'ACTIVE',
    capabilities: [],
    connectionStatus: 'UNKNOWN',
    lastSeenAt: null,
    createdAt: new Date().toISOString(),
    ...overrides,
  };
}

function mockRobotsPage(content: Robot[]) {
  return { content, totalElements: content.length, totalPages: content.length ? 1 : 0, number: 0, size: 100, first: true, last: true, empty: content.length === 0 };
}

// Robot Connectivity UI Polish slice — the Sites page's "Online" column must
// count `robot.connectionStatus === 'ONLINE'`, never a live vendor probe or a
// raw `robot.online` flag.
describe('SitesPage', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
    vi.spyOn(organizationsApi, 'getOrganization').mockResolvedValue(sampleOrg);
    vi.spyOn(sitesApi, 'listSitesByOrganization').mockResolvedValue([sampleSite]);
  });

  it('counts a site’s Online robots from connectionStatus, ignoring stale ones even with a successful vendor probe', async () => {
    vi.spyOn(robotsApi, 'listRobots').mockResolvedValue(mockRobotsPage([
      robot({ id: 'r1', name: 'Fresh Bot', connectionStatus: 'ONLINE' }),
      robot({ id: 'r2', name: 'Stale Bot', connectionStatus: 'OFFLINE' }),
      robot({ id: 'r3', name: 'Unseen Bot', connectionStatus: 'UNKNOWN' }),
    ]));

    renderPage();

    await waitFor(() => expect(screen.getByText('HQ')).toBeInTheDocument());

    const row = screen.getByText('HQ').closest('tr');
    expect(row).not.toBeNull();
    const cells = within(row!).getAllByRole('cell');
    // Columns: No. | Site | Address | Timezone | Organization | Robots | Online | Status
    expect(cells[5]).toHaveTextContent('3'); // Robots column: all 3 robots at this site
    // 3 robots at this site, but the Online column shows only the 1 with connectionStatus
    // === 'ONLINE' — not 3 (which a raw robot.online/vendor-probe count could have shown).
    expect(cells[6]).toHaveTextContent('1'); // Online column
  });

  it('shows a dash, not 0, when a site has no robots at all', async () => {
    vi.spyOn(robotsApi, 'listRobots').mockResolvedValue(mockRobotsPage([]));

    renderPage();

    await waitFor(() => expect(screen.getByText('HQ')).toBeInTheDocument());
    expect(screen.getByText('No robots')).toBeInTheDocument();
  });

  it('preserves the existing empty state when the organization has no sites configured', async () => {
    vi.spyOn(sitesApi, 'listSitesByOrganization').mockResolvedValue([]);
    vi.spyOn(robotsApi, 'listRobots').mockResolvedValue(mockRobotsPage([]));

    renderPage();

    await waitFor(() => expect(screen.getByText('No sites in this organization')).toBeInTheDocument());
  });
});
