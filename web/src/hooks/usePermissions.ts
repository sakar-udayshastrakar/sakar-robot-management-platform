import { useAuth } from '../features/auth/AuthContext';
import type { PermissionCode } from '../types/permissions';

export function usePermissions() {
  const { hasPermission, user } = useAuth();
  const hasAny = (permissions: PermissionCode[]) => permissions.some(hasPermission);
  const hasAll = (permissions: PermissionCode[]) => permissions.every(hasPermission);
  return { hasPermission, hasAny, hasAll, role: user?.role ?? null };
}
