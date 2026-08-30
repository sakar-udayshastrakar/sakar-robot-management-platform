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
