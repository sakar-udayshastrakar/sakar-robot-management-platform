import { apiClient, unwrap } from './client';
import type { ApiResponse } from '../types/api';
import type { PermissionCatalogEntry } from '../types/domain';

// Real, read-only permission list (GET /api/v1/permissions) — the live
// source for the role-permission editor (RolesPage's Edit action).
// types/permissions.ts's PERMISSION_CODES remains the hardcoded mirror used
// for UI-gating everywhere else in the app; this is not a replacement for it.
export function listPermissions(): Promise<PermissionCatalogEntry[]> {
  return unwrap(apiClient.get<ApiResponse<PermissionCatalogEntry[]>>('/permissions'));
}
