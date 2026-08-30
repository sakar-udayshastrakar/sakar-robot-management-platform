import axios from 'axios';
import { getAccessToken } from '../features/auth/session';

// GET /actuator/health is real and public (SecurityConfig PUBLIC_PATHS),
// but `management.endpoint.health.show-details: when-authorized` means an
// authenticated request may additionally receive a `components` breakdown
// (db/redis/etc, whatever Spring Boot's own auto-configured indicators
// produce) — there is no custom MQTT/WebSocket HealthIndicator in the
// backend, so those two are never present here even when authenticated.
// This calls the real backend root, not `/api/v1`.
const API_BASE = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080/api/v1';
const BACKEND_ORIGIN = API_BASE.replace(/\/api\/v1\/?$/, '');

export interface HealthComponent {
  status: string;
}

export interface HealthResponse {
  status: string;
  components?: Record<string, HealthComponent>;
}

export async function getBackendHealth(): Promise<HealthResponse> {
  const token = getAccessToken();
  const response = await axios.get<HealthResponse>(`${BACKEND_ORIGIN}/actuator/health`, {
    timeout: 5000,
    headers: token ? { Authorization: `Bearer ${token}` } : {},
  });
  return response.data;
}
