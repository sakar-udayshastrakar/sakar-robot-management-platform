package com.sakarrobotics.cloud.iam;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import com.sakarrobotics.cloud.IntegrationTestSupport;
import com.sakarrobotics.cloud.org.Organization;
import com.sakarrobotics.cloud.org.OrganizationType;

/**
 * The role list is fixed reference data (V9__seed_rbac.sql) — {@code GET} is
 * read-only, {@code PUT} edits an existing role's description/permission set
 * only (never its name — see {@link com.sakarrobotics.cloud.iam.dto.UpdateRoleRequest}).
 */
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

    @Test
    void superAdmin_canEditAnExistingRolesDescriptionAndPermissions() throws Exception {
        Role superAdmin = ensureRole(RoleName.SUPER_ADMIN, PermissionCode.USER_MANAGE, PermissionCode.ROLE_MANAGE);
        // Also ensures a ROBOT_DIAGNOSTICS Permission row exists, even in isolation, before the PUT below references it.
        // SITE_ADMIN, not TECHNICIAN: RobotCommandControllerTest relies elsewhere on TECHNICIAN
        // never being granted ROBOT_VIEW across this whole shared-DB suite — mutating it here,
        // even transiently, would race that unrelated test depending on execution order.
        Role siteAdmin = ensureRole(RoleName.SITE_ADMIN, PermissionCode.ROBOT_VIEW, PermissionCode.ROBOT_DIAGNOSTICS);
        ensureRole(RoleName.VIEWER); // must exist so createUser's default role assignment elsewhere still works
        Organization org = createOrganization("Org " + UUID.randomUUID(), OrganizationType.DIRECT_CLIENT, null);
        String email = "super-" + UUID.randomUUID() + "@example.com";
        createUser(email, "Password1!", superAdmin, org.getId());
        String token = login(email, "Password1!");

        // SITE_ADMIN is still one of only 6 fixed roles, shared and reused (cumulative-permission
        // `ensureRole`) across this entire suite's single, never-reset database — unlike every
        // other test in this class, a PUT genuinely REPLACES the role's whole permission set, so
        // this test snapshots and restores it, regardless of pass/fail, to avoid corrupting any
        // other test class's assumptions about what SITE_ADMIN grants.
        String originalDescription = siteAdmin.getDescription();
        Set<Permission> originalPermissions = new HashSet<>(siteAdmin.getPermissions());
        try {
            String body = "{\"description\":\"Updated description\",\"permissionCodes\":[\"ROBOT_VIEW\",\"ROBOT_DIAGNOSTICS\"]}";

            mockMvc.perform(put("/api/v1/roles/" + siteAdmin.getId())
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.name").value("SITE_ADMIN"))
                    .andExpect(jsonPath("$.data.description").value("Updated description"))
                    .andExpect(jsonPath("$.data.permissions.length()").value(2))
                    .andExpect(jsonPath("$.data.permissions[?(@=='ROBOT_DIAGNOSTICS')]").exists());
        } finally {
            Role fresh = roleRepository.findById(siteAdmin.getId()).orElseThrow();
            fresh.setDescription(originalDescription);
            fresh.getPermissions().clear();
            fresh.getPermissions().addAll(originalPermissions);
            roleRepository.save(fresh);
        }
    }

    @Test
    void orgAdmin_cannotEditARole_missingRoleManagePermission() throws Exception {
        // ORG_ADMIN holds USER_MANAGE (can GET /roles) but not the stronger ROLE_MANAGE (cannot PUT).
        // This PUT is rejected by @PreAuthorize before ever reaching RoleService, so — unlike the
        // test above — SITE_ADMIN is never actually mutated here; no restore is needed.
        Role orgAdmin = ensureRole(RoleName.ORG_ADMIN, PermissionCode.USER_MANAGE);
        Role siteAdmin = ensureRole(RoleName.SITE_ADMIN, PermissionCode.ROBOT_VIEW);
        Organization org = createOrganization("Org " + UUID.randomUUID(), OrganizationType.DIRECT_CLIENT, null);
        String email = "orgadmin-" + UUID.randomUUID() + "@example.com";
        createUser(email, "Password1!", orgAdmin, org.getId());
        String token = login(email, "Password1!");

        mockMvc.perform(put("/api/v1/roles/" + siteAdmin.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"x\",\"permissionCodes\":[\"ROBOT_VIEW\"]}"))
                .andExpect(status().isForbidden());
    }
}
