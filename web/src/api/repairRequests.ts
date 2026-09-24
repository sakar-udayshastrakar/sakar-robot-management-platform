import { apiClient, unwrap } from './client';
import type { ApiResponse } from '../types/api';
import type { RepairRequest, RepairRequestStatus } from '../types/domain';

// Operation And Maintenance Platform → Customer Repair Requests (real
// work-order log, GET/POST/PATCH /api/v1/repair-requests).
export function listRepairRequests(): Promise<RepairRequest[]> {
  return unwrap(apiClient.get<ApiResponse<RepairRequest[]>>('/repair-requests'));
}

export interface CreateRepairRequestInput {
  organizationId: string;
  siteId?: string | null;
  robotId?: string | null;
  symptom: string;
  reportedBy?: string | null;
  notes?: string | null;
}

export function createRepairRequest(input: CreateRepairRequestInput): Promise<RepairRequest> {
  return unwrap(apiClient.post<ApiResponse<RepairRequest>>('/repair-requests', input));
}

export function updateRepairRequestStatus(id: string, status: RepairRequestStatus, notes?: string | null): Promise<RepairRequest> {
  return unwrap(apiClient.patch<ApiResponse<RepairRequest>>(`/repair-requests/${id}/status`, { status, notes }));
}
