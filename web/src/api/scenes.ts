import { apiClient, unwrap } from './client';
import type { ApiResponse } from '../types/api';
import type { ResourceScene, SceneStatus } from '../types/domain';

// New Resource Configuration → Scene list (real CRUD, GET/POST/PUT/DELETE
// /api/v1/scenes). "Store" reuses the existing Site type (siteId), never a
// separate Store concept.
export function listScenes(): Promise<ResourceScene[]> {
  return unwrap(apiClient.get<ApiResponse<ResourceScene[]>>('/scenes'));
}

export interface CreateSceneInput {
  organizationId: string;
  siteId?: string | null;
  robotId?: string | null;
  name: string;
  resourcePackType?: string;
}

export function createScene(input: CreateSceneInput): Promise<ResourceScene> {
  return unwrap(apiClient.post<ApiResponse<ResourceScene>>('/scenes', input));
}

export interface UpdateSceneInput {
  siteId?: string | null;
  robotId?: string | null;
  name: string;
  resourcePackType?: string;
  status: SceneStatus;
}

export function updateScene(id: string, input: UpdateSceneInput): Promise<ResourceScene> {
  return unwrap(apiClient.put<ApiResponse<ResourceScene>>(`/scenes/${id}`, input));
}

export function deleteScene(id: string): Promise<void> {
  return unwrap(apiClient.delete<ApiResponse<void>>(`/scenes/${id}`));
}
