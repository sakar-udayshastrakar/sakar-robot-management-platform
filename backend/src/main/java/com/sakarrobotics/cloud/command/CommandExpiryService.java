package com.sakarrobotics.cloud.command;

import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sakarrobotics.cloud.audit.AuditService;

import lombok.RequiredArgsConstructor;

/**
 * Periodic sweep that resolves commands the agent never reported back on
 * before {@link RobotCommand#getExpiresAt()} (Roadmap Phase 6/7 "Robot
 * Agent Command Loop" idempotency requirement: an MQTT publish succeeding
 * is not the same thing as a robot completing the command, and a command
 * that silently never gets a result must not be left in {@code SENT}
 * forever). Follows the exact same "real, gated, wall-clock-driven sweep"
 * pattern as {@link com.sakarrobotics.cloud.alert.RobotOfflineWatcherService}.
 *
 * <p>Gated by {@code sakar.commands.expiry-sweep.enabled} (forced
 * {@code false} in the test profile) so tests never race this background
 * job — tests call {@link #sweep()} directly instead, exactly like {@code
 * RobotOfflineWatcherService}'s own test does for its sweep.
 */
@Service
@RequiredArgsConstructor
public class CommandExpiryService {

    private static final Logger log = LoggerFactory.getLogger(CommandExpiryService.class);

    private static final List<CommandStatus> NON_TERMINAL_STATUSES = List.of(
            CommandStatus.AUTHORIZED, CommandStatus.SIGNED, CommandStatus.SENT,
            CommandStatus.COMMAND_RECEIVED, CommandStatus.RUNNING);

    private final RobotCommandRepository robotCommandRepository;
    private final CommandResultRepository commandResultRepository;
    private final AuditService auditService;

    @Value("${sakar.commands.expiry-sweep.enabled:true}")
    private boolean enabled;

    /**
     * The actual {@code @Scheduled} entry point — gated by {@code enabled}
     * so the background timer never fires in tests. {@link #sweep()}
     * itself is deliberately NOT gated, so tests can call it directly
     * (exactly like {@code MqttInboundMessageService.handle}'s own
     * "testable without a running broker/timer" design) even with the
     * background trigger disabled.
     */
    @Scheduled(fixedDelayString = "${sakar.commands.expiry-sweep.interval-ms:15000}")
    public void scheduledSweep() {
        if (enabled) {
            sweep();
        }
    }

    @Transactional
    public void sweep() {
        Instant now = Instant.now();
        List<RobotCommand> overdue = robotCommandRepository.findByStatusInAndExpiresAtBefore(NON_TERMINAL_STATUSES, now);
        for (RobotCommand command : overdue) {
            command.setStatus(CommandStatus.COMMAND_TIMEOUT);
            command.setCompletedAt(now);
            robotCommandRepository.save(command);

            CommandResult result = new CommandResult();
            result.setCommandId(command.getId());
            result.setResult(CommandStatus.COMMAND_TIMEOUT.name());
            result.setDetail("No result reported by the agent before the command expired (expiresAt=" + command.getExpiresAt() + ")");
            commandResultRepository.save(result);

            auditService.record(null, command.getOrganizationId(), command.getRobotId(), "COMMAND_RESULT_COMMAND_TIMEOUT",
                    CommandStatus.COMMAND_TIMEOUT.name(), "expired with no agent report", null, null);
            log.info("Command {} for robot {} timed out with no agent report (expiresAt={})",
                    command.getId(), command.getRobotId(), command.getExpiresAt());
        }
    }
}
