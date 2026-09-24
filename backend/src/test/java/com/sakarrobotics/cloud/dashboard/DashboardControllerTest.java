package com.sakarrobotics.cloud.dashboard;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

import com.sakarrobotics.cloud.IntegrationTestSupport;
import com.sakarrobotics.cloud.iam.PermissionCode;
import com.sakarrobotics.cloud.iam.Role;
import com.sakarrobotics.cloud.iam.RoleName;
import com.sakarrobotics.cloud.org.Organization;
import com.sakarrobotics.cloud.org.OrganizationType;
import com.sakarrobotics.cloud.org.Site;
import com.sakarrobotics.cloud.org.SiteRepository;
import com.sakarrobotics.cloud.robot.registry.AdapterType;
import com.sakarrobotics.cloud.robot.registry.IntegrationPath;
import com.sakarrobotics.cloud.robot.registry.Robot;
import com.sakarrobotics.cloud.robot.registry.RobotCapability;
import com.sakarrobotics.cloud.robot.registry.RobotCapabilityRepository;
import com.sakarrobotics.cloud.robot.registry.RobotCapabilityType;
import com.sakarrobotics.cloud.robot.registry.RobotLifecycleStatus;
import com.sakarrobotics.cloud.robot.registry.RobotManufacturer;
import com.sakarrobotics.cloud.robot.registry.RobotManufacturerRepository;
import com.sakarrobotics.cloud.robot.registry.RobotModel;
import com.sakarrobotics.cloud.robot.registry.RobotModelRepository;
import com.sakarrobotics.cloud.robot.registry.RobotRepository;
import com.sakarrobotics.cloud.telemetry.RobotStatus;
import com.sakarrobotics.cloud.telemetry.RobotStatusRepository;

/** Operational Dashboard — every figure is computed from real robot_tasks/robots/sites rows; mileage/calls/rooms are always null ("not tracked"). */
class DashboardControllerTest extends IntegrationTestSupport {

    @Autowired
    private SiteRepository siteRepository;
    @Autowired
    private RobotManufacturerRepository manufacturerRepository;
    @Autowired
    private RobotModelRepository modelRepository;
    @Autowired
    private RobotRepository robotRepository;
    @Autowired
    private RobotCapabilityRepository robotCapabilityRepository;
    @Autowired
    private RobotStatusRepository robotStatusRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String tokenWithRobotAccess(UUID organizationId) throws Exception {
        Role orgAdmin = ensureRole(RoleName.ORG_ADMIN, PermissionCode.ROBOT_VIEW, PermissionCode.ROBOT_TASK_CREATE, PermissionCode.ROBOT_CONFIGURE);
        String email = "admin-" + UUID.randomUUID() + "@example.com";
        createUser(email, "Password1!", orgAdmin, organizationId);
        return login(email, "Password1!");
    }

    private Site aSite(UUID organizationId) {
        Site site = new Site();
        site.setOrganizationId(organizationId);
        site.setName("Store " + UUID.randomUUID());
        return siteRepository.save(site);
    }

    private Robot aRobot(UUID organizationId, UUID siteId) {
        RobotManufacturer manufacturer = manufacturerRepository.save(new RobotManufacturer("TestVendor-" + UUID.randomUUID()));
        RobotModel model = new RobotModel();
        model.setManufacturerId(manufacturer.getId());
        model.setName("Test Model");
        model.setAdapterType(AdapterType.SAKAR_NATIVE);
        model.setIntegrationPath(IntegrationPath.SAKAR_OWNED_LOCAL);
        model = modelRepository.save(model);
        robotCapabilityRepository.save(new RobotCapability(model.getId(), RobotCapabilityType.START_TASK, true));

        Robot robot = new Robot();
        robot.setOrganizationId(organizationId);
        robot.setSiteId(siteId);
        robot.setRobotModelId(model.getId());
        robot.setName("Robot " + UUID.randomUUID());
        robot.setSerialNumber("SN-" + UUID.randomUUID());
        robot.setStatus(RobotLifecycleStatus.REGISTERED);
        return robotRepository.save(robot);
    }

    private void markOnline(UUID robotId) {
        RobotStatus status = robotStatusRepository.findById(robotId).orElseGet(() -> {
            RobotStatus fresh = new RobotStatus();
            fresh.setRobotId(robotId);
            return fresh;
        });
        status.setOnline(true);
        status.setLastSeenAt(Instant.now());
        status.setUpdatedAt(Instant.now());
        robotStatusRepository.save(status);
    }

    private void backdateTask(UUID taskId, Instant createdAt, Instant updatedAt) {
        jdbcTemplate.update("UPDATE robot_tasks SET created_at = ?, updated_at = ? WHERE id = ?",
                java.sql.Timestamp.from(createdAt), java.sql.Timestamp.from(updatedAt), taskId);
    }

    @Test
    void operationRanking_countsRealTasksPerStoreAndRobot_mileageAndCallsAlwaysNull() throws Exception {
        Organization org = createOrganization("Org " + UUID.randomUUID(), OrganizationType.DIRECT_CLIENT, null);
        String token = tokenWithRobotAccess(org.getId());
        Site site = aSite(org.getId());
        Robot robot = aRobot(org.getId(), site.getId());

        mockMvc.perform(post("/api/v1/robots/" + robot.getId() + "/tasks")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"taskType\":\"SWEEP\"}"));

        mockMvc.perform(get("/api/v1/dashboard/operation-ranking").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalTasks").value(1))
                .andExpect(jsonPath("$.data.totalMileage").doesNotExist())
                .andExpect(jsonPath("$.data.totalCalls").doesNotExist())
                .andExpect(jsonPath("$.data.storeRankingsByTasks[0].label").value(site.getName()))
                .andExpect(jsonPath("$.data.storeRankingsByTasks[0].count").value(1))
                .andExpect(jsonPath("$.data.robotRankingsByTasks[0].label").value(robot.getSerialNumber()));
    }

    @Test
    void storeRealtimeStats_countsTodaysTasksAndActiveMachines() throws Exception {
        Organization org = createOrganization("Org " + UUID.randomUUID(), OrganizationType.DIRECT_CLIENT, null);
        String token = tokenWithRobotAccess(org.getId());
        Site site = aSite(org.getId());
        Robot robot = aRobot(org.getId(), site.getId());
        markOnline(robot.getId());

        mockMvc.perform(post("/api/v1/robots/" + robot.getId() + "/tasks")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"taskType\":\"MOP\"}"));

        mockMvc.perform(get("/api/v1/dashboard/store-realtime").param("siteId", site.getId().toString())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tasksToday").value(1))
                .andExpect(jsonPath("$.data.activeMachines").value(1))
                .andExpect(jsonPath("$.data.callsToday").doesNotExist())
                .andExpect(jsonPath("$.data.mileageToday").doesNotExist())
                .andExpect(jsonPath("$.data.taskModeProportionToday[0].taskType").value("MOP"))
                .andExpect(jsonPath("$.data.taskModeProportionToday[0].percentage").value(100.0));
    }

    @Test
    void retentionAnalytics_computesRealConsecutiveUseStreak_fromBackdatedTasks() throws Exception {
        Organization org = createOrganization("Org " + UUID.randomUUID(), OrganizationType.DIRECT_CLIENT, null);
        String token = tokenWithRobotAccess(org.getId());
        Site site = aSite(org.getId());
        Robot robot = aRobot(org.getId(), site.getId());

        Instant now = Instant.now();
        for (int daysAgo = 0; daysAgo < 3; daysAgo++) {
            String response = mockMvc.perform(post("/api/v1/robots/" + robot.getId() + "/tasks")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"taskType\":\"SWEEP\"}"))
                    .andReturn().getResponse().getContentAsString();
            UUID taskId = UUID.fromString(objectMapper.readTree(response).get("data").get("id").asText());
            Instant createdAt = now.minus(daysAgo, ChronoUnit.DAYS);
            backdateTask(taskId, createdAt, createdAt);
        }

        mockMvc.perform(get("/api/v1/dashboard/retention").param("days", "8")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].used3").value(1))
                .andExpect(jsonPath("$.data[0].used7").value(0))
                .andExpect(jsonPath("$.data[0].unused3").value(0));
    }

    @Test
    void hotelTaskRecord_sumsRealCumulativeDuration_forCompletedTasksOnly_mileageAndRoomsAlwaysNull() throws Exception {
        Organization org = createOrganization("Org " + UUID.randomUUID(), OrganizationType.DIRECT_CLIENT, null);
        String token = tokenWithRobotAccess(org.getId());
        Site site = aSite(org.getId());
        Robot robot = aRobot(org.getId(), site.getId());

        String response = mockMvc.perform(post("/api/v1/robots/" + robot.getId() + "/tasks")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"taskType\":\"SWEEP\"}"))
                .andReturn().getResponse().getContentAsString();
        UUID taskId = UUID.fromString(objectMapper.readTree(response).get("data").get("id").asText());
        Instant createdAt = Instant.now().minusSeconds(600);
        Instant completedAt = Instant.now();
        jdbcTemplate.update("UPDATE robot_tasks SET status = 'COMPLETED', created_at = ?, updated_at = ? WHERE id = ?",
                java.sql.Timestamp.from(createdAt), java.sql.Timestamp.from(completedAt), taskId);

        mockMvc.perform(get("/api/v1/dashboard/hotel-task-record").param("siteId", site.getId().toString())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalVolumeOfTask").value(1))
                .andExpect(jsonPath("$.data.cumulativeDurationSeconds").value(600))
                .andExpect(jsonPath("$.data.cumulativeMileage").doesNotExist())
                .andExpect(jsonPath("$.data.numberOfRooms").doesNotExist())
                .andExpect(jsonPath("$.data.taskTypeBreakdown[0].taskType").value("SWEEP"))
                .andExpect(jsonPath("$.data.taskTypeBreakdown[0].count").value(1))
                .andExpect(jsonPath("$.data.taskTypeBreakdown[0].percentage").value(100.0));
    }
}
