import { apiClient, unwrap } from './client';
import type { ApiResponse, Page } from '../types/api';
import type { RobotAlert } from '../types/domain';

// Organization-hierarchy scoped server-side (TenantAccessGuard) — no
// robotId/orgId query param exists, so cross-cutting filters (by robot,
// by severity) are applied client-side over this real, already-scoped page.
export function listAlerts(page = 0, pageSize = 25): Promise<Page<RobotAlert>> {
  return unwrap(apiClient.get<ApiResponse<Page<RobotAlert>>>('/alerts', { params: { page, pageSize } }));
}

export function acknowledgeAlert(id: string): Promise<RobotAlert> {
  return unwrap(apiClient.post<ApiResponse<RobotAlert>>(`/alerts/${id}/acknowledge`));
}

export function resolveAlert(id: string): Promise<RobotAlert> {
  return unwrap(apiClient.post<ApiResponse<RobotAlert>>(`/alerts/${id}/resolve`));
}
