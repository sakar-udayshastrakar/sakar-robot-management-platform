package com.sakarrobotics.c40agent.api.mqtt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.sakarrobotics.c40agent.api.mqtt.dto.CommandPayload;
import com.sakarrobotics.c40agent.api.mqtt.dto.CommandResultDetail;

/**
 * Roadmap Phase 6/7 "Robot Agent Command Loop" wire-format guard, in the
 * same spirit as {@link MqttEnvelopeSerializationTest}: the two sides of
 * this contract (this Gson-based agent, the backend's Jackson-based
 * {@code RobotCommandService}/{@code CommandResultDetail}) share no
 * schema class, so a field-name typo on either side would otherwise only
 * surface as a silent runtime no-op, not a compile error.
 */
class CommandPayloadSerializationTest {

    private final Gson gson = new Gson();

    @Test
    void commandPayload_deserializesTheExactFlatShapeTheBackendEmits() {
        // Verbatim shape of RobotCommandService.buildEnvelope() on the backend — flat, not
        // nested under a "payload" key, and with no agentId/sequence fields.
        String backendJson = "{"
                + "\"schemaVersion\":\"1.0\","
                + "\"messageId\":\"11111111-1111-1111-1111-111111111111\","
                + "\"robotId\":\"22222222-2222-2222-2222-222222222222\","
                + "\"timestamp\":\"" + Instant.now() + "\","
                + "\"messageType\":\"COMMAND\","
                + "\"commandType\":\"START_TASK\","
                + "\"nonce\":\"33333333-3333-3333-3333-333333333333\","
                + "\"expiresAt\":\"" + Instant.now().plusSeconds(30) + "\","
                + "\"params\":{\"zoneId\":3}"
                + "}";

        CommandPayload command = gson.fromJson(backendJson, CommandPayload.class);

        assertEquals("1.0", command.getSchemaVersion());
        assertEquals("11111111-1111-1111-1111-111111111111", command.getMessageId());
        assertEquals("11111111-1111-1111-1111-111111111111", command.getCommandId()); // correlation id == messageId
        assertEquals("22222222-2222-2222-2222-222222222222", command.getRobotId());
        assertEquals("COMMAND", command.getMessageType());
        assertEquals("START_TASK", command.getCommandType());
        assertEquals("33333333-3333-3333-3333-333333333333", command.getNonce());
        assertEquals(3.0, ((Number) command.getParams().get("zoneId")).doubleValue());
    }

    @Test
    void commandPayload_missingParams_doesNotThrow() {
        String backendJson = "{\"schemaVersion\":\"1.0\",\"messageId\":\"m-1\",\"robotId\":\"r-1\","
                + "\"timestamp\":\"" + Instant.now() + "\",\"messageType\":\"COMMAND\","
                + "\"commandType\":\"RETURN_TO_DOCK\",\"nonce\":\"n-1\",\"expiresAt\":\"" + Instant.now() + "\"}";

        CommandPayload command = gson.fromJson(backendJson, CommandPayload.class);

        assertEquals("RETURN_TO_DOCK", command.getCommandType());
        assertNull(command.getParams());
    }

    @Test
    void commandResultDetail_usesTheExactFieldNamesTheBackendExpects() {
        CommandResultDetail detail = new CommandResultDetail("11111111-1111-1111-1111-111111111111", "COMPLETED", "ok", 1500L);

        JsonObject json = gson.toJsonTree(detail).getAsJsonObject();

        assertEquals("11111111-1111-1111-1111-111111111111", json.get("commandId").getAsString());
        assertEquals("COMPLETED", json.get("status").getAsString());
        assertEquals("ok", json.get("detail").getAsString());
        assertEquals(1500L, json.get("durationMs").getAsLong());
    }

    @Test
    void commandResultDetail_nullDetailAndDuration_areOmittedNotNull() {
        CommandResultDetail detail = new CommandResultDetail("cmd-1", "RECEIVED", null, null);

        JsonObject json = gson.toJsonTree(detail).getAsJsonObject();

        assertEquals("RECEIVED", json.get("status").getAsString());
        // Gson's default configuration omits null fields — wire-compatible with the backend's
        // Jackson record deserialization, same convention MqttEnvelopeSerializationTest documents.
        org.junit.jupiter.api.Assertions.assertTrue(!json.has("detail") || json.get("detail").isJsonNull());
        org.junit.jupiter.api.Assertions.assertTrue(!json.has("durationMs") || json.get("durationMs").isJsonNull());
    }
}
