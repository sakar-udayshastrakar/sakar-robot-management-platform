package com.sakarrobotics.cloud.telemetry;

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
import com.sakarrobotics.cloud.telemetry.dto.RobotTelemetryResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * Read-only history for {@code robot_telemetry} — the raw time series
 * already populated by {@link TelemetryIngestionService} from inbound MQTT
 * {@code TELEMETRY} envelopes (Roadmap Phase 9 web-platform gap analysis,
 * "existing data -> real web UI"). This never invents a value: if no
 * telemetry has been ingested for a robot yet, the page is simply empty.
 *
 * <p>Gated on {@code ROBOT_LOG_VIEW} rather than {@code ROBOT_VIEW} —
 * telemetry history is diagnostic/historical data, the same tier as
 * events/errors/logs, not the live-status read {@code ROBOT_VIEW} already
 * covers via {@code GET /robots/{id}/status}. Deliberately not gated on any
 * {@code RobotCapabilityType} (e.g. {@code GET_TELEMETRY}): that capability
 * flag was graded against the Keenon Cloud REST adapter, not against
 * whether a robot's agent reports telemetry over MQTT — the two are
 * independent, so reusing it here could wrongly block a robot that reports
 * real telemetry via MQTT.
 */
@RestController
@RequestMapping("/api/v1/robots/{robotId}/telemetry")
@RequiredArgsConstructor
@Tag(name = "Telemetry")
public class TelemetryController {

    private final RobotService robotService;
    private final RobotTelemetryRepository robotTelemetryRepository;

    @GetMapping
    @PreAuthorize("hasAuthority('ROBOT_LOG_VIEW')")
    @Operation(summary = "List a robot's ingested telemetry history, most recent first")
    public ApiResponse<Page<RobotTelemetryResponse>> list(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID robotId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int pageSize) {
        robotService.getAccessibleOrThrow(principal, robotId);
        Page<RobotTelemetryResponse> result = robotTelemetryRepository
                .findByRobotIdOrderByRecordedAtDesc(robotId, PageRequest.of(page, pageSize))
                .map(RobotTelemetryResponse::from);
        return ApiResponse.ok(result);
    }
}
