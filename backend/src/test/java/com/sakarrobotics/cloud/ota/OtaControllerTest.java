package com.sakarrobotics.cloud.ota;

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
import com.sakarrobotics.cloud.robot.registry.RobotLifecycleStatus;
import com.sakarrobotics.cloud.robot.registry.RobotManufacturer;
import com.sakarrobotics.cloud.robot.registry.RobotManufacturerRepository;
import com.sakarrobotics.cloud.robot.registry.RobotModel;
import com.sakarrobotics.cloud.robot.registry.RobotModelRepository;
import com.sakarrobotics.cloud.robot.registry.RobotRepository;

/** OTA Management → System Version Management + Update record (governance doc Module B, approved for a real build). */
class OtaControllerTest extends IntegrationTestSupport {

    @Autowired
    private RobotManufacturerRepository manufacturerRepository;
    @Autowired
    private RobotModelRepository modelRepository;
    @Autowired
    private RobotRepository robotRepository;

    private String tokenWithRobotConfigure(UUID organizationId) throws Exception {
        Role orgAdmin = ensureRole(RoleName.ORG_ADMIN, PermissionCode.ROBOT_VIEW, PermissionCode.ROBOT_CONFIGURE);
        String email = "admin-" + UUID.randomUUID() + "@example.com";
        createUser(email, "Password1!", orgAdmin, organizationId);
        return login(email, "Password1!");
    }

    private Robot aRobot(UUID organizationId) {
        RobotManufacturer manufacturer = manufacturerRepository.save(new RobotManufacturer("TestVendor-" + UUID.randomUUID()));
        RobotModel model = new RobotModel();
        model.setManufacturerId(manufacturer.getId());
        model.setName("Test Model");
        model.setAdapterType(AdapterType.SAKAR_NATIVE);
        model.setIntegrationPath(IntegrationPath.SAKAR_OWNED_LOCAL);
        model = modelRepository.save(model);

        Robot robot = new Robot();
        robot.setOrganizationId(organizationId);
        robot.setRobotModelId(model.getId());
        robot.setName("Robot " + UUID.randomUUID());
        robot.setSerialNumber("SN-" + UUID.randomUUID());
        robot.setStatus(RobotLifecycleStatus.REGISTERED);
        return robotRepository.save(robot);
    }

    @Test
    void createVersion_thenPushToARobot_thenListDeploymentRecords() throws Exception {
        Organization org = createOrganization("Org " + UUID.randomUUID(), OrganizationType.DIRECT_CLIENT, null);
        String token = tokenWithRobotConfigure(org.getId());
        Robot robot = aRobot(org.getId());

        String createBody = "{\"organizationId\":\"" + org.getId() + "\",\"packageName\":\"clean\","
                + "\"wholeMachineSoftware\":\"C40 S-LS-M014C00-RW-F00\",\"packageVersion\":\"V258\","
                + "\"hardwareVersion\":\"C40 S-LS-M014C00\",\"grayscale\":true,\"sizeBytes\":307000}";

        String createResponse = mockMvc.perform(post("/api/v1/ota/versions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.packageVersion").value("V258"))
                .andExpect(jsonPath("$.data.grayscale").value(true))
                .andReturn().getResponse().getContentAsString();
        String versionId = objectMapper.readTree(createResponse).get("data").get("id").asText();

        mockMvc.perform(get("/api/v1/ota/versions").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.id=='" + versionId + "')]").exists());

        mockMvc.perform(post("/api/v1/ota/versions/" + versionId + "/push")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"robotId\":\"" + robot.getId() + "\",\"oldVersionNumber\":\"V257\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.newVersionNumber").value("V258"))
                .andExpect(jsonPath("$.data.oldVersionNumber").value("V257"))
                .andExpect(jsonPath("$.data.status").value("RECORDED"));

        mockMvc.perform(get("/api/v1/ota/deployment-records").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.robotId=='" + robot.getId() + "')]").exists());
    }

    @Test
    void pushToARobot_inAnUnrelatedOrganization_isRejected() throws Exception {
        Organization orgA = createOrganization("Org A " + UUID.randomUUID(), OrganizationType.DIRECT_CLIENT, null);
        Organization orgB = createOrganization("Org B " + UUID.randomUUID(), OrganizationType.DIRECT_CLIENT, null);
        String tokenA = tokenWithRobotConfigure(orgA.getId());
        Robot robotInOrgB = aRobot(orgB.getId());

        String createResponse = mockMvc.perform(post("/api/v1/ota/versions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"organizationId\":\"" + orgA.getId() + "\",\"packageName\":\"clean\",\"packageVersion\":\"V1\"}"))
                .andReturn().getResponse().getContentAsString();
        String versionId = objectMapper.readTree(createResponse).get("data").get("id").asText();

        mockMvc.perform(post("/api/v1/ota/versions/" + versionId + "/push")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"robotId\":\"" + robotInOrgB.getId() + "\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("ROBOT_NOT_FOUND"));
    }

    @Test
    void viewerRole_cannotCreateAVersion_missingRobotConfigurePermission() throws Exception {
        Organization org = createOrganization("Org " + UUID.randomUUID(), OrganizationType.DIRECT_CLIENT, null);
        Role viewer = ensureRole(RoleName.VIEWER, PermissionCode.ROBOT_VIEW);
        String email = "viewer-" + UUID.randomUUID() + "@example.com";
        createUser(email, "Password1!", viewer, org.getId());
        String token = login(email, "Password1!");

        mockMvc.perform(post("/api/v1/ota/versions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"organizationId\":\"" + org.getId() + "\",\"packageName\":\"clean\",\"packageVersion\":\"V1\"}"))
                .andExpect(status().isForbidden());
    }
}
