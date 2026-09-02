package com.sakarrobotics.cloud.telemetry;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
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
import com.sakarrobotics.cloud.robot.registry.AdapterType;
import com.sakarrobotics.cloud.robot.registry.IntegrationPath;
import com.sakarrobotics.cloud.robot.registry.Robot;
import com.sakarrobotics.cloud.robot.registry.RobotLifecycleStatus;
import com.sakarrobotics.cloud.robot.registry.RobotManufacturer;
import com.sakarrobotics.cloud.robot.registry.RobotManufacturerRepository;
import com.sakarrobotics.cloud.robot.registry.RobotModel;
import com.sakarrobotics.cloud.robot.registry.RobotModelRepository;
import com.sakarrobotics.cloud.robot.registry.RobotRepository;

/**
 * Success/empty/tenant-isolation/authorization coverage for the new
 * read-only telemetry-history endpoint (Roadmap Phase 9 web-platform gap
 * analysis, "existing data -> real web UI", STEP 2).
 */
class TelemetryControllerTest extends IntegrationTestSupport {

    @Autowired
    private RobotManufacturerRepository manufacturerRepository;
    @Autowired
    private RobotModelRepository modelRepository;
    @Autowired
    private RobotRepository robotRepository;
    @Autowired
    private RobotTelemetryRepository robotTelemetryRepository;

    @Test
    void telemetryExists_returnsItMostRecentFirst() throws Exception {
        Role orgAdmin = ensureRole(RoleName.ORG_ADMIN, PermissionCode.ROBOT_LOG_VIEW);
        Organization org = createOrganization("Org " + UUID.randomUUID(), OrganizationType.DIRECT_CLIENT, null);
        String email = "admin-" + UUID.randomUUID() + "@example.com";
        createUser(email, "Password1!", orgAdmin, org.getId());
        Robot robot = registerRobot(org.getId());

        saveTelemetry(robot.getId(), "battery_percent", 42.0, Instant.parse("2026-01-01T00:00:00Z"));
        saveTelemetry(robot.getId(), "battery_percent", 55.0, Instant.parse("2026-01-02T00:00:00Z"));

        String token = login(email, "Password1!");

        mockMvc.perform(get("/api/v1/robots/" + robot.getId() + "/telemetry")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(2))
                .andExpect(jsonPath("$.data.content[0].valueNumeric").value(55.0))
                .andExpect(jsonPath("$.data.content[1].valueNumeric").value(42.0));
    }

    @Test
    void noTelemetryIngestedYet_returnsAnEmptyPage_notFabricatedData() throws Exception {
        Role orgAdmin = ensureRole(RoleName.ORG_ADMIN, PermissionCode.ROBOT_LOG_VIEW);
        Organization org = createOrganization("Org " + UUID.randomUUID(), OrganizationType.DIRECT_CLIENT, null);
        String email = "admin-empty-" + UUID.randomUUID() + "@example.com";
        createUser(email, "Password1!", orgAdmin, org.getId());
        Robot robot = registerRobot(org.getId());

        String token = login(email, "Password1!");

        mockMvc.perform(get("/api/v1/robots/" + robot.getId() + "/telemetry")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(0))
                .andExpect(jsonPath("$.data.totalElements").value(0));
    }

    @Test
    void unknownRobotId_returnsNotFound() throws Exception {
        Role orgAdmin = ensureRole(RoleName.ORG_ADMIN, PermissionCode.ROBOT_LOG_VIEW);
        Organization org = createOrganization("Org " + UUID.randomUUID(), OrganizationType.DIRECT_CLIENT, null);
        String email = "admin-missing-" + UUID.randomUUID() + "@example.com";
        createUser(email, "Password1!", orgAdmin, org.getId());
        String token = login(email, "Password1!");

        mockMvc.perform(get("/api/v1/robots/" + UUID.randomUUID() + "/telemetry")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("ROBOT_NOT_FOUND"));
    }

    @Test
    void robotBelongsToAnUnrelatedOrganization_returnsNotFoundNotForbidden() throws Exception {
        Role orgAdmin = ensureRole(RoleName.ORG_ADMIN, PermissionCode.ROBOT_LOG_VIEW);
        Organization orgA = createOrganization("Org A " + UUID.randomUUID(), OrganizationType.DIRECT_CLIENT, null);
        Organization orgB = createOrganization("Org B " + UUID.randomUUID(), OrganizationType.DIRECT_CLIENT, null);
        String emailA = "admin-a-" + UUID.randomUUID() + "@example.com";
        createUser(emailA, "Password1!", orgAdmin, orgA.getId());
        Robot robotInOrgB = registerRobot(orgB.getId());
        saveTelemetry(robotInOrgB.getId(), "battery_percent", 90.0, Instant.now());

        String token = login(emailA, "Password1!");

        mockMvc.perform(get("/api/v1/robots/" + robotInOrgB.getId() + "/telemetry")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("ROBOT_NOT_FOUND"));
    }

    @Test
    void roleWithoutRobotLogViewPermission_isForbidden() throws Exception {
        Role limited = ensureRole(RoleName.OPERATOR, PermissionCode.ROBOT_CONTROL); // deliberately no ROBOT_LOG_VIEW
        Organization org = createOrganization("Org " + UUID.randomUUID(), OrganizationType.DIRECT_CLIENT, null);
        String email = "operator-" + UUID.randomUUID() + "@example.com";
        createUser(email, "Password1!", limited, org.getId());
        Robot robot = registerRobot(org.getId());
        String token = login(email, "Password1!");

        mockMvc.perform(get("/api/v1/robots/" + robot.getId() + "/telemetry")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    private void saveTelemetry(UUID robotId, String metric, double value, Instant recordedAt) {
        RobotTelemetry entity = new RobotTelemetry();
        entity.setRobotId(robotId);
        entity.setMetric(metric);
        entity.setValueNumeric(value);
        entity.setRecordedAt(recordedAt);
        robotTelemetryRepository.save(entity);
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
        robot.setStatus(RobotLifecycleStatus.REGISTERED);
        return robotRepository.save(robot);
    }
}
