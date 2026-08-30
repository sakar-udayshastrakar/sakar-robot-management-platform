package com.sakarrobotics.cloud.mqtt.dto;

/**
 * Nested JSON structure carried as the raw string in {@link EventPayload#payload()}
 * when {@link EventPayload#eventType()} is {@code "COMMAND_RESULT"} (Roadmap
 * Phase 6/7 "Robot Agent Command Loop").
 *
 * <p>This deliberately reuses the existing {@code EVENT} message
 * type/topic/ingestion pipeline instead of adding a new MQTT message type,
 * topic, dedup table, or rate-limit rule — an agent reporting "I received
 * / am executing / finished this command" is, structurally, exactly an
 * event about a robot, and every other piece of inbound-message plumbing
 * (schema validation, tenant/robot verification, idempotent delivery,
 * rate limiting) already applies to it unchanged.
 *
 * <p>{@code commandId} must equal the {@code messageId} the backend used
 * when it originally dispatched the command on the {@code commands} topic
 * (see {@code RobotCommandService.buildEnvelope()}, which sets
 * {@code envelope.messageId = command.getId()}) — that is the correlation
 * key {@link CommandResultIngestionService} uses to find the original
 * {@link com.sakarrobotics.cloud.command.RobotCommand} row. {@code status}
 * is one of {@code RECEIVED, EXECUTING, COMPLETED, FAILED, TIMEOUT} (agent
 * vocabulary — see {@code CommandResultIngestionService} for the mapping
 * onto {@link com.sakarrobotics.cloud.command.CommandStatus}). Field names
 * must match the agent's own {@code CommandResultDetail} class exactly —
 * SakarC40Agent's {@code CommandDispatcher} is the only producer of this
 * shape.
 */
public record CommandResultDetail(String commandId, String status, String detail, Long durationMs) {
}
