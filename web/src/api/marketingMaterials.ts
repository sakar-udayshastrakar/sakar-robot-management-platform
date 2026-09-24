import { apiClient, unwrap } from './client';
import type { ApiResponse } from '../types/api';
import type { MarketingMaterial, SceneStatus } from '../types/domain';

// New Resource Configuration → Marketing materials (real CRUD, GET/POST/PUT/
// DELETE /api/v1/marketing-materials). Named material packages only — no
// file/asset storage yet, see the backend controller's own Javadoc.
export function listMarketingMaterials(): Promise<MarketingMaterial[]> {
  return unwrap(apiClient.get<ApiResponse<MarketingMaterial[]>>('/marketing-materials'));
}

export interface CreateMarketingMaterialInput {
  organizationId: string;
  name: string;
  materialType?: string;
}

export function createMarketingMaterial(input: CreateMarketingMaterialInput): Promise<MarketingMaterial> {
  return unwrap(apiClient.post<ApiResponse<MarketingMaterial>>('/marketing-materials', input));
}

export interface UpdateMarketingMaterialInput {
  name: string;
  materialType?: string;
  status: SceneStatus;
}

export function updateMarketingMaterial(id: string, input: UpdateMarketingMaterialInput): Promise<MarketingMaterial> {
  return unwrap(apiClient.put<ApiResponse<MarketingMaterial>>(`/marketing-materials/${id}`, input));
}

export function deleteMarketingMaterial(id: string): Promise<void> {
  return unwrap(apiClient.delete<ApiResponse<void>>(`/marketing-materials/${id}`));
}
