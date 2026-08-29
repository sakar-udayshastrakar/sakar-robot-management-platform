import { Client, type IMessage } from '@stomp/stompjs';
import { getAccessToken } from '../features/auth/session';

// Thin wrapper around the confirmed backend STOMP protocol
// (WebSocketConfig.java: endpoint /ws, destinations
// /topic/organizations/{orgId}/robots/{robotId}/{status|telemetry}).
//
// Status: IMPLEMENTED / REQUIRES LIVE VALIDATION. The Phase 3 live MQTT
// validation confirmed the STOMP CONNECT+authentication phase works against
// a real backend, but the SUBSCRIBE phase was inconclusive there (see
// docs/requirements/SAKAR_PHASE_3_LIVE_MQTT_VALIDATION_REPORT.md #24). Do
// not treat a missing message on this channel as "robot offline" — always
// pair it with a REST poll fallback.

const WS_URL = import.meta.env.VITE_WS_URL ?? 'http://localhost:8080/ws';
const ENABLED = import.meta.env.VITE_ENABLE_WEBSOCKET === 'true';

export function isRealtimeEnabled(): boolean {
  return ENABLED;
}

let client: Client | null = null;

function getClient(): Client {
  if (client) {
    return client;
  }
  client = new Client({
    brokerURL: WS_URL.replace(/^http/, 'ws'),
    connectHeaders: {
      Authorization: `Bearer ${getAccessToken() ?? ''}`,
    },
    reconnectDelay: 5000,
    heartbeatIncoming: 10_000,
    heartbeatOutgoing: 10_000,
  });
  return client;
}

export interface RealtimeSubscriptionHandle {
  unsubscribe: () => void;
}

// Subscribes to one robot's status + telemetry topics. Returns a handle that
// tears down both the subscription and (if nothing else is using it) the
// underlying connection.
export function subscribeToRobot(
  organizationId: string,
  robotId: string,
  onStatus: (payload: unknown) => void,
  onTelemetry: (payload: unknown) => void,
): RealtimeSubscriptionHandle {
  if (!ENABLED) {
    return { unsubscribe: () => {} };
  }

  const c = getClient();
  const statusDest = `/topic/organizations/${organizationId}/robots/${robotId}/status`;
  const telemetryDest = `/topic/organizations/${organizationId}/robots/${robotId}/telemetry`;

  let statusSub: { unsubscribe: () => void } | null = null;
  let telemetrySub: { unsubscribe: () => void } | null = null;

  c.onConnect = () => {
    statusSub = c.subscribe(statusDest, (message: IMessage) => {
      try {
        onStatus(JSON.parse(message.body));
      } catch {
        onStatus(message.body);
      }
    });
    telemetrySub = c.subscribe(telemetryDest, (message: IMessage) => {
      try {
        onTelemetry(JSON.parse(message.body));
      } catch {
        onTelemetry(message.body);
      }
    });
  };

  if (!c.active) {
    c.activate();
  }

  return {
    unsubscribe: () => {
      statusSub?.unsubscribe();
      telemetrySub?.unsubscribe();
    },
  };
}

export function disconnectRealtime(): void {
  client?.deactivate();
  client = null;
}
