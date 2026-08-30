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
import com.sakarrobotics.cloud.common.error.ApiException;
import com.sakarrobotics.cloud.common.error.SakarErrorCode;
import com.sakarrobotics.cloud.robot.registry.Robot;
import com.sakarrobotics.cloud.robot.registry.RobotCapabilityService;
import com.sakarrobotics.cloud.robot.registry.RobotCapabilityType;
import com.sakarrobotics.cloud.robot.registry.RobotService;
import com.sakarrobotics.cloud.security.UserPrincipal;
import com.sakarrobotics.cloud.security.access.TenantAccessGuard;

import lombok.RequiredArgsConstructor;

/**
 * Real task lifecycle bookkeeping (Roadmap Phase 6, API spec §1.6 shape).
 * This is Sakar-side orchestration record-keeping only — it does not
 * dispatch anything to a robot (that is {@code RobotCommandController}'s
 * job, a deliberately separate concern). Every transition is recorded as
 * both an updated {@link RobotTask#getStatus()} and an immutable {@link
 * TaskEvent} row, so a task's full history is always reconstructable.
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
        RobotTask task = getAccessibleOrThrow(principal, taskId);
        Set<String> allowedFrom = ALLOWED_FROM.get(action);
        if (allowedFrom == null || !allowedFrom.contains(task.getStatus())) {
            throw new ApiException(SakarErrorCode.INVALID_TASK_TRANSITION,
                    "Cannot " + action + " a task in status " + task.getStatus());
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

    private void recordEvent(UUID taskId, String eventType, String detail) {
        TaskEvent event = new TaskEvent();
        event.setTaskId(taskId);
        event.setEventType(eventType);
        event.setDetail(detail);
        taskEventRepository.save(event);
    }
}
