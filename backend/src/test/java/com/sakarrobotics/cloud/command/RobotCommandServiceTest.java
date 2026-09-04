package com.sakarrobotics.cloud.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.sakarrobotics.cloud.audit.AuditService;
import com.sakarrobotics.cloud.common.error.ApiException;
import com.sakarrobotics.cloud.common.error.SakarErrorCode;
import com.sakarrobotics.cloud.iam.RoleName;
import com.sakarrobotics.cloud.mqtt.MqttGatewayService;
import com.sakarrobotics.cloud.mqtt.MqttTopicKind;
import com.sakarrobotics.cloud.mqtt.MqttTopicResolver;
import com.sakarrobotics.cloud.robot.adapter.RobotAdapter;
import com.sakarrobotics.cloud.robot.adapter.RobotAdapterRegistry;
import com.sakarrobotics.cloud.robot.adapter.dto.AdapterOperationResult;
import com.sakarrobotics.cloud.robot.adapter.dto.AdapterTaskRequest;
import com.sakarrobotics.cloud.robot.registry.AdapterType;
import com.sakarrobotics.cloud.robot.registry.Robot;
import com.sakarrobotics.cloud.robot.registry.RobotCapabilityService;
import com.sakarrobotics.cloud.robot.registry.RobotCapabilityType;
import com.sakarrobotics.cloud.robot.registry.RobotModel;
import com.sakarrobotics.cloud.robot.registry.RobotModelRepository;
import com.sakarrobotics.cloud.robot.registry.RobotService;
import com.sakarrobotics.cloud.security.UserPrincipal;
import com.sakarrobotics.cloud.security.access.TenantAccessGuard;

import tools.jackson.databind.ObjectMapper;

/**
 * Real Keenon command dispatch (Keenon integration audit, "Real Keenon
 * command dispatch" slice). Pure unit test of {@link RobotCommandService},
 * mirroring {@code KeenonRobotAdapterTest}'s own plain-Mockito style so the
 * KEENON_CLOUD-vs-MQTT branch can be exercised precisely with a mocked
 * {@link RobotAdapter} — no Spring context, no real HTTP call is possible
 * from this test by construction.
 *
 * <p>{@code robotCapabilityService.assertSupported(...)} is a mocked
 * {@code void} method: left unstubbed it is a silent no-op (capability
 * "passes"), so most tests here don't stub it at all — only the one test
 * that needs a rejection stubs it explicitly with {@code doThrow}.
 */
@ExtendWith(MockitoExtension.class)
class RobotCommandServiceTest {

    @Mock
    private RobotCommandRepository robotCommandRepository;
    @Mock
    private CommandResultRepository commandResultRepository;
    @Mock
    private RobotService robotService;
    @Mock
    private RobotCapabilityService robotCapabilityService;
    @Mock
    private RobotModelRepository robotModelRepository;
    @Mock
    private RobotAdapterRegistry robotAdapterRegistry;
    @Mock
    private TenantAccessGuard tenantAccessGuard;
    @Mock
    private AuditService auditService;
    @Mock
    private MqttGatewayService mqttGatewayService;
    @Mock
    private MqttTopicResolver mqttTopicResolver;
    @Mock
    private RobotAdapter keenonAdapter;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private UserPrincipal principal;

    private RobotCommandService service() {
        RobotCommandService service = new RobotCommandService(robotCommandRepository, commandResultRepository,
                robotService, robotCapabilityService, robotModelRepository, robotAdapterRegistry, tenantAccessGuard,
                auditService, mqttGatewayService, mqttTopicResolver, objectMapper);
        setExpirySeconds(service, 30L);
        return service;
    }

    private static void setExpirySeconds(RobotCommandService service, long seconds) {
        try {
            var field = RobotCommandService.class.getDeclaredField("expirySeconds");
            field.setAccessible(true);
            field.set(service, seconds);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    @BeforeEach
    void setUp() {
        principal = new UserPrincipal(UUID.randomUUID(), "admin@example.com", UUID.randomUUID(), "/org",
                RoleName.ORG_ADMIN, Set.of());
        // Every save() call in RobotCommandService chains off the entity it was given —
        // assign an id the first time (mimicking a real JPA insert) and return the same
        // instance back. lenient(): a couple of tests reject the command before ever
        // reaching save() at all.
        lenient().when(robotCommandRepository.save(any())).thenAnswer(inv -> {
            RobotCommand command = inv.getArgument(0);
            if (command.getId() == null) {
                command.setId(UUID.randomUUID());
            }
            return command;
        });
    }

    private Robot aKeenonRobot(UUID organizationId, UUID modelId) {
        Robot robot = new Robot();
        robot.setId(UUID.randomUUID());
        robot.setOrganizationId(organizationId);
        robot.setRobotModelId(modelId);
        robot.setExternalRobotId("94:BA:06:CA:99:F3");
        return robot;
    }

    private RobotModel keenonModel(UUID modelId) {
        RobotModel model = new RobotModel();
        model.setId(modelId);
        model.setAdapterType(AdapterType.KEENON_CLOUD);
        return model;
    }

    private RobotModel nativeModel(UUID modelId) {
        RobotModel model = new RobotModel();
        model.setId(modelId);
        model.setAdapterType(AdapterType.SAKAR_NATIVE);
        return model;
    }

    // ------------------------------------------------------------------
    // KEENON_CLOUD dispatch — the 4 commands this slice wires up.
    // ------------------------------------------------------------------

    @Test
    void keenonCloudRobot_startTask_dispatchesThroughTheKeenonAdapter_andIsAccepted() {
        UUID orgId = UUID.randomUUID();
        UUID modelId = UUID.randomUUID();
        Robot robot = aKeenonRobot(orgId, modelId);
        when(robotService.getAccessibleOrThrow(principal, robot.getId())).thenReturn(robot);
        when(robotModelRepository.findById(modelId)).thenReturn(Optional.of(keenonModel(modelId)));
        when(robotAdapterRegistry.resolve(AdapterType.KEENON_CLOUD)).thenReturn(keenonAdapter);
        when(keenonAdapter.startTask(eq(robot), any(AdapterTaskRequest.class)))
                .thenReturn(AdapterOperationResult.accepted("610000", "Accepted by Keenon Open Platform; not yet physically confirmed"));

        Map<String, Object> params = Map.of("areaIds", List.of(UUID.randomUUID().toString()), "mode", "SWEEP");
        RobotCommandService.Issued issued = service().issue(principal, robot.getId(), "START_TASK", params);

        assertThat(issued.command().getStatus()).isEqualTo(CommandStatus.COMMAND_DISPATCHED);
        assertThat(issued.dispatched()).isTrue();
        assertThat(issued.dispatchNote()).contains("Accepted by the Keenon Open Platform");
        verify(keenonAdapter).startTask(eq(robot), any(AdapterTaskRequest.class));
        verifyNoInteractions(mqttGatewayService);
        verify(commandResultRepository).save(argThatResult(r -> r.getResult().equals("COMMAND_DISPATCHED")));
    }

    @Test
    void keenonCloudRobot_pauseTask_dispatchesThroughTheKeenonAdapter() {
        UUID orgId = UUID.randomUUID();
        UUID modelId = UUID.randomUUID();
        Robot robot = aKeenonRobot(orgId, modelId);
        when(robotService.getAccessibleOrThrow(principal, robot.getId())).thenReturn(robot);
        when(robotModelRepository.findById(modelId)).thenReturn(Optional.of(keenonModel(modelId)));
        when(robotAdapterRegistry.resolve(AdapterType.KEENON_CLOUD)).thenReturn(keenonAdapter);
        when(keenonAdapter.pauseTask(robot)).thenReturn(AdapterOperationResult.accepted("610000", "accepted"));

        RobotCommandService.Issued issued = service().issue(principal, robot.getId(), "PAUSE_TASK", null);

        assertThat(issued.command().getStatus()).isEqualTo(CommandStatus.COMMAND_DISPATCHED);
        verify(keenonAdapter).pauseTask(robot);
        verifyNoInteractions(mqttGatewayService);
    }

    @Test
    void keenonCloudRobot_stopTask_dispatchesThroughTheKeenonAdapter() {
        UUID orgId = UUID.randomUUID();
        UUID modelId = UUID.randomUUID();
        Robot robot = aKeenonRobot(orgId, modelId);
        when(robotService.getAccessibleOrThrow(principal, robot.getId())).thenReturn(robot);
        when(robotModelRepository.findById(modelId)).thenReturn(Optional.of(keenonModel(modelId)));
        when(robotAdapterRegistry.resolve(AdapterType.KEENON_CLOUD)).thenReturn(keenonAdapter);
        when(keenonAdapter.stopTask(robot)).thenReturn(AdapterOperationResult.accepted("610000", "accepted"));

        RobotCommandService.Issued issued = service().issue(principal, robot.getId(), "STOP_TASK", null);

        assertThat(issued.command().getStatus()).isEqualTo(CommandStatus.COMMAND_DISPATCHED);
        verify(keenonAdapter).stopTask(robot);
        verifyNoInteractions(mqttGatewayService);
    }

    @Test
    void keenonCloudRobot_returnToDock_dispatchesThroughTheKeenonAdapter() {
        UUID orgId = UUID.randomUUID();
        UUID modelId = UUID.randomUUID();
        Robot robot = aKeenonRobot(orgId, modelId);
        when(robotService.getAccessibleOrThrow(principal, robot.getId())).thenReturn(robot);
        when(robotModelRepository.findById(modelId)).thenReturn(Optional.of(keenonModel(modelId)));
        when(robotAdapterRegistry.resolve(AdapterType.KEENON_CLOUD)).thenReturn(keenonAdapter);
        when(keenonAdapter.returnToDock(robot)).thenReturn(AdapterOperationResult.accepted("610000", "accepted"));

        RobotCommandService.Issued issued = service().issue(principal, robot.getId(), "RETURN_TO_DOCK", null);

        assertThat(issued.command().getStatus()).isEqualTo(CommandStatus.COMMAND_DISPATCHED);
        verify(keenonAdapter).returnToDock(robot);
        verifyNoInteractions(mqttGatewayService);
    }

    @Test
    void keenonCloudRobot_vendorRejection_isPersistedAsCommandFailed_notPhysicallyConfirmed() {
        UUID orgId = UUID.randomUUID();
        UUID modelId = UUID.randomUUID();
        Robot robot = aKeenonRobot(orgId, modelId);
        when(robotService.getAccessibleOrThrow(principal, robot.getId())).thenReturn(robot);
        when(robotModelRepository.findById(modelId)).thenReturn(Optional.of(keenonModel(modelId)));
        when(robotAdapterRegistry.resolve(AdapterType.KEENON_CLOUD)).thenReturn(keenonAdapter);
        when(keenonAdapter.returnToDock(robot)).thenReturn(AdapterOperationResult.rejectedByVendor("Keenon Open Platform returned code 500001"));

        RobotCommandService.Issued issued = service().issue(principal, robot.getId(), "RETURN_TO_DOCK", null);

        assertThat(issued.command().getStatus()).isEqualTo(CommandStatus.COMMAND_FAILED);
        // rejectedByVendor means the round trip to Keenon completed and got a definitive
        // answer — dispatched stays true, exactly as documented in the service Javadoc.
        assertThat(issued.dispatched()).isTrue();
        assertThat(issued.dispatchNote()).contains("Rejected by the Keenon Open Platform");
    }

    @Test
    void keenonCloudRobot_adapterPreVendorRejection_isNeverReportedAsDispatched() {
        // "Fix command dispatch semantics" slice: a pre-vendor-call rejection (Adapter
        // OperationResult.rejected — e.g. KeenonRobotAdapter's own "no area specified"
        // check) must be reported the same way a failed MQTT publish or an OAuth/network
        // failure is — dispatched=false — never true just because no exception was thrown.
        UUID orgId = UUID.randomUUID();
        UUID modelId = UUID.randomUUID();
        Robot robot = aKeenonRobot(orgId, modelId);
        when(robotService.getAccessibleOrThrow(principal, robot.getId())).thenReturn(robot);
        when(robotModelRepository.findById(modelId)).thenReturn(Optional.of(keenonModel(modelId)));
        when(robotAdapterRegistry.resolve(AdapterType.KEENON_CLOUD)).thenReturn(keenonAdapter);
        when(keenonAdapter.returnToDock(robot)).thenReturn(AdapterOperationResult.rejected("At least one area must be specified"));

        RobotCommandService.Issued issued = service().issue(principal, robot.getId(), "RETURN_TO_DOCK", null);

        assertThat(issued.command().getStatus()).isEqualTo(CommandStatus.COMMAND_FAILED);
        assertThat(issued.dispatched()).isFalse();
        assertThat(issued.dispatchNote()).startsWith("Not dispatched:");
        assertThat(issued.command().getSentAt()).isNull();
    }

    @Test
    void keenonCloudRobot_adapterThrowsApiException_isNotDispatched_andRobotIsNeverMarkedOffline() {
        UUID orgId = UUID.randomUUID();
        UUID modelId = UUID.randomUUID();
        Robot robot = aKeenonRobot(orgId, modelId);
        when(robotService.getAccessibleOrThrow(principal, robot.getId())).thenReturn(robot);
        when(robotModelRepository.findById(modelId)).thenReturn(Optional.of(keenonModel(modelId)));
        when(robotAdapterRegistry.resolve(AdapterType.KEENON_CLOUD)).thenReturn(keenonAdapter);
        when(keenonAdapter.returnToDock(robot))
                .thenThrow(new ApiException(SakarErrorCode.INTEGRATION_UNAVAILABLE, "Keenon Open Platform integration is disabled in this environment"));

        RobotCommandService.Issued issued = service().issue(principal, robot.getId(), "RETURN_TO_DOCK", null);

        assertThat(issued.command().getStatus()).isEqualTo(CommandStatus.COMMAND_FAILED);
        assertThat(issued.dispatched()).isFalse();
        assertThat(issued.dispatchNote()).startsWith("Not dispatched:").contains("Keenon Open Platform integration is disabled");
        assertThat(issued.command().getSentAt()).isNull();
        // This service never touches robot online/offline state at all — that is a
        // separate, deliberately unrelated concern (KeenonStatusSyncService).
        verifyNoInteractions(mqttGatewayService);
    }

    @Test
    void keenonCloudRobot_goToPoint_hasNoAdapterEquivalent_reportedAsUnsupportedRatherThanFabricated() {
        UUID orgId = UUID.randomUUID();
        UUID modelId = UUID.randomUUID();
        Robot robot = aKeenonRobot(orgId, modelId);
        when(robotService.getAccessibleOrThrow(principal, robot.getId())).thenReturn(robot);
        when(robotModelRepository.findById(modelId)).thenReturn(Optional.of(keenonModel(modelId)));
        when(robotAdapterRegistry.resolve(AdapterType.KEENON_CLOUD)).thenReturn(keenonAdapter);

        RobotCommandService.Issued issued = service().issue(principal, robot.getId(), "GO_TO_POINT", Map.of("destinationId", 5));

        assertThat(issued.command().getStatus()).isEqualTo(CommandStatus.COMMAND_FAILED);
        assertThat(issued.dispatched()).isFalse();
        assertThat(issued.dispatchNote()).contains("no Keenon Open Platform equivalent");
        verifyNoInteractions(keenonAdapter);
    }

    // ------------------------------------------------------------------
    // START_TASK parameter mapping / validation.
    // ------------------------------------------------------------------

    @Test
    void keenonCloudRobot_startTask_missingMode_isGracefullyRejectedWithoutCallingTheAdapter() {
        // Missing/malformed START_TASK params are handled the same way this codebase's own
        // adapter already treats a pre-vendor-call rejection (e.g. KeenonRobotAdapter's own
        // "at least one area must be specified" check) — a graceful COMMAND_FAILED result,
        // not an uncaught exception. The command row does get persisted (unlike GO_TO_POINT's
        // destinationId check, which runs before any robot/model lookup even happens) because
        // whether a request is Keenon-shaped is only knowable after the model is loaded.
        UUID orgId = UUID.randomUUID();
        UUID modelId = UUID.randomUUID();
        Robot robot = aKeenonRobot(orgId, modelId);
        when(robotService.getAccessibleOrThrow(principal, robot.getId())).thenReturn(robot);
        when(robotModelRepository.findById(modelId)).thenReturn(Optional.of(keenonModel(modelId)));
        when(robotAdapterRegistry.resolve(AdapterType.KEENON_CLOUD)).thenReturn(keenonAdapter);

        Map<String, Object> params = Map.of("areaIds", List.of(UUID.randomUUID().toString()));

        RobotCommandService.Issued issued = service().issue(principal, robot.getId(), "START_TASK", params);

        assertThat(issued.command().getStatus()).isEqualTo(CommandStatus.COMMAND_FAILED);
        assertThat(issued.dispatched()).isFalse();
        assertThat(issued.dispatchNote()).contains("mode");
        verifyNoInteractions(keenonAdapter);
    }

    @Test
    void keenonCloudRobot_startTask_defaultsRepeatCountWhenAbsent() {
        UUID orgId = UUID.randomUUID();
        UUID modelId = UUID.randomUUID();
        Robot robot = aKeenonRobot(orgId, modelId);
        when(robotService.getAccessibleOrThrow(principal, robot.getId())).thenReturn(robot);
        when(robotModelRepository.findById(modelId)).thenReturn(Optional.of(keenonModel(modelId)));
        when(robotAdapterRegistry.resolve(AdapterType.KEENON_CLOUD)).thenReturn(keenonAdapter);
        when(keenonAdapter.startTask(any(), any())).thenReturn(AdapterOperationResult.accepted("610000", "accepted"));

        Map<String, Object> params = Map.of("areaIds", List.of(UUID.randomUUID().toString()), "mode", "SWEEP");
        service().issue(principal, robot.getId(), "START_TASK", params);

        verify(keenonAdapter).startTask(eq(robot), argThatTaskRequest(r -> r.repeatCount() == 1 && r.mode().equals("SWEEP")));
    }

    // ------------------------------------------------------------------
    // Non-Keenon (MQTT / Sakar-agent) path — must remain unchanged.
    // ------------------------------------------------------------------

    @Test
    void sakarNativeRobot_returnToDock_stillUsesTheExistingMqttPath_notKeenon() {
        UUID orgId = UUID.randomUUID();
        UUID modelId = UUID.randomUUID();
        Robot robot = aKeenonRobot(orgId, modelId); // externalRobotId irrelevant on this path
        when(robotService.getAccessibleOrThrow(principal, robot.getId())).thenReturn(robot);
        when(robotModelRepository.findById(modelId)).thenReturn(Optional.of(nativeModel(modelId)));
        when(mqttTopicResolver.topic(any(), any(), any(), eq(MqttTopicKind.COMMANDS))).thenReturn("sakar/org/site/robot/commands");

        RobotCommandService.Issued issued = service().issue(principal, robot.getId(), "RETURN_TO_DOCK", null);

        verify(mqttGatewayService).publish(eq("sakar/org/site/robot/commands"), any(), eq(1), eq(false));
        assertThat(issued.command().getStatus()).isEqualTo(CommandStatus.SENT);
        verifyNoInteractions(robotAdapterRegistry);
    }

    @Test
    void sakarNativeRobot_mqttPublishFails_behavesExactlyAsBefore() {
        UUID orgId = UUID.randomUUID();
        UUID modelId = UUID.randomUUID();
        Robot robot = aKeenonRobot(orgId, modelId);
        when(robotService.getAccessibleOrThrow(principal, robot.getId())).thenReturn(robot);
        when(robotModelRepository.findById(modelId)).thenReturn(Optional.of(nativeModel(modelId)));
        when(mqttTopicResolver.topic(any(), any(), any(), eq(MqttTopicKind.COMMANDS))).thenReturn("sakar/org/site/robot/commands");
        doThrow(new ApiException(SakarErrorCode.INTEGRATION_UNAVAILABLE, "MQTT is disabled in this environment"))
                .when(mqttGatewayService).publish(anyString(), any(), anyInt(), anyBoolean());

        RobotCommandService.Issued issued = service().issue(principal, robot.getId(), "RETURN_TO_DOCK", null);

        assertThat(issued.dispatched()).isFalse();
        assertThat(issued.dispatchNote()).startsWith("Not dispatched:");
        assertThat(issued.command().getStatus()).isEqualTo(CommandStatus.AUTHORIZED);
        verifyNoInteractions(robotAdapterRegistry);
    }

    // ------------------------------------------------------------------
    // Security / tenant isolation is preserved by construction: the
    // existing robotService.getAccessibleOrThrow(...) call still runs
    // before any Keenon-vs-MQTT branching decision is made.
    // ------------------------------------------------------------------

    @Test
    void crossTenantRobot_isRejectedBeforeAnyDispatchDecision_keenonAdapterNeverConsulted() {
        UUID robotId = UUID.randomUUID();
        when(robotService.getAccessibleOrThrow(principal, robotId))
                .thenThrow(new ApiException(SakarErrorCode.ROBOT_NOT_FOUND, "Robot not found: " + robotId));

        Assertions.assertThrows(ApiException.class,
                () -> service().issue(principal, robotId, "RETURN_TO_DOCK", null));

        verifyNoInteractions(robotModelRepository, robotAdapterRegistry, keenonAdapter, mqttGatewayService);
    }

    @Test
    void unsupportedCommandForThisModel_isRejectedBeforeAnyAdapterOrMqttCall() {
        UUID orgId = UUID.randomUUID();
        UUID modelId = UUID.randomUUID();
        Robot robot = aKeenonRobot(orgId, modelId);
        when(robotService.getAccessibleOrThrow(principal, robot.getId())).thenReturn(robot);
        doThrow(new ApiException(SakarErrorCode.UNSUPPORTED_CAPABILITY, "This robot model does not support RETURN_TO_DOCK"))
                .when(robotCapabilityService).assertSupported(modelId, RobotCapabilityType.RETURN_TO_DOCK);

        Assertions.assertThrows(ApiException.class,
                () -> service().issue(principal, robot.getId(), "RETURN_TO_DOCK", null));

        verifyNoInteractions(robotModelRepository, robotAdapterRegistry, keenonAdapter, mqttGatewayService);
    }

    // ------------------------------------------------------------------
    // Small matcher helpers.
    // ------------------------------------------------------------------

    private static CommandResult argThatResult(Predicate<CommandResult> predicate) {
        return argThat(predicate::test);
    }

    private static AdapterTaskRequest argThatTaskRequest(Predicate<AdapterTaskRequest> predicate) {
        return argThat(predicate::test);
    }
}
