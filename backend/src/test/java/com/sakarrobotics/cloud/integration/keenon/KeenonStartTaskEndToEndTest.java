package com.sakarrobotics.cloud.integration.keenon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.sakarrobotics.cloud.audit.AuditService;
import com.sakarrobotics.cloud.command.CommandResult;
import com.sakarrobotics.cloud.command.CommandResultRepository;
import com.sakarrobotics.cloud.command.CommandStatus;
import com.sakarrobotics.cloud.command.RobotCommand;
import com.sakarrobotics.cloud.command.RobotCommandRepository;
import com.sakarrobotics.cloud.command.RobotCommandService;
import com.sakarrobotics.cloud.iam.RoleName;
import com.sakarrobotics.cloud.mqtt.MqttGatewayService;
import com.sakarrobotics.cloud.mqtt.MqttTopicResolver;
import com.sakarrobotics.cloud.robot.adapter.RobotAdapterRegistry;
import com.sakarrobotics.cloud.robot.registry.AdapterType;
import com.sakarrobotics.cloud.robot.registry.Robot;
import com.sakarrobotics.cloud.robot.registry.RobotCapabilityService;
import com.sakarrobotics.cloud.robot.registry.RobotModel;
import com.sakarrobotics.cloud.robot.registry.RobotModelRepository;
import com.sakarrobotics.cloud.robot.registry.RobotService;
import com.sakarrobotics.cloud.security.UserPrincipal;
import com.sakarrobotics.cloud.security.access.TenantAccessGuard;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * "Controlled START_TASK end-to-end validation" slice. Proves the full
 * chain — {@code RobotCommandService.issue()} → the REAL {@link
 * KeenonRobotAdapter} (wired through a REAL {@link RobotAdapterRegistry},
 * exactly as Spring would) → the real Sakar-area-id-to-vendor-area-id
 * translation → a mocked {@link KeenonApiClient} standing in for the only
 * real network boundary — using nothing but mocks/stubs at that one
 * boundary, so no real Keenon HTTP call is possible from this test by
 * construction.
 */
@ExtendWith(MockitoExtension.class)
class KeenonStartTaskEndToEndTest {

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
    private TenantAccessGuard tenantAccessGuard;
    @Mock
    private AuditService auditService;
    @Mock
    private MqttGatewayService mqttGatewayService;
    @Mock
    private MqttTopicResolver mqttTopicResolver;
    @Mock
    private KeenonApiClient keenonApiClient;
    @Mock
    private KeenonAreaMappingRepository areaMappingRepository;
    @Mock
    private KeenonAreaSyncService keenonAreaSyncService;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private UserPrincipal principal;
    private KeenonRobotAdapter realKeenonAdapter;
    private RobotAdapterRegistry realRegistry;

    private RobotCommandService service() {
        RobotCommandService service = new RobotCommandService(robotCommandRepository, commandResultRepository,
                robotService, robotCapabilityService, robotModelRepository, realRegistry, tenantAccessGuard,
                auditService, mqttGatewayService, mqttTopicResolver, objectMapper);
        try {
            var field = RobotCommandService.class.getDeclaredField("expirySeconds");
            field.setAccessible(true);
            field.set(service, 30L);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
        return service;
    }

    @BeforeEach
    void setUp() {
        principal = new UserPrincipal(UUID.randomUUID(), "admin@example.com", UUID.randomUUID(), "/org",
                RoleName.ORG_ADMIN, Set.of());
        // Real adapter, real registry — only the HTTP-boundary client is a mock.
        realKeenonAdapter = new KeenonRobotAdapter(keenonApiClient, areaMappingRepository, keenonAreaSyncService);
        realRegistry = new RobotAdapterRegistry(List.of(realKeenonAdapter));
        lenient().when(robotCommandRepository.save(any())).thenAnswer(inv -> {
            RobotCommand command = inv.getArgument(0);
            if (command.getId() == null) {
                command.setId(UUID.randomUUID());
            }
            return command;
        });
    }

    private Robot aKeenonRobot(UUID modelId) {
        Robot robot = new Robot();
        robot.setId(UUID.randomUUID());
        robot.setOrganizationId(UUID.randomUUID());
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

    @SuppressWarnings("unchecked")
    @Test
    void startTask_fullChain_areasEndpointIdToKeenonAcceptance_producesCommandDispatched() throws Exception {
        UUID modelId = UUID.randomUUID();
        Robot robot = aKeenonRobot(modelId);
        UUID areaMappingId = UUID.randomUUID();
        KeenonAreaMapping mapping = new KeenonAreaMapping();
        mapping.setId(areaMappingId);
        mapping.setKeenonAreaId("live-area-42"); // this is what GET /robots/{id}/areas would have surfaced as vendorAreaId
        mapping.setKeenonStoreId("C00715655");

        when(robotService.getAccessibleOrThrow(principal, robot.getId())).thenReturn(robot);
        when(robotModelRepository.findById(modelId)).thenReturn(Optional.of(keenonModel(modelId)));
        when(areaMappingRepository.findByIdAndActiveTrue(areaMappingId)).thenReturn(Optional.of(mapping));
        when(keenonApiClient.getBackPoints("94:BA:06:CA:99:F3"))
                .thenReturn(objectMapper.readTree(
                        "{\"code\":610000,\"data\":{\"robotSn\":\"94:BA:06:CA:99:F3\",\"backPointList\":[{\"backPointId\":\"BP-1\"}]}}"));
        JsonNode acceptedResponse = objectMapper.readTree("{\"code\":\"610000\",\"bizType\":\"CleanRobotTemporaryTask\"}");
        when(keenonApiClient.postTemporaryTask(any())).thenReturn(acceptedResponse);

        // Exactly what a caller would supply after reading sakarAreaId off GET /robots/{id}/areas.
        Map<String, Object> params = Map.of("areaIds", List.of(areaMappingId.toString()), "mode", "SWEEP_MOP", "repeatCount", 2);

        RobotCommandService.Issued issued = service().issue(principal, robot.getId(), "START_TASK", params);

        assertThat(issued.command().getStatus()).isEqualTo(CommandStatus.COMMAND_DISPATCHED);
        assertThat(issued.dispatched()).isTrue();
        assertThat(issued.dispatchNote()).contains("Accepted by the Keenon Open Platform");

        ArgumentCaptor<Map<String, Object>> bodyCaptor = ArgumentCaptor.forClass(Map.class);
        verify(keenonApiClient).postTemporaryTask(bodyCaptor.capture());
        Map<String, Object> body = bodyCaptor.getValue();
        assertThat(body.get("robotSn")).isEqualTo("94:BA:06:CA:99:F3");
        assertThat(body.get("areaIdList")).isEqualTo(List.of("live-area-42"));
        assertThat(body.get("cleanModelId")).isEqualTo(101); // SWEEP_MOP
        assertThat(body.get("cleanTimes")).isEqualTo(2);
        assertThat(body.get("backPointId")).isEqualTo("BP-1");

        ArgumentCaptor<CommandResult> resultCaptor = ArgumentCaptor.forClass(CommandResult.class);
        verify(commandResultRepository).save(resultCaptor.capture());
        assertThat(resultCaptor.getValue().getResult()).isEqualTo("COMMAND_DISPATCHED");

        // Structurally guaranteed, not just asserted: RobotCommandService/KeenonRobotAdapter
        // have no dependency on RobotStatusService at all — this chain cannot mark a robot
        // offline no matter what Keenon returns.
    }

    @Test
    void startTask_fullChain_keenonRejection_producesCommandFailed_notASilentSuccess() throws Exception {
        UUID modelId = UUID.randomUUID();
        Robot robot = aKeenonRobot(modelId);
        UUID areaMappingId = UUID.randomUUID();
        KeenonAreaMapping mapping = new KeenonAreaMapping();
        mapping.setId(areaMappingId);
        mapping.setKeenonAreaId("live-area-42");

        when(robotService.getAccessibleOrThrow(principal, robot.getId())).thenReturn(robot);
        when(robotModelRepository.findById(modelId)).thenReturn(Optional.of(keenonModel(modelId)));
        when(areaMappingRepository.findByIdAndActiveTrue(areaMappingId)).thenReturn(Optional.of(mapping));
        when(keenonApiClient.getBackPoints(anyString())).thenReturn(objectMapper.readTree(
                "{\"code\":610000,\"data\":{\"robotSn\":\"94:BA:06:CA:99:F3\",\"backPointList\":[{\"backPointId\":\"BP-1\"}]}}"));
        when(keenonApiClient.postTemporaryTask(any())).thenReturn(objectMapper.readTree("{\"code\":\"500001\",\"message\":\"failure\"}"));

        Map<String, Object> params = Map.of("areaIds", List.of(areaMappingId.toString()), "mode", "SWEEP");
        RobotCommandService.Issued issued = service().issue(principal, robot.getId(), "START_TASK", params);

        assertThat(issued.command().getStatus()).isEqualTo(CommandStatus.COMMAND_FAILED);
        assertThat(issued.dispatched()).isTrue(); // reached Keenon and got a definitive answer
        assertThat(issued.dispatchNote()).contains("Rejected by the Keenon Open Platform");
        verify(commandResultRepository).save(argThat(r -> r.getResult().equals("COMMAND_FAILED")));
    }

    @Test
    void startTask_fullChain_unknownSakarAreaMappingId_isRejectedWithoutFabricatingAVendorCall() {
        UUID modelId = UUID.randomUUID();
        Robot robot = aKeenonRobot(modelId);
        UUID unknownMappingId = UUID.randomUUID();

        when(robotService.getAccessibleOrThrow(principal, robot.getId())).thenReturn(robot);
        when(robotModelRepository.findById(modelId)).thenReturn(Optional.of(keenonModel(modelId)));
        when(areaMappingRepository.findByIdAndActiveTrue(unknownMappingId)).thenReturn(Optional.empty());

        Map<String, Object> params = Map.of("areaIds", List.of(unknownMappingId.toString()), "mode", "SWEEP");
        RobotCommandService.Issued issued = service().issue(principal, robot.getId(), "START_TASK", params);

        assertThat(issued.command().getStatus()).isEqualTo(CommandStatus.COMMAND_FAILED);
        assertThat(issued.dispatched()).isFalse();
        org.mockito.Mockito.verifyNoInteractions(keenonApiClient);
    }

    @Test
    void startTask_fullChain_emptyAreaIds_isRejectedWithoutFabricatingAVendorCall() {
        UUID modelId = UUID.randomUUID();
        Robot robot = aKeenonRobot(modelId);

        when(robotService.getAccessibleOrThrow(principal, robot.getId())).thenReturn(robot);
        when(robotModelRepository.findById(modelId)).thenReturn(Optional.of(keenonModel(modelId)));

        Map<String, Object> params = Map.of("areaIds", List.of(), "mode", "SWEEP");
        RobotCommandService.Issued issued = service().issue(principal, robot.getId(), "START_TASK", params);

        assertThat(issued.command().getStatus()).isEqualTo(CommandStatus.COMMAND_FAILED);
        // "Fix command dispatch semantics" slice: a pre-vendor-call rejection (the adapter's
        // own "no area specified" check, AdapterOperationResult.rejected) never contacted
        // Keenon at all — dispatched must be false, not true.
        assertThat(issued.dispatched()).isFalse();
        assertThat(issued.command().getSentAt()).isNull();
        org.mockito.Mockito.verifyNoInteractions(keenonApiClient);
    }

    private static CommandResult argThat(java.util.function.Predicate<CommandResult> predicate) {
        return org.mockito.ArgumentMatchers.argThat(predicate::test);
    }
}
