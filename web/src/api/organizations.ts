import { apiClient, unwrap } from './client';
import type { ApiResponse } from '../types/api';
import type { Organization, OrganizationType } from '../types/domain';

export function getOrganization(id: string): Promise<Organization> {
  return unwrap(apiClient.get<ApiResponse<Organization>>(`/organizations/${id}`));
}

export function getOrganizationChildren(id: string): Promise<Organization[]> {
  return unwrap(apiClient.get<ApiResponse<Organization[]>>(`/organizations/${id}/children`));
}

export interface CreateOrganizationInput {
  name: string;
  orgType: OrganizationType;
  parentOrganizationId?: string | null;
}

export function createOrganization(input: CreateOrganizationInput): Promise<Organization> {
  return unwrap(apiClient.post<ApiResponse<Organization>>('/organizations', input));
}
