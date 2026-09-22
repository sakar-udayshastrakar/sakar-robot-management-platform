package com.sakarrobotics.cloud.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MvcResult;

import com.sakarrobotics.cloud.IntegrationTestSupport;
import com.sakarrobotics.cloud.command.CommandStatus;
import com.sakarrobotics.cloud.command.RobotCommand;
import com.sakarrobotics.cloud.command.RobotCommandService;
import com.sakarrobotics.cloud.iam.PermissionCode;
import com.sakarrobotics.cloud.iam.Role;
import com.sakarrobotics.cloud.iam.RoleName;
import com.sakarrobotics.cloud.org.Organization;
import com.sakarrobotics.cloud.org.OrganizationType;
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

/**
 * Task orchestration lifecycle + capability-gating + tenant-isolation
 * coverage (Roadmap Phase 6, approved Decision 1 item 2), plus the CLEANING
 * START -&gt; RobotCommandService bridge ("Connect CLEANING RobotTask START to
 * the existing RobotCommand pipeline" slice). {@link RobotCommandService} is
 * mocked here (a real bean override, not a second implementation) so this
 * class can stay focused on task-lifecycle orchestration — the real
 * command/Keenon-adapter dispatch behavior itself is already covered by
 * {@code RobotCommandServiceTest} and {@code KeenonStartTaskEndToEndTest}.
 */
class RobotTaskControllerTest extends IntegrationTestSupport {

    @Autowired
    private RobotManufacturerRepository manufacturerRepository;
    @Autowired
    private RobotModelRepository modelRepository;
    @Autowired
    private RobotCapabilityRepository capabilityRepository;
    @Autowired
    private RobotRepository robotRepository;
    @MockitoBean
    private RobotCommandService robotCommandService;

    @Test
    void creatingATask_onAModelWithoutStartTaskCapability_isRejected() throws Exception {
        Role orgAdmin = ensureRole(RoleName.ORG_ADMIN, PermissionCode.ROBOT_TASK_CREATE, PermissionCode.ROBOT_CONFIGURE);
        Organization org = createOrganization("Org " + UUID.randomUUID(), OrganizationType.DIRECT_CLIENT, null);
        String email = "admin-" + UUID.randomUUID() + "@example.com";
        createUser(email, "Password1!", orgAdmin, org.getId());
        String token = login(email, "Password1!");

        RobotModel model = modelWithCapabilities(); // no capabilities registered
        Robot robot = registerRobot(org.getId(), model.getId());

        mockMvc.perform(post("/api/v1/robots/" + robot.getId() + "/tasks")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"taskType\":\"CLEANING\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("UNSUPPORTED_CAPABILITY"));
    }

    @Test
    void aCleaningTask_startedThenStopped_recordsACleaningSessionAndTaskEvents() throws Exception {
        Role orgAdmin = ensureRole(RoleName.ORG_ADMIN, PermissionCode.ROBOT_TASK_CREATE, PermissionCode.ROBOT_CONTROL,
                PermissionCode.ROBOT_VIEW, PermissionCode.ROBOT_CONFIGURE);
        Organization org = createOrganization("Org " + UUID.randomUUID(), OrganizationType.DIRECT_CLIENT, null);
        String email = "admin-" + UUID.randomUUID() + "@example.com";
        createUser(email, "Password1!", orgAdmin, org.getId());
        String token = login(email, "Password1!");

        RobotModel model = modelWithCapabilities(RobotCapabilityType.START_TASK, RobotCapabilityType.STOP_TASK);
        Robot robot = registerRobot(org.getId(), model.getId());
        RobotCommand dispatchedCommand = new RobotCommand();
        dispatchedCommand.setStatus(CommandStatus.COMMAND_DISPATCHED);
        when(robotCommandService.issue(any(), eq(robot.getId()), eq("START_TASK"), any()))
                .thenReturn(new RobotCommandService.Issued(dispatchedCommand, true,
                        "Accepted by the Keenon Open Platform (not yet physically confirmed): accepted"));
        when(robotCommandService.issue(any(), eq(robot.getId()), eq("STOP_TASK"), any()))
                .thenReturn(new RobotCommandService.Issued(dispatchedCommand, true,
                        "Accepted by the Keenon Open Platform (not yet physically confirmed): accepted"));

        String createResponse = mockMvc.perform(post("/api/v1/robots/" + robot.getId() + "/tasks")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cleaningTaskRequestBody("SWEEP", UUID.randomUUID().toString())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("CREATED"))
                .andReturn().getResponse().getContentAsString();
        String taskId = objectMapper.readTree(createResponse).get("data").get("id").asText();

        mockMvc.perform(post("/api/v1/tasks/" + taskId + "/start").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("RUNNING"));

        mockMvc.perform(post("/api/v1/tasks/" + taskId + "/stop").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"));

        verify(robotCommandService).issue(any(), eq(robot.getId()), eq("STOP_TASK"), any());

        mockMvc.perform(get("/api/v1/tasks/" + taskId).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.events.length()").value(3)); // CREATED, START, STOP

        mockMvc.perform(get("/api/v1/robots/" + robot.getId() + "/cleaning/history")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].result").value("COMPLETED"))
                .andExpect(jsonPath("$.data.content[0].taskId").value(taskId));
    }

    @Test
    @SuppressWarnings("unchecked")
    void startingACleaningTask_callsRobotCommandServiceIssue_withStartTaskAndTheTasksOwnParameters() throws Exception {
        Role orgAdmin = ensureRole(RoleName.ORG_ADMIN, PermissionCode.ROBOT_TASK_CREATE, PermissionCode.ROBOT_CONTROL,
                PermissionCode.ROBOT_CONFIGURE);
        Organization org = createOrganization("Org " + UUID.randomUUID(), OrganizationType.DIRECT_CLIENT, null);
        String email = "admin-" + UUID.randomUUID() + "@example.com";
        createUser(email, "Password1!", orgAdmin, org.getId());
        String token = login(email, "Password1!");

        RobotModel model = modelWithCapabilities(RobotCapabilityType.START_TASK);
        Robot robot = registerRobot(org.getId(), model.getId());
        RobotCommand dispatchedCommand = new RobotCommand();
        dispatchedCommand.setStatus(CommandStatus.COMMAND_DISPATCHED);
        when(robotCommandService.issue(any(), eq(robot.getId()), eq("START_TASK"), any()))
                .thenReturn(new RobotCommandService.Issued(dispatchedCommand, true, "accepted"));

        String areaId = UUID.randomUUID().toString();
        String createResponse = mockMvc.perform(post("/api/v1/robots/" + robot.getId() + "/tasks")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cleaningTaskRequestBody("SWEEP_MOP", areaId)))
                .andReturn().getResponse().getContentAsString();
        String taskId = objectMapper.readTree(createResponse).get("data").get("id").asText();

        mockMvc.perform(post("/api/v1/tasks/" + taskId + "/start").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("RUNNING"));

        ArgumentCaptor<Map<String, Object>> paramsCaptor = ArgumentCaptor.forClass(Map.class);
        verify(robotCommandService).issue(any(), eq(robot.getId()), eq("START_TASK"), paramsCaptor.capture());
        assertThatParamsMatch(paramsCaptor.getValue(), "SWEEP_MOP", areaId);
    }

    @Test
    void concurrentStart_onlyOneRequestDispatches_theSecondIsRejectedAfterTheFirstCommits() throws Exception {
        // C1 fix regression: two genuinely concurrent /start requests for the same
        // task must never both dispatch. robotCommandService.issue() is made to
        // block mid-call (simulating a slow real Keenon HTTP round trip) so the
        // first request's transaction — and its pessimistic row lock on the task,
        // acquired by RobotTaskRepository.lockByIdForUpdate before issue() is ever
        // called — is still open when the second request is submitted. The second
        // request's own lockByIdForUpdate call must therefore block at the real H2
        // database level until the first transaction commits, then re-read the
        // now-RUNNING status and be rejected by the existing ALLOWED_FROM check —
        // never reaching dispatchCleaningStart a second time.
        Role orgAdmin = ensureRole(RoleName.ORG_ADMIN, PermissionCode.ROBOT_TASK_CREATE, PermissionCode.ROBOT_CONTROL,
                PermissionCode.ROBOT_CONFIGURE);
        Organization org = createOrganization("Org " + UUID.randomUUID(), OrganizationType.DIRECT_CLIENT, null);
        String email = "admin-" + UUID.randomUUID() + "@example.com";
        createUser(email, "Password1!", orgAdmin, org.getId());
        String token = login(email, "Password1!");

        RobotModel model = modelWithCapabilities(RobotCapabilityType.START_TASK);
        Robot robot = registerRobot(org.getId(), model.getId());
        RobotCommand dispatchedCommand = new RobotCommand();
        dispatchedCommand.setStatus(CommandStatus.COMMAND_DISPATCHED);

        CountDownLatch dispatchEntered = new CountDownLatch(1);
        CountDownLatch releaseDispatch = new CountDownLatch(1);
        when(robotCommandService.issue(any(), eq(robot.getId()), eq("START_TASK"), any())).thenAnswer(inv -> {
            dispatchEntered.countDown();
            if (!releaseDispatch.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("test timed out waiting for the release latch");
            }
            return new RobotCommandService.Issued(dispatchedCommand, true, "accepted");
        });

        String createResponse = mockMvc.perform(post("/api/v1/robots/" + robot.getId() + "/tasks")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cleaningTaskRequestBody("SWEEP", UUID.randomUUID().toString())))
                .andReturn().getResponse().getContentAsString();
        String taskId = objectMapper.readTree(createResponse).get("data").get("id").asText();

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<MvcResult> firstStart = executor.submit(() -> mockMvc
                    .perform(post("/api/v1/tasks/" + taskId + "/start").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andReturn());

            // First request is now inside issue(), holding the row lock (acquired
            // before issue() was ever called) — only release the second request
            // once we know the first is genuinely mid-flight, never before.
            assertThat(dispatchEntered.await(10, TimeUnit.SECONDS)).isTrue();

            Future<MvcResult> secondStart = executor.submit(() -> mockMvc
                    .perform(post("/api/v1/tasks/" + taskId + "/start").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andReturn());

            // Give the second request time to actually reach lockByIdForUpdate and
            // register as blocked on the still-open first transaction's row lock
            // before we let the first transaction proceed to commit.
            Thread.sleep(500);
            releaseDispatch.countDown();

            MvcResult firstResult = firstStart.get(15, TimeUnit.SECONDS);
            MvcResult secondResult = secondStart.get(15, TimeUnit.SECONDS);

            assertThat(firstResult.getResponse().getStatus()).isEqualTo(200);
            assertThat(objectMapper.readTree(firstResult.getResponse().getContentAsString())
                    .get("data").get("status").asText()).isEqualTo("RUNNING");

            assertThat(secondResult.getResponse().getStatus()).isEqualTo(409);
            assertThat(objectMapper.readTree(secondResult.getResponse().getContentAsString())
                    .get("error").get("code").asText()).isEqualTo("INVALID_TASK_TRANSITION");

            verify(robotCommandService, Mockito.times(1)).issue(any(), eq(robot.getId()), eq("START_TASK"), any());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void startingACleaningTask_whoseCommandIsNotDispatched_marksTheTaskFailed_neverRunning() throws Exception {
        Role orgAdmin = ensureRole(RoleName.ORG_ADMIN, PermissionCode.ROBOT_TASK_CREATE, PermissionCode.ROBOT_CONTROL,
                PermissionCode.ROBOT_VIEW, PermissionCode.ROBOT_CONFIGURE);
        Organization org = createOrganization("Org " + UUID.randomUUID(), OrganizationType.DIRECT_CLIENT, null);
        String email = "admin-" + UUID.randomUUID() + "@example.com";
        createUser(email, "Password1!", orgAdmin, org.getId());
        String token = login(email, "Password1!");

        RobotModel model = modelWithCapabilities(RobotCapabilityType.START_TASK);
        Robot robot = registerRobot(org.getId(), model.getId());
        RobotCommand failedCommand = new RobotCommand();
        failedCommand.setStatus(CommandStatus.COMMAND_FAILED);
        when(robotCommandService.issue(any(), eq(robot.getId()), eq("START_TASK"), any()))
                .thenReturn(new RobotCommandService.Issued(failedCommand, false, "Not dispatched: no active area mapping"));

        String createResponse = mockMvc.perform(post("/api/v1/robots/" + robot.getId() + "/tasks")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cleaningTaskRequestBody("SWEEP", UUID.randomUUID().toString())))
                .andReturn().getResponse().getContentAsString();
        String taskId = objectMapper.readTree(createResponse).get("data").get("id").asText();

        // Not an HTTP error — mirrors POST /robots/{id}/commands's own convention of a 200
        // response whose payload honestly reports dispatched=false, rather than an error.
        mockMvc.perform(post("/api/v1/tasks/" + taskId + "/start").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("FAILED"));

        mockMvc.perform(get("/api/v1/tasks/" + taskId).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.task.status").value("FAILED"))
                .andExpect(jsonPath("$.data.events.length()").value(2)) // CREATED, START_FAILED
                .andExpect(jsonPath("$.data.events[1].eventType").value("START_FAILED"))
                .andExpect(jsonPath("$.data.events[1].detail").value("Not dispatched: no active area mapping"));
    }

    @Test
    void stoppingACleaningTask_whoseCommandIsNotDispatched_marksTheTaskFailed_neverCompleted() throws Exception {
        // TASK PANEL STOP -> ROBOT COMMAND PIPELINE slice: a STOP_TASK command that
        // is not dispatched (rejected pre-vendor-call, OAuth/network failure, or a
        // definitive vendor rejection) must never let the task report COMPLETED —
        // mirrors the equivalent START test above, now for STOP.
        Role orgAdmin = ensureRole(RoleName.ORG_ADMIN, PermissionCode.ROBOT_TASK_CREATE, PermissionCode.ROBOT_CONTROL,
                PermissionCode.ROBOT_VIEW, PermissionCode.ROBOT_CONFIGURE);
        Organization org = createOrganization("Org " + UUID.randomUUID(), OrganizationType.DIRECT_CLIENT, null);
        String email = "admin-" + UUID.randomUUID() + "@example.com";
        createUser(email, "Password1!", orgAdmin, org.getId());
        String token = login(email, "Password1!");

        RobotModel model = modelWithCapabilities(RobotCapabilityType.START_TASK, RobotCapabilityType.STOP_TASK);
        Robot robot = registerRobot(org.getId(), model.getId());
        RobotCommand dispatchedCommand = new RobotCommand();
        dispatchedCommand.setStatus(CommandStatus.COMMAND_DISPATCHED);
        when(robotCommandService.issue(any(), eq(robot.getId()), eq("START_TASK"), any()))
                .thenReturn(new RobotCommandService.Issued(dispatchedCommand, true, "accepted"));
        RobotCommand failedCommand = new RobotCommand();
        failedCommand.setStatus(CommandStatus.COMMAND_FAILED);
        when(robotCommandService.issue(any(), eq(robot.getId()), eq("STOP_TASK"), any()))
                .thenReturn(new RobotCommandService.Issued(failedCommand, false, "Not dispatched: Keenon OAuth failure"));

        String createResponse = mockMvc.perform(post("/api/v1/robots/" + robot.getId() + "/tasks")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cleaningTaskRequestBody("SWEEP", UUID.randomUUID().toString())))
                .andReturn().getResponse().getContentAsString();
        String taskId = objectMapper.readTree(createResponse).get("data").get("id").asText();

        mockMvc.perform(post("/api/v1/tasks/" + taskId + "/start").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("RUNNING"));

        // Not an HTTP error — mirrors the same "200 with an honest dispatched=false
        // payload" convention the START failure path already uses.
        mockMvc.perform(post("/api/v1/tasks/" + taskId + "/stop").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("FAILED"));

        mockMvc.perform(get("/api/v1/tasks/" + taskId).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.task.status").value("FAILED"))
                .andExpect(jsonPath("$.data.events.length()").value(3)) // CREATED, START, STOP_FAILED
                .andExpect(jsonPath("$.data.events[2].eventType").value("STOP_FAILED"))
                .andExpect(jsonPath("$.data.events[2].detail").value("Not dispatched: Keenon OAuth failure"));

        // Never a completed cleaning session for a stop that never reached the robot.
        mockMvc.perform(get("/api/v1/robots/" + robot.getId() + "/cleaning/history")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(0));
    }

    @Test
    void startingACleaningTask_withNoParameters_isRejectedAsValidationFailed_withoutCallingRobotCommandService() throws Exception {
        Role orgAdmin = ensureRole(RoleName.ORG_ADMIN, PermissionCode.ROBOT_TASK_CREATE, PermissionCode.ROBOT_CONTROL,
                PermissionCode.ROBOT_VIEW, PermissionCode.ROBOT_CONFIGURE);
        Organization org = createOrganization("Org " + UUID.randomUUID(), OrganizationType.DIRECT_CLIENT, null);
        String email = "admin-" + UUID.randomUUID() + "@example.com";
        createUser(email, "Password1!", orgAdmin, org.getId());
        String token = login(email, "Password1!");

        RobotModel model = modelWithCapabilities(RobotCapabilityType.START_TASK);
        Robot robot = registerRobot(org.getId(), model.getId());

        // Reproduces the exact reported request shape: {"taskType":"CLEANING","parameters":null}.
        String createResponse = mockMvc.perform(post("/api/v1/robots/" + robot.getId() + "/tasks")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"taskType\":\"CLEANING\",\"parameters\":null}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String taskId = objectMapper.readTree(createResponse).get("data").get("id").asText();

        mockMvc.perform(post("/api/v1/tasks/" + taskId + "/start").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));

        verifyNoInteractions(robotCommandService);
        mockMvc.perform(get("/api/v1/tasks/" + taskId).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(jsonPath("$.data.task.status").value("CREATED")); // untouched, safely retryable
    }

    @Test
    void startingANonCleaningTask_neverCallsRobotCommandService_existingBookkeepingOnlyBehaviorIsUnchanged() throws Exception {
        Role orgAdmin = ensureRole(RoleName.ORG_ADMIN, PermissionCode.ROBOT_TASK_CREATE, PermissionCode.ROBOT_CONTROL,
                PermissionCode.ROBOT_CONFIGURE);
        Organization org = createOrganization("Org " + UUID.randomUUID(), OrganizationType.DIRECT_CLIENT, null);
        String email = "admin-" + UUID.randomUUID() + "@example.com";
        createUser(email, "Password1!", orgAdmin, org.getId());
        String token = login(email, "Password1!");

        RobotModel model = modelWithCapabilities(RobotCapabilityType.START_TASK);
        Robot robot = registerRobot(org.getId(), model.getId());

        String createResponse = mockMvc.perform(post("/api/v1/robots/" + robot.getId() + "/tasks")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"taskType\":\"RETURN_TO_DOCK\"}"))
                .andReturn().getResponse().getContentAsString();
        String taskId = objectMapper.readTree(createResponse).get("data").get("id").asText();

        mockMvc.perform(post("/api/v1/tasks/" + taskId + "/start").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("RUNNING"));

        verifyNoInteractions(robotCommandService);
    }

    private static String cleaningTaskRequestBody(String mode, String areaId) {
        return "{\"taskType\":\"CLEANING\",\"parameters\":\""
                + "{\\\"mode\\\":\\\"" + mode + "\\\",\\\"areaIds\\\":[\\\"" + areaId + "\\\"]}\"}";
    }

    @SuppressWarnings("unchecked")
    private static void assertThatParamsMatch(Map<String, Object> params, String mode, String areaId) {
        assertThat(params.get("mode")).isEqualTo(mode);
        assertThat((List<String>) params.get("areaIds")).containsExactly(areaId);
    }

    @Test
    void stoppingATaskThatWasNeverStarted_isAnInvalidTransition() throws Exception {
        Role orgAdmin = ensureRole(RoleName.ORG_ADMIN, PermissionCode.ROBOT_TASK_CREATE, PermissionCode.ROBOT_CONTROL,
                PermissionCode.ROBOT_CONFIGURE);
        Organization org = createOrganization("Org " + UUID.randomUUID(), OrganizationType.DIRECT_CLIENT, null);
        String email = "admin-" + UUID.randomUUID() + "@example.com";
        createUser(email, "Password1!", orgAdmin, org.getId());
        String token = login(email, "Password1!");

        RobotModel model = modelWithCapabilities(RobotCapabilityType.START_TASK);
        Robot robot = registerRobot(org.getId(), model.getId());

        String createResponse = mockMvc.perform(post("/api/v1/robots/" + robot.getId() + "/tasks")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"taskType\":\"CLEANING\"}"))
                .andReturn().getResponse().getContentAsString();
        String taskId = objectMapper.readTree(createResponse).get("data").get("id").asText();

        mockMvc.perform(post("/api/v1/tasks/" + taskId + "/stop").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("INVALID_TASK_TRANSITION"));
    }

    @Test
    void user_cannotSeeATaskBelongingToAnUnrelatedOrganization_getsNotFoundNotForbidden() throws Exception {
        Role orgAdmin = ensureRole(RoleName.ORG_ADMIN, PermissionCode.ROBOT_TASK_CREATE, PermissionCode.ROBOT_VIEW,
                PermissionCode.ROBOT_CONFIGURE);
        Organization orgA = createOrganization("Org A " + UUID.randomUUID(), OrganizationType.DIRECT_CLIENT, null);
        Organization orgB = createOrganization("Org B " + UUID.randomUUID(), OrganizationType.DIRECT_CLIENT, null);
        String emailA = "admin-a-" + UUID.randomUUID() + "@example.com";
        createUser(emailA, "Password1!", orgAdmin, orgA.getId());
        String emailB = "admin-b-" + UUID.randomUUID() + "@example.com";
        createUser(emailB, "Password1!", orgAdmin, orgB.getId());

        RobotModel model = modelWithCapabilities(RobotCapabilityType.START_TASK);
        Robot robotInOrgB = registerRobot(orgB.getId(), model.getId());
        String tokenB = login(emailB, "Password1!");
        String createResponse = mockMvc.perform(post("/api/v1/robots/" + robotInOrgB.getId() + "/tasks")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"taskType\":\"CLEANING\"}"))
                .andReturn().getResponse().getContentAsString();
        String taskId = objectMapper.readTree(createResponse).get("data").get("id").asText();

        String tokenA = login(emailA, "Password1!");
        mockMvc.perform(get("/api/v1/tasks/" + taskId).header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("TASK_NOT_FOUND"));
    }

    private RobotModel modelWithCapabilities(RobotCapabilityType... capabilities) {
        RobotManufacturer manufacturer = manufacturerRepository.save(new RobotManufacturer("TestVendor-" + UUID.randomUUID()));
        RobotModel model = new RobotModel();
        model.setManufacturerId(manufacturer.getId());
        model.setName("Test Model");
        model.setAdapterType(AdapterType.SAKAR_NATIVE);
        model.setIntegrationPath(IntegrationPath.SAKAR_OWNED_LOCAL);
        model = modelRepository.save(model);
        for (RobotCapabilityType capability : capabilities) {
            capabilityRepository.save(new RobotCapability(model.getId(), capability, true));
        }
        return model;
    }

    private Robot registerRobot(UUID organizationId, UUID modelId) {
        Robot robot = new Robot();
        robot.setOrganizationId(organizationId);
        robot.setRobotModelId(modelId);
        robot.setName("Robot " + UUID.randomUUID());
        robot.setSerialNumber("SN-" + UUID.randomUUID());
        robot.setStatus(RobotLifecycleStatus.REGISTERED);
        return robotRepository.save(robot);
    }
}
