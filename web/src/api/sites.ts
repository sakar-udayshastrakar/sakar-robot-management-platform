import { apiClient, unwrap } from './client';
import type { ApiResponse } from '../types/api';
import type { Site } from '../types/domain';

export function listSitesByOrganization(organizationId: string): Promise<Site[]> {
  return unwrap(apiClient.get<ApiResponse<Site[]>>('/sites', { params: { organizationId } }));
}

export interface CreateSiteInput {
  organizationId: string;
  name: string;
  address?: string | null;
  timezone?: string | null;
}

export function createSite(input: CreateSiteInput): Promise<Site> {
  return unwrap(apiClient.post<ApiResponse<Site>>('/sites', input));
}
