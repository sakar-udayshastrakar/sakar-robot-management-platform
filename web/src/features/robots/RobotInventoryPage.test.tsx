import { describe, expect, it, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { RobotInventoryPage } from './RobotInventoryPage';
import { AuthProvider } from '../auth/AuthContext';
import { ToastProvider } from '../../components/ui/Toast';
import { clearSession, setTokens } from '../auth/session';
import * as robotsApi from '../../api/robots';
import * as robotModelsApi from '../../api/robotModels';
import * as organizationsApi from '../../api/organizations';
import * as sitesApi from '../../api/sites';
import type { Robot } from '../../types/domain';

function fakeToken(perms: string[], organizationId: string | null = 'org-1') {
  const header = btoa(JSON.stringify({ alg: 'HS384' }));
  const payload = btoa(
    JSON.stringify({
      sub: 'admin-1',
      email: 'admin@sakarrobotics.com',
      role: 'ORG_ADMIN',
      perms,
      org: organizationId,
      exp: Math.floor(Date.now() / 1000) + 900,
    }),
  );
  return `${header}.${payload}.sig`;
}

function renderPage(scope: 'ALL' | 'SUB_AGENT' = 'ALL') {
  setTokens(fakeToken(['ROBOT_VIEW', 'ROBOT_CONFIGURE']), 'refresh-token');
  return render(
    <MemoryRouter>
      <ToastProvider>
        <AuthProvider>
          <RobotInventoryPage scope={scope} />
        </AuthProvider>
      </ToastProvider>
    </MemoryRouter>,
  );
}

function mockPage(content: Robot[]) {
  return { content, totalElements: content.length, totalPages: content.length ? 1 : 0, number: 0, size: 100, first: true, last: true, empty: content.length === 0 };
}

const sampleRobot: Robot = {
  id: 'robot-1',
  organizationId: 'org-1',
  siteId: null,
  robotModelId: 'model-1',
  name: 'CleanBot Alpha',
  serialNumber: 'SN-ALPHA-001',
  vendorSerialNumber: 'VENDOR-001',
  status: 'ACTIVE',
  capabilities: [],
  connectionStatus: 'UNKNOWN',
  lastSeenAt: null,
  createdAt: '2026-01-01T00:00:00.000Z',
  useType: 'TRIAL',
  warrantyStartDate: null,
  warrantyEndDate: null,
};

describe('RobotInventoryPage', () => {
  beforeEach(() => {
    clearSession();
    vi.restoreAllMocks();
    vi.spyOn(robotModelsApi, 'listRobotModels').mockResolvedValue([
      { id: 'model-1', manufacturerName: 'Keenon', name: 'C40 S', sakarProductName: 'Sakar CleanBot 5000 Plus' },
    ]);
    vi.spyOn(sitesApi, 'listSitesByOrganization').mockResolvedValue([]);
    vi.spyOn(organizationsApi, 'getOrganizationChildren').mockResolvedValue([]);
  });

  it('renders robots with resolved model name and warranty columns', async () => {
    vi.spyOn(robotsApi, 'listRobots').mockResolvedValue(mockPage([sampleRobot]));

    renderPage();

    await waitFor(() => expect(screen.getByText('CleanBot Alpha')).toBeInTheDocument);
    expect(await screen.findByText('Sakar CleanBot 5000 Plus')).toBeInTheDocument();
    expect(screen.getByText('SN-ALPHA-001')).toBeInTheDocument();
    expect(screen.getByText('VENDOR-001')).toBeInTheDocument();
    expect(screen.getByText('Trial')).toBeInTheDocument();
  });

  it('binds a store through the real PUT /robots/{id}/inventory contract', async () => {
    vi.spyOn(robotsApi, 'listRobots').mockResolvedValue(mockPage([sampleRobot]));
    vi.spyOn(sitesApi, 'listSitesByOrganization').mockResolvedValue([
      { id: 'site-1', organizationId: 'org-1', name: 'Sakar HQ', address: null, timezone: null, createdAt: '', updatedAt: '' },
    ]);
    const updateSpy = vi.spyOn(robotsApi, 'updateRobotInventory').mockResolvedValue({ ...sampleRobot, siteId: 'site-1' });

    renderPage();
    await waitFor(() => expect(screen.getByText('CleanBot Alpha')).toBeInTheDocument());

    await userEvent.click(screen.getByRole('button', { name: 'Bind Store' }));
    await waitFor(() => expect(screen.getByLabelText('Store (Site)')).toBeInTheDocument());
    await userEvent.selectOptions(screen.getByLabelText('Store (Site)'), 'site-1');
    await userEvent.click(screen.getByRole('button', { name: 'Save' }));

    await waitFor(() =>
      expect(updateSpy).toHaveBeenCalledWith('robot-1', expect.objectContaining({ siteId: 'site-1' })),
    );
  });

  it('allocates a robot to a child organization through the real POST /robots/{id}/allocate contract', async () => {
    vi.spyOn(robotsApi, 'listRobots').mockResolvedValue(mockPage([sampleRobot]));
    vi.spyOn(organizationsApi, 'getOrganizationChildren').mockResolvedValue([
      { id: 'org-2', parentOrganizationId: 'org-1', name: 'Sub Agent Co', orgType: 'SUB_DISTRIBUTOR', status: 'ACTIVE', path: '/org-1/org-2/', createdAt: '', updatedAt: '' },
    ]);
    const allocateSpy = vi.spyOn(robotsApi, 'allocateRobot').mockResolvedValue({ ...sampleRobot, organizationId: 'org-2' });

    renderPage();
    await waitFor(() => expect(screen.getByText('CleanBot Alpha')).toBeInTheDocument());

    await userEvent.click(screen.getByRole('button', { name: 'Allocate' }));
    await waitFor(() => expect(screen.getByLabelText('Target organization')).toBeInTheDocument());
    await userEvent.selectOptions(screen.getByLabelText('Target organization'), 'org-2');
    // The row trigger and the modal's own submit button share the accessible name "Allocate" —
    // the modal's submit button is the one rendered later in the tree.
    const allocateButtons = screen.getAllByRole('button', { name: 'Allocate' });
    await userEvent.click(allocateButtons[allocateButtons.length - 1]);

    await waitFor(() => expect(allocateSpy).toHaveBeenCalledWith('robot-1', 'org-2'));
  });

  it('returns a robot to inventory through the real POST /robots/{id}/return-to-inventory contract', async () => {
    vi.spyOn(robotsApi, 'listRobots').mockResolvedValue(mockPage([sampleRobot]));
    const returnSpy = vi.spyOn(robotsApi, 'returnRobotToInventory').mockResolvedValue({ ...sampleRobot, siteId: null, status: 'REGISTERED' });

    renderPage();
    await waitFor(() => expect(screen.getByText('CleanBot Alpha')).toBeInTheDocument());

    await userEvent.click(screen.getByRole('button', { name: 'Return to Inventory' }));
    // The confirm dialog's own button shares the same accessible name as the row trigger —
    // wait for the dialog's message text first, then the last matching button is the dialog's.
    await waitFor(() => expect(screen.getByText(/Unassign CleanBot Alpha/)).toBeInTheDocument());
    const confirmButtons = screen.getAllByRole('button', { name: 'Return to Inventory' });
    await userEvent.click(confirmButtons[confirmButtons.length - 1]);

    await waitFor(() => expect(returnSpy).toHaveBeenCalledWith('robot-1'));
  });

  it('sub-agent scope filters to robots belonging to a direct child organization', async () => {
    const childRobot: Robot = { ...sampleRobot, id: 'robot-2', name: 'CleanBot Sub', organizationId: 'org-2' };
    vi.spyOn(robotsApi, 'listRobots').mockResolvedValue(mockPage([sampleRobot, childRobot]));
    vi.spyOn(organizationsApi, 'getOrganizationChildren').mockResolvedValue([
      { id: 'org-2', parentOrganizationId: 'org-1', name: 'Sub Agent Co', orgType: 'SUB_DISTRIBUTOR', status: 'ACTIVE', path: '/org-1/org-2/', createdAt: '', updatedAt: '' },
    ]);

    renderPage('SUB_AGENT');

    await waitFor(() => expect(screen.getByText('CleanBot Sub')).toBeInTheDocument());
    expect(screen.queryByText('CleanBot Alpha')).not.toBeInTheDocument();
  });
});
