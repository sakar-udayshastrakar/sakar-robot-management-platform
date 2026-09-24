package com.sakarrobotics.cloud.openplatform;

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

/** Open Platform → Customer registration + Application management. */
class OpenPlatformControllerTest extends IntegrationTestSupport {

    private String tokenWithRobotConfigure(UUID organizationId) throws Exception {
        Role orgAdmin = ensureRole(RoleName.ORG_ADMIN, PermissionCode.ROBOT_VIEW, PermissionCode.ROBOT_CONFIGURE);
        String email = "admin-" + UUID.randomUUID() + "@example.com";
        createUser(email, "Password1!", orgAdmin, organizationId);
        return login(email, "Password1!");
    }

    private String tokenWithRoleManage(UUID organizationId) throws Exception {
        Role reviewer = ensureRole(RoleName.ORG_ADMIN, PermissionCode.ROBOT_VIEW, PermissionCode.ROBOT_CONFIGURE, PermissionCode.ROLE_MANAGE);
        String email = "reviewer-" + UUID.randomUUID() + "@example.com";
        createUser(email, "Password1!", reviewer, organizationId);
        return login(email, "Password1!");
    }

    @Test
    void submitRegistration_thenReviewApprovesIt_neverAutoApproved() throws Exception {
        Organization org = createOrganization("Org " + UUID.randomUUID(), OrganizationType.DIRECT_CLIENT, null);
        String token = tokenWithRobotConfigure(org.getId());

        String body = "{\"organizationId\":\"" + org.getId() + "\",\"companyName\":\"Sakar Robotics\","
                + "\"area\":\"IN\",\"companyAddress\":\"Pune\",\"systemMatcher\":\"Clean\","
                + "\"contactInformation\":\"+919665999862\",\"dockingRequirements\":\"Robot Remote Call test\"}";

        String createResponse = mockMvc.perform(post("/api/v1/open-platform/registration")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.companyName").value("Sakar Robotics"))
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andReturn().getResponse().getContentAsString();
        String registrationId = objectMapper.readTree(createResponse).get("data").get("id").asText();

        mockMvc.perform(get("/api/v1/open-platform/registration").param("organizationId", org.getId().toString())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING"));

        String reviewerToken = tokenWithRoleManage(org.getId());
        mockMvc.perform(post("/api/v1/open-platform/registrations/" + registrationId + "/review")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + reviewerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"APPROVED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPROVED"))
                .andExpect(jsonPath("$.data.reviewedBy").isNotEmpty());
    }

    @Test
    void orgAdmin_cannotReviewTheirOwnRegistration_missingRoleManagePermission() throws Exception {
        Organization org = createOrganization("Org " + UUID.randomUUID(), OrganizationType.DIRECT_CLIENT, null);
        String token = tokenWithRobotConfigure(org.getId());

        String createResponse = mockMvc.perform(post("/api/v1/open-platform/registration")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"organizationId\":\"" + org.getId() + "\",\"companyName\":\"Sakar Robotics\"}"))
                .andReturn().getResponse().getContentAsString();
        String registrationId = objectMapper.readTree(createResponse).get("data").get("id").asText();

        mockMvc.perform(post("/api/v1/open-platform/registrations/" + registrationId + "/review")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"APPROVED\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void createApplication_returnsThePlaintextSecretOnlyOnce_neverAgainFromList() throws Exception {
        Organization org = createOrganization("Org " + UUID.randomUUID(), OrganizationType.DIRECT_CLIENT, null);
        String token = tokenWithRobotConfigure(org.getId());

        String createBody = "{\"organizationId\":\"" + org.getId() + "\",\"applicationName\":\"Sakar Web\",\"businessType\":\"industry\"}";
        String createResponse = mockMvc.perform(post("/api/v1/open-platform/applications")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.application.applicationName").value("Sakar Web"))
                .andExpect(jsonPath("$.data.secretKey").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        String secretKey = objectMapper.readTree(createResponse).get("data").get("secretKey").asText();
        String applicationId = objectMapper.readTree(createResponse).get("data").get("application").get("id").asText();

        mockMvc.perform(get("/api/v1/open-platform/applications").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].secretKeyMasked").value("••••••••" + secretKey.substring(secretKey.length() - 4)));

        mockMvc.perform(put("/api/v1/open-platform/applications/" + applicationId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"applicationName\":\"Sakar Web Renamed\",\"businessType\":\"industry\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.applicationName").value("Sakar Web Renamed"));
    }

    @Test
    void viewerRole_cannotCreateAnApplication_missingRobotConfigurePermission() throws Exception {
        Organization org = createOrganization("Org " + UUID.randomUUID(), OrganizationType.DIRECT_CLIENT, null);
        Role viewer = ensureRole(RoleName.VIEWER, PermissionCode.ROBOT_VIEW);
        String email = "viewer-" + UUID.randomUUID() + "@example.com";
        createUser(email, "Password1!", viewer, org.getId());
        String token = login(email, "Password1!");

        mockMvc.perform(post("/api/v1/open-platform/applications")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"organizationId\":\"" + org.getId() + "\",\"applicationName\":\"Sakar Web\"}"))
                .andExpect(status().isForbidden());
    }
}
