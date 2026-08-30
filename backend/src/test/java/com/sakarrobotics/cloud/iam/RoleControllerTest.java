package com.sakarrobotics.cloud.iam;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

import com.sakarrobotics.cloud.IntegrationTestSupport;
import com.sakarrobotics.cloud.org.Organization;
import com.sakarrobotics.cloud.org.OrganizationType;

/** The role list is fixed reference data (V9__seed_rbac.sql) — only read access is exercised here. */
class RoleControllerTest extends IntegrationTestSupport {

    @Test
    void orgAdmin_canListRolesWithTheirPermissions() throws Exception {
        Role orgAdmin = ensureRole(RoleName.ORG_ADMIN, PermissionCode.USER_MANAGE, PermissionCode.ROBOT_VIEW);
        Organization org = createOrganization("Org " + UUID.randomUUID(), OrganizationType.DIRECT_CLIENT, null);
        String email = "admin-" + UUID.randomUUID() + "@example.com";
        createUser(email, "Password1!", orgAdmin, org.getId());
        String token = login(email, "Password1!");

        mockMvc.perform(get("/api/v1/roles").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.name=='ORG_ADMIN')].permissions[0]").exists());
    }

    @Test
    void viewerRole_cannotListRoles_missingUserManagePermission() throws Exception {
        Role viewer = ensureRole(RoleName.VIEWER, PermissionCode.ROBOT_VIEW);
        Organization org = createOrganization("Org " + UUID.randomUUID(), OrganizationType.DIRECT_CLIENT, null);
        String email = "viewer-" + UUID.randomUUID() + "@example.com";
        createUser(email, "Password1!", viewer, org.getId());
        String token = login(email, "Password1!");

        mockMvc.perform(get("/api/v1/roles").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden());
    }
}
