package com.sakarrobotics.cloud.mqtt;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.sakarrobotics.cloud.IntegrationTestSupport;
import com.sakarrobotics.cloud.mqtt.dto.ErrorPayload;
import com.sakarrobotics.cloud.mqtt.dto.EventPayload;
import com.sakarrobotics.cloud.mqtt.dto.HeartbeatPayload;
import com.sakarrobotics.cloud.mqtt.dto.PresencePayload;
import com.sakarrobotics.cloud.mqtt.dto.TelemetryPayload;
import com.sakarrobotics.cloud.org.Organization;
import com.sakarrobotics.cloud.org.OrganizationType;
import com.sakarrobotics.cloud.robot.adapter.dto.TelemetryReading;
import com.sakarrobotics.cloud.robot.registry.AdapterType;
import com.sakarrobotics.cloud.robot.registry.IntegrationPath;
import com.sakarrobotics.cloud.robot.registry.Robot;
import com.sakarrobotics.cloud.robot.registry.RobotLifecycleStatus;
import com.sakarrobotics.cloud.robot.registry.RobotManufacturer;
import com.sakarrobotics.cloud.robot.registry.RobotManufacturerRepository;
import com.sakarrobotics.cloud.robot.registry.RobotModel;
import com.sakarrobotics.cloud.robot.registry.RobotModelRepository;
import com.sakarrobotics.cloud.robot.registry.RobotRepository;
import com.sakarrobotics.cloud.srels.RobotErrorRepository;
import com.sakarrobotics.cloud.srels.RobotEventRepository;
import com.sakarrobotics.cloud.telemetry.RobotStatusRepository;
import com.sakarrobotics.cloud.telemetry.RobotTelemetryRepository;

/**
 * End-to-end coverage of the MQTT ingestion pipeline (Phase 3 Part 16),
 * driven entirely through {@link MqttInboundMessageService#handle} — no
 * MQTT broker involved anywhere in this test, exactly per the class's own
 * "testable without a running broker" design.
 */
class MqttInboundMessageServiceTest extends IntegrationTestSupport {

    @Autowired
    private MqttInboundMessageService inboundMessageService;
    @Autowired
    private MqttTopicResolver topicResolver;
    @Autowired
    private RobotManufacturerRepository manufacturerRepository;
    @Autowired
    private RobotModelRepository modelRepository;
    @Autowired
    private RobotRepository robotRepository;
    @Autowired
    private RobotTelemetryRepository robotTelemetryRepository;
    @Autowired
    private RobotStatusRepository robotStatusRepository;
    @Autowired
    private RobotEventRepository robotEventRepository;
    @Autowired
    private RobotErrorRepository robotErrorRepository;
    @Autowired
    private MqttInboundMessageRepository mqttInboundMessageRepository;

    @Test
    void validTelemetry_isAcceptedAndPersistedAndUpdatesRobotStatus() {
        Robot robot = aRegisteredRobot();
        ParsedMqttTopic topic = topicFor(robot, MqttTopicKind.TELEMETRY);
        MqttEnvelope envelope = telemetryEnvelope(robot.getId(), UUID.randomUUID().toString(),
                new TelemetryReading("battery_percent", null, 87.0, Instant.now()));

        MqttIngestResult result = inboundMessageService.handle(topic, envelope);

        assertThat(result.accepted()).isTrue();
        assertThat(robotTelemetryRepository.findAll()).anyMatch(t -> t.getRobotId().equals(robot.getId()) && t.getMetric().equals("battery_percent"));
        assertThat(robotStatusRepository.findById(robot.getId())).hasValueSatisfying(status -> {
            assertThat(status.getBatteryPercent()).isEqualTo(87);
            assertThat(status.isOnline()).isTrue();
        });
    }

    @Test
    void redeliveredMessageId_isIdempotent_notDoubleIngested() {
        Robot robot = aRegisteredRobot();
        ParsedMqttTopic topic = topicFor(robot, MqttTopicKind.TELEMETRY);
        String messageId = UUID.randomUUID().toString();
        MqttEnvelope envelope = telemetryEnvelope(robot.getId(), messageId,
                new TelemetryReading("battery_percent", null, 55.0, Instant.now()));

        MqttIngestResult first = inboundMessageService.handle(topic, envelope);
        long countAfterFirst = robotTelemetryRepository.findAll().stream().filter(t -> t.getRobotId().equals(robot.getId())).count();
        MqttIngestResult second = inboundMessageService.handle(topic, envelope); // exact same envelope, redelivered
        long countAfterSecond = robotTelemetryRepository.findAll().stream().filter(t -> t.getRobotId().equals(robot.getId())).count();

        assertThat(first.accepted()).isTrue();
        assertThat(second.accepted()).isTrue(); // idempotent no-op is still ack'd as accepted
        assertThat(countAfterSecond).isEqualTo(countAfterFirst); // not doubled
        assertThat(mqttInboundMessageRepository.findByRobotIdAndMessageId(robot.getId(), messageId)).isPresent();
    }

    @Test
    void unknownRobot_isRejected() {
        UUID organizationId = createOrganization("Org " + UUID.randomUUID(), OrganizationType.DIRECT_CLIENT, null).getId();
        UUID unknownRobotId = UUID.randomUUID();
        ParsedMqttTopic topic = new ParsedMqttTopic(organizationId, null, unknownRobotId, MqttTopicKind.HEARTBEAT);
        MqttEnvelope envelope = heartbeatEnvelope(unknownRobotId, UUID.randomUUID().toString());

        MqttIngestResult result = inboundMessageService.handle(topic, envelope);

        assertThat(result.accepted()).isFalse();
        assertThat(result.reason()).isEqualTo(MqttRejectionReason.UNKNOWN_ROBOT);
    }

    @Test
    void deactivatedRobot_isRejectedAsUnauthorized() {
        Robot robot = aRegisteredRobot();
        robot.setStatus(RobotLifecycleStatus.DEACTIVATED);
        robotRepository.save(robot);
        ParsedMqttTopic topic = topicFor(robot, MqttTopicKind.HEARTBEAT);
        MqttEnvelope envelope = heartbeatEnvelope(robot.getId(), UUID.randomUUID().toString());

        MqttIngestResult result = inboundMessageService.handle(topic, envelope);

        assertThat(result.accepted()).isFalse();
        assertThat(result.reason()).isEqualTo(MqttRejectionReason.UNAUTHORIZED_ROBOT);
    }

    @Test
    void topicOrganizationNotMatchingTheRegisteredRobot_isRejectedAsTenantMismatch() {
        Robot robot = aRegisteredRobot();
        UUID unrelatedOrganizationId = createOrganization("Unrelated " + UUID.randomUUID(), OrganizationType.DIRECT_CLIENT, null).getId();
        ParsedMqttTopic spoofedTopic = new ParsedMqttTopic(unrelatedOrganizationId, null, robot.getId(), MqttTopicKind.HEARTBEAT);
        MqttEnvelope envelope = heartbeatEnvelope(robot.getId(), UUID.randomUUID().toString());

        MqttIngestResult result = inboundMessageService.handle(spoofedTopic, envelope);

        assertThat(result.accepted()).isFalse();
        assertThat(result.reason()).isEqualTo(MqttRejectionReason.TENANT_MISMATCH);
    }

    @Test
    void envelopeRobotIdNotMatchingTopicRobotId_isRejectedAsMalformed() {
        Robot robot = aRegisteredRobot();
        ParsedMqttTopic topic = topicFor(robot, MqttTopicKind.HEARTBEAT);
        MqttEnvelope envelope = heartbeatEnvelope(UUID.randomUUID(), UUID.randomUUID().toString()); // different robotId

        MqttIngestResult result = inboundMessageService.handle(topic, envelope);

        assertThat(result.accepted()).isFalse();
        assertThat(result.reason()).isEqualTo(MqttRejectionReason.MALFORMED_MESSAGE);
    }

    @Test
    void staleTimestamp_isRejected() {
        Robot robot = aRegisteredRobot();
        ParsedMqttTopic topic = topicFor(robot, MqttTopicKind.HEARTBEAT);
        MqttEnvelope envelope = new MqttEnvelope("1.0", UUID.randomUUID().toString(), robot.getId(), "agent-1",
                Instant.now().minus(1, ChronoUnit.DAYS), MqttMessageType.HEARTBEAT, 1,
                objectMapper.valueToTree(new HeartbeatPayload("0.1.0", 10, "CONNECTED")));

        MqttIngestResult result = inboundMessageService.handle(topic, envelope);

        assertThat(result.accepted()).isFalse();
        assertThat(result.reason()).isEqualTo(MqttRejectionReason.STALE_TIMESTAMP);
    }

    @Test
    void unsupportedSchemaVersion_isRejected() {
        Robot robot = aRegisteredRobot();
        ParsedMqttTopic topic = topicFor(robot, MqttTopicKind.HEARTBEAT);
        MqttEnvelope envelope = new MqttEnvelope("99.0", UUID.randomUUID().toString(), robot.getId(), "agent-1",
                Instant.now(), MqttMessageType.HEARTBEAT, 1,
                objectMapper.valueToTree(new HeartbeatPayload("0.1.0", 10, "CONNECTED")));

        MqttIngestResult result = inboundMessageService.handle(topic, envelope);

        assertThat(result.accepted()).isFalse();
        assertThat(result.reason()).isEqualTo(MqttRejectionReason.UNSUPPORTED_SCHEMA_VERSION);
    }

    @Test
    void heartbeat_marksRobotOnlineWithoutFabricatingTelemetry() {
        Robot robot = aRegisteredRobot();
        ParsedMqttTopic topic = topicFor(robot, MqttTopicKind.HEARTBEAT);
        MqttEnvelope envelope = heartbeatEnvelope(robot.getId(), UUID.randomUUID().toString());

        MqttIngestResult result = inboundMessageService.handle(topic, envelope);

        assertThat(result.accepted()).isTrue();
        assertThat(robotStatusRepository.findById(robot.getId())).hasValueSatisfying(status -> {
            assertThat(status.isOnline()).isTrue();
            assertThat(status.getBatteryPercent()).isNull(); // heartbeat must never fabricate robot telemetry
        });
        assertThat(robotTelemetryRepository.findAll()).noneMatch(t -> t.getRobotId().equals(robot.getId()));
    }

    @Test
    void presenceOffline_marksRobotOffline() {
        Robot robot = aRegisteredRobot();
        ParsedMqttTopic topic = topicFor(robot, MqttTopicKind.PRESENCE);
        MqttEnvelope envelope = new MqttEnvelope("1.0", UUID.randomUUID().toString(), robot.getId(), "agent-1",
                Instant.now(), MqttMessageType.PRESENCE, 1,
                objectMapper.valueToTree(new PresencePayload(PresencePayload.OFFLINE)));

        MqttIngestResult result = inboundMessageService.handle(topic, envelope);

        assertThat(result.accepted()).isTrue();
        assertThat(robotStatusRepository.findById(robot.getId())).hasValueSatisfying(status -> assertThat(status.isOnline()).isFalse());
    }

    @Test
    void event_isIngestedIntoRobotEvents() {
        Robot robot = aRegisteredRobot();
        ParsedMqttTopic topic = topicFor(robot, MqttTopicKind.EVENTS);
        MqttEnvelope envelope = new MqttEnvelope("1.0", UUID.randomUUID().toString(), robot.getId(), "agent-1",
                Instant.now(), MqttMessageType.EVENT, 1,
                objectMapper.valueToTree(new EventPayload("NAVIGATION_STARTED", "INFO", "{\"targetId\":3}", Instant.now())));

        MqttIngestResult result = inboundMessageService.handle(topic, envelope);

        assertThat(result.accepted()).isTrue();
        assertThat(robotEventRepository.findAll()).anyMatch(e -> e.getRobotId().equals(robot.getId()) && e.getEventType().equals("NAVIGATION_STARTED"));
    }

    @Test
    void negativeSequence_isRejectedAsInvalidSequence() {
        Robot robot = aRegisteredRobot();
        ParsedMqttTopic topic = topicFor(robot, MqttTopicKind.HEARTBEAT);
        MqttEnvelope envelope = new MqttEnvelope("1.0", UUID.randomUUID().toString(), robot.getId(), "agent-1",
                Instant.now(), MqttMessageType.HEARTBEAT, -1,
                objectMapper.valueToTree(new HeartbeatPayload("0.1.0", 10, "CONNECTED")));

        MqttIngestResult result = inboundMessageService.handle(topic, envelope);

        assertThat(result.accepted()).isFalse();
        assertThat(result.reason()).isEqualTo(MqttRejectionReason.INVALID_SEQUENCE);
    }

    @Test
    void exceedingThePerRobotRateLimit_isRejected() {
        // application-test.yml overrides sakar.mqtt.rate-limit-max-messages to 3 for this exact test.
        Robot robot = aRegisteredRobot();
        ParsedMqttTopic topic = topicFor(robot, MqttTopicKind.HEARTBEAT);

        MqttIngestResult first = inboundMessageService.handle(topic, heartbeatEnvelope(robot.getId(), UUID.randomUUID().toString()));
        MqttIngestResult second = inboundMessageService.handle(topic, heartbeatEnvelope(robot.getId(), UUID.randomUUID().toString()));
        MqttIngestResult third = inboundMessageService.handle(topic, heartbeatEnvelope(robot.getId(), UUID.randomUUID().toString()));
        MqttIngestResult fourth = inboundMessageService.handle(topic, heartbeatEnvelope(robot.getId(), UUID.randomUUID().toString()));

        assertThat(first.accepted()).isTrue();
        assertThat(second.accepted()).isTrue();
        assertThat(third.accepted()).isTrue();
        assertThat(fourth.accepted()).isFalse();
        assertThat(fourth.reason()).isEqualTo(MqttRejectionReason.RATE_LIMITED);
    }

    @Test
    void error_isIngestedIntoRobotErrors() {
        Robot robot = aRegisteredRobot();
        ParsedMqttTopic topic = topicFor(robot, MqttTopicKind.ERRORS);
        MqttEnvelope envelope = new MqttEnvelope("1.0", UUID.randomUUID().toString(), robot.getId(), "agent-1",
                Instant.now(), MqttMessageType.ERROR, 1,
                objectMapper.valueToTree(new ErrorPayload("E-100", "CRITICAL", "sdk", "motor fault", "MotorComponent.getStatus", Instant.now())));

        MqttIngestResult result = inboundMessageService.handle(topic, envelope);

        assertThat(result.accepted()).isTrue();
        assertThat(robotErrorRepository.findAll()).anyMatch(e -> e.getRobotId().equals(robot.getId()) && e.getErrorCode().equals("E-100"));
    }

    // ---------------------------------------------------------------

    private Robot aRegisteredRobot() {
        Organization org = createOrganization("Org " + UUID.randomUUID(), OrganizationType.DIRECT_CLIENT, null);
        RobotManufacturer manufacturer = manufacturerRepository.save(new RobotManufacturer("TestVendor-" + UUID.randomUUID()));
        RobotModel model = new RobotModel();
        model.setManufacturerId(manufacturer.getId());
        model.setName("Test Model");
        model.setAdapterType(AdapterType.SAKAR_NATIVE);
        model.setIntegrationPath(IntegrationPath.SAKAR_OWNED_LOCAL);
        model = modelRepository.save(model);

        Robot robot = new Robot();
        robot.setOrganizationId(org.getId());
        robot.setRobotModelId(model.getId());
        robot.setName("Robot " + UUID.randomUUID());
        robot.setSerialNumber("SN-" + UUID.randomUUID());
        robot.setStatus(RobotLifecycleStatus.ACTIVE);
        return robotRepository.save(robot);
    }

    private ParsedMqttTopic topicFor(Robot robot, MqttTopicKind kind) {
        return new ParsedMqttTopic(robot.getOrganizationId(), robot.getSiteId(), robot.getId(), kind);
    }

    private MqttEnvelope heartbeatEnvelope(UUID robotId, String messageId) {
        return new MqttEnvelope("1.0", messageId, robotId, "agent-1", Instant.now(), MqttMessageType.HEARTBEAT, 1,
                objectMapper.valueToTree(new HeartbeatPayload("0.1.0", 42, "CONNECTED")));
    }

    private MqttEnvelope telemetryEnvelope(UUID robotId, String messageId, TelemetryReading... readings) {
        return new MqttEnvelope("1.0", messageId, robotId, "agent-1", Instant.now(), MqttMessageType.TELEMETRY, 1,
                objectMapper.valueToTree(new TelemetryPayload(List.of(readings))));
    }
}
