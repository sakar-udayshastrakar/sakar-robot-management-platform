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

/** IoT Platform → Cloud ladder control configuration → Store binding. */
class LadderControlControllerTest extends IntegrationTestSupport {

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
    void bindAStoreToALadderControlVendor_thenListIt() throws Exception {
        Organization org = createOrganization("Org " + UUID.randomUUID(), OrganizationType.DIRECT_CLIENT, null);
        String token = tokenWithRobotConfigure(org.getId());
        Site site = aSite(org.getId());

        String body = "{\"organizationId\":\"" + org.getId() + "\",\"siteId\":\"" + site.getId()
                + "\",\"manufacturer\":\"OTIS\",\"buildingId\":\"BLDG-1\",\"clientId\":\"CLIENT-1\"}";
        String response = mockMvc.perform(post("/api/v1/iot/ladder-control-bindings")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.manufacturer").value("OTIS"))
                .andReturn().getResponse().getContentAsString();
        String bindingId = objectMapper.readTree(response).get("data").get("id").asText();

        mockMvc.perform(get("/api/v1/iot/ladder-control-bindings").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.id=='" + bindingId + "')]").exists());
    }

    @Test
    void bindingTheSameStoreTwice_isRejected() throws Exception {
        Organization org = createOrganization("Org " + UUID.randomUUID(), OrganizationType.DIRECT_CLIENT, null);
        String token = tokenWithRobotConfigure(org.getId());
        Site site = aSite(org.getId());
        String body = "{\"organizationId\":\"" + org.getId() + "\",\"siteId\":\"" + site.getId() + "\",\"manufacturer\":\"OTIS\"}";

        mockMvc.perform(post("/api/v1/iot/ladder-control-bindings")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));

        mockMvc.perform(post("/api/v1/iot/ladder-control-bindings")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict());
    }

    @Test
    void viewerRole_cannotCreateABinding_missingRobotConfigurePermission() throws Exception {
        Organization org = createOrganization("Org " + UUID.randomUUID(), OrganizationType.DIRECT_CLIENT, null);
        Role viewer = ensureRole(RoleName.VIEWER, PermissionCode.ROBOT_VIEW);
        String email = "viewer-" + UUID.randomUUID() + "@example.com";
        createUser(email, "Password1!", viewer, org.getId());
        String token = login(email, "Password1!");
        Site site = aSite(org.getId());

        mockMvc.perform(post("/api/v1/iot/ladder-control-bindings")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"organizationId\":\"" + org.getId() + "\",\"siteId\":\"" + site.getId() + "\",\"manufacturer\":\"OTIS\"}"))
                .andExpect(status().isForbidden());
    }
}
