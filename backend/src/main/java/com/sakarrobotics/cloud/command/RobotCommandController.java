package com.sakarrobotics.cloud.command;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.sakarrobotics.cloud.command.dto.CommandResponse;
import com.sakarrobotics.cloud.command.dto.IssueCommandRequest;
import com.sakarrobotics.cloud.common.web.ApiResponse;
import com.sakarrobotics.cloud.security.UserPrincipal;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Real, non-lock robot command API (Roadmap Phase 6). {@code LOCK}/{@code
 * UNLOCK} are rejected by {@link RobotCommandService} — this endpoint has
 * no code path that can issue either, by construction, not just by
 * convention.
 */
@RestController
@RequestMapping("/api/v1/robots/{robotId}/commands")
@RequiredArgsConstructor
@Tag(name = "Commands")
public class RobotCommandController {

    private final RobotCommandService robotCommandService;

    @PostMapping
    @PreAuthorize("hasAuthority('ROBOT_CONTROL')")
    @Operation(summary = "Issue a non-lock command to a robot (best-effort MQTT publish; delivery/execution not confirmed)")
    public ResponseEntity<ApiResponse<CommandResponse>> issue(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID robotId,
            @Valid @RequestBody IssueCommandRequest request) {
        RobotCommandService.Issued issued = robotCommandService.issue(principal, robotId, request.commandType(), request.params());
        CommandResponse response = CommandResponse.from(issued.command(), issued.dispatched(), issued.dispatchNote());
        return ResponseEntity.status(201).body(ApiResponse.ok(response));
    }

    @GetMapping("/{commandId}")
    @PreAuthorize("hasAuthority('ROBOT_VIEW')")
    @Operation(summary = "Get a command's current (never-fabricated) status")
    public ApiResponse<CommandResponse> get(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID robotId,
            @PathVariable UUID commandId) {
        RobotCommand command = robotCommandService.getAccessibleOrThrow(principal, commandId);
        boolean dispatched = command.getStatus() == CommandStatus.SENT;
        String note = dispatched
                ? "Published to MQTT — SakarC40Agent does not yet consume commands, so delivery/execution is not confirmed."
                : "Not dispatched.";
        return ApiResponse.ok(CommandResponse.from(command, dispatched, note));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('ROBOT_VIEW')")
    @Operation(summary = "List commands issued to a robot")
    public ApiResponse<Page<CommandResponse>> list(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID robotId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int pageSize) {
        Page<CommandResponse> result = robotCommandService.listByRobot(principal, robotId, page, pageSize)
                .map(c -> CommandResponse.from(c, c.getStatus() == CommandStatus.SENT, null));
        return ApiResponse.ok(result);
    }
}
