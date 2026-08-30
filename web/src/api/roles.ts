import { apiClient, unwrap } from './client';
import type { ApiResponse } from '../types/api';
import type { RoleWithPermissions } from '../types/domain';

// Roles/permissions are fixed reference data (backend V9__seed_rbac.sql) —
// there is no create/edit/delete endpoint to wire up here, by design.
export function listRoles(): Promise<RoleWithPermissions[]> {
  return unwrap(apiClient.get<ApiResponse<RoleWithPermissions[]>>('/roles'));
}
