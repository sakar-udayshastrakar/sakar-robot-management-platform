package com.sakarrobotics.cloud.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.sakarrobotics.cloud.audit.AuditService;
import com.sakarrobotics.cloud.cleaning.CleaningSessionService;
import com.sakarrobotics.cloud.command.CommandStatus;
import com.sakarrobotics.cloud.command.RobotCommand;
import com.sakarrobotics.cloud.command.RobotCommandService;
import com.sakarrobotics.cloud.common.error.ApiException;
import com.sakarrobotics.cloud.common.error.SakarErrorCode;
import com.sakarrobotics.cloud.iam.RoleName;
import com.sakarrobotics.cloud.robot.registry.Robot;
import com.sakarrobotics.cloud.robot.registry.RobotCapabilityService;
import com.sakarrobotics.cloud.robot.registry.RobotService;
import com.sakarrobotics.cloud.security.UserPrincipal;
import com.sakarrobotics.cloud.security.access.TenantAccessGuard;

import org.junit.jupiter.api.Assertions;
import tools.jackson.databind.ObjectMapper;

/**
 * "Connect CLEANING RobotTask START to the existing RobotCommand pipeline"
 * slice. Pure unit test of {@link RobotTaskService}, mirroring {@code
 * RobotCommandServiceTest}'s own plain-Mockito style — {@link
 * RobotCommandService} is mocked here so this class can assert precisely
 * what {@code transition("START")} sends it and how it reacts to the
 * result, without re-exercising the real Keenon/adapter dispatch logic
 * (already covered by {@code RobotCommandServiceTest}/{@code
 * KeenonStartTaskEndToEndTest}).
 */
@ExtendWith(MockitoExtension.class)
class RobotTaskServiceTest {

    @Mock
    private RobotTaskRepository robotTaskRepository;
    @Mock
    private TaskEventRepository taskEventRepository;
    @Mock
    private RobotService robotService;
    @Mock
    private RobotCapabilityService robotCapabilityService;
    @Mock
    private CleaningSessionService cleaningSessionService;
    @Mock
    private TenantAccessGuard tenantAccessGuard;
    @Mock
    private AuditService auditService;
    @Mock
    private RobotCommandService robotCommandService;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private UserPrincipal principal;

    private RobotTaskService service() {
        return new RobotTaskService(robotTaskRepository, taskEventRepository, robotService, robotCapabilityService,
                cleaningSessionService, tenantAccessGuard, auditService, robotCommandService, objectMapper);
    }

    @BeforeEach
    void setUp() {
        principal = new UserPrincipal(UUID.randomUUID(), "admin@example.com", UUID.randomUUID(), "/org",
                RoleName.ORG_ADMIN, Set.of());
        lenient().when(tenantAccessGuard.hasOrganizationAccess(eq(principal), any())).thenReturn(true);
        lenient().when(robotTaskRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private RobotTask aTask(UUID robotId, UUID orgId, String taskType, String parameters, TaskLifecycleStatus status) {
        RobotTask task = new RobotTask();
        task.setId(UUID.randomUUID());
        task.setRobotId(robotId);
        task.setOrganizationId(orgId);
        task.setCreatedBy(principal.getUserId());
        task.setTaskType(taskType);
        task.setParameters(parameters);
        task.setStatus(status.name());
        return task;
    }

    // ------------------------------------------------------------------
    // CLEANING START -> RobotCommandService.issue bridge.
    // ------------------------------------------------------------------

    @Test
    @SuppressWarnings("unchecked")
    void startingACleaningTask_issuesAStartTaskCommand_withTheTasksOwnModeAreaIdsAndRepeatCount() {
        UUID robotId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        String areaId1 = UUID.randomUUID().toString();
        String areaId2 = UUID.randomUUID().toString();
        RobotTask task = aTask(robotId, orgId, "CLEANING",
                "{\"mode\":\"SWEEP_MOP\",\"areaIds\":[\"" + areaId1 + "\",\"" + areaId2 + "\"],\"repeatCount\":3}", TaskLifecycleStatus.CREATED);
        when(robotTaskRepository.lockByIdForUpdate(task.getId())).thenReturn(Optional.of(task));
        RobotCommand command = new RobotCommand();
        command.setStatus(CommandStatus.COMMAND_DISPATCHED);
        when(robotCommandService.issue(eq(principal), eq(robotId), eq("START_TASK"), any()))
                .thenReturn(new RobotCommandService.Issued(command, true, "Accepted by the Keenon Open Platform"));

        RobotTask result = service().transition(principal, task.getId(), "START");

        assertThat(result.getStatus()).isEqualTo("RUNNING");
        ArgumentCaptor<Map<String, Object>> paramsCaptor = ArgumentCaptor.forClass(Map.class);
        verify(robotCommandService).issue(eq(principal), eq(robotId), eq("START_TASK"), paramsCaptor.capture());
        Map<String, Object> params = paramsCaptor.getValue();
        assertThat(params.get("mode")).isEqualTo("SWEEP_MOP");
        assertThat((List<String>) params.get("areaIds")).containsExactly(areaId1, areaId2);
        assertThat(params.get("repeatCount")).isEqualTo(3);
        verify(auditService).record(eq(principal), eq(orgId), eq(robotId), eq("TASK_START"), eq("SUCCESS"), any(), any(), any());
    }

    @Test
    void startingACleaningTask_withNoParameters_isRejected_withoutCallingRobotCommandService() {
        UUID robotId = UUID.randomUUID();
        RobotTask task = aTask(robotId, UUID.randomUUID(), "CLEANING", null, TaskLifecycleStatus.CREATED);
        when(robotTaskRepository.lockByIdForUpdate(task.getId())).thenReturn(Optional.of(task));

        ApiException ex = Assertions.assertThrows(ApiException.class, () -> service().transition(principal, task.getId(), "START"));

        assertThat(ex.getErrorCode()).isEqualTo(SakarErrorCode.VALIDATION_FAILED);
        verifyNoInteractions(robotCommandService);
        assertThat(task.getStatus()).isEqualTo("CREATED"); // untouched — never saved
        verify(robotTaskRepository, never()).save(any());
    }

    @Test
    void startingACleaningTask_withBlankMode_isRejected_withoutCallingRobotCommandService() {
        UUID robotId = UUID.randomUUID();
        RobotTask task = aTask(robotId, UUID.randomUUID(), "CLEANING",
                "{\"mode\":\"\",\"areaIds\":[\"" + UUID.randomUUID() + "\"]}", TaskLifecycleStatus.CREATED);
        when(robotTaskRepository.lockByIdForUpdate(task.getId())).thenReturn(Optional.of(task));

        ApiException ex = Assertions.assertThrows(ApiException.class, () -> service().transition(principal, task.getId(), "START"));

        assertThat(ex.getErrorCode()).isEqualTo(SakarErrorCode.VALIDATION_FAILED);
        assertThat(ex.getMessage()).contains("mode");
        verifyNoInteractions(robotCommandService);
    }

    @Test
    void startingACleaningTask_withEmptyAreaIds_isRejected_withoutCallingRobotCommandService() {
        UUID robotId = UUID.randomUUID();
        RobotTask task = aTask(robotId, UUID.randomUUID(), "CLEANING",
                "{\"mode\":\"SWEEP\",\"areaIds\":[]}", TaskLifecycleStatus.CREATED);
        when(robotTaskRepository.lockByIdForUpdate(task.getId())).thenReturn(Optional.of(task));

        ApiException ex = Assertions.assertThrows(ApiException.class, () -> service().transition(principal, task.getId(), "START"));

        assertThat(ex.getErrorCode()).isEqualTo(SakarErrorCode.VALIDATION_FAILED);
        assertThat(ex.getMessage()).contains("areaIds");
        verifyNoInteractions(robotCommandService);
    }

    @Test
    void startingACleaningTask_withMalformedAreaId_isRejected_withoutCallingRobotCommandServiceOrThrowingIllegalArgumentException() {
        // Reproduces the exact shape that, prior to this fix, would reach
        // KeenonRobotAdapter.startTask's own UUID.fromString(id) uncaught,
        // surfacing as an unhandled 500 instead of a clean VALIDATION_FAILED.
        UUID robotId = UUID.randomUUID();
        RobotTask task = aTask(robotId, UUID.randomUUID(), "CLEANING",
                "{\"mode\":\"SWEEP\",\"areaIds\":[\"abc\"]}", TaskLifecycleStatus.CREATED);
        when(robotTaskRepository.lockByIdForUpdate(task.getId())).thenReturn(Optional.of(task));

        ApiException ex = Assertions.assertThrows(ApiException.class, () -> service().transition(principal, task.getId(), "START"));

        assertThat(ex.getErrorCode()).isEqualTo(SakarErrorCode.VALIDATION_FAILED);
        assertThat(ex.getMessage()).contains("abc");
        verifyNoInteractions(robotCommandService);
        assertThat(task.getStatus()).isEqualTo("CREATED"); // untouched — never saved, safely retryable
        verify(robotTaskRepository, never()).save(any());
    }

    @Test
    void startingACleaningTask_withMalformedParametersJson_isRejected_withoutCallingRobotCommandService() {
        UUID robotId = UUID.randomUUID();
        RobotTask task = aTask(robotId, UUID.randomUUID(), "CLEANING", "not-json", TaskLifecycleStatus.CREATED);
        when(robotTaskRepository.lockByIdForUpdate(task.getId())).thenReturn(Optional.of(task));

        ApiException ex = Assertions.assertThrows(ApiException.class, () -> service().transition(principal, task.getId(), "START"));

        assertThat(ex.getErrorCode()).isEqualTo(SakarErrorCode.VALIDATION_FAILED);
        verifyNoInteractions(robotCommandService);
    }

    @Test
    void startingACleaningTask_whoseCommandIsNotDispatched_marksTheTaskFailed_recordsAnEventAndAudit_neverRunning() {
        UUID robotId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        RobotTask task = aTask(robotId, orgId, "CLEANING",
                "{\"mode\":\"SWEEP\",\"areaIds\":[\"" + UUID.randomUUID() + "\"]}", TaskLifecycleStatus.CREATED);
        when(robotTaskRepository.lockByIdForUpdate(task.getId())).thenReturn(Optional.of(task));
        RobotCommand command = new RobotCommand();
        command.setStatus(CommandStatus.COMMAND_FAILED);
        when(robotCommandService.issue(eq(principal), eq(robotId), eq("START_TASK"), any()))
                .thenReturn(new RobotCommandService.Issued(command, false, "Not dispatched: At least one area must be specified"));

        RobotTask result = service().transition(principal, task.getId(), "START");

        assertThat(result.getStatus()).isEqualTo("FAILED");
        verify(taskEventRepository).save(argThat(e -> e.getEventType().equals("START_FAILED")
                && e.getDetail().equals("Not dispatched: At least one area must be specified")));
        verify(auditService).record(eq(principal), eq(orgId), eq(robotId), eq("TASK_START"), eq("FAILED"),
                eq("Not dispatched: At least one area must be specified"), any(), any());
        verify(auditService, never()).record(any(), any(), any(), eq("TASK_START"), eq("SUCCESS"), any(), any(), any());
    }

    // ------------------------------------------------------------------
    // Non-CLEANING and non-START transitions — existing bookkeeping-only
    // behavior must be completely unchanged.
    // ------------------------------------------------------------------

    @Test
    void startingANonCleaningTask_neverCallsRobotCommandService() {
        UUID robotId = UUID.randomUUID();
        RobotTask task = aTask(robotId, UUID.randomUUID(), "RETURN_TO_DOCK", null, TaskLifecycleStatus.CREATED);
        when(robotTaskRepository.lockByIdForUpdate(task.getId())).thenReturn(Optional.of(task));

        RobotTask result = service().transition(principal, task.getId(), "START");

        assertThat(result.getStatus()).isEqualTo("RUNNING");
        verifyNoInteractions(robotCommandService);
    }

    @Test
    void pausingACleaningTask_neverCallsRobotCommandService_onlyStartDoes() {
        UUID robotId = UUID.randomUUID();
        RobotTask task = aTask(robotId, UUID.randomUUID(), "CLEANING", null, TaskLifecycleStatus.RUNNING);
        when(robotTaskRepository.findById(task.getId())).thenReturn(Optional.of(task));

        RobotTask result = service().transition(principal, task.getId(), "PAUSE");

        assertThat(result.getStatus()).isEqualTo("PAUSED");
        verifyNoInteractions(robotCommandService);
    }

    @Test
    void cancellingACreatedCleaningTask_neverCallsRobotCommandService() {
        UUID robotId = UUID.randomUUID();
        RobotTask task = aTask(robotId, UUID.randomUUID(), "CLEANING", null, TaskLifecycleStatus.CREATED);
        when(robotTaskRepository.findById(task.getId())).thenReturn(Optional.of(task));

        RobotTask result = service().transition(principal, task.getId(), "CANCEL");

        assertThat(result.getStatus()).isEqualTo("CANCELLED");
        verifyNoInteractions(robotCommandService);
    }

    @Test
    void invalidTransition_isRejectedBeforeEverCheckingCleaningParameters() {
        UUID robotId = UUID.randomUUID();
        // COMPLETED is not a valid "from" state for START — must fail on the transition
        // check itself, never reach CLEANING parameter parsing.
        RobotTask task = aTask(robotId, UUID.randomUUID(), "CLEANING", null, TaskLifecycleStatus.COMPLETED);
        when(robotTaskRepository.lockByIdForUpdate(task.getId())).thenReturn(Optional.of(task));

        ApiException ex = Assertions.assertThrows(ApiException.class, () -> service().transition(principal, task.getId(), "START"));

        assertThat(ex.getErrorCode()).isEqualTo(SakarErrorCode.INVALID_TASK_TRANSITION);
        verifyNoInteractions(robotCommandService);
    }

    // ------------------------------------------------------------------
    // C1 fix — duplicate-dispatch protection (pessimistic row lock on START).
    // ------------------------------------------------------------------

    @Test
    void startingATask_usesThePessimisticLockingReadRow_neverThePlainUnlockedFindById() {
        // Confirms transition("START") goes through lockByIdForUpdate (SELECT ...
        // FOR UPDATE) rather than the plain, unlocked findById every other read
        // (GET endpoints, PAUSE/RESUME/STOP/CANCEL) uses.
        UUID robotId = UUID.randomUUID();
        RobotTask task = aTask(robotId, UUID.randomUUID(), "RETURN_TO_DOCK", null, TaskLifecycleStatus.CREATED);
        when(robotTaskRepository.lockByIdForUpdate(task.getId())).thenReturn(Optional.of(task));

        service().transition(principal, task.getId(), "START");

        verify(robotTaskRepository).lockByIdForUpdate(task.getId());
        verify(robotTaskRepository, never()).findById(any());
    }

    @Test
    void secondStartAfterTheFirstHasAlreadyTransitioned_isRejected_andNeverCallsRobotCommandServiceAgain() {
        // Deterministic, single-threaded proof of the invariant the pessimistic
        // lock exists to guarantee under real concurrency: once a START has
        // moved a task out of CREATED, a second START for the same task id can
        // never reach dispatchCleaningStart again — it is rejected by the
        // existing ALLOWED_FROM check on re-read, exactly as it would be for a
        // second request that had to wait for the first transaction's row lock
        // to release before performing that same re-read. A genuine two-thread
        // test (see RobotTaskControllerTest) additionally exercises the actual
        // blocking-on-the-DB-lock behavior that a single-threaded test like
        // this one cannot: here, "the second request re-reads and rejects" is
        // asserted directly rather than induced by real lock contention.
        UUID robotId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        RobotTask task = aTask(robotId, orgId, "CLEANING",
                "{\"mode\":\"SWEEP\",\"areaIds\":[\"" + UUID.randomUUID() + "\"]}", TaskLifecycleStatus.CREATED);
        when(robotTaskRepository.lockByIdForUpdate(task.getId())).thenReturn(Optional.of(task));
        RobotCommand command = new RobotCommand();
        command.setStatus(CommandStatus.COMMAND_DISPATCHED);
        when(robotCommandService.issue(eq(principal), eq(robotId), eq("START_TASK"), any()))
                .thenReturn(new RobotCommandService.Issued(command, true, "accepted"));
        RobotTaskService service = service();

        RobotTask first = service.transition(principal, task.getId(), "START");
        assertThat(first.getStatus()).isEqualTo("RUNNING");

        // The mocked repository always returns the SAME mutated `task` instance
        // (lockByIdForUpdate/save both echo it back), so this second call sees
        // exactly what a real re-read after the first transaction committed
        // would see: status is no longer CREATED.
        ApiException ex = Assertions.assertThrows(ApiException.class,
                () -> service.transition(principal, task.getId(), "START"));
        assertThat(ex.getErrorCode()).isEqualTo(SakarErrorCode.INVALID_TASK_TRANSITION);

        verify(robotCommandService, org.mockito.Mockito.times(1)).issue(any(), any(), any(), any());
    }

    private static TaskEvent argThat(java.util.function.Predicate<TaskEvent> predicate) {
        return org.mockito.ArgumentMatchers.argThat(predicate::test);
    }
}
