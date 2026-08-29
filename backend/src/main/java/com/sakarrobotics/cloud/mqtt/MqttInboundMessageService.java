package com.sakarrobotics.cloud.mqtt;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.sakarrobotics.cloud.mqtt.dto.EventPayload;
import com.sakarrobotics.cloud.mqtt.dto.HeartbeatPayload;
import com.sakarrobotics.cloud.mqtt.dto.PresencePayload;
import com.sakarrobotics.cloud.mqtt.dto.TelemetryPayload;
import com.sakarrobotics.cloud.mqtt.dto.ErrorPayload;
import com.sakarrobotics.cloud.robot.registry.Robot;
import com.sakarrobotics.cloud.robot.registry.RobotLifecycleStatus;
import com.sakarrobotics.cloud.robot.registry.RobotRepository;
import com.sakarrobotics.cloud.srels.RobotErrorIngestionService;
import com.sakarrobotics.cloud.srels.RobotEventIngestionService;
import com.sakarrobotics.cloud.telemetry.HeartbeatService;
import com.sakarrobotics.cloud.telemetry.RobotStatus;
import com.sakarrobotics.cloud.telemetry.RobotStatusService;
import com.sakarrobotics.cloud.telemetry.TelemetryIngestionService;
import com.sakarrobotics.cloud.telemetry.dto.RobotStatusUpdate;
import com.sakarrobotics.cloud.websocket.RobotRealtimePublisher;

import lombok.RequiredArgsConstructor;
import tools.jackson.databind.ObjectMapper;

/**
 * The single, fully testable entry point for every inbound Agent-&gt;Cloud
 * MQTT message (Phase 3 Part 9 — "MQTT -&gt; Message validation -&gt; Robot
 * identity verification -&gt; Tenant verification -&gt; Telemetry parsing
 * -&gt; Telemetry service -&gt; PostgreSQL"). {@link MqttInboundListener} is
 * the only caller in production and is a thin Paho-callback adapter around
 * this method — every test in this pipeline calls {@link #handle} directly,
 * with no MQTT broker involved, exactly like {@code MqttGatewayService}'s
 * existing "testable without a running broker" design.
 */
@Service
@RequiredArgsConstructor
public class MqttInboundMessageService {

    private final MqttProperties properties;
    private final RobotRepository robotRepository;
    private final MqttDedupGuard dedupGuard;
    private final MqttRateLimiterService rateLimiterService;
    private final MqttLifecycleLogger lifecycleLogger;
    private final HeartbeatService heartbeatService;
    private final TelemetryIngestionService telemetryIngestionService;
    private final RobotEventIngestionService robotEventIngestionService;
    private final RobotErrorIngestionService robotErrorIngestionService;
    private final RobotStatusService robotStatusService;
    private final RobotRealtimePublisher realtimePublisher;
    private final ObjectMapper objectMapper;

    public MqttIngestResult handle(ParsedMqttTopic topic, MqttEnvelope envelope) {
        MqttIngestResult structural = validateStructure(envelope);
        if (!structural.accepted()) {
            lifecycleLogger.warn(safeRobotId(envelope), "MALFORMED_MESSAGE_REJECTED", structural.detail());
            return structural;
        }
        if (!envelope.robotId().equals(topic.robotId())) {
            lifecycleLogger.warn(envelope.robotId(), "MALFORMED_MESSAGE_REJECTED", "topic/envelope robotId mismatch");
            return MqttIngestResult.rejected(MqttRejectionReason.MALFORMED_MESSAGE, "robotId in topic does not match robotId in envelope");
        }

        // Rate-limited before any database lookup, deliberately on the still-unverified claimed
        // robotId — bounds a flood under a nonexistent/spoofed id too, not just a real one
        // (Phase 3 Security Hardening).
        if (rateLimiterService.isRateLimited(envelope.robotId())) {
            lifecycleLogger.warn(envelope.robotId(), "RATE_LIMITED_REJECTED", "exceeded rate limit for this robot");
            return MqttIngestResult.rejected(MqttRejectionReason.RATE_LIMITED, "Too many messages from this robot");
        }

        Optional<Robot> maybeRobot = robotRepository.findById(envelope.robotId());
        if (maybeRobot.isEmpty()) {
            lifecycleLogger.warn(envelope.robotId(), "UNKNOWN_ROBOT_REJECTED", "no such robot is registered");
            return MqttIngestResult.rejected(MqttRejectionReason.UNKNOWN_ROBOT, "Robot is not registered");
        }
        Robot robot = maybeRobot.get();

        if (robot.getStatus() == RobotLifecycleStatus.DEACTIVATED) {
            lifecycleLogger.warn(robot.getId(), "UNAUTHORIZED_ROBOT_REJECTED", "robot is deactivated");
            return MqttIngestResult.rejected(MqttRejectionReason.UNAUTHORIZED_ROBOT, "Robot is deactivated");
        }

        // Defense in depth: never trust the topic's org/site claim by itself — the broker-side ACL
        // (production requirement, see SAKAR_MQTT_ARCHITECTURE.md "Security") is what should actually
        // prevent a robot from publishing under another tenant's topic; this is the software-side check
        // in case that ACL is ever missing or misconfigured (SAKAR_SECURITY_REQUIREMENTS.md §10).
        if (!robot.getOrganizationId().equals(topic.organizationId()) || !Objects.equals(robot.getSiteId(), topic.siteId())) {
            lifecycleLogger.warn(robot.getId(), "UNAUTHORIZED_ROBOT_REJECTED", "topic organization/site does not match the registered robot");
            return MqttIngestResult.rejected(MqttRejectionReason.TENANT_MISMATCH, "Topic organization/site does not match the registered robot");
        }

        boolean firstDelivery;
        try {
            firstDelivery = dedupGuard.registerIfFirstDelivery(robot.getId(), envelope.messageId(), envelope.messageType(), envelope.sequence());
        } catch (RuntimeException dedupCheckFailed) {
            // Fail closed: if we can't positively confirm this is a first delivery, treat it as a
            // duplicate rather than risk a double-write (e.g. a genuine concurrent-redelivery race
            // losing to the unique constraint — see MqttDedupGuard's Javadoc).
            lifecycleLogger.warn(robot.getId(), "DUPLICATE_MESSAGE_IGNORED", "dedup check failed, treated as duplicate: " + dedupCheckFailed.getClass().getSimpleName());
            return MqttIngestResult.ok();
        }
        if (!firstDelivery) {
            lifecycleLogger.info(robot.getId(), "DUPLICATE_MESSAGE_IGNORED", envelope.messageId());
            return MqttIngestResult.ok(); // idempotent no-op — still ack'd as accepted, per §11
        }

        try {
            route(robot, envelope);
        } catch (Exception ex) {
            lifecycleLogger.error(robot.getId(), "PROCESSING_FAILED", envelope.messageType() + ": " + ex.getClass().getSimpleName());
            return MqttIngestResult.rejected(MqttRejectionReason.PROCESSING_FAILED, "Internal processing error");
        }

        lifecycleLogger.info(robot.getId(), acceptedEventCode(envelope.messageType()), envelope.messageId());
        return MqttIngestResult.ok();
    }

    private void route(Robot robot, MqttEnvelope envelope) {
        switch (envelope.messageType()) {
            case HEARTBEAT -> {
                HeartbeatPayload payload = convert(envelope, HeartbeatPayload.class);
                RobotStatus status = heartbeatService.record(robot.getId(), payload, envelope.timestamp());
                publishStatusSnapshot(robot, status);
            }
            case TELEMETRY -> {
                TelemetryPayload payload = convert(envelope, TelemetryPayload.class);
                telemetryIngestionService.ingest(robot.getId(), payload.readings());
                publishStatusSnapshot(robot, robotStatusService.current(robot.getId()));
                realtimePublisher.publishTelemetry(robot.getOrganizationId(), robot.getId(), payload);
            }
            case EVENT -> {
                EventPayload payload = convert(envelope, EventPayload.class);
                robotEventIngestionService.record(robot.getId(), payload);
            }
            case ERROR -> {
                ErrorPayload payload = convert(envelope, ErrorPayload.class);
                robotErrorIngestionService.record(robot.getId(), payload);
            }
            case PRESENCE -> {
                PresencePayload payload = convert(envelope, PresencePayload.class);
                RobotStatus status = PresencePayload.OFFLINE.equals(payload.status())
                        ? robotStatusService.markOffline(robot.getId(), envelope.timestamp())
                        : robotStatusService.markOnline(robot.getId(), envelope.timestamp());
                publishStatusSnapshot(robot, status);
            }
            default -> throw new IllegalArgumentException("Not an inbound (agent-authored) message type: " + envelope.messageType());
        }
    }

    private void publishStatusSnapshot(Robot robot, RobotStatus status) {
        realtimePublisher.publishStatus(robot.getOrganizationId(), robot.getId(), RobotStatusUpdate.from(status));
    }

    private <T> T convert(MqttEnvelope envelope, Class<T> type) {
        return objectMapper.convertValue(envelope.payload(), type);
    }

    private MqttIngestResult validateStructure(MqttEnvelope envelope) {
        if (envelope.robotId() == null || envelope.messageId() == null || envelope.messageId().isBlank()
                || envelope.messageType() == null || envelope.timestamp() == null || envelope.payload() == null) {
            return MqttIngestResult.rejected(MqttRejectionReason.MALFORMED_MESSAGE, "One or more required envelope fields are missing");
        }
        if (!properties.getSchemaVersion().equals(envelope.schemaVersion())) {
            return MqttIngestResult.rejected(MqttRejectionReason.UNSUPPORTED_SCHEMA_VERSION,
                    "Unsupported schemaVersion: " + envelope.schemaVersion());
        }
        Duration skew = Duration.between(envelope.timestamp(), Instant.now()).abs();
        if (skew.getSeconds() > properties.getMaxTimestampSkewSeconds()) {
            return MqttIngestResult.rejected(MqttRejectionReason.STALE_TIMESTAMP,
                    "Envelope timestamp is " + skew.getSeconds() + "s away from server time");
        }
        if (List.of(MqttMessageType.ACK, MqttMessageType.COMMAND).contains(envelope.messageType())) {
            return MqttIngestResult.rejected(MqttRejectionReason.MALFORMED_MESSAGE,
                    envelope.messageType() + " is cloud-authored, not a valid inbound message type");
        }
        // Sanity only, not strict monotonic-ordering enforcement: MQTT QoS 1 ("at least once") does
        // not guarantee delivery order, so two genuinely valid messages can legitimately arrive with
        // non-increasing sequence numbers — sequence here is observability/diagnostic, per the
        // envelope's own Javadoc. A negative value, however, can never be legitimate.
        if (envelope.sequence() < 0) {
            return MqttIngestResult.rejected(MqttRejectionReason.INVALID_SEQUENCE, "sequence must not be negative: " + envelope.sequence());
        }
        return MqttIngestResult.ok();
    }

    private static String acceptedEventCode(MqttMessageType type) {
        return switch (type) {
            case HEARTBEAT -> "HEARTBEAT_ACCEPTED";
            case TELEMETRY -> "TELEMETRY_ACCEPTED";
            case EVENT -> "EVENT_ACCEPTED";
            case ERROR -> "ERROR_ACCEPTED";
            case PRESENCE -> "PRESENCE_ACCEPTED";
            default -> "ACCEPTED";
        };
    }

    private static java.util.UUID safeRobotId(MqttEnvelope envelope) {
        return envelope == null ? null : envelope.robotId();
    }
}
