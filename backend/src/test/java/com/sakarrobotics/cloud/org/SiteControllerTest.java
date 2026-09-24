package com.sakarrobotics.cloud.org;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import com.sakarrobotics.cloud.IntegrationTestSupport;
import com.sakarrobotics.cloud.iam.PermissionCode;
import com.sakarrobotics.cloud.iam.Role;
import com.sakarrobotics.cloud.iam.RoleName;

/** Site management, also this platform's "Store Management" (Robot Management sidebar group). */
class SiteControllerTest extends IntegrationTestSupport {

    private String tokenWithRobotConfigure(UUID organizationId) throws Exception {
        Role orgAdmin = ensureRole(RoleName.ORG_ADMIN, PermissionCode.ROBOT_VIEW, PermissionCode.ROBOT_CONFIGURE);
        String email = "admin-" + UUID.randomUUID() + "@example.com";
        createUser(email, "Password1!", orgAdmin, organizationId);
        return login(email, "Password1!");
    }

    @Test
    void create_withStoreFields_thenListAcrossAccessibleOrganizations_thenUpdate_thenDelete() throws Exception {
        Organization org = createOrganization("Org " + UUID.randomUUID(), OrganizationType.DIRECT_CLIENT, null);
        String token = tokenWithRobotConfigure(org.getId());
        String storeName = "Sakar robotics office " + UUID.randomUUID();

        String body = "{\"organizationId\":\"" + org.getId() + "\",\"name\":\"" + storeName + "\","
                + "\"area\":\"INDIA\",\"contactName\":\"Uday\",\"phone\":\"12345\",\"email\":\"uday@sakarrobotics.com\","
                + "\"sceneType\":\"Hotel\",\"chainBrand\":false}";

        String createResponse = mockMvc.perform(post("/api/v1/sites")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.name").value(storeName))
                .andExpect(jsonPath("$.data.area").value("INDIA"))
                .andExpect(jsonPath("$.data.sceneType").value("Hotel"))
                .andExpect(jsonPath("$.data.chainBrand").value(false))
                .andReturn().getResponse().getContentAsString();
        String id = objectMapper.readTree(createResponse).get("data").get("id").asText();

        // No organizationId param — lists across every organization the caller can access.
        mockMvc.perform(get("/api/v1/sites").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.name=='" + storeName + "')]").exists());

        String renamed = storeName + " renamed";
        mockMvc.perform(put("/api/v1/sites/" + id)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + renamed + "\",\"chainBrand\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value(renamed))
                .andExpect(jsonPath("$.data.chainBrand").value(true));

        mockMvc.perform(delete("/api/v1/sites/" + id).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/sites").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.name=='" + renamed + "')]").doesNotExist());
    }

    @Test
    void list_withOrganizationId_isRejectedForAnUnrelatedOrganization() throws Exception {
        Organization orgA = createOrganization("Org A " + UUID.randomUUID(), OrganizationType.DIRECT_CLIENT, null);
        Organization orgB = createOrganization("Org B " + UUID.randomUUID(), OrganizationType.DIRECT_CLIENT, null);
        String token = tokenWithRobotConfigure(orgA.getId());

        mockMvc.perform(get("/api/v1/sites").param("organizationId", orgB.getId().toString())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("TENANT_ACCESS_DENIED"));
    }

    @Test
    void viewerRole_cannotCreateASite_missingRobotConfigurePermission() throws Exception {
        Organization org = createOrganization("Org " + UUID.randomUUID(), OrganizationType.DIRECT_CLIENT, null);
        Role viewer = ensureRole(RoleName.VIEWER, PermissionCode.ROBOT_VIEW);
        String email = "viewer-" + UUID.randomUUID() + "@example.com";
        createUser(email, "Password1!", viewer, org.getId());
        String token = login(email, "Password1!");

        mockMvc.perform(post("/api/v1/sites")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"organizationId\":\"" + org.getId() + "\",\"name\":\"X\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void cannotEditASite_belongingToAnUnrelatedOrganization() throws Exception {
        Organization orgA = createOrganization("Org A " + UUID.randomUUID(), OrganizationType.DIRECT_CLIENT, null);
        Organization orgB = createOrganization("Org B " + UUID.randomUUID(), OrganizationType.DIRECT_CLIENT, null);
        String tokenA = tokenWithRobotConfigure(orgA.getId());
        String tokenB = tokenWithRobotConfigure(orgB.getId());

        String createResponse = mockMvc.perform(post("/api/v1/sites")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"organizationId\":\"" + orgA.getId() + "\",\"name\":\"Org A Store\"}"))
                .andReturn().getResponse().getContentAsString();
        String id = objectMapper.readTree(createResponse).get("data").get("id").asText();

        mockMvc.perform(put("/api/v1/sites/" + id)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Hijacked\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("SITE_NOT_FOUND"));
    }
}
