import type { ReactNode } from 'react';
import { Navigate, useLocation } from 'react-router-dom';
import { useAuth } from './AuthContext';
import type { PermissionCode } from '../../types/permissions';

interface ProtectedRouteProps {
  children: ReactNode;
  requirePermission?: PermissionCode;
}

export function ProtectedRoute({ children, requirePermission }: ProtectedRouteProps) {
  const { isAuthenticated, isInitializing, hasPermission } = useAuth();
  const location = useLocation();

  if (isInitializing) {
    return null;
  }

  if (!isAuthenticated) {
    return <Navigate to="/login" replace state={{ from: location }} />;
  }

  if (requirePermission && !hasPermission(requirePermission)) {
    return <Navigate to="/forbidden" replace />;
  }

  return <>{children}</>;
}
