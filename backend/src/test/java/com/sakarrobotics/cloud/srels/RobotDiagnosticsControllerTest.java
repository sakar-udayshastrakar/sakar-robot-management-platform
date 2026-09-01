package com.sakarrobotics.cloud.srels;

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
 * read-only events/errors/application-logs history endpoints (Roadmap
 * Phase 9 web-platform gap analysis, "existing data -> real web UI", STEP 2).
 */
class RobotDiagnosticsControllerTest extends IntegrationTestSupport {

    @Autowired
    private RobotManufacturerRepository manufacturerRepository;
    @Autowired
    private RobotModelRepository modelRepository;
    @Autowired
    private RobotRepository robotRepository;
    @Autowired
    private RobotEventRepository robotEventRepository;
    @Autowired
    private RobotErrorRepository robotErrorRepository;
    @Autowired
    private ApplicationLogRepository applicationLogRepository;

    // ---------------------------------------------------------------
    // Events
    // ---------------------------------------------------------------

    @Test
    void events_exist_returnedMostRecentFirst() throws Exception {
        String token = adminToken();
        Robot robot = registerRobot(soleOrg);
        saveEvent(robot.getId(), "COMMAND_RESULT", "INFO", Instant.parse("2026-01-01T00:00:00Z"));
        saveEvent(robot.getId(), "COMMAND_RESULT", "ERROR", Instant.parse("2026-01-02T00:00:00Z"));

        mockMvc.perform(get("/api/v1/robots/" + robot.getId() + "/events")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(2))
                .andExpect(jsonPath("$.data.content[0].severity").value("ERROR"))
                .andExpect(jsonPath("$.data.content[1].severity").value("INFO"));
    }

    @Test
    void events_noneIngestedYet_returnsEmptyPage() throws Exception {
        String token = adminToken();
        Robot robot = registerRobot(soleOrg);

        mockMvc.perform(get("/api/v1/robots/" + robot.getId() + "/events")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(0));
    }

    @Test
    void events_unknownRobot_returnsNotFound() throws Exception {
        String token = adminToken();

        mockMvc.perform(get("/api/v1/robots/" + UUID.randomUUID() + "/events")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("ROBOT_NOT_FOUND"));
    }

    @Test
    void events_robotInUnrelatedOrganization_returnsNotFoundNotForbidden() throws Exception {
        Role orgAdmin = ensureRole(RoleName.ORG_ADMIN, PermissionCode.ROBOT_LOG_VIEW);
        Organization orgA = createOrganization("Org A " + UUID.randomUUID(), OrganizationType.DIRECT_CLIENT, null);
        Organization orgB = createOrganization("Org B " + UUID.randomUUID(), OrganizationType.DIRECT_CLIENT, null);
        String emailA = "admin-a-" + UUID.randomUUID() + "@example.com";
        createUser(emailA, "Password1!", orgAdmin, orgA.getId());
        Robot robotInOrgB = registerRobot(orgB);
        saveEvent(robotInOrgB.getId(), "COMMAND_RESULT", "INFO", Instant.now());
        String token = login(emailA, "Password1!");

        mockMvc.perform(get("/api/v1/robots/" + robotInOrgB.getId() + "/events")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("ROBOT_NOT_FOUND"));
    }

    @Test
    void events_roleWithoutLogViewPermission_isForbidden() throws Exception {
        Role limited = ensureRole(RoleName.OPERATOR, PermissionCode.ROBOT_CONTROL);
        Organization org = createOrganization("Org " + UUID.randomUUID(), OrganizationType.DIRECT_CLIENT, null);
        String email = "operator-" + UUID.randomUUID() + "@example.com";
        createUser(email, "Password1!", limited, org.getId());
        Robot robot = registerRobot(org);
        String token = login(email, "Password1!");

        mockMvc.perform(get("/api/v1/robots/" + robot.getId() + "/events")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    // ---------------------------------------------------------------
    // Errors
    // ---------------------------------------------------------------

    @Test
    void errors_exist_returnedMostRecentFirst() throws Exception {
        String token = adminToken();
        Robot robot = registerRobot(soleOrg);
        saveError(robot.getId(), "E-1001", Instant.parse("2026-01-01T00:00:00Z"));
        saveError(robot.getId(), "E-1002", Instant.parse("2026-01-02T00:00:00Z"));

        mockMvc.perform(get("/api/v1/robots/" + robot.getId() + "/errors")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(2))
                .andExpect(jsonPath("$.data.content[0].errorCode").value("E-1002"))
                .andExpect(jsonPath("$.data.content[1].errorCode").value("E-1001"));
    }

    @Test
    void errors_noneIngestedYet_returnsEmptyPage() throws Exception {
        String token = adminToken();
        Robot robot = registerRobot(soleOrg);

        mockMvc.perform(get("/api/v1/robots/" + robot.getId() + "/errors")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(0));
    }

    @Test
    void errors_unknownRobot_returnsNotFound() throws Exception {
        String token = adminToken();

        mockMvc.perform(get("/api/v1/robots/" + UUID.randomUUID() + "/errors")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("ROBOT_NOT_FOUND"));
    }

    @Test
    void errors_robotInUnrelatedOrganization_returnsNotFoundNotForbidden() throws Exception {
        Role orgAdmin = ensureRole(RoleName.ORG_ADMIN, PermissionCode.ROBOT_LOG_VIEW);
        Organization orgA = createOrganization("Org A " + UUID.randomUUID(), OrganizationType.DIRECT_CLIENT, null);
        Organization orgB = createOrganization("Org B " + UUID.randomUUID(), OrganizationType.DIRECT_CLIENT, null);
        String emailA = "admin-a-" + UUID.randomUUID() + "@example.com";
        createUser(emailA, "Password1!", orgAdmin, orgA.getId());
        Robot robotInOrgB = registerRobot(orgB);
        saveError(robotInOrgB.getId(), "E-1001", Instant.now());
        String token = login(emailA, "Password1!");

        mockMvc.perform(get("/api/v1/robots/" + robotInOrgB.getId() + "/errors")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("ROBOT_NOT_FOUND"));
    }

    @Test
    void errors_roleWithoutLogViewPermission_isForbidden() throws Exception {
        Role limited = ensureRole(RoleName.OPERATOR, PermissionCode.ROBOT_CONTROL);
        Organization org = createOrganization("Org " + UUID.randomUUID(), OrganizationType.DIRECT_CLIENT, null);
        String email = "operator-errors-" + UUID.randomUUID() + "@example.com";
        createUser(email, "Password1!", limited, org.getId());
        Robot robot = registerRobot(org);
        String token = login(email, "Password1!");

        mockMvc.perform(get("/api/v1/robots/" + robot.getId() + "/errors")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    // ---------------------------------------------------------------
    // Application logs
    // ---------------------------------------------------------------

    @Test
    void logs_exist_returnedMostRecentFirst() throws Exception {
        String token = adminToken();
        Robot robot = registerRobot(soleOrg);
        saveLog(robot.getId(), "INFO", "first");
        // application_logs has no caller-settable timestamp (created_at is an auto @CreationTimestamp) -
        // a short real gap is needed so the two rows land in a deterministic order on platforms with
        // coarser Instant.now() resolution, unlike the telemetry/event/error tests above which pass an
        // explicit recordedAt/occurredAt.
        Thread.sleep(20);
        saveLog(robot.getId(), "WARN", "second");

        mockMvc.perform(get("/api/v1/robots/" + robot.getId() + "/logs")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(2))
                .andExpect(jsonPath("$.data.content[0].message").value("second"))
                .andExpect(jsonPath("$.data.content[1].message").value("first"));
    }

    @Test
    void logs_noneIngestedYet_returnsEmptyPage() throws Exception {
        String token = adminToken();
        Robot robot = registerRobot(soleOrg);

        mockMvc.perform(get("/api/v1/robots/" + robot.getId() + "/logs")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(0));
    }

    @Test
    void logs_unknownRobot_returnsNotFound() throws Exception {
        String token = adminToken();

        mockMvc.perform(get("/api/v1/robots/" + UUID.randomUUID() + "/logs")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("ROBOT_NOT_FOUND"));
    }

    @Test
    void logs_robotInUnrelatedOrganization_returnsNotFoundNotForbidden() throws Exception {
        Role orgAdmin = ensureRole(RoleName.ORG_ADMIN, PermissionCode.ROBOT_LOG_VIEW);
        Organization orgA = createOrganization("Org A " + UUID.randomUUID(), OrganizationType.DIRECT_CLIENT, null);
        Organization orgB = createOrganization("Org B " + UUID.randomUUID(), OrganizationType.DIRECT_CLIENT, null);
        String emailA = "admin-a-logs-" + UUID.randomUUID() + "@example.com";
        createUser(emailA, "Password1!", orgAdmin, orgA.getId());
        Robot robotInOrgB = registerRobot(orgB);
        saveLog(robotInOrgB.getId(), "INFO", "secret");
        String token = login(emailA, "Password1!");

        mockMvc.perform(get("/api/v1/robots/" + robotInOrgB.getId() + "/logs")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("ROBOT_NOT_FOUND"));
    }

    @Test
    void logs_roleWithoutLogViewPermission_isForbidden() throws Exception {
        Role limited = ensureRole(RoleName.OPERATOR, PermissionCode.ROBOT_CONTROL);
        Organization org = createOrganization("Org " + UUID.randomUUID(), OrganizationType.DIRECT_CLIENT, null);
        String email = "operator-logs-" + UUID.randomUUID() + "@example.com";
        createUser(email, "Password1!", limited, org.getId());
        Robot robot = registerRobot(org);
        String token = login(email, "Password1!");

        mockMvc.perform(get("/api/v1/robots/" + robot.getId() + "/logs")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    // ---------------------------------------------------------------

    private Organization soleOrg;

    private String adminToken() throws Exception {
        Role orgAdmin = ensureRole(RoleName.ORG_ADMIN, PermissionCode.ROBOT_LOG_VIEW);
        soleOrg = createOrganization("Org " + UUID.randomUUID(), OrganizationType.DIRECT_CLIENT, null);
        String email = "admin-" + UUID.randomUUID() + "@example.com";
        createUser(email, "Password1!", orgAdmin, soleOrg.getId());
        return login(email, "Password1!");
    }

    private void saveEvent(UUID robotId, String eventType, String severity, Instant occurredAt) {
        RobotEvent event = new RobotEvent();
        event.setRobotId(robotId);
        event.setEventType(eventType);
        event.setSeverity(severity);
        event.setOccurredAt(occurredAt);
        robotEventRepository.save(event);
    }

    private void saveError(UUID robotId, String errorCode, Instant occurredAt) {
        RobotError error = new RobotError();
        error.setRobotId(robotId);
        error.setErrorCode(errorCode);
        error.setSeverity("ERROR");
        error.setOccurredAt(occurredAt);
        robotErrorRepository.save(error);
    }

    private void saveLog(UUID robotId, String level, String message) {
        ApplicationLog log = new ApplicationLog();
        log.setSource("mqtt");
        log.setRobotId(robotId);
        log.setLevel(level);
        log.setMessage(message);
        applicationLogRepository.save(log);
    }

    private Robot registerRobot(Organization organization) {
        RobotManufacturer manufacturer = manufacturerRepository.save(new RobotManufacturer("TestVendor-" + UUID.randomUUID()));
        RobotModel model = new RobotModel();
        model.setManufacturerId(manufacturer.getId());
        model.setName("Test Model");
        model.setAdapterType(AdapterType.SAKAR_NATIVE);
        model.setIntegrationPath(IntegrationPath.SAKAR_OWNED_LOCAL);
        model = modelRepository.save(model);

        Robot robot = new Robot();
        robot.setOrganizationId(organization.getId());
        robot.setRobotModelId(model.getId());
        robot.setName("Robot " + UUID.randomUUID());
        robot.setSerialNumber("SN-" + UUID.randomUUID());
        robot.setStatus(RobotLifecycleStatus.REGISTERED);
        return robotRepository.save(robot);
    }
}
