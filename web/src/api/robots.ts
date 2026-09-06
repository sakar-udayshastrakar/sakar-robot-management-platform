import { apiClient, unwrap } from './client';
import type { ApiResponse, Page } from '../types/api';
import type {
  Robot,
  RobotArea,
  RobotBatteryInfo,
  RobotMapMetadata,
  RobotMqttCredentialResponse,
  RobotStatusSnapshot,
} from '../types/domain';

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

// Calls a live robot adapter (GET_AREAS capability) — real Keenon area-list
// call (KeenonRobotAdapter.getAreas()), now wired to a REST endpoint for the
// first time. Metadata only (vendor area id + display name) — no polygon
// geometry exists anywhere upstream of this call, so none is modeled here.
// A rejected promise means "areas unavailable" (e.g. RESOURCE_NOT_FOUND when
// no Keenon store mapping has been synced for this robot yet), never "no areas".
export function getRobotAreas(id: string): Promise<RobotArea[]> {
  return unwrap(apiClient.get<ApiResponse<RobotArea[]>>(`/robots/${id}/areas`));
}

// Real map metadata (GET /robots/{id}/map, GET_MAP-independent — see the
// backend controller's own Javadoc for why this is never gated on the
// GET_MAP capability). vendorMapId/name/width/height/mapMd5/updatedAt only —
// never a filesystem path or raw vendor payload. A rejected promise with
// status 404 means no map has ever been synced for this robot.
export function getRobotMap(id: string): Promise<RobotMapMetadata> {
  return unwrap(apiClient.get<ApiResponse<RobotMapMetadata>>(`/robots/${id}/map`));
}

// Real map PNG (GET /robots/{id}/map/image) — served only from Sakar's own
// local storage (RobotMapImageService reads a previously-synced, validated
// file; this never makes a live Keenon call and never exposes a Keenon URL
// or a filesystem path). Unlike every other function in this file, the
// response is NOT the ApiResponse<T> JSON envelope — the backend responds
// with the raw PNG bytes and Content-Type: image/png directly, so this
// fetches it as a Blob instead of going through unwrap(). A rejected promise
// with status 404 means no map image has been synced for this robot yet.
export function getRobotMapImage(id: string): Promise<Blob> {
  return apiClient.get<Blob>(`/robots/${id}/map/image`, { responseType: 'blob' }).then((response) => response.data);
}

export function provisionMqttCredentials(id: string): Promise<RobotMqttCredentialResponse> {
  return unwrap(apiClient.post<ApiResponse<RobotMqttCredentialResponse>>(`/robots/${id}/mqtt-credentials`));
}

export function revokeMqttCredentials(id: string): Promise<void> {
  return unwrap(apiClient.delete<ApiResponse<void>>(`/robots/${id}/mqtt-credentials`));
}
