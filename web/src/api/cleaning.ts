import { apiClient, unwrap } from './client';
import type { ApiResponse, Page } from '../types/api';
import type { CleaningSession } from '../types/domain';

// Read-only — the backend has exactly one writer for this table (the
// task-completion hook in RobotTaskService), so there is no create/edit
// endpoint to wire up here.
export function listCleaningHistory(robotId: string, page = 0, pageSize = 25): Promise<Page<CleaningSession>> {
  return unwrap(apiClient.get<ApiResponse<Page<CleaningSession>>>(`/robots/${robotId}/cleaning/history`, { params: { page, pageSize } }));
}
