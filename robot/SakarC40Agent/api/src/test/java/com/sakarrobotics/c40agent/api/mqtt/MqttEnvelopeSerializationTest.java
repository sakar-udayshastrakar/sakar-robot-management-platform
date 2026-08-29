package com.sakarrobotics.c40agent.api.mqtt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.sakarrobotics.c40agent.api.mqtt.dto.HeartbeatPayload;
import com.sakarrobotics.c40agent.api.mqtt.dto.TelemetryFieldReading;
import com.sakarrobotics.c40agent.api.mqtt.dto.TelemetryPayload;

/**
 * The wire format this module produces must use the exact field names the
 * Sakar Cloud backend's Jackson-based {@code MqttEnvelope}/payload records
 * expect (Phase 3 Part 3) - this is the one seam nothing else catches,
 * since the two sides use different JSON libraries (Gson here, Jackson 3
 * there) with no shared schema class.
 */
class MqttEnvelopeSerializationTest {

    private final Gson gson = new Gson();

    @Test
    void envelope_serializesWithTheExactFieldNamesTheBackendExpects() {
        JsonObject payload = gson.toJsonTree(new HeartbeatPayload("0.1.0", 42, "CONNECTED")).getAsJsonObject();
        MqttEnvelope envelope = new MqttEnvelope("1.0", "msg-1", "robot-1", "agent-1", Instant.now().toString(),
                MqttMessageType.HEARTBEAT, 7, payload);

        JsonObject json = gson.toJsonTree(envelope).getAsJsonObject();

        assertTrue(json.has("schemaVersion"));
        assertTrue(json.has("messageId"));
        assertTrue(json.has("robotId"));
        assertTrue(json.has("agentId"));
        assertTrue(json.has("timestamp"));
        assertTrue(json.has("messageType"));
        assertTrue(json.has("sequence"));
        assertTrue(json.has("payload"));
        assertEquals("HEARTBEAT", json.get("messageType").getAsString());
        assertEquals("msg-1", json.get("messageId").getAsString());
    }

    @Test
    void heartbeatPayload_usesTheBackendRecordsComponentNames() {
        JsonObject json = gson.toJsonTree(new HeartbeatPayload("0.1.0", 42, "CONNECTED")).getAsJsonObject();

        assertEquals("0.1.0", json.get("agentVersion").getAsString());
        assertEquals(42, json.get("uptimeSeconds").getAsLong());
        assertEquals("CONNECTED", json.get("connectionStatus").getAsString());
    }

    @Test
    void telemetryPayload_readingsUseTheBackendTelemetryReadingFieldNames() {
        TelemetryFieldReading reading = new TelemetryFieldReading("battery_percent", null, 87.0, Instant.now().toString());
        JsonObject json = gson.toJsonTree(new TelemetryPayload(List.of(reading))).getAsJsonObject();

        JsonObject firstReading = json.getAsJsonArray("readings").get(0).getAsJsonObject();
        assertEquals("battery_percent", firstReading.get("metric").getAsString());
        assertEquals(87.0, firstReading.get("valueNumeric").getAsDouble());
        assertTrue(firstReading.has("recordedAt"));
        // Gson's default configuration omits null fields entirely (no serializeNulls()) rather than
        // writing `"valueText":null` - Jackson's record deserializer treats a missing property the
        // same as an explicit null for a reference-type component, so this is wire-compatible, not a bug.
        assertTrue(!firstReading.has("valueText") || firstReading.get("valueText").isJsonNull());
    }

    @Test
    void timestampFormat_isJavaInstantToString_parseableAsAnInstant() {
        String timestamp = Instant.now().toString();
        assertEquals(timestamp, Instant.parse(timestamp).toString());
    }
}
