package com.sakarrobotics.cloud.command.dto;

import java.time.Instant;
import java.util.UUID;

import com.sakarrobotics.cloud.command.CommandResult;

/**
 * One lifecycle-history row for a command (see {@link
 * com.sakarrobotics.cloud.command.CommandResultIngestionService} and {@code
 * CommandExpiryService} — its only two writers). {@code result} is always a
 * {@code CommandStatus} name (e.g. {@code COMMAND_RECEIVED}, {@code
 * COMMAND_SUCCESS}), never fabricated. {@code command_results} is
 * append-only, so this history is never edited in place — only appended to.
 */
public record CommandResultResponse(
        UUID commandId,
        String result,
        String detail,
        Long durationMs,
        Instant createdAt) {

    public static CommandResultResponse from(CommandResult result) {
        return new CommandResultResponse(
                result.getCommandId(), result.getResult(), result.getDetail(), result.getDurationMs(), result.getCreatedAt());
    }
}
