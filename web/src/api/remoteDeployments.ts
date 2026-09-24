import { apiClient, unwrap } from './client';
import type { ApiResponse } from '../types/api';
import type { RemoteDeploymentRecord, RemoteDeploymentRecordStatus } from '../types/domain';

// Operation And Maintenance Platform → Remote Deployment (bookkeeping only,
// GET/POST/PATCH /api/v1/remote-deployments) — see the RemoteDeploymentRecord
// type's own comment for why status is operator-set, never a fabricated
// device confirmation.
export function listRemoteDeployments(): Promise<RemoteDeploymentRecord[]> {
  return unwrap(apiClient.get<ApiResponse<RemoteDeploymentRecord[]>>('/remote-deployments'));
}

export function createRemoteDeployment(robotId: string, notes?: string | null): Promise<RemoteDeploymentRecord> {
  return unwrap(apiClient.post<ApiResponse<RemoteDeploymentRecord>>('/remote-deployments', { robotId, notes }));
}

export function updateRemoteDeploymentStatus(id: string, status: RemoteDeploymentRecordStatus): Promise<RemoteDeploymentRecord> {
  return unwrap(apiClient.patch<ApiResponse<RemoteDeploymentRecord>>(`/remote-deployments/${id}/status`, { status }));
}
