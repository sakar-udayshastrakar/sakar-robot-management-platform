import { apiClient, unwrap } from './client';
import type { ApiResponse, Page } from '../types/api';
import type { Robot, RobotBatteryInfo, RobotMqttCredentialResponse, RobotStatusSnapshot } from '../types/domain';

export function listRobots(page = 0, pageSize = 25): Promise<Page<Robot>> {
  return unwrap(apiClient.get<ApiResponse<Page<Robot>>>('/robots', { params: { page, pageSize } }));
}

export function getRobot(id: string): Promise<Robot> {
  return unwrap(apiClient.get<ApiResponse<Robot>>(`/robots/${id}`));
}

export interface RegisterRobotInput {
  organizationId: string;
  siteId?: string | null;
  robotModelId: string;
  name: string;
  serialNumber: string;
  externalRobotId?: string | null;
}

export function registerRobot(input: RegisterRobotInput): Promise<Robot> {
  return unwrap(apiClient.post<ApiResponse<Robot>>('/robots', input));
}

export function activateRobot(id: string): Promise<Robot> {
  return unwrap(apiClient.post<ApiResponse<Robot>>(`/robots/${id}/activate`));
}

export function deactivateRobot(id: string): Promise<Robot> {
  return unwrap(apiClient.post<ApiResponse<Robot>>(`/robots/${id}/deactivate`));
}

// Calls a live robot adapter (GET_STATUS capability). With no physical robot
// connected this commonly fails or returns UNSUPPORTED_CAPABILITY — callers
// must treat a rejected promise as "status unavailable", never as "offline".
export function getRobotStatus(id: string): Promise<RobotStatusSnapshot> {
  return unwrap(apiClient.get<ApiResponse<RobotStatusSnapshot>>(`/robots/${id}/status`));
}

// Calls a live robot adapter (GET_BATTERY capability) — same real
// adapter.getBattery() call already implemented for KEENON_CLOUD, now
// wired to a REST endpoint for the first time. Same failure semantics as
// getRobotStatus: a rejected promise means "unavailable", never "offline"
// or "0%".
export function getRobotBattery(id: string): Promise<RobotBatteryInfo> {
  return unwrap(apiClient.get<ApiResponse<RobotBatteryInfo>>(`/robots/${id}/battery`));
}

export function provisionMqttCredentials(id: string): Promise<RobotMqttCredentialResponse> {
  return unwrap(apiClient.post<ApiResponse<RobotMqttCredentialResponse>>(`/robots/${id}/mqtt-credentials`));
}

export function revokeMqttCredentials(id: string): Promise<void> {
  return unwrap(apiClient.delete<ApiResponse<void>>(`/robots/${id}/mqtt-credentials`));
}
