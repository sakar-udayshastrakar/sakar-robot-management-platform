package com.sakarrobotics.cloud.org;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

/**
 * Privilege-escalation coverage: an ORG_ADMIN must not be able to create an
 * organization anywhere outside their own subtree, even though they hold
 * {@code USER_MANAGE} (Master Requirements Part 19.B: "a user must never be
 * able to grant themselves ... a scope outside what their own role is
 * authorized to assign").
 */
class OrganizationControllerSecurityTest extends IntegrationTestSupport {

    @Test
    void orgAdmin_cannotCreateOrganizationUnderAnUnrelatedOrganization() throws Exception {
        Role orgAdmin = ensureRole(RoleName.ORG_ADMIN, PermissionCode.ROBOT_VIEW, PermissionCode.USER_MANAGE);
        Organization ownOrg = createOrganization("Own " + UUID.randomUUID(), OrganizationType.DISTRIBUTOR, null);
        Organization unrelatedOrg = createOrganization("Unrelated " + UUID.randomUUID(), OrganizationType.DISTRIBUTOR, null);
        String email = "orgadmin-esc-" + UUID.randomUUID() + "@example.com";
        createUser(email, "Password1!", orgAdmin, ownOrg.getId());
        String token = login(email, "Password1!");

        String body = "{\"name\":\"Sneaky Sub-org\",\"orgType\":\"CLIENT\",\"parentOrganizationId\":\""
                + unrelatedOrg.getId() + "\"}";

        mockMvc.perform(post("/api/v1/organizations")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("TENANT_ACCESS_DENIED"));
    }

    @Test
    void orgAdmin_canCreateOrganizationUnderTheirOwnSubtree() throws Exception {
        Role orgAdmin = ensureRole(RoleName.ORG_ADMIN, PermissionCode.ROBOT_VIEW, PermissionCode.USER_MANAGE);
        Organization ownOrg = createOrganization("Own " + UUID.randomUUID(), OrganizationType.DISTRIBUTOR, null);
        String email = "orgadmin-ok-" + UUID.randomUUID() + "@example.com";
        createUser(email, "Password1!", orgAdmin, ownOrg.getId());
        String token = login(email, "Password1!");

        String body = "{\"name\":\"Legit Sub-org\",\"orgType\":\"CLIENT\",\"parentOrganizationId\":\""
                + ownOrg.getId() + "\"}";

        mockMvc.perform(post("/api/v1/organizations")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());
    }

    @Test
    void nonSuperAdmin_cannotCreateARootLevelOrganization() throws Exception {
        Role orgAdmin = ensureRole(RoleName.ORG_ADMIN, PermissionCode.ROBOT_VIEW, PermissionCode.USER_MANAGE);
        Organization ownOrg = createOrganization("Own " + UUID.randomUUID(), OrganizationType.DISTRIBUTOR, null);
        String email = "orgadmin-root-" + UUID.randomUUID() + "@example.com";
        createUser(email, "Password1!", orgAdmin, ownOrg.getId());
        String token = login(email, "Password1!");

        String body = "{\"name\":\"New Root\",\"orgType\":\"DISTRIBUTOR\"}";

        mockMvc.perform(post("/api/v1/organizations")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());
    }
}
