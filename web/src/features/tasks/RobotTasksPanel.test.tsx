import { describe, expect, it, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { RobotTasksPanel } from './RobotTasksPanel';
import { AuthProvider } from '../auth/AuthContext';
import { ToastProvider } from '../../components/ui/Toast';
import { clearSession, setTokens } from '../auth/session';
import * as tasksApi from '../../api/tasks';
import * as robotsApi from '../../api/robots';
import { ApiRequestError } from '../../api/client';
import type { Page } from '../../types/api';
import type { RobotArea, RobotTask } from '../../types/domain';

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

function tasksPage(content: RobotTask[]): Page<RobotTask> {
  return { content, totalElements: content.length, totalPages: content.length ? 1 : 0, number: 0, size: 10, first: true, last: true, empty: content.length === 0 };
}

const sampleTask: RobotTask = {
  id: 'task-1',
  robotId: 'robot-1',
  organizationId: 'org-1',
  createdBy: 'user-1',
  taskType: 'CLEANING',
  parameters: null,
  status: 'CREATED',
  createdAt: '2026-01-01T00:00:00.000Z',
  updatedAt: '2026-01-01T00:00:00.000Z',
};

const sampleAreas: RobotArea[] = [
  { vendorAreaId: 'vendor-area-1', displayName: 'Lobby', sakarAreaId: 'mapping-1' },
  { vendorAreaId: 'vendor-area-2', displayName: 'Conference Room', sakarAreaId: 'mapping-2' },
  { vendorAreaId: 'vendor-area-3', displayName: null, sakarAreaId: null }, // not yet synced — must never be selectable
];

// Extracted so a robot-switching test can `rerender` the same tree with a
// different robotId prop — exactly what TasksPage does when RobotPicker's
// selection changes — without re-mounting the ToastProvider/AuthProvider.
function panelTree(robotId: string) {
  return (
    <ToastProvider>
      <AuthProvider>
        <RobotTasksPanel robotId={robotId} />
      </AuthProvider>
    </ToastProvider>
  );
}

function renderPanel(perms: string[] = ['ROBOT_TASK_CREATE', 'ROBOT_CONTROL', 'ROBOT_TASK_CANCEL'], robotId = 'robot-1') {
  setTokens(fakeToken(perms), 'refresh-token');
  return render(panelTree(robotId));
}

describe('RobotTasksPanel', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
    clearSession();
    vi.spyOn(tasksApi, 'listRobotTasks').mockResolvedValue(tasksPage([]));
    vi.spyOn(robotsApi, 'getRobotAreas').mockResolvedValue(sampleAreas);
  });

  it('displays the existing task list unchanged', async () => {
    vi.spyOn(tasksApi, 'listRobotTasks').mockResolvedValue(tasksPage([sampleTask]));

    renderPanel();

    expect(await screen.findByText('CLEANING')).toBeInTheDocument();
    expect(screen.getByText('CREATED')).toBeInTheDocument();
  });

  it('calls startTask and cancelTask from the existing task-action buttons unchanged', async () => {
    vi.spyOn(tasksApi, 'listRobotTasks').mockResolvedValue(tasksPage([sampleTask]));
    const startSpy = vi.spyOn(tasksApi, 'startTask').mockResolvedValue({ ...sampleTask, status: 'RUNNING' });

    renderPanel();
    await screen.findByText('CLEANING');

    await userEvent.click(screen.getByRole('button', { name: 'Start' }));
    await waitFor(() => expect(startSpy).toHaveBeenCalledWith('task-1'));

    vi.spyOn(tasksApi, 'listRobotTasks').mockResolvedValue(tasksPage([{ ...sampleTask, status: 'RUNNING' }]));
    const cancelSpy = vi.spyOn(tasksApi, 'cancelTask').mockResolvedValue({ ...sampleTask, status: 'CANCELLED' });
    await userEvent.click(await screen.findByRole('button', { name: 'Cancel' }));
    await waitFor(() => expect(cancelSpy).toHaveBeenCalledWith('task-1'));
  });

  it('shows the cleaning mode dropdown with the backend-supported modes', async () => {
    renderPanel();

    const select = await screen.findByLabelText('Cleaning mode');
    const options = Array.from(select.querySelectorAll('option')).map((o) => o.textContent);
    expect(options).toEqual(['Select a mode…', 'Sweep', 'Sweep + Mop', 'Sweep + Vacuum', 'Sweep (push)', 'Water suction']);
  });

  it('fetches and displays selectable areas from the real GET /robots/{id}/areas endpoint', async () => {
    renderPanel();

    expect(await screen.findByText('Lobby')).toBeInTheDocument();
    expect(screen.getByText('Conference Room')).toBeInTheDocument();
    // The unsynced area (no sakarAreaId) must never be offered as a selectable checkbox.
    expect(screen.queryByText('vendor-area-3')).not.toBeInTheDocument();
    expect(screen.getAllByRole('checkbox')).toHaveLength(2);
  });

  it('allows selecting and deselecting an area checkbox', async () => {
    renderPanel();

    const lobbyCheckbox = (await screen.findByText('Lobby')).closest('label')!.querySelector('input')!;
    expect(lobbyCheckbox).not.toBeChecked();

    await userEvent.click(lobbyCheckbox);
    expect(lobbyCheckbox).toBeChecked();

    await userEvent.click(lobbyCheckbox);
    expect(lobbyCheckbox).not.toBeChecked();
  });

  it('rejects submission with no cleaning mode selected, without calling createTask', async () => {
    const createSpy = vi.spyOn(tasksApi, 'createTask');
    renderPanel();
    await screen.findByText('Lobby');

    await userEvent.click(screen.getByRole('button', { name: 'Create task' }));

    expect(await screen.findByText('Cleaning mode is required.')).toBeInTheDocument();
    expect(createSpy).not.toHaveBeenCalled();
  });

  it('rejects submission with no area selected, without calling createTask', async () => {
    const createSpy = vi.spyOn(tasksApi, 'createTask');
    renderPanel();
    await screen.findByText('Lobby');

    await userEvent.selectOptions(screen.getByLabelText('Cleaning mode'), 'SWEEP');
    await userEvent.click(screen.getByRole('button', { name: 'Create task' }));

    expect(await screen.findByText('Select at least one area.')).toBeInTheDocument();
    expect(createSpy).not.toHaveBeenCalled();
  });

  it('submits a structured parameters payload with mode, areaIds, and repeatCount', async () => {
    const createSpy = vi.spyOn(tasksApi, 'createTask').mockResolvedValue({ ...sampleTask, status: 'CREATED' });
    renderPanel();
    await screen.findByText('Lobby');

    await userEvent.selectOptions(screen.getByLabelText('Cleaning mode'), 'SWEEP_MOP');
    await userEvent.click((await screen.findByText('Lobby')).closest('label')!.querySelector('input')!);
    await userEvent.click(screen.getByRole('button', { name: 'Create task' }));

    await waitFor(() => expect(createSpy).toHaveBeenCalled());
    const [robotId, input] = createSpy.mock.calls[0];
    expect(robotId).toBe('robot-1');
    expect(input.taskType).toBe('CLEANING');
    expect(JSON.parse(input.parameters as string)).toEqual({ mode: 'SWEEP_MOP', areaIds: ['mapping-1'], repeatCount: 1 });
  });

  it('creates a non-CLEANING task without a structured parameters payload, unchanged', async () => {
    const createSpy = vi.spyOn(tasksApi, 'createTask').mockResolvedValue({ ...sampleTask, taskType: 'RETURN_TO_DOCK' });
    renderPanel();

    await userEvent.selectOptions(await screen.findByLabelText('Task type'), 'RETURN_TO_DOCK');
    await userEvent.click(screen.getByRole('button', { name: 'Create task' }));

    await waitFor(() => expect(createSpy).toHaveBeenCalledWith('robot-1', { taskType: 'RETURN_TO_DOCK' }));
  });

  it('does not render the create-task form for a caller without ROBOT_TASK_CREATE', async () => {
    renderPanel([]);

    await waitFor(() => expect(screen.queryByLabelText('Task type')).not.toBeInTheDocument());
  });

  // Regression coverage for the "Could not load areas" bug report: live
  // verification (GET /robots/{id}/areas → 200 with the real synced area)
  // found no reproducible frontend defect — useApi already resets its own
  // status/error/data on every dependency change — but this locks the
  // observed-correct behavior in so a future regression is caught here
  // rather than by another live screenshot.
  describe('area loading (GET /robots/{id}/areas)', () => {
    it('shows the synced area and no error when the request succeeds (HTTP 200)', async () => {
      vi.spyOn(robotsApi, 'getRobotAreas').mockResolvedValue([
        { vendorAreaId: 'b8acf9a058f4493891b10b7364a0c988', displayName: 'Area6', sakarAreaId: '9f85029a-8826-4d29-aa43-7713ed204be8' },
      ]);

      renderPanel();

      expect(await screen.findByText('Area6')).toBeInTheDocument();
      expect(screen.queryByText(/Could not load areas/)).not.toBeInTheDocument();
    });

    it('shows a loading indicator before the areas request resolves', async () => {
      vi.spyOn(robotsApi, 'getRobotAreas').mockReturnValue(new Promise(() => {})); // never settles

      renderPanel();

      expect(await screen.findByText('Loading areas…')).toBeInTheDocument();
    });

    it('shows the backend error message when the request fails (e.g. HTTP 404, no synced store mapping)', async () => {
      vi.spyOn(robotsApi, 'getRobotAreas').mockRejectedValue(
        new ApiRequestError('No synced Keenon store mapping for robot robot-1', 404, null),
      );

      renderPanel();

      expect(
        await screen.findByText('Could not load areas: No synced Keenon store mapping for robot robot-1'),
      ).toBeInTheDocument();
    });

    it('shows an empty-state message, not an error, when the robot has zero synced areas', async () => {
      vi.spyOn(robotsApi, 'getRobotAreas').mockResolvedValue([]);

      renderPanel();

      expect(await screen.findByText('No synced areas available for this robot.')).toBeInTheDocument();
      expect(screen.queryByText(/Could not load areas/)).not.toBeInTheDocument();
    });

    it('parses the real Sakar API envelope, using vendorAreaId as the label when displayName is not yet set', async () => {
      vi.spyOn(robotsApi, 'getRobotAreas').mockResolvedValue([
        { vendorAreaId: 'raw-vendor-area-id', displayName: null, sakarAreaId: 'mapping-9' },
      ]);

      renderPanel();

      expect(await screen.findByText('raw-vendor-area-id')).toBeInTheDocument();
    });

    it("fetches the newly selected robot's areas by its own id and clears the previous robot's areas — no stale area lingers", async () => {
      const getAreasSpy = vi.spyOn(robotsApi, 'getRobotAreas').mockResolvedValueOnce([
        { vendorAreaId: 'v-area6', displayName: 'Area6', sakarAreaId: 'area6-mapping' },
      ]);

      const { rerender } = renderPanel(undefined, 'robot-1');
      expect(await screen.findByText('Area6')).toBeInTheDocument();
      expect(getAreasSpy).toHaveBeenCalledWith('robot-1');

      getAreasSpy.mockResolvedValueOnce([
        { vendorAreaId: 'v-lobby', displayName: 'Lobby', sakarAreaId: 'lobby-mapping' },
      ]);
      rerender(panelTree('robot-2'));

      expect(await screen.findByText('Lobby')).toBeInTheDocument();
      expect(getAreasSpy).toHaveBeenCalledWith('robot-2');
      expect(screen.queryByText('Area6')).not.toBeInTheDocument();
    });

    it("clears the previous robot's area-load error once the newly selected robot's areas load successfully", async () => {
      const getAreasSpy = vi
        .spyOn(robotsApi, 'getRobotAreas')
        .mockRejectedValueOnce(new ApiRequestError('No synced Keenon store mapping for robot robot-1', 404, null));

      const { rerender } = renderPanel(undefined, 'robot-1');
      expect(await screen.findByText('Could not load areas: No synced Keenon store mapping for robot robot-1')).toBeInTheDocument();

      getAreasSpy.mockResolvedValueOnce([
        { vendorAreaId: 'v-lobby', displayName: 'Lobby', sakarAreaId: 'lobby-mapping' },
      ]);
      rerender(panelTree('robot-2'));

      expect(await screen.findByText('Lobby')).toBeInTheDocument();
      expect(screen.queryByText(/Could not load areas/)).not.toBeInTheDocument();
    });
  });
});
