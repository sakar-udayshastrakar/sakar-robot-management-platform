import { apiClient, unwrap } from './client';
import type { ApiResponse, Page } from '../types/api';
import type { RobotTask, TaskDetail } from '../types/domain';

export function listRobotTasks(robotId: string, page = 0, pageSize = 25): Promise<Page<RobotTask>> {
  return unwrap(apiClient.get<ApiResponse<Page<RobotTask>>>(`/robots/${robotId}/tasks`, { params: { page, pageSize } }));
}

// Mission Log (Operation And Maintenance Platform) — every task within the
// caller's organization scope, newest first.
export function listAllAccessibleTasks(page = 0, pageSize = 200): Promise<Page<RobotTask>> {
  return unwrap(apiClient.get<ApiResponse<Page<RobotTask>>>('/tasks', { params: { page, pageSize } }));
}

export interface CreateTaskInput {
  taskType: string;
  parameters?: string | null;
}

export function createTask(robotId: string, input: CreateTaskInput): Promise<RobotTask> {
  return unwrap(apiClient.post<ApiResponse<RobotTask>>(`/robots/${robotId}/tasks`, input));
}

export function getTask(taskId: string): Promise<TaskDetail> {
  return unwrap(apiClient.get<ApiResponse<TaskDetail>>(`/tasks/${taskId}`));
}

// Each of these mirrors one real lifecycle-transition endpoint — see
// RobotTaskService's ALLOWED_FROM table for exactly which prior status each
// one requires; an invalid transition is rejected by the backend
// (INVALID_TASK_TRANSITION), never silently accepted here.
export function startTask(taskId: string): Promise<RobotTask> {
  return unwrap(apiClient.post<ApiResponse<RobotTask>>(`/tasks/${taskId}/start`));
}

export function pauseTask(taskId: string): Promise<RobotTask> {
  return unwrap(apiClient.post<ApiResponse<RobotTask>>(`/tasks/${taskId}/pause`));
}

export function resumeTask(taskId: string): Promise<RobotTask> {
  return unwrap(apiClient.post<ApiResponse<RobotTask>>(`/tasks/${taskId}/resume`));
}

export function stopTask(taskId: string): Promise<RobotTask> {
  return unwrap(apiClient.post<ApiResponse<RobotTask>>(`/tasks/${taskId}/stop`));
}

export function cancelTask(taskId: string): Promise<RobotTask> {
  return unwrap(apiClient.post<ApiResponse<RobotTask>>(`/tasks/${taskId}/cancel`));
}
