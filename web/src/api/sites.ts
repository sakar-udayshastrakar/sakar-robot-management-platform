import { apiClient, unwrap } from './client';
import type { ApiResponse } from '../types/api';
import type { Site } from '../types/domain';

export function listSitesByOrganization(organizationId: string): Promise<Site[]> {
  return unwrap(apiClient.get<ApiResponse<Site[]>>('/sites', { params: { organizationId } }));
}

// Every site ("store") across the caller's accessible organizations — Store
// Management's store list (Robot Management sidebar group). No
// organizationId param, unlike listSitesByOrganization above.
export function listAllAccessibleSites(): Promise<Site[]> {
  return unwrap(apiClient.get<ApiResponse<Site[]>>('/sites'));
}

export interface SiteStoreFields {
  address?: string | null;
  timezone?: string | null;
  area?: string | null;
  contactName?: string | null;
  phone?: string | null;
  email?: string | null;
  sceneType?: string | null;
  chainBrand?: boolean;
}

export interface CreateSiteInput extends SiteStoreFields {
  organizationId: string;
  name: string;
}

export function createSite(input: CreateSiteInput): Promise<Site> {
  return unwrap(apiClient.post<ApiResponse<Site>>('/sites', input));
}

export interface UpdateSiteInput extends SiteStoreFields {
  name: string;
}

export function updateSite(id: string, input: UpdateSiteInput): Promise<Site> {
  return unwrap(apiClient.put<ApiResponse<Site>>(`/sites/${id}`, input));
}

export function deleteSite(id: string): Promise<void> {
  return unwrap(apiClient.delete<ApiResponse<void>>(`/sites/${id}`));
}
