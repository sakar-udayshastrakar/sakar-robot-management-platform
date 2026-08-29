package com.sakarrobotics.cloud.robot.registry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;

import com.sakarrobotics.cloud.IntegrationTestSupport;
import com.sakarrobotics.cloud.audit.AuditLogRepository;
import com.sakarrobotics.cloud.iam.PermissionCode;
import com.sakarrobotics.cloud.iam.Role;
import com.sakarrobotics.cloud.iam.RoleName;
import com.sakarrobotics.cloud.org.Organization;
import com.sakarrobotics.cloud.org.OrganizationType;

/** Phase 3 Part 4/5 — robot MQTT credential provisioning/rotation. */
class RobotCredentialControllerTest extends IntegrationTestSupport {

    @Autowired
    private RobotManufacturerRepository manufacturerRepository;
    @Autowired
    private RobotModelRepository modelRepository;
    @Autowired
    private RobotRepository robotRepository;
    @Autowired
    private RobotCredentialRepository robotCredentialRepository;
    @Autowired
    private AuditLogRepository auditLogRepository;

    @Test
    void provisioning_returnsARawSecretOnceAndStoresOnlyItsHash() throws Exception {
        Role orgAdmin = ensureRole(RoleName.ORG_ADMIN, PermissionCode.ROBOT_VIEW, PermissionCode.ROBOT_CONFIGURE);
        Organization org = createOrganization("Org " + UUID.randomUUID(), OrganizationType.DIRECT_CLIENT, null);
        String email = "orgadmin-cred-" + UUID.randomUUID() + "@example.com";
        createUser(email, "Password1!", orgAdmin, org.getId());
        Robot robot = registerRobot(org.getId());
        String token = login(email, "Password1!");

        String response = mockMvc.perform(post("/api/v1/robots/" + robot.getId() + "/mqtt-credentials")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.robotId").value(robot.getId().toString()))
                .andExpect(jsonPath("$.data.mqttUsername").value(robot.getId().toString()))
                .andExpect(jsonPath("$.data.mqttPassword").exists())
                .andReturn().getResponse().getContentAsString();

        String rawSecret = objectMapper.readTree(response).get("data").get("mqttPassword").asText();
        assertThat(rawSecret).isNotBlank();
        assertThat(robotCredentialRepository.findByRobotId(robot.getId())).hasValueSatisfying(credential -> {
            assertThat(credential.getCredentialType()).isEqualTo("MQTT");
            assertThat(credential.getCredentialValueHash()).isNotEqualTo(rawSecret); // never stored in plaintext
            assertThat(passwordEncoder.matches(rawSecret, credential.getCredentialValueHash())).isTrue();
        });
    }

    @Test
    void rotating_issuesADifferentSecretAndInvalidatesTheOldHash() throws Exception {
        Role orgAdmin = ensureRole(RoleName.ORG_ADMIN, PermissionCode.ROBOT_VIEW, PermissionCode.ROBOT_CONFIGURE);
        Organization org = createOrganization("Org " + UUID.randomUUID(), OrganizationType.DIRECT_CLIENT, null);
        String email = "orgadmin-rotate-" + UUID.randomUUID() + "@example.com";
        createUser(email, "Password1!", orgAdmin, org.getId());
        Robot robot = registerRobot(org.getId());
        String token = login(email, "Password1!");

        String first = mockMvc.perform(post("/api/v1/robots/" + robot.getId() + "/mqtt-credentials")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String second = mockMvc.perform(post("/api/v1/robots/" + robot.getId() + "/mqtt-credentials")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        String firstSecret = objectMapper.readTree(first).get("data").get("mqttPassword").asText();
        String secondSecret = objectMapper.readTree(second).get("data").get("mqttPassword").asText();
        assertThat(firstSecret).isNotEqualTo(secondSecret);

        var credential = robotCredentialRepository.findByRobotId(robot.getId()).orElseThrow();
        assertThat(passwordEncoder.matches(firstSecret, credential.getCredentialValueHash())).isFalse();
        assertThat(passwordEncoder.matches(secondSecret, credential.getCredentialValueHash())).isTrue();
        assertThat(credential.getRotatedAt()).isNotNull();
    }

    @Test
    void viewerRole_cannotProvisionCredentials_missingRobotConfigurePermission() throws Exception {
        Role viewer = ensureRole(RoleName.VIEWER, PermissionCode.ROBOT_VIEW);
        Organization org = createOrganization("Org " + UUID.randomUUID(), OrganizationType.DIRECT_CLIENT, null);
        String email = "viewer-cred-" + UUID.randomUUID() + "@example.com";
        createUser(email, "Password1!", viewer, org.getId());
        Robot robot = registerRobot(org.getId());
        String token = login(email, "Password1!");

        mockMvc.perform(post("/api/v1/robots/" + robot.getId() + "/mqtt-credentials")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void cannotProvisionCredentialsForARobotInAnUnrelatedOrganization() throws Exception {
        Role orgAdmin = ensureRole(RoleName.ORG_ADMIN, PermissionCode.ROBOT_VIEW, PermissionCode.ROBOT_CONFIGURE);
        Organization orgA = createOrganization("Org A " + UUID.randomUUID(), OrganizationType.DIRECT_CLIENT, null);
        Organization orgB = createOrganization("Org B " + UUID.randomUUID(), OrganizationType.DIRECT_CLIENT, null);
        String email = "orgadmin-a-cred-" + UUID.randomUUID() + "@example.com";
        createUser(email, "Password1!", orgAdmin, orgA.getId());
        Robot robotInOrgB = registerRobot(orgB.getId());
        String token = login(email, "Password1!");

        mockMvc.perform(post("/api/v1/robots/" + robotInOrgB.getId() + "/mqtt-credentials")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("ROBOT_NOT_FOUND"));
    }

    @Test
    void provisioning_isAuditLoggedWithoutTheRawSecretAnywhereInTheAuditTrail() throws Exception {
        Role orgAdmin = ensureRole(RoleName.ORG_ADMIN, PermissionCode.ROBOT_VIEW, PermissionCode.ROBOT_CONFIGURE);
        Organization org = createOrganization("Org " + UUID.randomUUID(), OrganizationType.DIRECT_CLIENT, null);
        String email = "orgadmin-audit-" + UUID.randomUUID() + "@example.com";
        createUser(email, "Password1!", orgAdmin, org.getId());
        Robot robot = registerRobot(org.getId());
        String token = login(email, "Password1!");

        String response = mockMvc.perform(post("/api/v1/robots/" + robot.getId() + "/mqtt-credentials")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String rawSecret = objectMapper.readTree(response).get("data").get("mqttPassword").asText();

        assertThat(auditLogRepository.findAll())
                .anyMatch(entry -> "MQTT_CREDENTIAL_PROVISIONED".equals(entry.getAction()) && robot.getId().equals(entry.getRobotId()));
        // The raw secret must never appear anywhere in the audit trail - AuditService.record() has no
        // parameter for it at all, but this asserts the actual persisted rows, not just the API shape.
        assertThat(auditLogRepository.findAll())
                .noneMatch(entry -> containsSecret(entry.getAction(), rawSecret) || containsSecret(entry.getReason(), rawSecret)
                        || containsSecret(entry.getDevice(), rawSecret) || containsSecret(entry.getIpAddress(), rawSecret));
    }

    @Test
    void revoking_deletesTheStoredCredentialAndIsAuditLogged() throws Exception {
        Role orgAdmin = ensureRole(RoleName.ORG_ADMIN, PermissionCode.ROBOT_VIEW, PermissionCode.ROBOT_CONFIGURE);
        Organization org = createOrganization("Org " + UUID.randomUUID(), OrganizationType.DIRECT_CLIENT, null);
        String email = "orgadmin-revoke-" + UUID.randomUUID() + "@example.com";
        createUser(email, "Password1!", orgAdmin, org.getId());
        Robot robot = registerRobot(org.getId());
        String token = login(email, "Password1!");

        mockMvc.perform(post("/api/v1/robots/" + robot.getId() + "/mqtt-credentials")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());
        assertThat(robotCredentialRepository.findByRobotId(robot.getId())).isPresent();

        mockMvc.perform(delete("/api/v1/robots/" + robot.getId() + "/mqtt-credentials")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());

        assertThat(robotCredentialRepository.findByRobotId(robot.getId())).isEmpty();
        assertThat(auditLogRepository.findAll())
                .anyMatch(entry -> "MQTT_CREDENTIAL_REVOKED".equals(entry.getAction()) && robot.getId().equals(entry.getRobotId()));
    }

    @Test
    void revoking_withoutRobotConfigurePermission_isForbidden() throws Exception {
        Role viewer = ensureRole(RoleName.VIEWER, PermissionCode.ROBOT_VIEW);
        Organization org = createOrganization("Org " + UUID.randomUUID(), OrganizationType.DIRECT_CLIENT, null);
        String email = "viewer-revoke-" + UUID.randomUUID() + "@example.com";
        createUser(email, "Password1!", viewer, org.getId());
        Robot robot = registerRobot(org.getId());
        String token = login(email, "Password1!");

        mockMvc.perform(delete("/api/v1/robots/" + robot.getId() + "/mqtt-credentials")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    private static boolean containsSecret(String field, String rawSecret) {
        return field != null && field.contains(rawSecret);
    }

    private Robot registerRobot(UUID organizationId) {
        RobotManufacturer manufacturer = manufacturerRepository.save(new RobotManufacturer("TestVendor-" + UUID.randomUUID()));
        RobotModel model = new RobotModel();
        model.setManufacturerId(manufacturer.getId());
        model.setName("Test Model");
        model.setAdapterType(AdapterType.SAKAR_NATIVE);
        model.setIntegrationPath(IntegrationPath.SAKAR_OWNED_LOCAL);
        model = modelRepository.save(model);

        Robot robot = new Robot();
        robot.setOrganizationId(organizationId);
        robot.setRobotModelId(model.getId());
        robot.setName("Robot " + UUID.randomUUID());
        robot.setSerialNumber("SN-" + UUID.randomUUID());
        robot.setStatus(RobotLifecycleStatus.ACTIVE);
        return robotRepository.save(robot);
    }
}
