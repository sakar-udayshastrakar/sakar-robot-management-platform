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

/** IoT Platform → Phone Module → Device management. */
class PhoneDeviceControllerTest extends IntegrationTestSupport {

    @Autowired
    private SiteRepository siteRepository;

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

    @Test
    void registerAPhoneDevice_thenListIt() throws Exception {
        Organization org = createOrganization("Org " + UUID.randomUUID(), OrganizationType.DIRECT_CLIENT, null);
        String token = tokenWithRobotConfigure(org.getId());
        Site site = aSite(org.getId());

        String body = "{\"organizationId\":\"" + org.getId() + "\",\"siteId\":\"" + site.getId()
                + "\",\"deviceId\":\"PH-001\",\"deviceName\":\"Front Desk Phone\",\"networkingMode\":\"WiFi\"}";
        String response = mockMvc.perform(post("/api/v1/iot/phone-devices")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.deviceId").value("PH-001"))
                .andReturn().getResponse().getContentAsString();
        String deviceId = objectMapper.readTree(response).get("data").get("id").asText();

        mockMvc.perform(get("/api/v1/iot/phone-devices").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.id=='" + deviceId + "')]").exists());
    }

    @Test
    void registeringADevice_forASiteInAnUnrelatedOrganization_isRejected() throws Exception {
        Organization orgA = createOrganization("Org A " + UUID.randomUUID(), OrganizationType.DIRECT_CLIENT, null);
        Organization orgB = createOrganization("Org B " + UUID.randomUUID(), OrganizationType.DIRECT_CLIENT, null);
        String tokenA = tokenWithRobotConfigure(orgA.getId());
        Site siteInOrgB = aSite(orgB.getId());

        mockMvc.perform(post("/api/v1/iot/phone-devices")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"organizationId\":\"" + orgA.getId() + "\",\"siteId\":\"" + siteInOrgB.getId() + "\",\"deviceId\":\"PH-1\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("SITE_NOT_FOUND"));
    }

    @Test
    void viewerRole_cannotRegisterADevice_missingRobotConfigurePermission() throws Exception {
        Organization org = createOrganization("Org " + UUID.randomUUID(), OrganizationType.DIRECT_CLIENT, null);
        Role viewer = ensureRole(RoleName.VIEWER, PermissionCode.ROBOT_VIEW);
        String email = "viewer-" + UUID.randomUUID() + "@example.com";
        createUser(email, "Password1!", viewer, org.getId());
        String token = login(email, "Password1!");
        Site site = aSite(org.getId());

        mockMvc.perform(post("/api/v1/iot/phone-devices")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"organizationId\":\"" + org.getId() + "\",\"siteId\":\"" + site.getId() + "\",\"deviceId\":\"PH-1\"}"))
                .andExpect(status().isForbidden());
    }
}
