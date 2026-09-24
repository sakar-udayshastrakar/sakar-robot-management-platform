import { apiClient, unwrap } from './client';
import type { ApiResponse } from '../types/api';
import type { HotelTaskRecordResponse, OperationRankingResponse, RetentionRow, StoreRealtimeStatsResponse } from '../types/domain';

// Operational Dashboard (real GET /api/v1/dashboard/*) — every figure is
// computed from real robot_tasks/robots/sites rows; mileage/calls/rooms are
// always null ("not tracked"), never a fabricated number. See
// OperationRankingResponse's own comment (types/domain.ts).

export function getOperationRanking(): Promise<OperationRankingResponse> {
  return unwrap(apiClient.get<ApiResponse<OperationRankingResponse>>('/dashboard/operation-ranking'));
}

export function getStoreRealtimeStats(siteId?: string | null): Promise<StoreRealtimeStatsResponse> {
  return unwrap(apiClient.get<ApiResponse<StoreRealtimeStatsResponse>>('/dashboard/store-realtime', { params: siteId ? { siteId } : {} }));
}

export function getRetentionAnalytics(days = 8): Promise<RetentionRow[]> {
  return unwrap(apiClient.get<ApiResponse<RetentionRow[]>>('/dashboard/retention', { params: { days } }));
}

export interface HotelTaskRecordFilters {
  siteId?: string | null;
  robotId?: string | null;
  taskType?: string | null;
  from?: string | null;
  to?: string | null;
}

export function getHotelTaskRecord(filters: HotelTaskRecordFilters = {}): Promise<HotelTaskRecordResponse> {
  const params: Record<string, string> = {};
  if (filters.siteId) params.siteId = filters.siteId;
  if (filters.robotId) params.robotId = filters.robotId;
  if (filters.taskType) params.taskType = filters.taskType;
  if (filters.from) params.from = filters.from;
  if (filters.to) params.to = filters.to;
  return unwrap(apiClient.get<ApiResponse<HotelTaskRecordResponse>>('/dashboard/hotel-task-record', { params }));
}
