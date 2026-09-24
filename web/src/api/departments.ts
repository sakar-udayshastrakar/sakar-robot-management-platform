import { apiClient, unwrap } from './client';
import type { ApiResponse } from '../types/api';
import type { Department } from '../types/domain';

// Real CRUD (GET/POST/PUT/DELETE /api/v1/departments) — a flat, Sakar-wide
// list used only to group INTERNAL users (Account Permission Platform,
// Phase 1). No organization scoping, matching the backend's own model.
export function listDepartments(): Promise<Department[]> {
  return unwrap(apiClient.get<ApiResponse<Department[]>>('/departments'));
}

export function createDepartment(name: string): Promise<Department> {
  return unwrap(apiClient.post<ApiResponse<Department>>('/departments', { name }));
}

export function renameDepartment(id: string, name: string): Promise<Department> {
  return unwrap(apiClient.put<ApiResponse<Department>>(`/departments/${id}`, { name }));
}

export function deleteDepartment(id: string): Promise<void> {
  return unwrap(apiClient.delete<ApiResponse<void>>(`/departments/${id}`));
}
