import { apiClient, unwrap } from './client';
import type { ApiResponse } from '../types/api';
import type { OpenPlatformApplication, OpenPlatformRegistration, OpenPlatformRegistrationStatus } from '../types/domain';

// Open Platform → Customer registration + Application management (real
// CRUD, GET/POST/PUT /api/v1/open-platform/*). A registration's status is
// never a fabricated "pass" — see OpenPlatformRegistrationStatus's own
// comment (types/domain.ts). An application's secret key is returned in
// plaintext ONLY from createApplication's own response, exactly once —
// every later read sees only `secretKeyMasked`.

export function getRegistration(organizationId: string): Promise<OpenPlatformRegistration | null> {
  return unwrap(apiClient.get<ApiResponse<OpenPlatformRegistration | null>>('/open-platform/registration', { params: { organizationId } }));
}

export interface SubmitRegistrationInput {
  organizationId: string;
  companyName: string;
  area?: string | null;
  companyAddress?: string | null;
  systemMatcher?: string | null;
  contactInformation?: string | null;
  dockingRequirements?: string | null;
}

export function submitRegistration(input: SubmitRegistrationInput): Promise<OpenPlatformRegistration> {
  return unwrap(apiClient.post<ApiResponse<OpenPlatformRegistration>>('/open-platform/registration', input));
}

export function reviewRegistration(id: string, status: OpenPlatformRegistrationStatus): Promise<OpenPlatformRegistration> {
  return unwrap(apiClient.post<ApiResponse<OpenPlatformRegistration>>(`/open-platform/registrations/${id}/review`, { status }));
}

// ROLE_MANAGE only — every registration within the caller's organization scope, for review.
export function listRegistrations(): Promise<OpenPlatformRegistration[]> {
  return unwrap(apiClient.get<ApiResponse<OpenPlatformRegistration[]>>('/open-platform/registrations'));
}

export function listApplications(): Promise<OpenPlatformApplication[]> {
  return unwrap(apiClient.get<ApiResponse<OpenPlatformApplication[]>>('/open-platform/applications'));
}

export interface CreateApplicationResult {
  application: OpenPlatformApplication;
  secretKey: string;
}

export function createApplication(organizationId: string, applicationName: string, businessType?: string | null): Promise<CreateApplicationResult> {
  return unwrap(apiClient.post<ApiResponse<CreateApplicationResult>>('/open-platform/applications', { organizationId, applicationName, businessType }));
}

export function updateApplication(id: string, applicationName: string, businessType?: string | null): Promise<OpenPlatformApplication> {
  return unwrap(apiClient.put<ApiResponse<OpenPlatformApplication>>(`/open-platform/applications/${id}`, { applicationName, businessType }));
}

export function getApplication(id: string): Promise<OpenPlatformApplication> {
  return unwrap(apiClient.get<ApiResponse<OpenPlatformApplication>>(`/open-platform/applications/${id}`));
}
