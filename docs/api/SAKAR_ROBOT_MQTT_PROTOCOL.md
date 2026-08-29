# Sakar Robot Agent MQTT Protocol (Phase 3)

**Status: IMPLEMENTED, matches actual source code as of Phase 3.** This is the concrete wire-format realization of the generic contract `SAKAR_ROBOT_PLATFORM_API_SPEC.md` §2 deliberately left transport-agnostic ("It does not fix a final wire format… that is an implementation decision within the constraints above"). Where this document and that one differ only in *shape* (batched telemetry, field names), this document governs the actual MQTT bytes on the wire; §2's *requirements* (never trust client-supplied org/site, message security, etc.) still apply and are enforced as described here and in `SAKAR_MQTT_ARCHITECTURE.md`.

## Topics

See `SAKAR_MQTT_ARCHITECTURE.md` §2. Built by `MqttTopicResolver` (backend) / `AgentMqttTopics` (agent).

## Envelope

| Field | Type | Notes |
|---|---|---|
| `schemaVersion` | string | Currently `"1.0"` (`sakar.mqtt.schema-version`); a mismatch is rejected as `UNSUPPORTED_SCHEMA_VERSION` |
| `messageId` | string (UUID) | Idempotency key, scoped per-robot |
| `robotId` | string (UUID) | Must match the topic's robot segment |
| `agentId` | string | Observability only, not an authorization input |
| `timestamp` | string, ISO-8601 instant | e.g. `java.time.Instant.now().toString()`; must be within `sakar.mqtt.max-timestamp-skew-seconds` (default 300s) of server time |
| `messageType` | string | `PRESENCE` \| `HEARTBEAT` \| `TELEMETRY` \| `EVENT` \| `ERROR` (agent-authored) or `ACK` \| `COMMAND` (cloud-authored; `COMMAND` declared for completeness only, not dispatched in this phase) |
| `sequence` | number | Monotonically increasing per agent process; observability only — MQTT QoS 1 does not guarantee ordering, so nothing on the backend depends on strict sequence continuity in this phase |
| `payload` | object | Shape depends on `messageType`, below |

**Cross-library compatibility note:** the backend deserializes this with Jackson 3 (`tools.jackson`); the agent serializes it with Gson (no JSON library shared between the two). Field names below are therefore load-bearing — `MqttEnvelopeSerializationTest` (agent side) pins them.

## Payloads

### HEARTBEAT (agent → cloud)
```json
{ "agentVersion": "0.1.0", "uptimeSeconds": 42, "connectionStatus": "CONNECTED" }
```
Agent health only — never a robot-state field (Master Requirements roadmap intent: heartbeat drives `robot_status.online`/`last_seen_at`, nothing else).

### TELEMETRY (agent → cloud)
```json
{ "readings": [ { "metric": "battery_percent", "valueText": null, "valueNumeric": 87.0, "recordedAt": "2026-08-29T10:15:30.000Z" } ] }
```
Batched (supports "buffer while offline, replay on reconnect"). `readings[].metric`/`valueText`/`valueNumeric`/`recordedAt` map 1:1 onto `robot_telemetry` columns and the existing `TelemetryReading` record — no new shape was invented. See `SAKAR_MQTT_ARCHITECTURE.md` §6 for which metrics the current agent actually publishes and why others are deliberately omitted.

### EVENT (agent → cloud)
```json
{ "eventType": "NAVIGATION_STARTED", "severity": "INFO", "payload": "{...raw...}", "occurredAt": "2026-08-29T10:15:30.000Z" }
```
Maps to `robot_events`; `payload` is stored raw (many SDK topic schemas remain unconfirmed — same rationale as `robot_events.payload` generally).

### ERROR (agent → cloud)
```json
{ "errorCode": "E-100", "severity": "CRITICAL", "source": "sdk", "message": "motor fault", "sdkApi": "MotorComponent.getStatus", "occurredAt": "2026-08-29T10:15:30.000Z" }
```
Maps to `robot_errors`.

### PRESENCE (agent → cloud, retained)
```json
{ "status": "ONLINE" }
```
or `"OFFLINE"`. Published explicitly on connect (`ONLINE`) and as the MQTT Last Will (`OFFLINE`, broker-delivered on unclean disconnect).

### ACK (cloud → agent)
```json
{ "messageId": "<uuid>", "robotId": "<uuid>", "accepted": true, "timestamp": "2026-08-29T10:15:31.000Z", "reason": null }
```
`reason` is present and non-null only when `accepted` is `false`, and is always one of the stable `MqttRejectionReason` codes (`MALFORMED_MESSAGE`, `UNSUPPORTED_SCHEMA_VERSION`, `STALE_TIMESTAMP`, `UNKNOWN_ROBOT`, `UNAUTHORIZED_ROBOT`, `TENANT_MISMATCH`, `DUPLICATE_MESSAGE`, `PROCESSING_FAILED`, `PAYLOAD_TOO_LARGE`, `INVALID_SEQUENCE`, `RATE_LIMITED` — the last three added by the Phase 3 Security Hardening pass) — never a raw exception message.

## What This Phase Deliberately Does Not Implement

- `COMMAND` dispatch (backend → agent) — declared in the enum, never published or handled.
- Lock/unlock — no message type, no handler, nothing wired anywhere in this phase.
- Message-level signing (`SAKAR_ROBOT_PLATFORM_API_SPEC.md` §2.11's command-signature requirement applies only to commands, which don't exist yet in this phase).
- Broker-enforced per-robot authentication/ACL (see `SAKAR_MQTT_ARCHITECTURE.md` §7).
