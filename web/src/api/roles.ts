import { apiClient, unwrap } from './client';
import type { ApiResponse } from '../types/api';
import type { RoleWithPermissions } from '../types/domain';
import type { PermissionCode } from '../types/permissions';

// The role NAME is fixed reference data (backend V9__seed_rbac.sql) — there
// is no rename/create/delete endpoint, and none is invented here. The
// description and permission set of an existing role can be edited, though
// — see updateRole below (Account Permission Platform, Phase 1).
export function listRoles(): Promise<RoleWithPermissions[]> {
  return unwrap(apiClient.get<ApiResponse<RoleWithPermissions[]>>('/roles'));
}

export function updateRole(id: string, description: string | null, permissionCodes: PermissionCode[]): Promise<RoleWithPermissions> {
  return unwrap(
    apiClient.put<ApiResponse<RoleWithPermissions>>(`/roles/${id}`, { description, permissionCodes }),
  );
}
