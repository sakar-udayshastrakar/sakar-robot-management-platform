import { describe, expect, it, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { KeenonSceneConfigPanel } from './KeenonSceneConfigPanel';
import { AuthProvider } from '../auth/AuthContext';
import { ToastProvider } from '../../components/ui/Toast';
import { clearSession, setTokens } from '../auth/session';
import * as keenonApi from '../../api/keenon';
import { ApiRequestError } from '../../api/client';

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

function renderPanel(perms: string[] = ['ROBOT_CONFIGURE']) {
  setTokens(fakeToken(perms), 'refresh-token');
  return render(
    <ToastProvider>
      <AuthProvider>
        <KeenonSceneConfigPanel robotId="robot-1" />
      </AuthProvider>
    </ToastProvider>,
  );
}

describe('KeenonSceneConfigPanel', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
    clearSession();
  });

  it('calls getSceneConfig with the correct robotId', async () => {
    const spy = vi.spyOn(keenonApi, 'getSceneConfig').mockResolvedValue({ sceneCode: '7ClJPR', sceneName: 'F' });

    renderPanel();

    await waitFor(() => expect(spy).toHaveBeenCalledWith('robot-1'));
  });

  it('shows a loading state while scene configuration is being fetched', () => {
    vi.spyOn(keenonApi, 'getSceneConfig').mockReturnValue(new Promise(() => {}));

    renderPanel();

    expect(screen.getByText('Loading Keenon scene configuration…')).toBeInTheDocument();
  });

  it('renders the configured sceneCode and sceneName', async () => {
    vi.spyOn(keenonApi, 'getSceneConfig').mockResolvedValue({ sceneCode: '7ClJPR', sceneName: 'F' });

    renderPanel();

    expect(await screen.findByText('7ClJPR')).toBeInTheDocument();
    expect(screen.getByText('F')).toBeInTheDocument();
  });

  it('renders "—" when sceneName is not set', async () => {
    vi.spyOn(keenonApi, 'getSceneConfig').mockResolvedValue({ sceneCode: '7ClJPR', sceneName: null });

    renderPanel();

    expect(await screen.findByText('7ClJPR')).toBeInTheDocument();
    expect(screen.getByText('—')).toBeInTheDocument();
  });

  it('shows a setup state and form when no scene is configured (404)', async () => {
    vi.spyOn(keenonApi, 'getSceneConfig').mockRejectedValue(
      new ApiRequestError('No Keenon scene configured for robot robot-1', 404, null),
    );

    renderPanel();

    expect(await screen.findByText('Not configured yet')).toBeInTheDocument();
    expect(screen.getByLabelText('Scene code')).toBeInTheDocument();
  });

  it('shows an "unavailable for this robot" state for a non-Keenon robot (422)', async () => {
    vi.spyOn(keenonApi, 'getSceneConfig').mockRejectedValue(
      new ApiRequestError('Keenon scene configuration is only supported for KEENON_CLOUD robots', 422, null),
    );

    renderPanel();

    expect(await screen.findByText('Not available for this robot')).toBeInTheDocument();
    expect(screen.queryByLabelText('Scene code')).not.toBeInTheDocument();
  });

  it('shows a retry-able error state for other failures', async () => {
    vi.spyOn(keenonApi, 'getSceneConfig').mockRejectedValue(
      new ApiRequestError('Request failed with status code 500', 500, null),
    );

    renderPanel();

    expect(await screen.findByText('Could not load scene configuration')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Retry' })).toBeInTheDocument();
  });

  it('rejects a blank scene code without calling the API', async () => {
    vi.spyOn(keenonApi, 'getSceneConfig').mockRejectedValue(new ApiRequestError('not found', 404, null));
    const setSpy = vi.spyOn(keenonApi, 'setSceneConfig');

    renderPanel();
    await screen.findByLabelText('Scene code');

    await userEvent.clear(screen.getByLabelText('Scene code'));
    await userEvent.click(screen.getByRole('button', { name: 'Save scene configuration' }));

    expect(await screen.findByText('Scene code is required.')).toBeInTheDocument();
    expect(setSpy).not.toHaveBeenCalled();
  });

  it('calls setSceneConfig with the trimmed sceneCode/sceneName and refreshes the displayed configuration', async () => {
    vi.spyOn(keenonApi, 'getSceneConfig')
      .mockRejectedValueOnce(new ApiRequestError('not found', 404, null))
      .mockResolvedValueOnce({ sceneCode: '7ClJPR', sceneName: 'F' });
    const setSpy = vi.spyOn(keenonApi, 'setSceneConfig').mockResolvedValue({ sceneCode: '7ClJPR', sceneName: 'F' });

    renderPanel();
    await screen.findByLabelText('Scene code');

    await userEvent.type(screen.getByLabelText('Scene code'), '  7ClJPR  ');
    await userEvent.type(screen.getByLabelText('Scene name (optional)'), '  F  ');
    await userEvent.click(screen.getByRole('button', { name: 'Save scene configuration' }));

    await waitFor(() => expect(setSpy).toHaveBeenCalledWith('robot-1', { sceneCode: '7ClJPR', sceneName: 'F' }));
    expect(await screen.findByText('Keenon scene configuration saved')).toBeInTheDocument();

    // Displayed configuration refetches and now visibly shows the saved value —
    // not just the toast — and the "not configured yet" setup state is gone.
    expect(await screen.findByText('Scene code:')).toBeInTheDocument();
    expect(screen.getByText('7ClJPR')).toBeInTheDocument();
    expect(screen.getByText('Scene name:')).toBeInTheDocument();
    expect(screen.getByText('F')).toBeInTheDocument();
    expect(screen.queryByText('Not configured yet')).not.toBeInTheDocument();
  });

  it('does not render the setup/edit form for a caller without ROBOT_CONFIGURE', async () => {
    vi.spyOn(keenonApi, 'getSceneConfig').mockRejectedValue(new ApiRequestError('not found', 404, null));

    renderPanel([]);

    expect(await screen.findByText('Not configured yet')).toBeInTheDocument();
    expect(screen.queryByLabelText('Scene code')).not.toBeInTheDocument();
  });
});
