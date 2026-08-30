import { apiClient, unwrap } from './client';
import type { ApiResponse, Page } from '../types/api';
import type { PlatformUser } from '../types/domain';
import type { RoleName } from '../types/permissions';

export function listUsers(page = 0, pageSize = 25): Promise<Page<PlatformUser>> {
  return unwrap(apiClient.get<ApiResponse<Page<PlatformUser>>>('/users', { params: { page, pageSize } }));
}

export function getUser(id: string): Promise<PlatformUser> {
  return unwrap(apiClient.get<ApiResponse<PlatformUser>>(`/users/${id}`));
}

export interface CreateUserInput {
  organizationId?: string | null;
  email: string;
  password: string;
  fullName: string;
  roleName: RoleName;
}

export function createUser(input: CreateUserInput): Promise<PlatformUser> {
  return unwrap(apiClient.post<ApiResponse<PlatformUser>>('/users', input));
}

export function suspendUser(id: string): Promise<PlatformUser> {
  return unwrap(apiClient.post<ApiResponse<PlatformUser>>(`/users/${id}/suspend`));
}

export function activateUser(id: string): Promise<PlatformUser> {
  return unwrap(apiClient.post<ApiResponse<PlatformUser>>(`/users/${id}/activate`));
}

export function changeUserRole(id: string, roleName: RoleName): Promise<PlatformUser> {
  return unwrap(apiClient.post<ApiResponse<PlatformUser>>(`/users/${id}/role`, { roleName }));
}
