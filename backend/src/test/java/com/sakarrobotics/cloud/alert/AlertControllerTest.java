package com.sakarrobotics.cloud.alert;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;

import com.sakarrobotics.cloud.IntegrationTestSupport;
import com.sakarrobotics.cloud.iam.PermissionCode;
import com.sakarrobotics.cloud.iam.Role;
import com.sakarrobotics.cloud.iam.RoleName;
import com.sakarrobotics.cloud.org.Organization;
import com.sakarrobotics.cloud.org.OrganizationType;

class AlertControllerTest extends IntegrationTestSupport {

    @Autowired
    private RobotAlertRepository robotAlertRepository;

    @Test
    void orgAdmin_canAcknowledgeThenResolveAnAlertInOwnOrganization() throws Exception {
        Role orgAdmin = ensureRole(RoleName.ORG_ADMIN, PermissionCode.ROBOT_VIEW, PermissionCode.ROBOT_CONTROL);
        Organization org = createOrganization("Org " + UUID.randomUUID(), OrganizationType.DIRECT_CLIENT, null);
        String email = "admin-" + UUID.randomUUID() + "@example.com";
        createUser(email, "Password1!", orgAdmin, org.getId());
        String token = login(email, "Password1!");

        RobotAlert alert = openAlert(org.getId());

        mockMvc.perform(post("/api/v1/alerts/" + alert.getId() + "/acknowledge")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACKNOWLEDGED"))
                .andExpect(jsonPath("$.data.acknowledgedBy").exists());

        mockMvc.perform(post("/api/v1/alerts/" + alert.getId() + "/resolve")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("RESOLVED"));
    }

    @Test
    void user_cannotAcknowledgeAnAlertBelongingToAnUnrelatedOrganization_getsNotFoundNotForbidden() throws Exception {
        Role orgAdmin = ensureRole(RoleName.ORG_ADMIN, PermissionCode.ROBOT_VIEW, PermissionCode.ROBOT_CONTROL);
        Organization orgA = createOrganization("Org A " + UUID.randomUUID(), OrganizationType.DIRECT_CLIENT, null);
        Organization orgB = createOrganization("Org B " + UUID.randomUUID(), OrganizationType.DIRECT_CLIENT, null);
        String emailA = "admin-a-" + UUID.randomUUID() + "@example.com";
        createUser(emailA, "Password1!", orgAdmin, orgA.getId());
        String tokenA = login(emailA, "Password1!");

        RobotAlert alertInOrgB = openAlert(orgB.getId());

        mockMvc.perform(post("/api/v1/alerts/" + alertInOrgB.getId() + "/acknowledge")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("ALERT_NOT_FOUND"));
    }

    @Test
    void orgAdmin_listsOnlyAlertsWithinOwnOrganizationScope() throws Exception {
        Role orgAdmin = ensureRole(RoleName.ORG_ADMIN, PermissionCode.ROBOT_VIEW);
        Organization orgA = createOrganization("Org A " + UUID.randomUUID(), OrganizationType.DIRECT_CLIENT, null);
        Organization orgB = createOrganization("Org B " + UUID.randomUUID(), OrganizationType.DIRECT_CLIENT, null);
        String emailA = "admin-a-" + UUID.randomUUID() + "@example.com";
        createUser(emailA, "Password1!", orgAdmin, orgA.getId());
        String tokenA = login(emailA, "Password1!");

        openAlert(orgA.getId());
        RobotAlert alertInOrgB = openAlert(orgB.getId());

        mockMvc.perform(get("/api/v1/alerts").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[?(@.id=='" + alertInOrgB.getId() + "')]").doesNotExist());
    }

    private RobotAlert openAlert(UUID organizationId) {
        RobotAlert alert = new RobotAlert();
        alert.setRobotId(UUID.randomUUID());
        alert.setOrganizationId(organizationId);
        alert.setAlertType(AlertType.LOW_BATTERY.name());
        alert.setSeverity(AlertSeverity.MEDIUM.name());
        alert.setMessage("Battery at 15%");
        alert.setStatus(AlertStatus.OPEN.name());
        return robotAlertRepository.save(alert);
    }
}
