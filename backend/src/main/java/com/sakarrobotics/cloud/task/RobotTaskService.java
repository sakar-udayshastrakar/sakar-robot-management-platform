package com.sakarrobotics.cloud.task;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sakarrobotics.cloud.audit.AuditService;
import com.sakarrobotics.cloud.cleaning.CleaningSessionService;
import com.sakarrobotics.cloud.command.RobotCommandService;
import com.sakarrobotics.cloud.common.error.ApiException;
import com.sakarrobotics.cloud.common.error.SakarErrorCode;
import com.sakarrobotics.cloud.robot.registry.Robot;
import com.sakarrobotics.cloud.robot.registry.RobotCapabilityService;
import com.sakarrobotics.cloud.robot.registry.RobotCapabilityType;
import com.sakarrobotics.cloud.robot.registry.RobotService;
import com.sakarrobotics.cloud.security.UserPrincipal;
import com.sakarrobotics.cloud.security.access.TenantAccessGuard;

import lombok.RequiredArgsConstructor;
import tools.jackson.databind.ObjectMapper;

/**
 * Real task lifecycle bookkeeping (Roadmap Phase 6, API spec §1.6 shape).
 * This is Sakar-side orchestration record-keeping only — it does not itself
 * talk to any vendor API; the exceptions are starting and stopping a {@code
 * CLEANING} task, both of which bridge into the EXISTING {@link
 * RobotCommandService#issue} (the same internal service method {@code
 * RobotCommandController} calls) rather than duplicating any Keenon/command
 * validation or dispatch logic here. PAUSE/RESUME/CANCEL remain
 * bookkeeping-only for now — bridging them is a separate, not-yet-approved
 * slice. Every transition is recorded as both an updated {@link
 * RobotTask#getStatus()} and an immutable {@link TaskEvent} row, so a
 * task's full history is always reconstructable.
 */
@Service
@RequiredArgsConstructor
public class RobotTaskService {

    private static final Map<String, Set<String>> ALLOWED_FROM = Map.of(
            "START", Set.of(TaskLifecycleStatus.CREATED.name()),
            "PAUSE", Set.of(TaskLifecycleStatus.RUNNING.name()),
            "RESUME", Set.of(TaskLifecycleStatus.PAUSED.name()),
            "STOP", Set.of(TaskLifecycleStatus.RUNNING.name(), TaskLifecycleStatus.PAUSED.name()),
            "CANCEL", Set.of(TaskLifecycleStatus.CREATED.name(), TaskLifecycleStatus.RUNNING.name(), TaskLifecycleStatus.PAUSED.name()));

    private static final Map<String, TaskLifecycleStatus> TARGET_STATUS = Map.of(
            "START", TaskLifecycleStatus.RUNNING,
            "PAUSE", TaskLifecycleStatus.PAUSED,
            "RESUME", TaskLifecycleStatus.RUNNING,
            "STOP", TaskLifecycleStatus.COMPLETED,
            "CANCEL", TaskLifecycleStatus.CANCELLED);

    private final RobotTaskRepository robotTaskRepository;
    private final TaskEventRepository taskEventRepository;
    private final RobotService robotService;
    private final RobotCapabilityService robotCapabilityService;
    private final CleaningSessionService cleaningSessionService;
    private final TenantAccessGuard tenantAccessGuard;
    private final AuditService auditService;
    private final RobotCommandService robotCommandService;
    private final ObjectMapper objectMapper;

    @Transactional
    public RobotTask create(UserPrincipal principal, UUID robotId, String taskType, String parameters) {
        Robot robot = robotService.getAccessibleOrThrow(principal, robotId);
        robotCapabilityService.assertSupported(robot.getRobotModelId(), RobotCapabilityType.START_TASK);

        RobotTask task = new RobotTask();
        task.setRobotId(robotId);
        task.setOrganizationId(robot.getOrganizationId());
        task.setCreatedBy(principal.getUserId());
        task.setTaskType(taskType);
        task.setParameters(parameters);
        task.setStatus(TaskLifecycleStatus.CREATED.name());
        RobotTask saved = robotTaskRepository.save(task);

        recordEvent(saved.getId(), "CREATED", null);
        auditService.record(principal, robot.getOrganizationId(), robotId, "TASK_CREATED", "SUCCESS", null, null, null);
        return saved;
    }

    public RobotTask getAccessibleOrThrow(UserPrincipal principal, UUID taskId) {
        RobotTask task = robotTaskRepository.findById(taskId)
                .orElseThrow(() -> new ApiException(SakarErrorCode.TASK_NOT_FOUND, "Task not found: " + taskId));
        if (!tenantAccessGuard.hasOrganizationAccess(principal, task.getOrganizationId())) {
            throw new ApiException(SakarErrorCode.TASK_NOT_FOUND, "Task not found: " + taskId);
        }
        return task;
    }

    /**
     * Same tenant-access resolution as {@link #getAccessibleOrThrow}, but via
     * {@link RobotTaskRepository#lockByIdForUpdate} — used ONLY for the START
     * and STOP transitions (see {@link #transition}), never for a plain read
     * (GET endpoints, PAUSE/RESUME/CANCEL), so this never adds lock
     * contention to anything but the two actions that can trigger a physical
     * dispatch.
     */
    private RobotTask getAccessibleOrThrowForUpdate(UserPrincipal principal, UUID taskId) {
        RobotTask task = robotTaskRepository.lockByIdForUpdate(taskId)
                .orElseThrow(() -> new ApiException(SakarErrorCode.TASK_NOT_FOUND, "Task not found: " + taskId));
        if (!tenantAccessGuard.hasOrganizationAccess(principal, task.getOrganizationId())) {
            throw new ApiException(SakarErrorCode.TASK_NOT_FOUND, "Task not found: " + taskId);
        }
        return task;
    }

    public Page<RobotTask> listByRobot(UserPrincipal principal, UUID robotId, int page, int pageSize) {
        robotService.getAccessibleOrThrow(principal, robotId);
        return robotTaskRepository.findByRobotIdOrderByIdDesc(robotId, PageRequest.of(page, pageSize));
    }

    public List<TaskEvent> events(UserPrincipal principal, UUID taskId) {
        getAccessibleOrThrow(principal, taskId);
        return taskEventRepository.findByTaskIdOrderByIdAsc(taskId);
    }

    @Transactional
    public RobotTask transition(UserPrincipal principal, UUID taskId, String action) {
        // START and STOP are the only transitions that can trigger a real, physical
        // dispatch (CLEANING tasks — see dispatchCleaningStart/dispatchCleaningStop),
        // so they're the only ones that need the pessimistic row lock: a second
        // concurrent START/STOP for the same task blocks here until the first
        // transaction commits, then re-reads the now-updated status and is correctly
        // rejected by the ALLOWED_FROM check below — it can never reach the dispatch
        // methods a second time. PAUSE/RESUME/CANCEL never dispatch, so they keep
        // using the plain unlocked read.
        boolean lockForDispatch = "START".equals(action) || "STOP".equals(action);
        RobotTask task = lockForDispatch
                ? getAccessibleOrThrowForUpdate(principal, taskId)
                : getAccessibleOrThrow(principal, taskId);
        Set<String> allowedFrom = ALLOWED_FROM.get(action);
        if (allowedFrom == null || !allowedFrom.contains(task.getStatus())) {
            throw new ApiException(SakarErrorCode.INVALID_TASK_TRANSITION,
                    "Cannot " + action + " a task in status " + task.getStatus());
        }

        if ("START".equals(action) && "CLEANING".equalsIgnoreCase(task.getTaskType())) {
            RobotTask failed = dispatchCleaningStart(principal, task);
            if (failed != null) {
                // Never dispatched — the task must not move to RUNNING for an attempt
                // that never reached the robot. dispatchCleaningStart already recorded
                // the FAILED status, its own TaskEvent, and its own audit entry.
                return failed;
            }
        }

        if ("STOP".equals(action) && "CLEANING".equalsIgnoreCase(task.getTaskType())) {
            RobotTask failed = dispatchCleaningStop(principal, task);
            if (failed != null) {
                // Never dispatched — the task must not report COMPLETED for a stop
                // attempt that never reached the robot. dispatchCleaningStop already
                // recorded the FAILED status, its own TaskEvent, and its own audit
                // entry.
                return failed;
            }
        }

        TaskLifecycleStatus target = TARGET_STATUS.get(action);
        task.setStatus(target.name());
        RobotTask saved = robotTaskRepository.save(task);
        recordEvent(taskId, action, null);
        auditService.record(principal, task.getOrganizationId(), task.getRobotId(), "TASK_" + action, "SUCCESS", null, null, null);

        if (target == TaskLifecycleStatus.COMPLETED && "CLEANING".equalsIgnoreCase(task.getTaskType())) {
            Robot robot = robotService.getAccessibleOrThrow(principal, task.getRobotId());
            cleaningSessionService.recordFromCompletedTask(saved, robot.getSiteId());
        }
        return saved;
    }

    /**
     * Bridges a CLEANING task's START transition into the EXISTING {@link
     * RobotCommandService#issue} — the same internal service method {@code
     * RobotCommandController} calls — rather than a second Keenon/OAuth/MQTT
     * implementation. Reuses that method's own command-type/mode/area
     * validation and Keenon adapter dispatch wholesale; this method only
     * does the minimal structural check needed to avoid ever attempting a
     * dispatch with obviously-incomplete parameters (a totally missing/blank
     * {@code mode} or empty {@code areaIds}), and only for a request-shape
     * problem that {@code issue()} itself has no visibility into (it never
     * sees the raw JSON string {@link RobotTask#getParameters()} holds).
     *
     * <p>Returns {@code null} when the command was dispatched (the caller
     * should proceed to the normal RUNNING transition), or the already-saved
     * {@code FAILED} task when it was not — mirroring the same "not an
     * uncaught exception, a normal reported outcome" convention {@code
     * RobotCommandService.issue}/{@code POST /robots/{id}/commands} already
     * use (a 200 response whose payload says {@code dispatched=false} rather
     * than an HTTP error), which also sidesteps this method's own
     * {@code @Transactional} boundary: nothing here is thrown, so nothing
     * gets rolled back — the RobotCommand/CommandResult rows {@code issue()}
     * already persisted, and this method's own FAILED status/event/audit,
     * all commit together.
     */
    private RobotTask dispatchCleaningStart(UserPrincipal principal, RobotTask task) {
        Map<String, Object> params = parseCleaningStartParams(task.getParameters());
        RobotCommandService.Issued issued = robotCommandService.issue(principal, task.getRobotId(), "START_TASK", params);
        if (issued.dispatched()) {
            return null;
        }

        task.setStatus(TaskLifecycleStatus.FAILED.name());
        RobotTask saved = robotTaskRepository.save(task);
        recordEvent(task.getId(), "START_FAILED", issued.dispatchNote());
        auditService.record(principal, task.getOrganizationId(), task.getRobotId(), "TASK_START", "FAILED",
                issued.dispatchNote(), null, null);
        return saved;
    }

    /**
     * Bridges a CLEANING task's STOP transition into the EXISTING {@link
     * RobotCommandService#issue} — mirrors {@link #dispatchCleaningStart}
     * exactly, using {@code STOP_TASK} instead of {@code START_TASK}.
     * Unlike START, no task-supplied parameters are required or sent:
     * {@code STOP_TASK} maps to {@link
     * com.sakarrobotics.cloud.integration.keenon.KeenonRobotAdapter#stopTask}
     * (the real, already-implemented {@code POST
     * /api/open/custom/clean/robot/finish/task} call), which takes only the
     * robot itself — there is no per-call payload for it to validate or
     * forward.
     *
     * <p>Returns {@code null} when the command was dispatched (the caller
     * should proceed to the normal COMPLETED transition), or the
     * already-saved {@code FAILED} task when it was not — the same
     * "not an uncaught exception, a normal reported outcome" convention
     * {@code dispatchCleaningStart} already uses, so a rejected/undispatched
     * STOP_TASK command can never be reported to the caller as a
     * successfully stopped task.
     */
    private RobotTask dispatchCleaningStop(UserPrincipal principal, RobotTask task) {
        RobotCommandService.Issued issued = robotCommandService.issue(principal, task.getRobotId(), "STOP_TASK", Map.of());
        if (issued.dispatched()) {
            return null;
        }

        task.setStatus(TaskLifecycleStatus.FAILED.name());
        RobotTask saved = robotTaskRepository.save(task);
        recordEvent(task.getId(), "STOP_FAILED", issued.dispatchNote());
        auditService.record(principal, task.getOrganizationId(), task.getRobotId(), "TASK_STOP", "FAILED",
                issued.dispatchNote(), null, null);
        return saved;
    }

    /**
     * The only structural check performed here — {@code mode}/{@code
     * areaIds} presence — before handing the parsed map to {@link
     * RobotCommandService#issue}, which owns every deeper check (whether
     * {@code mode} is one of the Keenon-supported cleaning modes, whether
     * each {@code areaIds} entry is a valid, active {@code
     * KeenonAreaMapping}, {@code repeatCount} defaulting to 1 when absent).
     * Never invents a parameter this method doesn't already require, and
     * never guesses a default for {@code mode}/{@code areaIds} — a CLEANING
     * task's own {@code parameters} column (set at creation time) is the
     * only source for them.
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> parseCleaningStartParams(String rawParameters) {
        if (rawParameters == null || rawParameters.isBlank()) {
            throw new ApiException(SakarErrorCode.VALIDATION_FAILED,
                    "CLEANING task requires parameters with 'mode' and 'areaIds' to start");
        }
        Map<String, Object> parsed;
        try {
            parsed = (Map<String, Object>) (Map<?, ?>) objectMapper.readValue(rawParameters, Map.class);
        } catch (Exception ex) {
            throw new ApiException(SakarErrorCode.VALIDATION_FAILED,
                    "CLEANING task parameters must be a JSON object with 'mode' and 'areaIds'");
        }
        if (!(parsed.get("mode") instanceof String modeStr) || modeStr.isBlank()) {
            throw new ApiException(SakarErrorCode.VALIDATION_FAILED,
                    "CLEANING task requires a non-blank 'mode' in parameters");
        }
        if (!(parsed.get("areaIds") instanceof List<?> areaIdList) || areaIdList.isEmpty()) {
            throw new ApiException(SakarErrorCode.VALIDATION_FAILED,
                    "CLEANING task requires at least one entry in 'areaIds'");
        }
        // Structural UUID-format check only — NOT whether the id resolves to an
        // active KeenonAreaMapping (that deeper check stays inside
        // KeenonRobotAdapter.startTask, reused as-is). Without this,
        // java.util.UUID.fromString(...) deep inside KeenonRobotAdapter.startTask
        // throws an uncaught IllegalArgumentException (not an ApiException) for a
        // malformed entry, which dispatchViaKeenonAdapter's own
        // catch (ApiException ex) does not catch — this rejects that shape here,
        // before any RobotCommand row is created or any Keenon call is attempted.
        for (Object areaId : areaIdList) {
            try {
                UUID.fromString(String.valueOf(areaId));
            } catch (IllegalArgumentException ex) {
                throw new ApiException(SakarErrorCode.VALIDATION_FAILED,
                        "CLEANING task 'areaIds' entry is not a valid id: " + areaId);
            }
        }
        return parsed;
    }

    private void recordEvent(UUID taskId, String eventType, String detail) {
        TaskEvent event = new TaskEvent();
        event.setTaskId(taskId);
        event.setEventType(eventType);
        event.setDetail(detail);
        taskEventRepository.save(event);
    }
}
