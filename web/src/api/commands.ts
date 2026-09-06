import { apiClient, unwrap } from './client';
import type { ApiResponse, Page } from '../types/api';
import type { CommandResult, NonLockCommandType, RobotCommand } from '../types/domain';

// LOCK/UNLOCK are deliberately absent from NonLockCommandType — there is no
// way to construct a request this function would accept for either, and the
// backend rejects them anyway (VALIDATION_FAILED) even if one were forced in.
export interface IssueCommandInput {
  commandType: NonLockCommandType;
  params?: Record<string, unknown> | null;
}

export function issueCommand(robotId: string, input: IssueCommandInput): Promise<RobotCommand> {
  return unwrap(apiClient.post<ApiResponse<RobotCommand>>(`/robots/${robotId}/commands`, input));
}

export function getCommand(robotId: string, commandId: string): Promise<RobotCommand> {
  return unwrap(apiClient.get<ApiResponse<RobotCommand>>(`/robots/${robotId}/commands/${commandId}`));
}

export function listCommands(robotId: string, page = 0, pageSize = 25): Promise<Page<RobotCommand>> {
  return unwrap(apiClient.get<ApiResponse<Page<RobotCommand>>>(`/robots/${robotId}/commands`, { params: { page, pageSize } }));
}

// Real command_results lifecycle history (GET .../commands/{commandId}/results,
// newest first) — never fabricated, reflects only what the agent (or, for a
// KEENON_CLOUD robot, the synchronous vendor round trip) actually reported.
export function getCommandResults(
  robotId: string,
  commandId: string,
  page = 0,
  pageSize = 25,
): Promise<Page<CommandResult>> {
  return unwrap(
    apiClient.get<ApiResponse<Page<CommandResult>>>(`/robots/${robotId}/commands/${commandId}/results`, {
      params: { page, pageSize },
    }),
  );
}
