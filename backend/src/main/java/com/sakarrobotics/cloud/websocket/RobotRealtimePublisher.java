package com.sakarrobotics.cloud.websocket;

import java.util.UUID;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

/**
 * Publishes real-time robot updates onto the existing, already-authorized
 * WebSocket transport (Phase 3 Part 15) — {@link WebSocketConfig}'s
 * Javadoc previously noted "no controller in this codebase publishes to
 * these destinations yet." Destinations are org-scoped
 * ({@code /topic/organizations/{orgId}/...}), matching exactly what
 * {@link WebSocketAuthChannelInterceptor#authorizeDestination} already
 * authorizes at SUBSCRIBE time — this class does not need to (and must
 * not) duplicate that authorization logic; it only needs to publish to the
 * correct, already-enforced destination.
 *
 * <p>Not a Web UI (explicitly out of scope for Phase 3) — this is backend
 * real-time capability only, for a future dashboard to consume.
 */
@Component
@RequiredArgsConstructor
public class RobotRealtimePublisher {

    private final SimpMessagingTemplate messagingTemplate;

    public void publishStatus(UUID organizationId, UUID robotId, Object statusPayload) {
        messagingTemplate.convertAndSend(statusDestination(organizationId, robotId), statusPayload);
    }

    public void publishTelemetry(UUID organizationId, UUID robotId, Object telemetryPayload) {
        messagingTemplate.convertAndSend(telemetryDestination(organizationId, robotId), telemetryPayload);
    }

    private static String statusDestination(UUID organizationId, UUID robotId) {
        return "/topic/organizations/" + organizationId + "/robots/" + robotId + "/status";
    }

    private static String telemetryDestination(UUID organizationId, UUID robotId) {
        return "/topic/organizations/" + organizationId + "/robots/" + robotId + "/telemetry";
    }
}
