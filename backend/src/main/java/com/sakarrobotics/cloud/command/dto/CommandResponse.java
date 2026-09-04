package com.sakarrobotics.cloud.command.dto;

import java.time.Instant;
import java.util.UUID;

import com.sakarrobotics.cloud.command.RobotCommand;

/**
 * {@code dispatched}/{@code dispatchNote} are the honest bridge between
 * "the backend accepted and persisted this command" and "a robot actually
 * received/executed it" (Master Requirements Part 40's governing rule) —
 * {@code status} alone (a {@link com.sakarrobotics.cloud.command.CommandStatus})
 * never implies physical execution. For an MQTT/Sakar-agent robot, status
 * only ever advances past {@code SENT} via the agent's own later report
 * (see {@code CommandResultIngestionService}). For a KEENON_CLOUD robot,
 * the Keenon Open Platform round trip happens synchronously inside {@code
 * RobotCommandService.issue()} itself, so status may already be {@code
 * COMMAND_DISPATCHED}/{@code COMMAND_FAILED} in the very response to the
 * issuing request — neither ever claims physical completion either.
 */
public record CommandResponse(
        UUID id,
        UUID robotId,
        String commandType,
        String status,
        String nonce,
        Instant expiresAt,
        Instant sentAt,
        boolean dispatched,
        String dispatchNote,
        Instant createdAt) {

    public static CommandResponse from(RobotCommand command, boolean dispatched, String dispatchNote) {
        return new CommandResponse(command.getId(), command.getRobotId(), command.getCommandType(),
                command.getStatus().name(), command.getNonce(), command.getExpiresAt(), command.getSentAt(),
                dispatched, dispatchNote, command.getCreatedAt());
    }
}
