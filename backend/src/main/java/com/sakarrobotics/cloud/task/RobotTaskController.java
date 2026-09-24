package com.sakarrobotics.cloud.task;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.sakarrobotics.cloud.common.web.ApiResponse;
import com.sakarrobotics.cloud.security.UserPrincipal;
import com.sakarrobotics.cloud.task.dto.CreateTaskRequest;
import com.sakarrobotics.cloud.task.dto.TaskDetailResponse;
import com.sakarrobotics.cloud.task.dto.TaskEventResponse;
import com.sakarrobotics.cloud.task.dto.TaskResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Real task orchestration API (Roadmap Phase 6, API spec §1.6 path shape).
 * Bookkeeping only — see {@link RobotTaskService}'s Javadoc for the
 * explicit boundary between this and robot command dispatch.
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "Tasks")
public class RobotTaskController {

    private final RobotTaskService robotTaskService;

    @PostMapping("/api/v1/robots/{robotId}/tasks")
    @PreAuthorize("hasAuthority('ROBOT_TASK_CREATE')")
    @Operation(summary = "Create a task for a robot")
    public ResponseEntity<ApiResponse<TaskResponse>> create(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID robotId,
            @Valid @RequestBody CreateTaskRequest request) {
        RobotTask task = robotTaskService.create(principal, robotId, request.taskType(), request.parameters());
        return ResponseEntity.status(201).body(ApiResponse.ok(TaskResponse.from(task)));
    }

    @GetMapping("/api/v1/robots/{robotId}/tasks")
    @PreAuthorize("hasAuthority('ROBOT_VIEW')")
    @Operation(summary = "List a robot's tasks")
    public ApiResponse<Page<TaskResponse>> listByRobot(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID robotId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int pageSize) {
        return ApiResponse.ok(robotTaskService.listByRobot(principal, robotId, page, pageSize).map(TaskResponse::from));
    }

    @GetMapping("/api/v1/tasks")
    @PreAuthorize("hasAuthority('ROBOT_VIEW')")
    @Operation(summary = "Mission Log — list every task within the caller's organization scope, newest first")
    public ApiResponse<Page<TaskResponse>> listAccessible(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "200") int pageSize) {
        return ApiResponse.ok(robotTaskService.listAccessible(principal, page, pageSize).map(TaskResponse::from));
    }

    @GetMapping("/api/v1/tasks/{id}")
    @PreAuthorize("hasAuthority('ROBOT_VIEW')")
    @Operation(summary = "Get a task and its full event history")
    public ApiResponse<TaskDetailResponse> get(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id) {
        RobotTask task = robotTaskService.getAccessibleOrThrow(principal, id);
        List<TaskEventResponse> events = robotTaskService.events(principal, id).stream().map(TaskEventResponse::from).toList();
        return ApiResponse.ok(new TaskDetailResponse(TaskResponse.from(task), events));
    }

    @PostMapping("/api/v1/tasks/{id}/start")
    @PreAuthorize("hasAuthority('ROBOT_CONTROL')")
    @Operation(summary = "Start a created task")
    public ApiResponse<TaskResponse> start(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id) {
        return ApiResponse.ok(TaskResponse.from(robotTaskService.transition(principal, id, "START")));
    }

    @PostMapping("/api/v1/tasks/{id}/pause")
    @PreAuthorize("hasAuthority('ROBOT_CONTROL')")
    @Operation(summary = "Pause a running task")
    public ApiResponse<TaskResponse> pause(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id) {
        return ApiResponse.ok(TaskResponse.from(robotTaskService.transition(principal, id, "PAUSE")));
    }

    @PostMapping("/api/v1/tasks/{id}/resume")
    @PreAuthorize("hasAuthority('ROBOT_CONTROL')")
    @Operation(summary = "Resume a paused task")
    public ApiResponse<TaskResponse> resume(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id) {
        return ApiResponse.ok(TaskResponse.from(robotTaskService.transition(principal, id, "RESUME")));
    }

    @PostMapping("/api/v1/tasks/{id}/stop")
    @PreAuthorize("hasAuthority('ROBOT_CONTROL')")
    @Operation(summary = "Stop a task, marking it completed")
    public ApiResponse<TaskResponse> stop(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id) {
        return ApiResponse.ok(TaskResponse.from(robotTaskService.transition(principal, id, "STOP")));
    }

    @PostMapping("/api/v1/tasks/{id}/cancel")
    @PreAuthorize("hasAuthority('ROBOT_TASK_CANCEL')")
    @Operation(summary = "Cancel a task")
    public ApiResponse<TaskResponse> cancel(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id) {
        return ApiResponse.ok(TaskResponse.from(robotTaskService.transition(principal, id, "CANCEL")));
    }
}
