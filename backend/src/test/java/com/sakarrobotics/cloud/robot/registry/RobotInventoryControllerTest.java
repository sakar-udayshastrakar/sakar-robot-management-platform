package com.sakarrobotics.cloud.robot.registry;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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

/**
 * "Bind store" / "allocate to lower level agent" / "return to inventory"
 * (Robot Management, Phase 2).
 */
class RobotInventoryControllerTest extends IntegrationTestSupport {

    @Autowired
    private RobotManufacturerRepository manufacturerRepository;
    @Autowired
    private RobotModelRepository modelRepository;
    @Autowired
    private RobotRepository robotRepository;
    @Autowired
    private SiteRepository siteRepository;

    private RobotModel aModel() {
        RobotManufacturer manufacturer = manufacturerRepository.save(new RobotManufacturer("TestVendor-" + UUID.randomUUID()));
        RobotModel model = new RobotModel();
        model.setManufacturerId(manufacturer.getId());
        model.setName("Test Model");
        model.setAdapterType(AdapterType.SAKAR_NATIVE);
        model.setIntegrationPath(IntegrationPath.SAKAR_OWNED_LOCAL);
        return modelRepository.save(model);
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

    private String tokenWithRobotConfigure(UUID organizationId) throws Exception {
        Role orgAdmin = ensureRole(RoleName.ORG_ADMIN, PermissionCode.ROBOT_VIEW, PermissionCode.ROBOT_CONFIGURE);
        String email = "admin-" + UUID.randomUUID() + "@example.com";
        createUser(email, "Password1!", orgAdmin, organizationId);
        return login(email, "Password1!");
    }

    @Test
    void bindStore_setsStoreAndWarrantyDates() throws Exception {
        Organization org = createOrganization("Org " + UUID.randomUUID(), OrganizationType.DIRECT_CLIENT, null);
        Site site = siteRepository.save(newSite(org.getId(), "Sakar HQ"));
        RobotModel model = aModel();
        Robot robot = registerRobot(org.getId(), model.getId());
        String token = tokenWithRobotConfigure(org.getId());

        String body = "{\"siteId\":\"" + site.getId() + "\",\"warrantyStartDate\":\"2026-01-01\",\"warrantyEndDate\":\"2027-01-01\"}";

        mockMvc.perform(put("/api/v1/robots/" + robot.getId() + "/inventory")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.warrantyStartDate").value("2026-01-01"))
                .andExpect(jsonPath("$.data.warrantyEndDate").value("2027-01-01"));
    }

    @Test
    void allocate_toADirectChildOrganization_succeeds() throws Exception {
        Organization parent = createOrganization("Parent " + UUID.randomUUID(), OrganizationType.DISTRIBUTOR, null);
        Organization child = createOrganization("Child " + UUID.randomUUID(), OrganizationType.SUB_DISTRIBUTOR, parent.getId());
        RobotModel model = aModel();
        Robot robot = registerRobot(parent.getId(), model.getId());
        String token = tokenWithRobotConfigure(parent.getId());

        mockMvc.perform(post("/api/v1/robots/" + robot.getId() + "/allocate")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"organizationId\":\"" + child.getId() + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.organizationId").value(child.getId().toString()));
    }

    @Test
    void allocate_toANonChildOrganization_isRejected() throws Exception {
        Organization org = createOrganization("Org " + UUID.randomUUID(), OrganizationType.DIRECT_CLIENT, null);
        Organization unrelated = createOrganization("Unrelated " + UUID.randomUUID(), OrganizationType.DIRECT_CLIENT, null);
        RobotModel model = aModel();
        Robot robot = registerRobot(org.getId(), model.getId());
        String token = tokenWithRobotConfigure(org.getId());

        mockMvc.perform(post("/api/v1/robots/" + robot.getId() + "/allocate")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"organizationId\":\"" + unrelated.getId() + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
    }

    @Test
    void returnToInventory_clearsSiteAndResetsStatus() throws Exception {
        Organization org = createOrganization("Org " + UUID.randomUUID(), OrganizationType.DIRECT_CLIENT, null);
        Site site = siteRepository.save(newSite(org.getId(), "Sakar HQ"));
        RobotModel model = aModel();
        Robot robot = registerRobot(org.getId(), model.getId());
        robot.setSiteId(site.getId());
        robot.setStatus(RobotLifecycleStatus.ACTIVE);
        robotRepository.save(robot);
        String token = tokenWithRobotConfigure(org.getId());

        mockMvc.perform(post("/api/v1/robots/" + robot.getId() + "/return-to-inventory")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.siteId").doesNotExist())
                .andExpect(jsonPath("$.data.status").value("REGISTERED"));
    }

    @Test
    void viewerRole_cannotBindStore_missingRobotConfigurePermission() throws Exception {
        Organization org = createOrganization("Org " + UUID.randomUUID(), OrganizationType.DIRECT_CLIENT, null);
        Role viewer = ensureRole(RoleName.VIEWER, PermissionCode.ROBOT_VIEW);
        String email = "viewer-" + UUID.randomUUID() + "@example.com";
        createUser(email, "Password1!", viewer, org.getId());
        String token = login(email, "Password1!");
        RobotModel model = aModel();
        Robot robot = registerRobot(org.getId(), model.getId());

        mockMvc.perform(put("/api/v1/robots/" + robot.getId() + "/inventory")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"siteId\":null,\"warrantyStartDate\":null,\"warrantyEndDate\":null}"))
                .andExpect(status().isForbidden());
    }

    private static Site newSite(UUID organizationId, String name) {
        Site site = new Site();
        site.setOrganizationId(organizationId);
        site.setName(name);
        return site;
    }
}
