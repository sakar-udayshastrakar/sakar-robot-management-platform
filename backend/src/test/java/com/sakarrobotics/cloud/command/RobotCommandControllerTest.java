package com.sakarrobotics.cloud.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import com.sakarrobotics.cloud.IntegrationTestSupport;
import com.sakarrobotics.cloud.iam.PermissionCode;
import com.sakarrobotics.cloud.iam.Role;
import com.sakarrobotics.cloud.iam.RoleName;
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

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Non-lock-only enforcement + capability gating + honest dispatch reporting
 * (Roadmap Phase 6 "Remote Commands (non-lock)"). {@code sakar.mqtt.enabled}
 * is {@code false} in the test profile, so every dispatch attempt here
 * genuinely exercises the disabled-MQTT path — {@code dispatched=false} is
 * real behavior, not a stub.
 */
class RobotCommandControllerTest extends IntegrationTestSupport {

    @Autowired
    private RobotManufacturerRepository manufacturerRepository;
    @Autowired
    private RobotModelRepository modelRepository;
    @Autowired
    private RobotCapabilityRepository capabilityRepository;
    @Autowired
    private RobotRepository robotRepository;
    @Autowired
    private CommandResultRepository commandResultRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void lockCommandType_isRejected_noEndpointAnywhereAcceptsLockOrUnlock() throws Exception {
        Role orgAdmin = ensureRole(RoleName.ORG_ADMIN, PermissionCode.ROBOT_CONTROL, PermissionCode.ROBOT_CONFIGURE);
        Organization org = createOrganization("Org " + UUID.randomUUID(), OrganizationType.DIRECT_CLIENT, null);
        String email = "admin-" + UUID.randomUUID() + "@example.com";
        createUser(email, "Password1!", orgAdmin, org.getId());
        String token = login(email, "Password1!");

        RobotModel model = modelWithCapabilities(RobotCapabilityType.LOCK);
        Robot robot = registerRobot(org.getId(), model.getId());

        mockMvc.perform(post("/api/v1/robots/" + robot.getId() + "/commands")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"commandType\":\"LOCK\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
    }

    @Test
    void nonLockCommand_onAModelWithoutTheCapability_isRejected() throws Exception {
        Role orgAdmin = ensureRole(RoleName.ORG_ADMIN, PermissionCode.ROBOT_CONTROL, PermissionCode.ROBOT_CONFIGURE);
        Organization org = createOrganization("Org " + UUID.randomUUID(), OrganizationType.DIRECT_CLIENT, null);
        String email = "admin-" + UUID.randomUUID() + "@example.com";
        createUser(email, "Password1!", orgAdmin, org.getId());
        String token = login(email, "Password1!");

        RobotModel model = modelWithCapabilities(); // no RETURN_TO_DOCK capability registered
        Robot robot = registerRobot(org.getId(), model.getId());

        mockMvc.perform(post("/api/v1/robots/" + robot.getId() + "/commands")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"commandType\":\"RETURN_TO_DOCK\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("UNSUPPORTED_CAPABILITY"));
    }

    @Test
    void issuingASupportedNonLockCommand_isPersistedAndHonestlyReportedAsNotDispatched_mqttDisabledInTests() throws Exception {
        Role orgAdmin = ensureRole(RoleName.ORG_ADMIN, PermissionCode.ROBOT_CONTROL, PermissionCode.ROBOT_CONFIGURE);
        Organization org = createOrganization("Org " + UUID.randomUUID(), OrganizationType.DIRECT_CLIENT, null);
        String email = "admin-" + UUID.randomUUID() + "@example.com";
        createUser(email, "Password1!", orgAdmin, org.getId());
        String token = login(email, "Password1!");

        RobotModel model = modelWithCapabilities(RobotCapabilityType.RETURN_TO_DOCK);
        Robot robot = registerRobot(org.getId(), model.getId());

        mockMvc.perform(post("/api/v1/robots/" + robot.getId() + "/commands")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"commandType\":\"RETURN_TO_DOCK\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.commandType").value("RETURN_TO_DOCK"))
                .andExpect(jsonPath("$.data.status").value("AUTHORIZED"))
                .andExpect(jsonPath("$.data.dispatched").value(false))
                .andExpect(jsonPath("$.data.dispatchNote", containsString("Not dispatched")));
    }

    // ---------------------------------------------------------------
    // GO_TO_POINT (Roadmap Phase 8, C40_S_GO_TO_POINT_SDK_INVESTIGATION.md).
    // NavigationComponent.setTarget(IDataCallback, int) takes a
    // pre-registered destination id, not raw coordinates — these tests
    // never invent or assume any specific id is valid on a real robot's
    // map, they only check this backend's own payload validation.
    // ---------------------------------------------------------------

    @Test
    void goToPointCommand_missingDestinationId_isRejected() throws Exception {
        Role orgAdmin = ensureRole(RoleName.ORG_ADMIN, PermissionCode.ROBOT_CONTROL, PermissionCode.ROBOT_CONFIGURE);
        Organization org = createOrganization("Org " + UUID.randomUUID(), OrganizationType.DIRECT_CLIENT, null);
        String email = "admin-" + UUID.randomUUID() + "@example.com";
        createUser(email, "Password1!", orgAdmin, org.getId());
        String token = login(email, "Password1!");

        RobotModel model = modelWithCapabilities(RobotCapabilityType.GO_TO_POINT);
        Robot robot = registerRobot(org.getId(), model.getId());

        mockMvc.perform(post("/api/v1/robots/" + robot.getId() + "/commands")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"commandType\":\"GO_TO_POINT\",\"params\":{}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.error.message", containsString("destinationId")));
    }

    @Test
    void goToPointCommand_negativeDestinationId_isRejected() throws Exception {
        Role orgAdmin = ensureRole(RoleName.ORG_ADMIN, PermissionCode.ROBOT_CONTROL, PermissionCode.ROBOT_CONFIGURE);
        Organization org = createOrganization("Org " + UUID.randomUUID(), OrganizationType.DIRECT_CLIENT, null);
        String email = "admin-" + UUID.randomUUID() + "@example.com";
        createUser(email, "Password1!", orgAdmin, org.getId());
        String token = login(email, "Password1!");

        RobotModel model = modelWithCapabilities(RobotCapabilityType.GO_TO_POINT);
        Robot robot = registerRobot(org.getId(), model.getId());

        mockMvc.perform(post("/api/v1/robots/" + robot.getId() + "/commands")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"commandType\":\"GO_TO_POINT\",\"params\":{\"destinationId\":-1}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
    }

    @Test
    void goToPointCommand_withoutTheCapability_isRejected() throws Exception {
        Role orgAdmin = ensureRole(RoleName.ORG_ADMIN, PermissionCode.ROBOT_CONTROL, PermissionCode.ROBOT_CONFIGURE);
        Organization org = createOrganization("Org " + UUID.randomUUID(), OrganizationType.DIRECT_CLIENT, null);
        String email = "admin-" + UUID.randomUUID() + "@example.com";
        createUser(email, "Password1!", orgAdmin, org.getId());
        String token = login(email, "Password1!");

        RobotModel model = modelWithCapabilities(); // no GO_TO_POINT capability registered
        Robot robot = registerRobot(org.getId(), model.getId());

        mockMvc.perform(post("/api/v1/robots/" + robot.getId() + "/commands")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"commandType\":\"GO_TO_POINT\",\"params\":{\"destinationId\":5}}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("UNSUPPORTED_CAPABILITY"));
    }

    @Test
    void goToPointCommand_withAValidDestinationId_isPersistedAndHonestlyReportedAsNotDispatched_mqttDisabledInTests() throws Exception {
        Role orgAdmin = ensureRole(RoleName.ORG_ADMIN, PermissionCode.ROBOT_CONTROL, PermissionCode.ROBOT_CONFIGURE);
        Organization org = createOrganization("Org " + UUID.randomUUID(), OrganizationType.DIRECT_CLIENT, null);
        String email = "admin-" + UUID.randomUUID() + "@example.com";
        createUser(email, "Password1!", orgAdmin, org.getId());
        String token = login(email, "Password1!");

        RobotModel model = modelWithCapabilities(RobotCapabilityType.GO_TO_POINT);
        Robot robot = registerRobot(org.getId(), model.getId());

        // destinationId=5 here is an arbitrary payload value used only to prove this backend's
        // own validation/persistence/dispatch-note logic - it is NOT a claim that "5" is a real,
        // confirmed destination on any physical C40's map. See C40_S_GO_TO_POINT_SDK_INVESTIGATION.md.
        mockMvc.perform(post("/api/v1/robots/" + robot.getId() + "/commands")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"commandType\":\"GO_TO_POINT\",\"params\":{\"destinationId\":5}}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.commandType").value("GO_TO_POINT"))
                .andExpect(jsonPath("$.data.status").value("AUTHORIZED"))
                .andExpect(jsonPath("$.data.dispatched").value(false))
                .andExpect(jsonPath("$.data.dispatchNote", containsString("Not dispatched")));
    }

    // ---------------------------------------------------------------
    // Real Keenon command dispatch (Keenon integration audit, "Real Keenon
    // command dispatch" slice) — full Spring context, the REAL (unmocked)
    // KeenonRobotAdapter bean. sakar.integration.keenon.enabled=false in
    // the test profile, so KeenonOAuthTokenService fails closed before any
    // HTTP request is ever built — these tests make zero real network
    // calls by construction, while still proving the KEENON_CLOUD routing
    // decision and tenant isolation end-to-end through the real beans.
    // ---------------------------------------------------------------

    @Test
    void keenonCloudRobot_command_isHonestlyReportedAsCommandFailed_whenKeenonIntegrationIsDisabled() throws Exception {
        Role orgAdmin = ensureRole(RoleName.ORG_ADMIN, PermissionCode.ROBOT_CONTROL, PermissionCode.ROBOT_CONFIGURE);
        Organization org = createOrganization("Org " + UUID.randomUUID(), OrganizationType.DIRECT_CLIENT, null);
        String email = "admin-" + UUID.randomUUID() + "@example.com";
        createUser(email, "Password1!", orgAdmin, org.getId());
        String token = login(email, "Password1!");

        RobotModel model = keenonModelWithCapabilities(RobotCapabilityType.RETURN_TO_DOCK);
        Robot robot = registerRobot(org.getId(), model.getId());
        robot.setExternalRobotId("94:BA:06:CA:99:F3");
        robot = robotRepository.save(robot);

        mockMvc.perform(post("/api/v1/robots/" + robot.getId() + "/commands")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"commandType\":\"RETURN_TO_DOCK\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.commandType").value("RETURN_TO_DOCK"))
                .andExpect(jsonPath("$.data.status").value("COMMAND_FAILED"))
                .andExpect(jsonPath("$.data.dispatched").value(false))
                .andExpect(jsonPath("$.data.dispatchNote", containsString("Not dispatched")))
                // KeenonApiClient.post() catches any Exception (including the OAuth
                // service's own INTEGRATION_UNAVAILABLE for a disabled integration) and
                // rewraps it as a generic VENDOR_API_ERROR — pre-existing behavior, not
                // changed by this slice. The outcome is still correctly "not dispatched".
                .andExpect(jsonPath("$.data.dispatchNote", containsString("Keenon Open Platform request failed")));
    }

    @Test
    void crossOrganizationRobot_keenonCommand_isRejectedBeforeAnyKeenonDispatchAttempt() throws Exception {
        Role orgAdmin = ensureRole(RoleName.ORG_ADMIN, PermissionCode.ROBOT_CONTROL, PermissionCode.ROBOT_CONFIGURE);
        Organization orgA = createOrganization("Org A " + UUID.randomUUID(), OrganizationType.DIRECT_CLIENT, null);
        Organization orgB = createOrganization("Org B " + UUID.randomUUID(), OrganizationType.DIRECT_CLIENT, null);
        String emailA = "admin-a-" + UUID.randomUUID() + "@example.com";
        createUser(emailA, "Password1!", orgAdmin, orgA.getId());
        String token = login(emailA, "Password1!");

        RobotModel model = keenonModelWithCapabilities(RobotCapabilityType.RETURN_TO_DOCK);
        Robot robotInOrgB = registerRobot(orgB.getId(), model.getId());

        // Same 404-not-403 anti-enumeration behavior as every other robot endpoint —
        // proves the tenant guard runs and rejects before any Keenon-vs-MQTT dispatch
        // decision is even reached for this cross-org robot.
        mockMvc.perform(post("/api/v1/robots/" + robotInOrgB.getId() + "/commands")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"commandType\":\"RETURN_TO_DOCK\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("ROBOT_NOT_FOUND"));
    }

    // ---------------------------------------------------------------
    // GET /{commandId}/results — command lifecycle/result history
    // (CommandResultResponse integration). Every CommandResult row here is
    // written directly to commandResultRepository, exactly as the real
    // writers (CommandResultIngestionService/CommandExpiryService/
    // RobotCommandService#recordResult) would, since MQTT is disabled in
    // tests and no agent exists to report one through the real pipeline.
    // ---------------------------------------------------------------

    @Test
    void commandResults_returnsHistoryNewestFirst_withCorrectFieldMapping() throws Exception {
        Role orgAdmin = ensureRole(RoleName.ORG_ADMIN, PermissionCode.ROBOT_CONTROL, PermissionCode.ROBOT_VIEW);
        Organization org = createOrganization("Org " + UUID.randomUUID(), OrganizationType.DIRECT_CLIENT, null);
        String email = "admin-" + UUID.randomUUID() + "@example.com";
        createUser(email, "Password1!", orgAdmin, org.getId());
        String token = login(email, "Password1!");

        RobotModel model = modelWithCapabilities(RobotCapabilityType.RETURN_TO_DOCK);
        Robot robot = registerRobot(org.getId(), model.getId());
        UUID commandId = issueCommand(token, robot.getId(), "RETURN_TO_DOCK");

        CommandResult first = saveResult(commandId, "COMMAND_RECEIVED", "received by agent", null);
        Thread.sleep(5);
        CommandResult second = saveResult(commandId, "COMMAND_SUCCESS", "docked", 4200L);

        mockMvc.perform(get("/api/v1/robots/" + robot.getId() + "/commands/" + commandId + "/results")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(2))
                .andExpect(jsonPath("$.data.content[0].commandId").value(commandId.toString()))
                .andExpect(jsonPath("$.data.content[0].result").value("COMMAND_SUCCESS"))
                .andExpect(jsonPath("$.data.content[0].detail").value("docked"))
                .andExpect(jsonPath("$.data.content[0].durationMs").value(4200))
                .andExpect(jsonPath("$.data.content[1].result").value("COMMAND_RECEIVED"))
                .andExpect(jsonPath("$.data.content[1].detail").value("received by agent"))
                .andExpect(jsonPath("$.data.content[1].durationMs").doesNotExist())
                .andExpect(jsonPath("$.data.totalElements").value(2));

        assertThat(first.getId()).isNotEqualTo(second.getId());
    }

    @Test
    void commandResults_pagination_respectsPageAndPageSizeParams() throws Exception {
        Role orgAdmin = ensureRole(RoleName.ORG_ADMIN, PermissionCode.ROBOT_CONTROL, PermissionCode.ROBOT_VIEW);
        Organization org = createOrganization("Org " + UUID.randomUUID(), OrganizationType.DIRECT_CLIENT, null);
        String email = "admin-" + UUID.randomUUID() + "@example.com";
        createUser(email, "Password1!", orgAdmin, org.getId());
        String token = login(email, "Password1!");

        RobotModel model = modelWithCapabilities(RobotCapabilityType.RETURN_TO_DOCK);
        Robot robot = registerRobot(org.getId(), model.getId());
        UUID commandId = issueCommand(token, robot.getId(), "RETURN_TO_DOCK");

        saveResult(commandId, "COMMAND_RECEIVED", "r1", null);
        Thread.sleep(5);
        saveResult(commandId, "RUNNING", "r2", null);
        Thread.sleep(5);
        saveResult(commandId, "COMMAND_SUCCESS", "r3", 1000L);

        mockMvc.perform(get("/api/v1/robots/" + robot.getId() + "/commands/" + commandId
                        + "/results?page=0&pageSize=1")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].detail").value("r3"))
                .andExpect(jsonPath("$.data.totalElements").value(3))
                .andExpect(jsonPath("$.data.totalPages").value(3));

        mockMvc.perform(get("/api/v1/robots/" + robot.getId() + "/commands/" + commandId
                        + "/results?page=1&pageSize=1")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].detail").value("r2"));
    }

    @Test
    void commandResults_noResultsYet_returnsNormalEmptyPage() throws Exception {
        Role orgAdmin = ensureRole(RoleName.ORG_ADMIN, PermissionCode.ROBOT_CONTROL, PermissionCode.ROBOT_VIEW);
        Organization org = createOrganization("Org " + UUID.randomUUID(), OrganizationType.DIRECT_CLIENT, null);
        String email = "admin-" + UUID.randomUUID() + "@example.com";
        createUser(email, "Password1!", orgAdmin, org.getId());
        String token = login(email, "Password1!");

        // A plain non-Keenon dispatch with MQTT disabled in tests never writes a
        // CommandResult row at all (see RobotCommandService#issue) — a genuine
        // "no history yet" case, not a fabricated empty list.
        RobotModel model = modelWithCapabilities(RobotCapabilityType.RETURN_TO_DOCK);
        Robot robot = registerRobot(org.getId(), model.getId());
        UUID commandId = issueCommand(token, robot.getId(), "RETURN_TO_DOCK");

        mockMvc.perform(get("/api/v1/robots/" + robot.getId() + "/commands/" + commandId + "/results")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(0))
                .andExpect(jsonPath("$.data.totalElements").value(0));
    }

    @Test
    void commandResults_unknownCommandId_returnsCommandNotFound() throws Exception {
        Role orgAdmin = ensureRole(RoleName.ORG_ADMIN, PermissionCode.ROBOT_CONTROL, PermissionCode.ROBOT_VIEW);
        Organization org = createOrganization("Org " + UUID.randomUUID(), OrganizationType.DIRECT_CLIENT, null);
        String email = "admin-" + UUID.randomUUID() + "@example.com";
        createUser(email, "Password1!", orgAdmin, org.getId());
        String token = login(email, "Password1!");

        RobotModel model = modelWithCapabilities(RobotCapabilityType.RETURN_TO_DOCK);
        Robot robot = registerRobot(org.getId(), model.getId());

        mockMvc.perform(get("/api/v1/robots/" + robot.getId() + "/commands/" + UUID.randomUUID() + "/results")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("COMMAND_NOT_FOUND"));
    }

    @Test
    void commandResults_commandBelongingToAnotherRobot_isRejectedAsCommandNotFound() throws Exception {
        Role orgAdmin = ensureRole(RoleName.ORG_ADMIN, PermissionCode.ROBOT_CONTROL, PermissionCode.ROBOT_VIEW);
        Organization org = createOrganization("Org " + UUID.randomUUID(), OrganizationType.DIRECT_CLIENT, null);
        String email = "admin-" + UUID.randomUUID() + "@example.com";
        createUser(email, "Password1!", orgAdmin, org.getId());
        String token = login(email, "Password1!");

        RobotModel model = modelWithCapabilities(RobotCapabilityType.RETURN_TO_DOCK);
        Robot robotA = registerRobot(org.getId(), model.getId());
        Robot robotB = registerRobot(org.getId(), model.getId());
        UUID commandForRobotA = issueCommand(token, robotA.getId(), "RETURN_TO_DOCK");
        saveResult(commandForRobotA, "COMMAND_RECEIVED", "belongs to robot A", null);

        // Same organization, same caller, but commandForRobotA does not belong to robotB —
        // must be indistinguishable from an unknown commandId, never a different error.
        mockMvc.perform(get("/api/v1/robots/" + robotB.getId() + "/commands/" + commandForRobotA + "/results")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("COMMAND_NOT_FOUND"));
    }

    @Test
    void commandResults_crossOrganizationRobot_isRejectedAsRobotNotFound() throws Exception {
        Role orgAdmin = ensureRole(RoleName.ORG_ADMIN, PermissionCode.ROBOT_CONTROL, PermissionCode.ROBOT_VIEW);
        Organization orgA = createOrganization("Org A " + UUID.randomUUID(), OrganizationType.DIRECT_CLIENT, null);
        Organization orgB = createOrganization("Org B " + UUID.randomUUID(), OrganizationType.DIRECT_CLIENT, null);
        String emailA = "admin-a-" + UUID.randomUUID() + "@example.com";
        createUser(emailA, "Password1!", orgAdmin, orgA.getId());
        String tokenA = login(emailA, "Password1!");
        String emailB = "admin-b-" + UUID.randomUUID() + "@example.com";
        createUser(emailB, "Password1!", orgAdmin, orgB.getId());
        String tokenB = login(emailB, "Password1!");

        RobotModel model = modelWithCapabilities(RobotCapabilityType.RETURN_TO_DOCK);
        Robot robotInOrgB = registerRobot(orgB.getId(), model.getId());
        UUID commandId = issueCommand(tokenB, robotInOrgB.getId(), "RETURN_TO_DOCK");

        // Caller from Org A must never see Org B's robot or its command history —
        // same 404-not-403 anti-enumeration posture as every other robot endpoint.
        mockMvc.perform(get("/api/v1/robots/" + robotInOrgB.getId() + "/commands/" + commandId + "/results")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("ROBOT_NOT_FOUND"));
    }

    @Test
    void commandResults_callerWithoutRobotViewPermission_isForbidden() throws Exception {
        // A role granted ROBOT_CONTROL (enough to issue the command that sets up this test)
        // but deliberately never ROBOT_VIEW — a fresh RoleName unused by any other test in
        // this class, so it cannot accidentally inherit ROBOT_VIEW from another test's grant.
        Role technician = ensureRole(RoleName.TECHNICIAN, PermissionCode.ROBOT_CONTROL);
        Organization org = createOrganization("Org " + UUID.randomUUID(), OrganizationType.DIRECT_CLIENT, null);
        String email = "tech-" + UUID.randomUUID() + "@example.com";
        createUser(email, "Password1!", technician, org.getId());
        String token = login(email, "Password1!");

        RobotModel model = modelWithCapabilities(RobotCapabilityType.RETURN_TO_DOCK);
        Robot robot = registerRobot(org.getId(), model.getId());
        UUID commandId = issueCommand(token, robot.getId(), "RETURN_TO_DOCK");

        mockMvc.perform(get("/api/v1/robots/" + robot.getId() + "/commands/" + commandId + "/results")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    private UUID issueCommand(String token, UUID robotId, String commandType) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/robots/" + robotId + "/commands")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"commandType\":\"" + commandType + "\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return UUID.fromString(body.get("data").get("id").asText());
    }

    private CommandResult saveResult(UUID commandId, String result, String detail, Long durationMs) {
        CommandResult commandResult = new CommandResult();
        commandResult.setCommandId(commandId);
        commandResult.setResult(result);
        commandResult.setDetail(detail);
        commandResult.setDurationMs(durationMs);
        return commandResultRepository.save(commandResult);
    }

    private RobotModel modelWithCapabilities(RobotCapabilityType... capabilities) {
        RobotManufacturer manufacturer = manufacturerRepository.save(new RobotManufacturer("Vendor-" + UUID.randomUUID()));
        RobotModel model = new RobotModel();
        model.setManufacturerId(manufacturer.getId());
        model.setName("Model");
        model.setAdapterType(AdapterType.SAKAR_NATIVE);
        model.setIntegrationPath(IntegrationPath.SAKAR_OWNED_LOCAL);
        model = modelRepository.save(model);
        for (RobotCapabilityType capability : capabilities) {
            capabilityRepository.save(new RobotCapability(model.getId(), capability, true));
        }
        return model;
    }

    private RobotModel keenonModelWithCapabilities(RobotCapabilityType... capabilities) {
        RobotManufacturer manufacturer = manufacturerRepository.save(new RobotManufacturer("Keenon-" + UUID.randomUUID()));
        RobotModel model = new RobotModel();
        model.setManufacturerId(manufacturer.getId());
        model.setName("C40 S");
        model.setAdapterType(AdapterType.KEENON_CLOUD);
        model.setIntegrationPath(IntegrationPath.KEENON_CLOUD_DEPENDENT);
        model = modelRepository.save(model);
        for (RobotCapabilityType capability : capabilities) {
            capabilityRepository.save(new RobotCapability(model.getId(), capability, true));
        }
        return model;
    }

    private Robot registerRobot(UUID organizationId, UUID modelId) {
        Robot robot = new Robot();
        robot.setOrganizationId(organizationId);
        robot.setRobotModelId(modelId);
        robot.setName("Robot " + UUID.randomUUID());
        robot.setSerialNumber("SN-" + UUID.randomUUID());
        robot.setStatus(RobotLifecycleStatus.REGISTERED);
        return robotRepository.save(robot);
    }
}
