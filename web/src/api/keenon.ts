import { apiClient, unwrap } from './client';
import type { ApiResponse } from '../types/api';
import type { SceneConfig } from '../types/domain';

// Real Keenon sceneCode configuration (GET/PUT .../keenon/scene-config) —
// Sakar-owned per-robot configuration data, never a live Keenon read (see
// KeenonMapMetadataSyncService's own backend Javadoc). A rejected promise
// with status 404 means no sceneCode has been configured for this robot
// yet; 422 means this robot model isn't a KEENON_CLOUD adapter at all.
export function getSceneConfig(robotId: string): Promise<SceneConfig> {
  return unwrap(apiClient.get<ApiResponse<SceneConfig>>(`/robots/${robotId}/keenon/scene-config`));
}

export interface SetSceneConfigInput {
  sceneCode: string;
  sceneName?: string | null;
}

export function setSceneConfig(robotId: string, input: SetSceneConfigInput): Promise<SceneConfig> {
  return unwrap(apiClient.put<ApiResponse<SceneConfig>>(`/robots/${robotId}/keenon/scene-config`, input));
}
