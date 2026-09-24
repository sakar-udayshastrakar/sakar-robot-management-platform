import { apiClient, unwrap } from './client';
import type { ApiResponse } from '../types/api';
import type { DeploymentRecord, SoftwareVersion } from '../types/domain';

// OTA Management → System Version Management + Update record (real CRUD,
// GET/POST /api/v1/ota/*). "Push" only ever records that an operator
// pushed a version to a robot — see the backend service's own Javadoc for
// why this never claims real device delivery/confirmation.
export function listSoftwareVersions(): Promise<SoftwareVersion[]> {
  return unwrap(apiClient.get<ApiResponse<SoftwareVersion[]>>('/ota/versions'));
}

export interface CreateSoftwareVersionInput {
  organizationId: string;
  packageName: string;
  wholeMachineSoftware?: string | null;
  packageVersion: string;
  hardwareVersion?: string | null;
  grayscale?: boolean;
  sizeBytes?: number | null;
  notes?: string | null;
}

export function createSoftwareVersion(input: CreateSoftwareVersionInput): Promise<SoftwareVersion> {
  return unwrap(apiClient.post<ApiResponse<SoftwareVersion>>('/ota/versions', input));
}

export function pushSoftwareVersion(versionId: string, robotId: string, oldVersionNumber?: string | null): Promise<DeploymentRecord> {
  return unwrap(apiClient.post<ApiResponse<DeploymentRecord>>(`/ota/versions/${versionId}/push`, { robotId, oldVersionNumber }));
}

export function listDeploymentRecords(): Promise<DeploymentRecord[]> {
  return unwrap(apiClient.get<ApiResponse<DeploymentRecord[]>>('/ota/deployment-records'));
}
