package com.sakarrobotics.cloud.resourceconfig;

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
import com.sakarrobotics.cloud.org.Organization;
import com.sakarrobotics.cloud.org.OrganizationType;

/** New Resource Configuration → Scene list (Robot Management sidebar group). */
class ResourceSceneControllerTest extends IntegrationTestSupport {

    private String tokenWithRobotConfigure(UUID organizationId) throws Exception {
        Role orgAdmin = ensureRole(RoleName.ORG_ADMIN, PermissionCode.ROBOT_VIEW, PermissionCode.ROBOT_CONFIGURE);
        String email = "admin-" + UUID.randomUUID() + "@example.com";
        createUser(email, "Password1!", orgAdmin, organizationId);
        return login(email, "Password1!");
    }

    @Test
    void create_thenList_thenUpdate_thenDelete() throws Exception {
        Organization org = createOrganization("Org " + UUID.randomUUID(), OrganizationType.DIRECT_CLIENT, null);
        String token = tokenWithRobotConfigure(org.getId());
        String sceneName = "Office demo " + UUID.randomUUID();

        String createResponse = mockMvc.perform(post("/api/v1/scenes")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"organizationId\":\"" + org.getId() + "\",\"name\":\"" + sceneName + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.name").value(sceneName))
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.resourcePackType").value("STANDARD"))
                .andReturn().getResponse().getContentAsString();
        String id = objectMapper.readTree(createResponse).get("data").get("id").asText();

        mockMvc.perform(get("/api/v1/scenes").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.name=='" + sceneName + "')]").exists());

        mockMvc.perform(put("/api/v1/scenes/" + id)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + sceneName + "\",\"status\":\"PUBLISHED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PUBLISHED"));

        mockMvc.perform(delete("/api/v1/scenes/" + id).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/scenes").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.name=='" + sceneName + "')]").doesNotExist());
    }

    @Test
    void viewerRole_cannotCreateAScene_missingRobotConfigurePermission() throws Exception {
        Organization org = createOrganization("Org " + UUID.randomUUID(), OrganizationType.DIRECT_CLIENT, null);
        Role viewer = ensureRole(RoleName.VIEWER, PermissionCode.ROBOT_VIEW);
        String email = "viewer-" + UUID.randomUUID() + "@example.com";
        createUser(email, "Password1!", viewer, org.getId());
        String token = login(email, "Password1!");

        mockMvc.perform(post("/api/v1/scenes")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"organizationId\":\"" + org.getId() + "\",\"name\":\"X\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void cannotCreateAScene_inAnUnrelatedOrganization() throws Exception {
        Organization orgA = createOrganization("Org A " + UUID.randomUUID(), OrganizationType.DIRECT_CLIENT, null);
        Organization orgB = createOrganization("Org B " + UUID.randomUUID(), OrganizationType.DIRECT_CLIENT, null);
        String token = tokenWithRobotConfigure(orgA.getId());

        mockMvc.perform(post("/api/v1/scenes")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"organizationId\":\"" + orgB.getId() + "\",\"name\":\"X\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("TENANT_ACCESS_DENIED"));
    }
}
