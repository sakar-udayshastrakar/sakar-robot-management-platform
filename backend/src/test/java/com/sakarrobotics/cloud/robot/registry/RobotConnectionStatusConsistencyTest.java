package com.sakarrobotics.cloud.robot.registry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;

import com.sakarrobotics.cloud.IntegrationTestSupport;
import com.sakarrobotics.cloud.alert.AlertGenerationService;
import com.sakarrobotics.cloud.alert.AlertStatus;
import com.sakarrobotics.cloud.alert.AlertType;
import com.sakarrobotics.cloud.alert.RobotAlertRepository;
import com.sakarrobotics.cloud.iam.PermissionCode;
import com.sakarrobotics.cloud.iam.Role;
import com.sakarrobotics.cloud.iam.RoleName;
import com.sakarrobotics.cloud.org.Organization;
import com.sakarrobotics.cloud.org.OrganizationType;
import com.sakarrobotics.cloud.telemetry.RobotStatus;
import com.sakarrobotics.cloud.telemetry.RobotStatusRepository;

/**
 * End-to-end proof, over the real REST layer, that the Robots list, the
 * robot detail endpoint, and the offline-alert layer all agree ("Fix robot
 * online/offline status consistency" slice).
 *
 * <p>The bug this pins: the UI's connection badge was fed by {@code GET
 * /robots/{id}/status}, whose {@code online} flag means "the Keenon API
 * returned a data object". A robot silent for hours therefore rendered
 * ONLINE while the Alerts page showed an open "No heartbeat or telemetry
 * received within the configured offline threshold" alert for it.
 */
class RobotConnectionStatusConsistencyTest extends IntegrationTestSupport {

    @Autowired
    private RobotManufacturerRepository manufacturerRepository;
    @Autowired
    private RobotModelRepository modelRepository;
    @Autowired
    private RobotRepository robotRepository;
    @Autowired
    private RobotStatusRepository robotStatusRepository;
    @Autowired
    private RobotAlertRepository robotAlertRepository;
    @Autowired
    private AlertGenerationService alertGenerationService;

    private record Fixture(String token, Robot robot) {
    }

    private Fixture fixture() throws Exception {
        Role orgAdmin = ensureRole(RoleName.ORG_ADMIN, PermissionCode.ROBOT_VIEW, PermissionCode.ROBOT_CONFIGURE);
        Organization org = createOrganization("Org " + UUID.randomUUID(), OrganizationType.DIRECT_CLIENT, null);
        String email = "admin-" + UUID.randomUUID() + "@example.com";
        createUser(email, "Password1!", orgAdmin, org.getId());
        return new Fixture(login(email, "Password1!"), registerRobot(org.getId()));
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

    private void recordLastSeen(UUID robotId, Instant lastSeenAt, boolean onlineFlag) {
        RobotStatus status = robotStatusRepository.findById(robotId).orElseGet(() -> {
            RobotStatus fresh = new RobotStatus();
            fresh.setRobotId(robotId);
            return fresh;
        });
        status.setOnline(onlineFlag);
        status.setLastSeenAt(lastSeenAt);
        status.setUpdatedAt(Instant.now());
        robotStatusRepository.save(status);
    }

    /** The connectionStatus this robot reports on the list endpoint. */
    private String listedStatus(String token, UUID robotId) throws Exception {
        String body = mockMvc.perform(get("/api/v1/robots?page=0&pageSize=100")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        for (var node : objectMapper.readTree(body).get("data").get("content")) {
            if (robotId.toString().equals(node.get("id").asText())) {
                return node.get("connectionStatus").asText();
            }
        }
        throw new AssertionError("Robot " + robotId + " not present in the list response");
    }

    private String detailStatus(String token, UUID robotId) throws Exception {
        String body = mockMvc.perform(get("/api/v1/robots/" + robotId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("data").get("connectionStatus").asText();
    }

    @Test
    void staleHeartbeat_reportsOfflineOnBothTheListAndTheDetailEndpoint() throws Exception {
        Fixture f = fixture();
        // Two hours silent, and the stored flag still says online because
        // RobotOfflineWatcherService's sweep has not run yet — exactly the state that
        // used to render as ONLINE in the UI.
        recordLastSeen(f.robot().getId(), Instant.now().minusSeconds(7200), true);

        assertThat(listedStatus(f.token(), f.robot().getId())).isEqualTo("OFFLINE");
        assertThat(detailStatus(f.token(), f.robot().getId())).isEqualTo("OFFLINE");
    }

    @Test
    void freshHeartbeat_reportsOnlineOnBothEndpoints() throws Exception {
        Fixture f = fixture();
        recordLastSeen(f.robot().getId(), Instant.now().minusSeconds(5), true);

        assertThat(listedStatus(f.token(), f.robot().getId())).isEqualTo("ONLINE");
        assertThat(detailStatus(f.token(), f.robot().getId())).isEqualTo("ONLINE");
    }

    @Test
    void neverSeenRobot_reportsUnknown_andIsNeverRenderedOnline() throws Exception {
        Fixture f = fixture();
        // No robot_status row written at all.

        assertThat(listedStatus(f.token(), f.robot().getId())).isEqualTo("UNKNOWN");
        assertThat(detailStatus(f.token(), f.robot().getId())).isEqualTo("UNKNOWN");
    }

    @Test
    void anOpenOfflineAlert_andTheRobotsEndpoints_agree() throws Exception {
        Fixture f = fixture();
        UUID robotId = f.robot().getId();
        recordLastSeen(robotId, Instant.now().minusSeconds(7200), false);
        // The same condition the sweep detects, recorded through the real alert service.
        alertGenerationService.evaluateOffline(robotId);

        assertThat(robotAlertRepository.existsByRobotIdAndAlertTypeAndStatus(
                robotId, AlertType.OFFLINE.name(), AlertStatus.OPEN.name())).isTrue();
        // The Alerts page and the Robots page can no longer contradict each other.
        assertThat(listedStatus(f.token(), robotId)).isEqualTo("OFFLINE");
        assertThat(detailStatus(f.token(), robotId)).isEqualTo("OFFLINE");
    }

    @Test
    void robotsInTheSameListAreScoredIndependently() throws Exception {
        Fixture f = fixture();
        Robot staleRobot = f.robot();
        Robot freshRobot = registerRobot(staleRobot.getOrganizationId());
        recordLastSeen(staleRobot.getId(), Instant.now().minusSeconds(7200), true);
        recordLastSeen(freshRobot.getId(), Instant.now().minusSeconds(3), true);

        assertThat(listedStatus(f.token(), staleRobot.getId())).isEqualTo("OFFLINE");
        assertThat(listedStatus(f.token(), freshRobot.getId())).isEqualTo("ONLINE");
    }

    @Test
    void theResponseAlsoCarriesLastSeenAt_forDisplayOnly() throws Exception {
        Fixture f = fixture();
        recordLastSeen(f.robot().getId(), Instant.now().minusSeconds(30), true);

        mockMvc.perform(get("/api/v1/robots/" + f.robot().getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + f.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.connectionStatus").value("ONLINE"))
                .andExpect(jsonPath("$.data.lastSeenAt").exists());
    }
}
