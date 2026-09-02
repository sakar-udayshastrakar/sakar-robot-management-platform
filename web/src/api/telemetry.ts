import { apiClient, unwrap } from './client';
import type { ApiResponse, Page } from '../types/api';
import type { RobotTelemetryEntry } from '../types/domain';

// Tenant-scoped server-side via the same RobotService.getAccessibleOrThrow
// check every other per-robot endpoint uses (TenantAccessGuard) — a robot
// id outside the caller's organization scope returns 404, not empty data.
export function listRobotTelemetry(robotId: string, page = 0, pageSize = 25): Promise<Page<RobotTelemetryEntry>> {
  return unwrap(
    apiClient.get<ApiResponse<Page<RobotTelemetryEntry>>>(`/robots/${robotId}/telemetry`, { params: { page, pageSize } }),
  );
}
