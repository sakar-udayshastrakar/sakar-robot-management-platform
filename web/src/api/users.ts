import { apiClient, unwrap } from './client';
import type { ApiResponse, Page } from '../types/api';
import type { PlatformUser, UserType } from '../types/domain';
import type { RoleName } from '../types/permissions';

export function listUsers(page = 0, pageSize = 25, userType?: UserType): Promise<Page<PlatformUser>> {
  return unwrap(
    apiClient.get<ApiResponse<Page<PlatformUser>>>('/users', { params: { page, pageSize, userType } }),
  );
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
  // Nullable/omittable: the backend defaults a missing value to EXTERNAL.
  // departmentId is only valid when userType is 'INTERNAL' — the backend
  // rejects it otherwise (400 VALIDATION_FAILED).
  userType?: UserType;
  departmentId?: string | null;
}

export function createUser(input: CreateUserInput): Promise<PlatformUser> {
  return unwrap(apiClient.post<ApiResponse<PlatformUser>>('/users', input));
}

export interface UpdateUserInput {
  fullName: string;
  departmentId?: string | null;
}

export function updateUser(id: string, input: UpdateUserInput): Promise<PlatformUser> {
  return unwrap(apiClient.put<ApiResponse<PlatformUser>>(`/users/${id}`, input));
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
