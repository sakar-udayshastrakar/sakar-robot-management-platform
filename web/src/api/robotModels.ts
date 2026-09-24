import { apiClient, unwrap } from './client';
import type { ApiResponse } from '../types/api';
import type { RobotModel } from '../types/domain';

// Real, read-only lookup (GET /api/v1/robot-models) — previously nothing
// exposed RobotModel/RobotManufacturer to the frontend, so robot pages could
// only show a raw, truncated robotModelId UUID.
export function listRobotModels(): Promise<RobotModel[]> {
  return unwrap(apiClient.get<ApiResponse<RobotModel[]>>('/robot-models'));
}
