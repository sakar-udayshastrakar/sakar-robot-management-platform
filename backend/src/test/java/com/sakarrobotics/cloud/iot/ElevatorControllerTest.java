package com.sakarrobotics.cloud.iot;

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
import com.sakarrobotics.cloud.org.Site;
import com.sakarrobotics.cloud.org.SiteRepository;
import com.sakarrobotics.cloud.robot.registry.AdapterType;
import com.sakarrobotics.cloud.robot.registry.IntegrationPath;
import com.sakarrobotics.cloud.robot.registry.Robot;
import com.sakarrobotics.cloud.robot.registry.RobotLifecycleStatus;
import com.sakarrobotics.cloud.robot.registry.RobotManufacturer;
import com.sakarrobotics.cloud.robot.registry.RobotManufacturerRepository;
import com.sakarrobotics.cloud.robot.registry.RobotModel;
import com.sakarrobotics.cloud.robot.registry.RobotModelRepository;
import com.sakarrobotics.cloud.robot.registry.RobotRepository;

/** IoT Platform → Elevator Module (Elevator management, Elevator configuration + set record, delivery bookkeeping). */
class ElevatorControllerTest extends IntegrationTestSupport {

    @Autowired
    private SiteRepository siteRepository;
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

    private Site aSite(UUID organizationId) {
        Site site = new Site();
        site.setOrganizationId(organizationId);
        site.setName("Store " + UUID.randomUUID());
        return siteRepository.save(site);
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
    void registerDevice_createConfiguration_recordSetRecord_thenDeliver() throws Exception {
        Organization org = createOrganization("Org " + UUID.randomUUID(), OrganizationType.DIRECT_CLIENT, null);
        String token = tokenWithRobotConfigure(org.getId());
        Site site = aSite(org.getId());
        Robot robot = aRobot(org.getId());

        String deviceBody = "{\"organizationId\":\"" + org.getId() + "\",\"siteId\":\"" + site.getId()
                + "\",\"deviceId\":\"EL-001\",\"deviceName\":\"Lobby Elevator\",\"building\":\"Tower A\","
                + "\"protocol\":\"OTIS-IoT\",\"networkingMode\":\"WiFi\",\"communicationMode\":\"MQTT\"}";
        String deviceResponse = mockMvc.perform(post("/api/v1/iot/elevator-devices")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(deviceBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.deviceId").value("EL-001"))
                .andReturn().getResponse().getContentAsString();
        String deviceId = objectMapper.readTree(deviceResponse).get("data").get("id").asText();

        mockMvc.perform(get("/api/v1/iot/elevator-devices").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.id=='" + deviceId + "')]").exists());

        String configBody = "{\"organizationId\":\"" + org.getId() + "\",\"siteId\":\"" + site.getId()
                + "\",\"elevatorDeviceId\":\"" + deviceId + "\",\"robotId\":\"" + robot.getId()
                + "\",\"name\":\"Lobby to Floor 3\"}";
        String configResponse = mockMvc.perform(post("/api/v1/iot/elevator-configurations")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(configBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.name").value("Lobby to Floor 3"))
                .andReturn().getResponse().getContentAsString();
        String configId = objectMapper.readTree(configResponse).get("data").get("id").asText();

        mockMvc.perform(get("/api/v1/iot/elevator-configurations/" + configId + "/events")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.eventType=='CREATED')]").exists());

        mockMvc.perform(post("/api/v1/iot/elevator-configurations/" + configId + "/deliver")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"robotId\":\"" + robot.getId() + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("RECORDED"));

        mockMvc.perform(get("/api/v1/iot/elevator-configuration-deliveries")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.elevatorConfigurationId=='" + configId + "')]").exists());

        mockMvc.perform(get("/api/v1/iot/elevator-configurations/" + configId + "/events")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.eventType=='DELIVERED')]").exists());
    }

    @Test
    void creatingAConfiguration_forARobotInAnUnrelatedOrganization_isRejected() throws Exception {
        Organization orgA = createOrganization("Org A " + UUID.randomUUID(), OrganizationType.DIRECT_CLIENT, null);
        Organization orgB = createOrganization("Org B " + UUID.randomUUID(), OrganizationType.DIRECT_CLIENT, null);
        String tokenA = tokenWithRobotConfigure(orgA.getId());
        Site siteA = aSite(orgA.getId());
        Robot robotInOrgB = aRobot(orgB.getId());

        String deviceResponse = mockMvc.perform(post("/api/v1/iot/elevator-devices")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"organizationId\":\"" + orgA.getId() + "\",\"siteId\":\"" + siteA.getId() + "\",\"deviceId\":\"EL-1\"}"))
                .andReturn().getResponse().getContentAsString();
        String deviceId = objectMapper.readTree(deviceResponse).get("data").get("id").asText();

        mockMvc.perform(post("/api/v1/iot/elevator-configurations")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"organizationId\":\"" + orgA.getId() + "\",\"siteId\":\"" + siteA.getId()
                                + "\",\"elevatorDeviceId\":\"" + deviceId + "\",\"robotId\":\"" + robotInOrgB.getId()
                                + "\",\"name\":\"x\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("ROBOT_NOT_FOUND"));
    }

    @Test
    void viewerRole_cannotRegisterADevice_missingRobotConfigurePermission() throws Exception {
        Organization org = createOrganization("Org " + UUID.randomUUID(), OrganizationType.DIRECT_CLIENT, null);
        Role viewer = ensureRole(RoleName.VIEWER, PermissionCode.ROBOT_VIEW);
        String email = "viewer-" + UUID.randomUUID() + "@example.com";
        createUser(email, "Password1!", viewer, org.getId());
        String token = login(email, "Password1!");
        Site site = aSite(org.getId());

        mockMvc.perform(post("/api/v1/iot/elevator-devices")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"organizationId\":\"" + org.getId() + "\",\"siteId\":\"" + site.getId() + "\",\"deviceId\":\"EL-1\"}"))
                .andExpect(status().isForbidden());
    }
}
