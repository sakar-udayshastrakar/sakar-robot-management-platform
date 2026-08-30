package com.sakarrobotics.cloud.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import com.sakarrobotics.cloud.IntegrationTestSupport;
import com.sakarrobotics.cloud.audit.AuditLogRepository;
import com.sakarrobotics.cloud.iam.PermissionCode;
import com.sakarrobotics.cloud.iam.Role;
import com.sakarrobotics.cloud.iam.RoleName;
import com.sakarrobotics.cloud.mqtt.MqttEnvelope;
import com.sakarrobotics.cloud.mqtt.MqttInboundMessageService;
import com.sakarrobotics.cloud.mqtt.MqttMessageType;
import com.sakarrobotics.cloud.mqtt.MqttTopicKind;
import com.sakarrobotics.cloud.mqtt.MqttIngestResult;
import com.sakarrobotics.cloud.mqtt.ParsedMqttTopic;
import com.sakarrobotics.cloud.mqtt.dto.CommandResultDetail;
import com.sakarrobotics.cloud.mqtt.dto.EventPayload;
import com.sakarrobotics.cloud.org.Organization;
import com.sakarrobotics.cloud.org.OrganizationType;
import com.sakarrobotics.cloud.robot.registry.AdapterType;
import com.sakarrobotics.cloud.robot.registry.IntegrationPath;
import com.sakarrobotics.cloud.robot.registry.Robot;
import com.sakarrobotics.cloud.robot.registry.RobotCapability;
import com.sakarrobotics.cloud.robot.registry.RobotCapabilityRepository;
import com.sakarrobotics.cloud.robot.registry.RobotCapabilityType;
import com.sakarrobotics.cloud.robot.registry.RobotLifecycleStatus;
import com.sakarrobotics.cloud.robot.registry.RobotManufacturer;
import com.sakarrobotics.cloud.robot.registry.RobotManufacturerRepository;
import com.sakarrobotics.cloud.robot.registry.RobotModel;
import com.sakarrobotics.cloud.robot.registry.RobotModelRepository;
import com.sakarrobotics.cloud.robot.registry.RobotRepository;

/**
 * Roadmap Phase 6/7 "Robot Agent Command Loop" — verifies an agent-reported
 * {@code COMMAND_RESULT} event (arriving exactly like any other EVENT, per
 * {@link CommandResultDetail}'s Javadoc) correctly advances the originating
 * {@link RobotCommand}, appends {@link CommandResult} history, and cannot
 * be spoofed across robots/tenants — driven entirely through {@link
 * MqttInboundMessageService#handle}, no MQTT broker involved, matching
 * {@code MqttInboundMessageServiceTest}'s own convention.
 */
class CommandResultIngestionServiceTest extends IntegrationTestSupport {

    @Autowired
    private MqttInboundMessageService inboundMessageService;
    @Autowired
    private RobotManufacturerRepository manufacturerRepository;
    @Autowired
    private RobotModelRepository modelRepository;
    @Autowired
    private RobotCapabilityRepository capabilityRepository;
    @Autowired
    private RobotRepository robotRepository;
    @Autowired
    private RobotCommandRepository robotCommandRepository;
    @Autowired
    private CommandResultRepository commandResultRepository;
    @Autowired
    private AuditLogRepository auditLogRepository;

    @Test
    void returnToDock_dispatchedStatus_mapsToCommandDispatched_isTerminal_andIsAudited() throws Exception {
        Robot robot = aRobotSupporting(RobotCapabilityType.RETURN_TO_DOCK);
        UUID commandId = issueCommand(robot, "RETURN_TO_DOCK");

        accept(robot, commandId, "RECEIVED", null, null);
        accept(robot, commandId, "EXECUTING", null, null);
        accept(robot, commandId, "DISPATCHED",
                "Peanut SDK BatteryComponent.autoCharge() accepted the return-to-dock request", 850L);

        assertThat(robotCommandRepository.findById(commandId)).hasValueSatisfying(c -> {
            // NOT COMMAND_SUCCESS — a local-interface acceptance is not confirmed physical completion.
            assertThat(c.getStatus()).isEqualTo(CommandStatus.COMMAND_DISPATCHED);
            assertThat(c.getStatus().isTerminal()).isTrue();
            assertThat(c.getCompletedAt()).isNotNull();
        });

        // Frozen exactly like every other terminal status: a late EXECUTING must not resurrect it.
        accept(robot, commandId, "EXECUTING", null, null);
        assertThat(robotCommandRepository.findById(commandId)).hasValueSatisfying(
                c -> assertThat(c.getStatus()).isEqualTo(CommandStatus.COMMAND_DISPATCHED));

        assertThat(commandResultRepository.findAll()).anyMatch(r -> r.getCommandId().equals(commandId)
                && r.getResult().equals(CommandStatus.COMMAND_DISPATCHED.name()));
        assertThat(auditLogRepository.findAll()).anyMatch(a -> robot.getId().equals(a.getRobotId())
                && "COMMAND_RESULT_COMMAND_DISPATCHED".equals(a.getAction()));
    }

    @Test
    void fullLifecycle_receivedThenExecutingThenCompleted_advancesStatusAndAppendsHistory() throws Exception {
        Robot robot = aRobotSupporting(RobotCapabilityType.START_TASK);
        UUID commandId = issueCommand(robot, "START_TASK");

        accept(robot, commandId, "RECEIVED", null, null);
        assertThat(robotCommandRepository.findById(commandId)).hasValueSatisfying(
                c -> assertThat(c.getStatus()).isEqualTo(CommandStatus.COMMAND_RECEIVED));

        accept(robot, commandId, "EXECUTING", null, null);
        assertThat(robotCommandRepository.findById(commandId)).hasValueSatisfying(
                c -> assertThat(c.getStatus()).isEqualTo(CommandStatus.RUNNING));

        accept(robot, commandId, "COMPLETED", "simulated", 2000L);
        assertThat(robotCommandRepository.findById(commandId)).hasValueSatisfying(c -> {
            assertThat(c.getStatus()).isEqualTo(CommandStatus.COMMAND_SUCCESS);
            assertThat(c.getCompletedAt()).isNotNull();
        });
        assertThat(commandResultRepository.findAll()).filteredOn(r -> r.getCommandId().equals(commandId)).hasSize(3);
    }

    @Test
    void terminalStatus_isFrozen_lateOutOfOrderReportNeverResurrectsIt() throws Exception {
        Robot robot = aRobotSupporting(RobotCapabilityType.START_TASK);
        UUID commandId = issueCommand(robot, "START_TASK");

        accept(robot, commandId, "FAILED", "boom", 500L);
        assertThat(robotCommandRepository.findById(commandId)).hasValueSatisfying(
                c -> assertThat(c.getStatus()).isEqualTo(CommandStatus.COMMAND_FAILED));

        // A delayed "EXECUTING" arrives after the terminal FAILED — must not resurrect it.
        accept(robot, commandId, "EXECUTING", null, null);
        assertThat(robotCommandRepository.findById(commandId)).hasValueSatisfying(
                c -> assertThat(c.getStatus()).isEqualTo(CommandStatus.COMMAND_FAILED));
        // The out-of-order report is still recorded in the append-only history for audit purposes.
        assertThat(commandResultRepository.findAll()).filteredOn(r -> r.getCommandId().equals(commandId)).hasSize(2);
    }

    @Test
    void unknownCommandId_isIgnoredWithoutError() throws Exception {
        Robot robot = aRobotSupporting(RobotCapabilityType.START_TASK);
        UUID nonexistentCommandId = UUID.randomUUID();

        MqttIngestResult result = accept(robot, nonexistentCommandId, "COMPLETED", null, null);

        assertThat(result.accepted()).isTrue(); // the EVENT itself is still valid/accepted
        assertThat(commandResultRepository.findAll()).noneMatch(r -> r.getCommandId().equals(nonexistentCommandId));
    }

    @Test
    void commandBelongingToAnotherRobot_isRejectedNotAppliedToTheWrongCommand() throws Exception {
        Robot robotA = aRobotSupporting(RobotCapabilityType.START_TASK);
        Robot robotB = aRobotSupporting(RobotCapabilityType.START_TASK);
        UUID commandForRobotA = issueCommand(robotA, "START_TASK");

        // robotB reports a result for a command that actually belongs to robotA.
        accept(robotB, commandForRobotA, "COMPLETED", null, null);

        assertThat(robotCommandRepository.findById(commandForRobotA)).hasValueSatisfying(
                c -> assertThat(c.getStatus()).isEqualTo(CommandStatus.AUTHORIZED)); // unchanged
        assertThat(commandResultRepository.findAll()).noneMatch(r -> r.getCommandId().equals(commandForRobotA));
    }

    @Test
    void unrecognizedStatusString_isIgnoredWithoutError() throws Exception {
        Robot robot = aRobotSupporting(RobotCapabilityType.START_TASK);
        UUID commandId = issueCommand(robot, "START_TASK");

        MqttIngestResult result = accept(robot, commandId, "SOMETHING_MADE_UP", null, null);

        assertThat(result.accepted()).isTrue();
        assertThat(robotCommandRepository.findById(commandId)).hasValueSatisfying(
                c -> assertThat(c.getStatus()).isEqualTo(CommandStatus.AUTHORIZED)); // unchanged
    }

    // ---------------------------------------------------------------

    private UUID issueCommand(Robot robot, String commandType) throws Exception {
        Role orgAdmin = ensureRole(RoleName.ORG_ADMIN, PermissionCode.ROBOT_CONTROL, PermissionCode.ROBOT_CONFIGURE);
        String email = "admin-" + UUID.randomUUID() + "@example.com";
        createUser(email, "Password1!", orgAdmin, robot.getOrganizationId());
        String token = login(email, "Password1!");

        String response = mockMvc.perform(post("/api/v1/robots/" + robot.getId() + "/commands")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"commandType\":\"" + commandType + "\"}"))
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(response).get("data").get("id").asText());
    }

    private MqttIngestResult accept(Robot robot, UUID commandId, String status, String detail, Long durationMs) throws Exception {
        CommandResultDetail resultDetail = new CommandResultDetail(commandId.toString(), status, detail, durationMs);
        EventPayload eventPayload = new EventPayload("COMMAND_RESULT", "INFO",
                objectMapper.writeValueAsString(resultDetail), Instant.now());
        ParsedMqttTopic topic = new ParsedMqttTopic(robot.getOrganizationId(), robot.getSiteId(), robot.getId(), MqttTopicKind.EVENTS);
        MqttEnvelope envelope = new MqttEnvelope("1.0", UUID.randomUUID().toString(), robot.getId(), "agent-1",
                Instant.now(), MqttMessageType.EVENT, 1, objectMapper.valueToTree(eventPayload));
        return inboundMessageService.handle(topic, envelope);
    }

    private Robot aRobotSupporting(RobotCapabilityType capability) {
        RobotManufacturer manufacturer = manufacturerRepository.save(new RobotManufacturer("Vendor-" + UUID.randomUUID()));
        RobotModel model = new RobotModel();
        model.setManufacturerId(manufacturer.getId());
        model.setName("Model");
        model.setAdapterType(AdapterType.SAKAR_NATIVE);
        model.setIntegrationPath(IntegrationPath.SAKAR_OWNED_LOCAL);
        model = modelRepository.save(model);
        capabilityRepository.save(new RobotCapability(model.getId(), capability, true));

        Organization org = createOrganization("Org " + UUID.randomUUID(), OrganizationType.DIRECT_CLIENT, null);
        Robot robot = new Robot();
        robot.setOrganizationId(org.getId());
        robot.setRobotModelId(model.getId());
        robot.setName("Robot " + UUID.randomUUID());
        robot.setSerialNumber("SN-" + UUID.randomUUID());
        robot.setStatus(RobotLifecycleStatus.ACTIVE);
        return robotRepository.save(robot);
    }
}
