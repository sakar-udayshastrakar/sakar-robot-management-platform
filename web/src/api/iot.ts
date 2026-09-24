import { apiClient, unwrap } from './client';
import type { ApiResponse } from '../types/api';
import type {
  ElevatorConfiguration,
  ElevatorConfigurationDelivery,
  ElevatorConfigurationEvent,
  ElevatorDeliveryStatus,
  ElevatorDevice,
  LadderControlStoreBinding,
  PhoneDevice,
} from '../types/domain';

// IoT Platform → Elevator Module + Phone Module (real CRUD + bookkeeping,
// GET/POST/PUT /api/v1/iot/*). See ElevatorConfigurationDelivery's own
// comment (types/domain.ts) for why "delivered" is operator-recorded only.

export function listElevatorDevices(): Promise<ElevatorDevice[]> {
  return unwrap(apiClient.get<ApiResponse<ElevatorDevice[]>>('/iot/elevator-devices'));
}

export interface CreateElevatorDeviceInput {
  organizationId: string;
  siteId: string;
  deviceId: string;
  deviceName?: string | null;
  building?: string | null;
  protocol?: string | null;
  networkingMode?: string | null;
  communicationMode?: string | null;
}

export function registerElevatorDevice(input: CreateElevatorDeviceInput): Promise<ElevatorDevice> {
  return unwrap(apiClient.post<ApiResponse<ElevatorDevice>>('/iot/elevator-devices', input));
}

export function listElevatorConfigurations(): Promise<ElevatorConfiguration[]> {
  return unwrap(apiClient.get<ApiResponse<ElevatorConfiguration[]>>('/iot/elevator-configurations'));
}

export interface CreateElevatorConfigurationInput {
  organizationId: string;
  siteId: string;
  elevatorDeviceId: string;
  robotId: string;
  name: string;
  notes?: string | null;
}

export function createElevatorConfiguration(input: CreateElevatorConfigurationInput): Promise<ElevatorConfiguration> {
  return unwrap(apiClient.post<ApiResponse<ElevatorConfiguration>>('/iot/elevator-configurations', input));
}

export function updateElevatorConfiguration(id: string, name: string, notes?: string | null): Promise<ElevatorConfiguration> {
  return unwrap(apiClient.put<ApiResponse<ElevatorConfiguration>>(`/iot/elevator-configurations/${id}`, { name, notes }));
}

export function listElevatorConfigurationEvents(id: string): Promise<ElevatorConfigurationEvent[]> {
  return unwrap(apiClient.get<ApiResponse<ElevatorConfigurationEvent[]>>(`/iot/elevator-configurations/${id}/events`));
}

export function deliverElevatorConfiguration(id: string, robotId: string, status?: ElevatorDeliveryStatus): Promise<ElevatorConfigurationDelivery> {
  return unwrap(apiClient.post<ApiResponse<ElevatorConfigurationDelivery>>(`/iot/elevator-configurations/${id}/deliver`, { robotId, status }));
}

export function listElevatorConfigurationDeliveries(): Promise<ElevatorConfigurationDelivery[]> {
  return unwrap(apiClient.get<ApiResponse<ElevatorConfigurationDelivery[]>>('/iot/elevator-configuration-deliveries'));
}

export function listLadderControlBindings(): Promise<LadderControlStoreBinding[]> {
  return unwrap(apiClient.get<ApiResponse<LadderControlStoreBinding[]>>('/iot/ladder-control-bindings'));
}

export interface CreateLadderControlBindingInput {
  organizationId: string;
  siteId: string;
  manufacturer: string;
  buildingId?: string | null;
  clientId?: string | null;
}

export function createLadderControlBinding(input: CreateLadderControlBindingInput): Promise<LadderControlStoreBinding> {
  return unwrap(apiClient.post<ApiResponse<LadderControlStoreBinding>>('/iot/ladder-control-bindings', input));
}

export function listPhoneDevices(): Promise<PhoneDevice[]> {
  return unwrap(apiClient.get<ApiResponse<PhoneDevice[]>>('/iot/phone-devices'));
}

export interface CreatePhoneDeviceInput {
  organizationId: string;
  siteId: string;
  deviceId: string;
  deviceName?: string | null;
  networkingMode?: string | null;
}

export function registerPhoneDevice(input: CreatePhoneDeviceInput): Promise<PhoneDevice> {
  return unwrap(apiClient.post<ApiResponse<PhoneDevice>>('/iot/phone-devices', input));
}
