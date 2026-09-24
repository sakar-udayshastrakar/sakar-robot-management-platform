package com.sakarrobotics.cloud.iam;

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
import com.sakarrobotics.cloud.org.Organization;
import com.sakarrobotics.cloud.org.OrganizationType;

/** RBAC / tenant-isolation coverage for the real user administration API (Master Requirements Part 19). */
class UserControllerTest extends IntegrationTestSupport {

    @Test
    void orgAdmin_canCreateAndListAUserWithinOwnOrganization() throws Exception {
        Role orgAdmin = ensureRole(RoleName.ORG_ADMIN, PermissionCode.USER_MANAGE);
        ensureRole(RoleName.VIEWER); // target role for the created user
        Organization org = createOrganization("Org " + UUID.randomUUID(), OrganizationType.DIRECT_CLIENT, null);
        String adminEmail = "admin-" + UUID.randomUUID() + "@example.com";
        createUser(adminEmail, "Password1!", orgAdmin, org.getId());
        String token = login(adminEmail, "Password1!");

        String newEmail = "created-" + UUID.randomUUID() + "@example.com";
        String body = "{\"organizationId\":\"" + org.getId() + "\",\"email\":\"" + newEmail
                + "\",\"password\":\"SomePassword123!\",\"fullName\":\"New User\",\"roleName\":\"VIEWER\"}";

        mockMvc.perform(post("/api/v1/users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.email").value(newEmail))
                .andExpect(jsonPath("$.data.roleName").value("VIEWER"));

        mockMvc.perform(get("/api/v1/users").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[?(@.email=='" + newEmail + "')]").exists());
    }

    @Test
    void orgAdmin_cannotCreateAUserInAnUnrelatedOrganization() throws Exception {
        Role orgAdmin = ensureRole(RoleName.ORG_ADMIN, PermissionCode.USER_MANAGE);
        ensureRole(RoleName.VIEWER);
        Organization orgA = createOrganization("Org A " + UUID.randomUUID(), OrganizationType.DIRECT_CLIENT, null);
        Organization orgB = createOrganization("Org B " + UUID.randomUUID(), OrganizationType.DIRECT_CLIENT, null);
        String adminEmail = "admin-a-" + UUID.randomUUID() + "@example.com";
        createUser(adminEmail, "Password1!", orgAdmin, orgA.getId());
        String token = login(adminEmail, "Password1!");

        String body = "{\"organizationId\":\"" + orgB.getId() + "\",\"email\":\"x-" + UUID.randomUUID()
                + "@example.com\",\"password\":\"SomePassword123!\",\"fullName\":\"X\",\"roleName\":\"VIEWER\"}";

        mockMvc.perform(post("/api/v1/users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("TENANT_ACCESS_DENIED"));
    }

    @Test
    void creatingAUser_withAnAlreadyRegisteredEmail_returnsConflict() throws Exception {
        Role orgAdmin = ensureRole(RoleName.ORG_ADMIN, PermissionCode.USER_MANAGE);
        ensureRole(RoleName.VIEWER);
        Organization org = createOrganization("Org " + UUID.randomUUID(), OrganizationType.DIRECT_CLIENT, null);
        String adminEmail = "admin-" + UUID.randomUUID() + "@example.com";
        createUser(adminEmail, "Password1!", orgAdmin, org.getId());
        String token = login(adminEmail, "Password1!");

        String body = "{\"organizationId\":\"" + org.getId() + "\",\"email\":\"" + adminEmail
                + "\",\"password\":\"SomePassword123!\",\"fullName\":\"Dup\",\"roleName\":\"VIEWER\"}";

        mockMvc.perform(post("/api/v1/users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("DUPLICATE_EMAIL"));
    }

    @Test
    void viewerRole_cannotListUsers_missingUserManagePermission() throws Exception {
        Role viewer = ensureRole(RoleName.VIEWER, PermissionCode.ROBOT_VIEW);
        Organization org = createOrganization("Org " + UUID.randomUUID(), OrganizationType.DIRECT_CLIENT, null);
        String email = "viewer-" + UUID.randomUUID() + "@example.com";
        createUser(email, "Password1!", viewer, org.getId());
        String token = login(email, "Password1!");

        mockMvc.perform(get("/api/v1/users").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    @Test
    void orgAdmin_canSuspendThenReactivateAUser() throws Exception {
        Role orgAdmin = ensureRole(RoleName.ORG_ADMIN, PermissionCode.USER_MANAGE);
        Role viewer = ensureRole(RoleName.VIEWER);
        Organization org = createOrganization("Org " + UUID.randomUUID(), OrganizationType.DIRECT_CLIENT, null);
        String adminEmail = "admin-" + UUID.randomUUID() + "@example.com";
        createUser(adminEmail, "Password1!", orgAdmin, org.getId());
        var target = createUser("target-" + UUID.randomUUID() + "@example.com", "Password1!", viewer, org.getId());
        String token = login(adminEmail, "Password1!");

        mockMvc.perform(post("/api/v1/users/" + target.getId() + "/suspend")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SUSPENDED"));

        mockMvc.perform(post("/api/v1/users/" + target.getId() + "/activate")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));
    }

    @Test
    void creatingAUser_withNoUserType_defaultsToExternal() throws Exception {
        Role orgAdmin = ensureRole(RoleName.ORG_ADMIN, PermissionCode.USER_MANAGE);
        ensureRole(RoleName.VIEWER);
        Organization org = createOrganization("Org " + UUID.randomUUID(), OrganizationType.DIRECT_CLIENT, null);
        String adminEmail = "admin-" + UUID.randomUUID() + "@example.com";
        createUser(adminEmail, "Password1!", orgAdmin, org.getId());
        String token = login(adminEmail, "Password1!");

        String newEmail = "created-" + UUID.randomUUID() + "@example.com";
        String body = "{\"organizationId\":\"" + org.getId() + "\",\"email\":\"" + newEmail
                + "\",\"password\":\"SomePassword123!\",\"fullName\":\"New User\",\"roleName\":\"VIEWER\"}";

        mockMvc.perform(post("/api/v1/users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.userType").value("EXTERNAL"))
                .andExpect(jsonPath("$.data.departmentId").doesNotExist());
    }

    @Test
    void creatingAnInternalUser_withARealDepartment_isAssigned() throws Exception {
        Role orgAdmin = ensureRole(RoleName.ORG_ADMIN, PermissionCode.USER_MANAGE);
        ensureRole(RoleName.VIEWER);
        Organization org = createOrganization("Org " + UUID.randomUUID(), OrganizationType.DIRECT_CLIENT, null);
        String adminEmail = "admin-" + UUID.randomUUID() + "@example.com";
        createUser(adminEmail, "Password1!", orgAdmin, org.getId());
        String token = login(adminEmail, "Password1!");

        String departmentName = "Engineering-" + UUID.randomUUID();
        String deptResponse = mockMvc.perform(post("/api/v1/departments")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + departmentName + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String departmentId = objectMapper.readTree(deptResponse).get("data").get("id").asText();

        String newEmail = "staff-" + UUID.randomUUID() + "@example.com";
        String body = "{\"organizationId\":\"" + org.getId() + "\",\"email\":\"" + newEmail
                + "\",\"password\":\"SomePassword123!\",\"fullName\":\"Internal Staff\",\"roleName\":\"VIEWER\","
                + "\"userType\":\"INTERNAL\",\"departmentId\":\"" + departmentId + "\"}";

        String createResponse = mockMvc.perform(post("/api/v1/users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.userType").value("INTERNAL"))
                .andExpect(jsonPath("$.data.departmentId").value(departmentId))
                .andExpect(jsonPath("$.data.departmentName").value(departmentName))
                .andReturn().getResponse().getContentAsString();
        String userId = objectMapper.readTree(createResponse).get("data").get("id").asText();

        mockMvc.perform(get("/api/v1/users?userType=INTERNAL").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[?(@.id=='" + userId + "')]").exists());

        mockMvc.perform(get("/api/v1/users?userType=EXTERNAL").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[?(@.id=='" + userId + "')]").doesNotExist());
    }

    @Test
    void assigningADepartment_toAnExternalUser_isRejected() throws Exception {
        Role orgAdmin = ensureRole(RoleName.ORG_ADMIN, PermissionCode.USER_MANAGE);
        ensureRole(RoleName.VIEWER);
        Organization org = createOrganization("Org " + UUID.randomUUID(), OrganizationType.DIRECT_CLIENT, null);
        String adminEmail = "admin-" + UUID.randomUUID() + "@example.com";
        createUser(adminEmail, "Password1!", orgAdmin, org.getId());
        String token = login(adminEmail, "Password1!");

        String deptResponse = mockMvc.perform(post("/api/v1/departments")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Support-" + UUID.randomUUID() + "\"}"))
                .andReturn().getResponse().getContentAsString();
        String departmentId = objectMapper.readTree(deptResponse).get("data").get("id").asText();

        String body = "{\"organizationId\":\"" + org.getId() + "\",\"email\":\"x-" + UUID.randomUUID()
                + "@example.com\",\"password\":\"SomePassword123!\",\"fullName\":\"X\",\"roleName\":\"VIEWER\","
                + "\"userType\":\"EXTERNAL\",\"departmentId\":\"" + departmentId + "\"}";

        mockMvc.perform(post("/api/v1/users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
    }

    @Test
    void orgAdmin_canEditAUsersFullNameAndDepartment() throws Exception {
        Role orgAdmin = ensureRole(RoleName.ORG_ADMIN, PermissionCode.USER_MANAGE);
        Role viewer = ensureRole(RoleName.VIEWER);
        Organization org = createOrganization("Org " + UUID.randomUUID(), OrganizationType.DIRECT_CLIENT, null);
        String adminEmail = "admin-" + UUID.randomUUID() + "@example.com";
        createUser(adminEmail, "Password1!", orgAdmin, org.getId());
        User target = createUser("target-" + UUID.randomUUID() + "@example.com", "Password1!", viewer, org.getId());
        target.setUserType(UserType.INTERNAL);
        userRepository.save(target);
        String token = login(adminEmail, "Password1!");

        String deptResponse = mockMvc.perform(post("/api/v1/departments")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Field-" + UUID.randomUUID() + "\"}"))
                .andReturn().getResponse().getContentAsString();
        String departmentId = objectMapper.readTree(deptResponse).get("data").get("id").asText();

        mockMvc.perform(put("/api/v1/users/" + target.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"Renamed User\",\"departmentId\":\"" + departmentId + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fullName").value("Renamed User"))
                .andExpect(jsonPath("$.data.departmentId").value(departmentId));
    }
}
