package com.sakarrobotics.cloud.srels;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.sakarrobotics.cloud.common.web.ApiResponse;
import com.sakarrobotics.cloud.robot.registry.RobotService;
import com.sakarrobotics.cloud.security.UserPrincipal;
import com.sakarrobotics.cloud.srels.dto.ApplicationLogResponse;
import com.sakarrobotics.cloud.srels.dto.RobotErrorResponse;
import com.sakarrobotics.cloud.srels.dto.RobotEventResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * Read-only history for the SRELS domain — {@code robot_events}, {@code
 * robot_errors}, {@code application_logs} — already populated by {@link
 * RobotEventIngestionService}/{@link RobotErrorIngestionService}/{@code
 * MqttLifecycleLogger} from inbound MQTT traffic (Roadmap Phase 9
 * web-platform gap analysis, "existing data -> real web UI"). Never
 * invents a row: an empty page means nothing has been ingested for that
 * robot yet, not that data was hidden.
 *
 * <p>All three gated on {@code ROBOT_LOG_VIEW}, the same permission the
 * seed data already grants to every role with any robot access
 * (SUPER_ADMIN/ORG_ADMIN/SITE_ADMIN/OPERATOR/TECHNICIAN/VIEWER) — matching
 * {@code robot_errors}/{@code robot_events}/{@code application_logs}' own
 * nature as diagnostic history, not live control. Deliberately not gated
 * on a {@code RobotCapabilityType}: no capability value exists for errors
 * or logs at all, and reusing {@code GET_EVENTS} for events only while
 * leaving errors/logs ungated would be an inconsistent, invented rule —
 * tenant + permission scoping alone is the existing authorization
 * mechanism this reuses.
 */
@RestController
@RequestMapping("/api/v1/robots/{robotId}")
@RequiredArgsConstructor
@Tag(name = "Robot Diagnostics")
public class RobotDiagnosticsController {

    private final RobotService robotService;
    private final RobotEventRepository robotEventRepository;
    private final RobotErrorRepository robotErrorRepository;
    private final ApplicationLogRepository applicationLogRepository;

    @GetMapping("/events")
    @PreAuthorize("hasAuthority('ROBOT_LOG_VIEW')")
    @Operation(summary = "List a robot's ingested event history, most recent first")
    public ApiResponse<Page<RobotEventResponse>> events(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID robotId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int pageSize) {
        robotService.getAccessibleOrThrow(principal, robotId);
        Page<RobotEventResponse> result = robotEventRepository
                .findByRobotIdOrderByOccurredAtDesc(robotId, PageRequest.of(page, pageSize))
                .map(RobotEventResponse::from);
        return ApiResponse.ok(result);
    }

    @GetMapping("/errors")
    @PreAuthorize("hasAuthority('ROBOT_LOG_VIEW')")
    @Operation(summary = "List a robot's ingested error history, most recent first")
    public ApiResponse<Page<RobotErrorResponse>> errors(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID robotId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int pageSize) {
        robotService.getAccessibleOrThrow(principal, robotId);
        Page<RobotErrorResponse> result = robotErrorRepository
                .findByRobotIdOrderByOccurredAtDesc(robotId, PageRequest.of(page, pageSize))
                .map(RobotErrorResponse::from);
        return ApiResponse.ok(result);
    }

    @GetMapping("/logs")
    @PreAuthorize("hasAuthority('ROBOT_LOG_VIEW')")
    @Operation(summary = "List a robot's ingested application-log history, most recent first")
    public ApiResponse<Page<ApplicationLogResponse>> logs(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID robotId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int pageSize) {
        robotService.getAccessibleOrThrow(principal, robotId);
        Page<ApplicationLogResponse> result = applicationLogRepository
                .findByRobotIdOrderByCreatedAtDesc(robotId, PageRequest.of(page, pageSize))
                .map(ApplicationLogResponse::from);
        return ApiResponse.ok(result);
    }
}
