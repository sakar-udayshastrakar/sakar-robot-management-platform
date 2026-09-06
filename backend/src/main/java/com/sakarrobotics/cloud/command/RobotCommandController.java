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
import com.sakarrobotics.cloud.command.dto.CommandResultResponse;
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
        boolean dispatched = command.getSentAt() != null;
        return ApiResponse.ok(CommandResponse.from(command, dispatched, dispatchNoteFor(command)));
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
                .map(c -> CommandResponse.from(c, c.getSentAt() != null, dispatchNoteFor(c)));
        return ApiResponse.ok(result);
    }

    @GetMapping("/{commandId}/results")
    @PreAuthorize("hasAuthority('ROBOT_VIEW')")
    @Operation(summary = "List a command's append-only lifecycle/result history, newest first (paginated, same "
            + "page/pageSize convention as GET /robots/{robotId}/commands). Never fabricated — reflects only what "
            + "the agent (or, for a KEENON_CLOUD robot, the synchronous vendor round trip) actually reported. "
            + "Returns COMMAND_NOT_FOUND if commandId does not exist, does not belong to robotId, or is outside "
            + "the caller's organization.")
    public ApiResponse<Page<CommandResultResponse>> results(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID robotId,
            @PathVariable UUID commandId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int pageSize) {
        return ApiResponse.ok(robotCommandService.listResultsByCommand(principal, robotId, commandId, page, pageSize));
    }

    /**
     * {@code dispatched}/{@code sentAt} only ever mean "the backend
     * published this to MQTT" — the note is the honest, human-readable
     * bridge to "here is what is actually known about what happened after
     * that" (Master Requirements Part 40), driven entirely by {@link
     * CommandStatus}, never fabricated.
     */
    private static String dispatchNoteFor(RobotCommand command) {
        if (command.getSentAt() == null) {
            return "Not dispatched.";
        }
        return switch (command.getStatus()) {
            case SENT -> "Published to MQTT — awaiting the agent's acknowledgement.";
            case COMMAND_RECEIVED -> "The agent received the command and has not yet reported an execution outcome.";
            case RUNNING -> "The agent reported the command is executing.";
            case COMMAND_DISPATCHED ->
                "The agent's local robot control interface accepted and dispatched this command. This does NOT "
                        + "confirm the command's real-world effect completed (e.g. the robot arriving at a "
                        + "charging dock) — no further confirmation signal was available for this command type.";
            case COMMAND_SUCCESS ->
                "The agent reported successful completion. This reflects the agent's own command executor only — "
                        + "see the agent/robot documentation for whether that executor is a real robot call or a software simulation.";
            case COMMAND_FAILED -> "The agent reported that command execution failed.";
            case COMMAND_TIMEOUT -> "No result was reported by the agent before the command expired.";
            case CANCELLED -> "This command was cancelled.";
            default -> "Published to MQTT — awaiting the agent's acknowledgement.";
        };
    }
}
