import { apiClient, unwrap } from './client';
import type { ApiResponse, Page } from '../types/api';
import type { ApplicationLogEntry, RobotErrorEntry, RobotEventEntry } from '../types/domain';

// Tenant-scoped server-side via the same RobotService.getAccessibleOrThrow
// check every other per-robot endpoint uses (TenantAccessGuard) — a robot
// id outside the caller's organization scope returns 404, not empty data.
export function listRobotEvents(robotId: string, page = 0, pageSize = 25): Promise<Page<RobotEventEntry>> {
  return unwrap(apiClient.get<ApiResponse<Page<RobotEventEntry>>>(`/robots/${robotId}/events`, { params: { page, pageSize } }));
}

export function listRobotErrors(robotId: string, page = 0, pageSize = 25): Promise<Page<RobotErrorEntry>> {
  return unwrap(apiClient.get<ApiResponse<Page<RobotErrorEntry>>>(`/robots/${robotId}/errors`, { params: { page, pageSize } }));
}

export function listRobotLogs(robotId: string, page = 0, pageSize = 25): Promise<Page<ApplicationLogEntry>> {
  return unwrap(apiClient.get<ApiResponse<Page<ApplicationLogEntry>>>(`/robots/${robotId}/logs`, { params: { page, pageSize } }));
}
