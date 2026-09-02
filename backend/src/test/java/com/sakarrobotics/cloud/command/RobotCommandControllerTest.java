package com.sakarrobotics.cloud.command;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

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
