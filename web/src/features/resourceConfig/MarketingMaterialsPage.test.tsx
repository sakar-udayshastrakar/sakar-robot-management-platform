import { describe, expect, it, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { MarketingMaterialsPage } from './MarketingMaterialsPage';
import { AuthProvider } from '../auth/AuthContext';
import { ToastProvider } from '../../components/ui/Toast';
import { clearSession, setTokens } from '../auth/session';
import * as materialsApi from '../../api/marketingMaterials';
import { ApiRequestError } from '../../api/client';
import type { MarketingMaterial } from '../../types/domain';

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
          <MarketingMaterialsPage />
        </AuthProvider>
      </ToastProvider>
    </MemoryRouter>,
  );
}

const sampleMaterial: MarketingMaterial = {
  id: 'material-1',
  organizationId: 'org-1',
  name: 'Launch banner',
  materialType: 'GENERAL',
  status: 'DRAFT',
  createdAt: '2026-01-01T00:00:00.000Z',
  updatedAt: '2026-01-01T00:00:00.000Z',
};

describe('MarketingMaterialsPage', () => {
  beforeEach(() => {
    clearSession();
    vi.restoreAllMocks();
  });

  it('renders materials from the real GET /marketing-materials shape', async () => {
    vi.spyOn(materialsApi, 'listMarketingMaterials').mockResolvedValue([sampleMaterial]);

    renderPage();

    await waitFor(() => expect(screen.getByText('Launch banner')).toBeInTheDocument());
    expect(screen.getByText('Draft')).toBeInTheDocument();
  });

  it('creates a material through the real POST /marketing-materials contract', async () => {
    vi.spyOn(materialsApi, 'listMarketingMaterials').mockResolvedValue([]);
    const createSpy = vi.spyOn(materialsApi, 'createMarketingMaterial').mockResolvedValue(sampleMaterial);

    renderPage();
    await waitFor(() => expect(screen.getByText('Materials (0)')).toBeInTheDocument());

    await userEvent.click(screen.getByRole('button', { name: 'Add' }));
    await userEvent.type(screen.getByLabelText('Material package name'), 'Launch banner');
    // The page-header trigger and the modal's own submit button share the accessible
    // name "Add" — the modal's submit button is the one rendered later in the tree.
    const addButtons = screen.getAllByRole('button', { name: 'Add' });
    await userEvent.click(addButtons[addButtons.length - 1]);

    await waitFor(() =>
      expect(createSpy).toHaveBeenCalledWith(expect.objectContaining({ organizationId: 'org-1', name: 'Launch banner' })),
    );
  });

  it('hides Add / Edit / Delete for a caller without ROBOT_CONFIGURE', async () => {
    vi.spyOn(materialsApi, 'listMarketingMaterials').mockResolvedValue([sampleMaterial]);

    renderPage(['ROBOT_VIEW']);

    await waitFor(() => expect(screen.getByText('Launch banner')).toBeInTheDocument());
    expect(screen.queryByRole('button', { name: 'Add' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Edit' })).not.toBeInTheDocument();
  });

  it('shows an error state when the backend call fails', async () => {
    vi.spyOn(materialsApi, 'listMarketingMaterials').mockRejectedValue(new ApiRequestError('Service unavailable', 503, null));

    renderPage();

    await waitFor(() => expect(screen.getByText('Could not load marketing materials')).toBeInTheDocument());
  });
});
