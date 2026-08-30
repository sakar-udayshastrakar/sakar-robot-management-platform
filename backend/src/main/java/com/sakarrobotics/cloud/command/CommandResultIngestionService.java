package com.sakarrobotics.cloud.command;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sakarrobotics.cloud.audit.AuditService;
import com.sakarrobotics.cloud.mqtt.dto.CommandResultDetail;
import com.sakarrobotics.cloud.robot.registry.Robot;

import lombok.RequiredArgsConstructor;

/**
 * Applies an agent-reported {@link CommandResultDetail} (arriving as an
 * {@code eventType="COMMAND_RESULT"} EVENT — see that DTO's Javadoc for why
 * no new MQTT message type/topic was added) to the originating {@link
 * RobotCommand}, and appends a {@link CommandResult} history row.
 *
 * <p>Called from {@link com.sakarrobotics.cloud.mqtt.MqttInboundMessageService}
 * <em>after</em> the generic event-ingestion/tenant/dedup checks have
 * already passed for the envelope as a whole — this service still performs
 * its own, narrower check that the reported {@code commandId} actually
 * belongs to the robot the envelope claims to be from (an agent must not
 * be able to report a result for another robot's command, even though the
 * outer envelope's own robotId was already verified).
 *
 * <p><strong>Idempotency (Roadmap Phase 6/7 "Robot Agent Command Loop"):</strong>
 * a {@link CommandResult} row is appended for every accepted report (that
 * table is an append-only ledger of every lifecycle signal received, by
 * design), but the mutable {@link RobotCommand#getStatus()} pointer only
 * ever moves forward per {@link CommandStatus#progressRank()} and is never
 * touched once {@link CommandStatus#isTerminal()} — this is what makes a
 * duplicated or out-of-order MQTT redelivery safe: {@link
 * com.sakarrobotics.cloud.mqtt.MqttDedupGuard} already prevents the exact
 * same envelope being processed twice, but nothing upstream prevents the
 * agent from legitimately publishing several distinct result events for
 * the same command over time, and a network can reorder them in transit.
 */
@Service
@RequiredArgsConstructor
public class CommandResultIngestionService {

    private static final Logger log = LoggerFactory.getLogger(CommandResultIngestionService.class);

    private final RobotCommandRepository robotCommandRepository;
    private final CommandResultRepository commandResultRepository;
    private final AuditService auditService;

    @Transactional
    public void record(Robot robot, CommandResultDetail detail) {
        UUID commandId = parseCommandId(detail.commandId());
        if (commandId == null) {
            log.warn("COMMAND_RESULT from robot {} had a missing/malformed commandId — ignored", robot.getId());
            return;
        }

        Optional<RobotCommand> maybeCommand = robotCommandRepository.findById(commandId);
        if (maybeCommand.isEmpty()) {
            log.warn("COMMAND_RESULT from robot {} referenced unknown commandId {} — ignored", robot.getId(), commandId);
            return;
        }
        RobotCommand command = maybeCommand.get();

        if (!command.getRobotId().equals(robot.getId())) {
            // A robot reporting a result for a command that was issued to a different robot —
            // never trust the claim, regardless of how it got past the outer envelope's own
            // (correct) robotId check.
            log.warn("COMMAND_RESULT from robot {} claimed commandId {} which belongs to robot {} — rejected",
                    robot.getId(), commandId, command.getRobotId());
            auditService.record(null, robot.getOrganizationId(), robot.getId(), "COMMAND_RESULT_REJECTED",
                    "COMMAND_UNAUTHORIZED", "commandId belongs to a different robot", null, null);
            return;
        }

        CommandStatus reported = mapReportedStatus(detail.status());
        if (reported == null) {
            log.warn("COMMAND_RESULT from robot {} for command {} had an unrecognized status '{}' — ignored",
                    robot.getId(), commandId, detail.status());
            return;
        }

        appendResult(command, reported, detail);
        applyStatusIfForward(command, reported);

        auditService.record(null, robot.getOrganizationId(), robot.getId(), "COMMAND_RESULT_" + reported.name(),
                reported.name(), null, null, null);
    }

    private void appendResult(RobotCommand command, CommandStatus reported, CommandResultDetail detail) {
        CommandResult result = new CommandResult();
        result.setCommandId(command.getId());
        result.setResult(reported.name());
        result.setDetail(detail.detail());
        result.setDurationMs(detail.durationMs());
        commandResultRepository.save(result);
    }

    private void applyStatusIfForward(RobotCommand command, CommandStatus reported) {
        if (command.getStatus().isTerminal()) {
            // Frozen — a late/out-of-order report must never resurrect a finished command.
            return;
        }
        if (reported.progressRank() < command.getStatus().progressRank()) {
            // Out-of-order redelivery of an earlier lifecycle stage — the CommandResult row
            // above still recorded it (for audit/debugging), but the current-status pointer
            // only ever moves forward.
            return;
        }
        command.setStatus(reported);
        if (reported.isTerminal()) {
            command.setCompletedAt(Instant.now());
        }
        robotCommandRepository.save(command);
    }

    private static UUID parseCommandId(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException malformed) {
            return null;
        }
    }

    /**
     * Agent vocabulary (RECEIVED/EXECUTING/COMPLETED/DISPATCHED/FAILED/TIMEOUT)
     * onto this backend's existing {@link CommandStatus}. {@code ACCEPTED}
     * from the command-lifecycle vocabulary is deliberately not
     * agent-reported — it corresponds to this backend's own pre-dispatch
     * {@code AUTHORIZED}/{@code SENT} states, which are set by {@link
     * RobotCommandService} before the agent ever sees the command.
     *
     * <p>{@code DISPATCHED} (Roadmap Phase 7 "RETURN_TO_DOCK") maps to
     * {@link CommandStatus#COMMAND_DISPATCHED}, not {@code COMMAND_SUCCESS}
     * — it means a local robot control interface accepted the command,
     * not that the command's real-world effect was confirmed complete.
     * {@code PeanutSdkReturnToDockExecutor} (agent side) is the only
     * current producer of this status.
     */
    private static CommandStatus mapReportedStatus(String status) {
        if (status == null) {
            return null;
        }
        return switch (status) {
            case "RECEIVED" -> CommandStatus.COMMAND_RECEIVED;
            case "EXECUTING" -> CommandStatus.RUNNING;
            case "COMPLETED" -> CommandStatus.COMMAND_SUCCESS;
            case "DISPATCHED" -> CommandStatus.COMMAND_DISPATCHED;
            case "FAILED" -> CommandStatus.COMMAND_FAILED;
            case "TIMEOUT" -> CommandStatus.COMMAND_TIMEOUT;
            default -> null;
        };
    }
}
