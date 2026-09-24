package com.sakarrobotics.cloud.iam;

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
import com.sakarrobotics.cloud.org.Organization;
import com.sakarrobotics.cloud.org.OrganizationType;

/** CRUD coverage for the flat department list (Account Permission Platform, Phase 1). */
class DepartmentControllerTest extends IntegrationTestSupport {

    private String tokenWithUserManage() throws Exception {
        Role orgAdmin = ensureRole(RoleName.ORG_ADMIN, PermissionCode.USER_MANAGE);
        Organization org = createOrganization("Org " + UUID.randomUUID(), OrganizationType.DIRECT_CLIENT, null);
        String email = "admin-" + UUID.randomUUID() + "@example.com";
        createUser(email, "Password1!", orgAdmin, org.getId());
        return login(email, "Password1!");
    }

    @Test
    void create_thenList_thenRename_thenDelete() throws Exception {
        String token = tokenWithUserManage();
        String name = "Engineering-" + UUID.randomUUID();

        String createResponse = mockMvc.perform(post("/api/v1/departments")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.name").value(name))
                .andReturn().getResponse().getContentAsString();
        String id = objectMapper.readTree(createResponse).get("data").get("id").asText();

        mockMvc.perform(get("/api/v1/departments").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.name=='" + name + "')]").exists());

        String renamed = name + "-renamed";
        mockMvc.perform(put("/api/v1/departments/" + id)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + renamed + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value(renamed));

        mockMvc.perform(delete("/api/v1/departments/" + id).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/departments").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.name=='" + renamed + "')]").doesNotExist());
    }

    @Test
    void creatingADepartment_withADuplicateName_returnsConflict() throws Exception {
        String token = tokenWithUserManage();
        String name = "Support-" + UUID.randomUUID();

        mockMvc.perform(post("/api/v1/departments")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/departments")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("DUPLICATE_DEPARTMENT_NAME"));
    }

    @Test
    void deletingADepartment_stillAssignedToAUser_isRejected() throws Exception {
        String token = tokenWithUserManage();
        Role viewer = ensureRole(RoleName.VIEWER);
        Organization org = createOrganization("Org " + UUID.randomUUID(), OrganizationType.DIRECT_CLIENT, null);

        String createResponse = mockMvc.perform(post("/api/v1/departments")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Ops-" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID departmentId = UUID.fromString(objectMapper.readTree(createResponse).get("data").get("id").asText());

        User staff = createUser("staff-" + UUID.randomUUID() + "@example.com", "Password1!", viewer, org.getId());
        staff.setUserType(UserType.INTERNAL);
        staff.setDepartmentId(departmentId);
        userRepository.save(staff);

        mockMvc.perform(delete("/api/v1/departments/" + departmentId).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("DEPARTMENT_IN_USE"));
    }

    @Test
    void viewerRole_cannotManageDepartments_missingUserManagePermission() throws Exception {
        Role viewer = ensureRole(RoleName.VIEWER, PermissionCode.ROBOT_VIEW);
        Organization org = createOrganization("Org " + UUID.randomUUID(), OrganizationType.DIRECT_CLIENT, null);
        String email = "viewer-" + UUID.randomUUID() + "@example.com";
        createUser(email, "Password1!", viewer, org.getId());
        String token = login(email, "Password1!");

        mockMvc.perform(get("/api/v1/departments").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden());
    }
}
