import { describe, expect, it, vi, beforeEach } from 'vitest';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { CommandsPanel } from './CommandsPanel';
import { AuthProvider } from '../auth/AuthContext';
import { ToastProvider } from '../../components/ui/Toast';
import * as commandsApi from '../../api/commands';
import { ApiRequestError } from '../../api/client';
import type { Page } from '../../types/api';
import type { CommandResult, Robot, RobotCommand } from '../../types/domain';

const sampleRobot: Robot = {
  id: 'robot-1',
  organizationId: 'org-1',
  siteId: 'site-1',
  robotModelId: 'model-1',
  name: 'CleanBot Alpha',
  serialNumber: 'SN-ALPHA-001',
  status: 'ACTIVE',
  capabilities: ['RETURN_TO_DOCK'],
  createdAt: '2026-01-01T00:00:00.000Z',
};

const sampleCommand: RobotCommand = {
  id: 'cmd-1',
  robotId: 'robot-1',
  commandType: 'RETURN_TO_DOCK',
  status: 'SENT',
  nonce: 'nonce-1',
  expiresAt: '2026-01-01T00:01:00.000Z',
  sentAt: '2026-01-01T00:00:01.000Z',
  dispatched: true,
  dispatchNote: 'Published to MQTT — awaiting the agent\'s acknowledgement.',
  createdAt: '2026-01-01T00:00:00.000Z',
};

function commandsPage(content: RobotCommand[]): Page<RobotCommand> {
  return { content, totalElements: content.length, totalPages: content.length ? 1 : 0, number: 0, size: 10, first: true, last: true, empty: content.length === 0 };
}

function resultsPage(content: CommandResult[]): Page<CommandResult> {
  return { content, totalElements: content.length, totalPages: content.length ? 1 : 0, number: 0, size: 25, first: true, last: true, empty: content.length === 0 };
}

function renderPanel() {
  return render(
    <ToastProvider>
      <AuthProvider>
        <CommandsPanel robot={sampleRobot} />
      </AuthProvider>
    </ToastProvider>,
  );
}

describe('CommandsPanel', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
    vi.spyOn(commandsApi, 'listCommands').mockResolvedValue(commandsPage([sampleCommand]));
  });

  it('renders the existing command history list unchanged', async () => {
    renderPanel();

    expect(await screen.findByText('RETURN_TO_DOCK')).toBeInTheDocument();
    expect(screen.getByText('SENT')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'View history' })).toBeInTheDocument();
  });

  it('does not show the lifecycle history section until a command is selected', async () => {
    renderPanel();

    await screen.findByText('RETURN_TO_DOCK');
    expect(screen.queryByText('Command Lifecycle History')).not.toBeInTheDocument();
  });

  it('calls getCommandResults with the correct robotId, commandId, and pagination params', async () => {
    const spy = vi.spyOn(commandsApi, 'getCommandResults').mockResolvedValue(resultsPage([]));
    renderPanel();

    await userEvent.click(await screen.findByRole('button', { name: 'View history' }));

    await waitFor(() => expect(spy).toHaveBeenCalledWith('robot-1', 'cmd-1', 0, 25));
  });

  it('shows a loading state while lifecycle history is being fetched', async () => {
    vi.spyOn(commandsApi, 'getCommandResults').mockReturnValue(new Promise(() => {}));
    renderPanel();

    await userEvent.click(await screen.findByRole('button', { name: 'View history' }));

    expect(await screen.findByText('Loading command history…')).toBeInTheDocument();
  });

  it('renders populated lifecycle history newest-first, with result/detail/duration/timestamp', async () => {
    vi.spyOn(commandsApi, 'getCommandResults').mockResolvedValue(
      resultsPage([
        { commandId: 'cmd-1', result: 'COMMAND_SUCCESS', detail: 'docked', durationMs: 4200, createdAt: '2026-01-01T00:00:05.000Z' },
        { commandId: 'cmd-1', result: 'COMMAND_RECEIVED', detail: 'received by agent', durationMs: null, createdAt: '2026-01-01T00:00:01.000Z' },
      ]),
    );
    renderPanel();

    await userEvent.click(await screen.findByRole('button', { name: 'View history' }));

    const historyHeading = await screen.findByText('Command Lifecycle History');
    const historyCard = historyHeading.closest('.sakar-card') ?? historyHeading.parentElement!;
    const rows = within(historyCard as HTMLElement).getAllByRole('row');
    // rows[0] is the header row; rows[1] must be the newest event (COMMAND_SUCCESS).
    expect(rows[1]).toHaveTextContent('COMMAND_SUCCESS');
    expect(rows[1]).toHaveTextContent('docked');
    expect(rows[1]).toHaveTextContent('4200 ms');
    expect(rows[2]).toHaveTextContent('COMMAND_RECEIVED');
    expect(rows[2]).toHaveTextContent('received by agent');
    expect(rows[2]).toHaveTextContent('—');
  });

  it('shows "No lifecycle events recorded yet" for an empty history', async () => {
    vi.spyOn(commandsApi, 'getCommandResults').mockResolvedValue(resultsPage([]));
    renderPanel();

    await userEvent.click(await screen.findByRole('button', { name: 'View history' }));

    expect(await screen.findByText('No lifecycle events recorded yet')).toBeInTheDocument();
  });

  it('shows a retry-able error state when lifecycle history fails to load', async () => {
    vi.spyOn(commandsApi, 'getCommandResults').mockRejectedValue(
      new ApiRequestError('Command not found: cmd-1', 404, null),
    );
    renderPanel();

    await userEvent.click(await screen.findByRole('button', { name: 'View history' }));

    expect(await screen.findByText('Could not load command history')).toBeInTheDocument();
    expect(screen.getByText('Command not found: cmd-1')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Retry' })).toBeInTheDocument();
  });

  it('hides the history section again when "Hide history" is clicked', async () => {
    vi.spyOn(commandsApi, 'getCommandResults').mockResolvedValue(resultsPage([]));
    renderPanel();

    await userEvent.click(await screen.findByRole('button', { name: 'View history' }));
    await screen.findByText('Command Lifecycle History');

    await userEvent.click(screen.getByRole('button', { name: 'Hide history' }));

    expect(screen.queryByText('Command Lifecycle History')).not.toBeInTheDocument();
  });
});
