import { apiClient, unwrap } from './client';
import type { ApiResponse, Page } from '../types/api';
import type { AuditLog } from '../types/domain';

export function listAuditLogs(page = 0, pageSize = 25): Promise<Page<AuditLog>> {
  return unwrap(apiClient.get<ApiResponse<Page<AuditLog>>>('/audit-logs', { params: { page, pageSize } }));
}
