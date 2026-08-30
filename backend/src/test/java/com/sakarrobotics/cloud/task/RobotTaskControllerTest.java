package com.sakarrobotics.cloud.task;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
 * Task orchestration lifecycle + capability-gating + tenant-isolation
 * coverage (Roadmap Phase 6, approved Decision 1 item 2).
 */
class RobotTaskControllerTest extends IntegrationTestSupport {

    @Autowired
    private RobotManufacturerRepository manufacturerRepository;
    @Autowired
    private RobotModelRepository modelRepository;
    @Autowired
    private RobotCapabilityRepository capabilityRepository;
    @Autowired
    private RobotRepository robotRepository;

    @Test
    void creatingATask_onAModelWithoutStartTaskCapability_isRejected() throws Exception {
        Role orgAdmin = ensureRole(RoleName.ORG_ADMIN, PermissionCode.ROBOT_TASK_CREATE, PermissionCode.ROBOT_CONFIGURE);
        Organization org = createOrganization("Org " + UUID.randomUUID(), OrganizationType.DIRECT_CLIENT, null);
        String email = "admin-" + UUID.randomUUID() + "@example.com";
        createUser(email, "Password1!", orgAdmin, org.getId());
        String token = login(email, "Password1!");

        RobotModel model = modelWithCapabilities(); // no capabilities registered
        Robot robot = registerRobot(org.getId(), model.getId());

        mockMvc.perform(post("/api/v1/robots/" + robot.getId() + "/tasks")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"taskType\":\"CLEANING\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("UNSUPPORTED_CAPABILITY"));
    }

    @Test
    void aCleaningTask_startedThenStopped_recordsACleaningSessionAndTaskEvents() throws Exception {
        Role orgAdmin = ensureRole(RoleName.ORG_ADMIN, PermissionCode.ROBOT_TASK_CREATE, PermissionCode.ROBOT_CONTROL,
                PermissionCode.ROBOT_VIEW, PermissionCode.ROBOT_CONFIGURE);
        Organization org = createOrganization("Org " + UUID.randomUUID(), OrganizationType.DIRECT_CLIENT, null);
        String email = "admin-" + UUID.randomUUID() + "@example.com";
        createUser(email, "Password1!", orgAdmin, org.getId());
        String token = login(email, "Password1!");

        RobotModel model = modelWithCapabilities(RobotCapabilityType.START_TASK);
        Robot robot = registerRobot(org.getId(), model.getId());

        String createResponse = mockMvc.perform(post("/api/v1/robots/" + robot.getId() + "/tasks")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"taskType\":\"CLEANING\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("CREATED"))
                .andReturn().getResponse().getContentAsString();
        String taskId = objectMapper.readTree(createResponse).get("data").get("id").asText();

        mockMvc.perform(post("/api/v1/tasks/" + taskId + "/start").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("RUNNING"));

        mockMvc.perform(post("/api/v1/tasks/" + taskId + "/stop").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"));

        mockMvc.perform(get("/api/v1/tasks/" + taskId).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.events.length()").value(3)); // CREATED, START, STOP

        mockMvc.perform(get("/api/v1/robots/" + robot.getId() + "/cleaning/history")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].result").value("COMPLETED"))
                .andExpect(jsonPath("$.data.content[0].taskId").value(taskId));
    }

    @Test
    void stoppingATaskThatWasNeverStarted_isAnInvalidTransition() throws Exception {
        Role orgAdmin = ensureRole(RoleName.ORG_ADMIN, PermissionCode.ROBOT_TASK_CREATE, PermissionCode.ROBOT_CONTROL,
                PermissionCode.ROBOT_CONFIGURE);
        Organization org = createOrganization("Org " + UUID.randomUUID(), OrganizationType.DIRECT_CLIENT, null);
        String email = "admin-" + UUID.randomUUID() + "@example.com";
        createUser(email, "Password1!", orgAdmin, org.getId());
        String token = login(email, "Password1!");

        RobotModel model = modelWithCapabilities(RobotCapabilityType.START_TASK);
        Robot robot = registerRobot(org.getId(), model.getId());

        String createResponse = mockMvc.perform(post("/api/v1/robots/" + robot.getId() + "/tasks")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"taskType\":\"CLEANING\"}"))
                .andReturn().getResponse().getContentAsString();
        String taskId = objectMapper.readTree(createResponse).get("data").get("id").asText();

        mockMvc.perform(post("/api/v1/tasks/" + taskId + "/stop").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("INVALID_TASK_TRANSITION"));
    }

    @Test
    void user_cannotSeeATaskBelongingToAnUnrelatedOrganization_getsNotFoundNotForbidden() throws Exception {
        Role orgAdmin = ensureRole(RoleName.ORG_ADMIN, PermissionCode.ROBOT_TASK_CREATE, PermissionCode.ROBOT_VIEW,
                PermissionCode.ROBOT_CONFIGURE);
        Organization orgA = createOrganization("Org A " + UUID.randomUUID(), OrganizationType.DIRECT_CLIENT, null);
        Organization orgB = createOrganization("Org B " + UUID.randomUUID(), OrganizationType.DIRECT_CLIENT, null);
        String emailA = "admin-a-" + UUID.randomUUID() + "@example.com";
        createUser(emailA, "Password1!", orgAdmin, orgA.getId());
        String emailB = "admin-b-" + UUID.randomUUID() + "@example.com";
        createUser(emailB, "Password1!", orgAdmin, orgB.getId());

        RobotModel model = modelWithCapabilities(RobotCapabilityType.START_TASK);
        Robot robotInOrgB = registerRobot(orgB.getId(), model.getId());
        String tokenB = login(emailB, "Password1!");
        String createResponse = mockMvc.perform(post("/api/v1/robots/" + robotInOrgB.getId() + "/tasks")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"taskType\":\"CLEANING\"}"))
                .andReturn().getResponse().getContentAsString();
        String taskId = objectMapper.readTree(createResponse).get("data").get("id").asText();

        String tokenA = login(emailA, "Password1!");
        mockMvc.perform(get("/api/v1/tasks/" + taskId).header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("TASK_NOT_FOUND"));
    }

    private RobotModel modelWithCapabilities(RobotCapabilityType... capabilities) {
        RobotManufacturer manufacturer = manufacturerRepository.save(new RobotManufacturer("TestVendor-" + UUID.randomUUID()));
        RobotModel model = new RobotModel();
        model.setManufacturerId(manufacturer.getId());
        model.setName("Test Model");
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
