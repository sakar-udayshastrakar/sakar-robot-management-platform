# Sakar Robot Management Platform — MQTT Architecture (Phase 3)

**Status: IMPLEMENTED and SECURITY-HARDENED (software) / NOT PHYSICALLY VALIDATED.** This document describes the first working Sakar-owned robot communication pipeline, built in Phase 3 (Master Requirements roadmap Phase 2/Story 3) and hardened in a follow-on security pass. It is checked against actual source code, not aspirational — see `SAKAR_PHASE_3_IMPLEMENTATION_REPORT.md` and `SAKAR_PHASE_3_SECURITY_HARDENING_REPORT.md` for the audit trail behind every claim below. No physical C40 was connected to build or verify any of this; see "Known Limitations".

---

## 1. Scope

The end-to-end pipeline built in this phase:

```
Sakar Robot Agent (SakarC40Agent, :api module)
        |  MQTT (Eclipse Paho, same client/version as the backend)
        v
Sakar MQTT Broker (Mosquitto, dev-only)
        |
        v
Sakar Cloud Backend (MqttSubscriptionManager -> MqttInboundListener -> MqttInboundMessageService)
        |
        v
PostgreSQL (robot_status, robot_telemetry, robot_events, robot_errors, mqtt_inbound_messages)
        |
        v
WebSocket (RobotRealtimePublisher, org-scoped, already-authenticated transport)
```

Explicitly out of scope (unchanged from before this phase): Web UI, Mobile app, remote lock/unlock, arbitrary robot commands, cleaning/task orchestration, fleet dashboard, production deployment, physical C40 testing.

## 2. Topic Scheme

```
{prefix}/{organizationId}/{siteId|_}/{robotId}/presence
{prefix}/{organizationId}/{siteId|_}/{robotId}/heartbeat
{prefix}/{organizationId}/{siteId|_}/{robotId}/telemetry
{prefix}/{organizationId}/{siteId|_}/{robotId}/events
{prefix}/{organizationId}/{siteId|_}/{robotId}/errors
{prefix}/{organizationId}/{siteId|_}/{robotId}/ack
```

`{prefix}` defaults to `sakar` (`sakar.mqtt.topic-prefix`). `_` is the literal "no site assigned" segment (`Robot.siteId` is nullable). **This is the organization/site/robot-namespaced scheme already specified in `SAKAR_SECURITY_REQUIREMENTS.md` §10** — chosen over inventing a new one, per that document's own topic-isolation and ACL-scoping rationale. No segment carries anything beyond three UUIDs and a fixed suffix word — no PII, no credential, no free text.

Implemented in `MqttTopicResolver` (backend, builds + parses) and `AgentMqttTopics` (agent, builds only).

## 3. Message Envelope

```json
{
  "schemaVersion": "1.0",
  "messageId": "<uuid>",
  "robotId": "<uuid>",
  "agentId": "<string>",
  "timestamp": "<ISO-8601 instant>",
  "messageType": "HEARTBEAT | TELEMETRY | EVENT | ERROR | PRESENCE | ACK",
  "sequence": 123,
  "payload": { }
}
```

- Backend side: `MqttEnvelope` (Java record, Jackson 3 / `tools.jackson`).
- Agent side: `MqttEnvelope` (plain POJO, Gson) — see `SAKAR_ROBOT_MQTT_PROTOCOL.md` for the exact field-name mapping verified by `MqttEnvelopeSerializationTest`.
- `payload` shape depends on `messageType` — see the protocol document.
- `ACK` and `COMMAND` are cloud-authored; the agent never publishes them. `COMMAND` is declared in the backend's `MqttMessageType` enum for future completeness only — **no command dispatch is implemented in this phase.**

## 4. Robot Identity & Tenant Verification

- Every message's `robotId` must match the topic's robot segment (rejected as `MALFORMED_MESSAGE` otherwise).
- The robot must exist in `robots` and not be `DEACTIVATED` (rejected as `UNKNOWN_ROBOT` / `UNAUTHORIZED_ROBOT`).
- **The topic's `organizationId`/`siteId` are never trusted by themselves** — `MqttInboundMessageService` cross-checks them against the registered `Robot` row's actual `organizationId`/`siteId` and rejects a mismatch as `TENANT_MISMATCH`. This is a defense-in-depth software check; the authoritative control is broker-side ACL (see §7), which is **not** implemented in this phase.
- `agentId` is recorded for observability only — Phase 3 has no separate `agents` identity table, so it is never itself an authorization input.

## 5. Idempotency

Every accepted envelope is recorded in `mqtt_inbound_messages` (unique on `robot_id, message_id`) before it is routed to ingestion — the same pattern `vendor_webhook_events` already uses for Keenon webhook idempotency. A redelivered message (QoS 1 "at least once", or a genuine reconnect replay) is a no-op, not a duplicate row. See `MqttDedupGuard`'s Javadoc for why this is a check-then-insert, not an insert-and-catch: a failed JPA flush leaves that persistence context/transaction unusable regardless of whether the application catches the translated exception, which a naive insert-and-catch would hit on every real duplicate.

## 6. Telemetry Mapping

The agent publishes only fields `RuntimeSnapshot` (the SDK's confirmed, typed `RuntimeInfo` mirror) actually exposes:

| Metric | Source | Note |
|---|---|---|
| `battery_percent` | `RuntimeSnapshot.getPower()` | `SAKAR_ROBOT_PLATFORM_REQUIREMENTS.md`: "Battery/online-state: CONFIRMED available via `RuntimeInfo.getPower()`" — this mapping is the platform's own documented interpretation, not invented here |
| `work_mode`, `sync_status`, `motor_status` | `RuntimeSnapshot` | raw SDK integer codes |
| `total_odo` | `RuntimeSnapshot.getTotalOdo()` | omitted if null |
| `emergency_enable`, `emergency_open` | `RuntimeSnapshot` | as text `"true"`/`"false"` |
| `robot_ip` | `RuntimeSnapshot.getRobotIp()` | omitted if null |

**Deliberately never published:** robot position (the only SDK path, `queryRobotPosition`, is marked UNCONFIRMED on the physical C40 in this codebase's own docs) and any battery/motor-status value derived from the raw async SDK calls (`getBattery`/`getMotorStatus`) — those responses are deliberately left unparsed (`RawSnapshot`'s own Javadoc: "the Peanut SDK v1.3.0 documentation does not publish a confirmed JSON schema for these responses"). Inventing a parser for them here would be exactly the fabrication this phase's instructions forbid. See `C40TelemetrySnapshotProvider`'s Javadoc.

## 7. Security — DEVELOPMENT vs PRODUCTION REQUIRED

**Updated by the Phase 3 Security Hardening pass — see `SAKAR_PHASE_3_SECURITY_HARDENING_REPORT.md` for the full finding-by-finding account.**

| Control | Status |
|---|---|
| Per-robot credential issuance (`robot_credentials`, BCrypt-hashed, rotatable, **now revocable**) | **IMPLEMENTED** — `RobotCredentialService`, `POST`/`DELETE /api/v1/robots/{id}/mqtt-credentials`, every action audit-logged |
| Agent uses that credential as its MQTT username/password | **IMPLEMENTED** (agent side, `SakarMqttConfig`/`AgentMqttClient`) |
| Broker enforces that credential (rejects anonymous/wrong-credential connections) | **NOT IMPLEMENTED / PRODUCTION REQUIRED** — the dev broker (`backend/docker/mosquitto.conf`) has `allow_anonymous true` and performs no authentication at all |
| Per-client topic ACL (a robot's credential can only publish/subscribe under its own `{org}/{site}/{robot}` prefix) | **NOT IMPLEMENTED / PRODUCTION REQUIRED** — requires a Mosquitto password file or dynamic-security plugin; not configured |
| TLS on the MQTT connection | **DESIGN COMPLETE (hardening pass)** — both backend and agent accept `ssl://` broker URLs with real, platform-default CA validation (never trust-all) plus optional private-CA truststore config; **never run against a live TLS-terminating broker** — REQUIRES NETWORK TEST |
| Software-side robot/tenant cross-check (independent of broker enforcement) | **IMPLEMENTED** — §4 above; this is real defense-in-depth, not a placeholder |
| Message schema/timestamp/sequence validation, rejection of malformed/stale/wrong-schema-version/negative-sequence messages | **IMPLEMENTED** (sequence sanity check added in the hardening pass) |
| Maximum payload size (rejects oversized messages before JSON parsing) | **IMPLEMENTED (hardening pass)** — 64 KiB default, configurable |
| Idempotent duplicate handling | **IMPLEMENTED** — §5 |
| Rate limiting on MQTT ingestion | **IMPLEMENTED (hardening pass)** — Redis-backed, per-robot, 120 msg/60s default, configurable |
| Message signing (cryptographic, independent of TLS) | **NOT IMPLEMENTED** — see `SAKAR_PHASE_3_SECURITY_HARDENING_REPORT.md` §3.5. TLS protects transport integrity; it is not a substitute for per-message signing |
| Command signing/replay protection | **N/A this phase** — no commands are dispatched |
| Credential-action audit logging | **IMPLEMENTED (hardening pass)** — provision/rotate/revoke all recorded via the existing `AuditService`, never the raw secret |

**This backend does not claim production-grade MQTT security.** Credential issuance/rotation/revocation, software-side identity/tenant checks, TLS client configuration, payload-size limits, and rate limiting are all real and test-verified. Broker-side enforcement of credentials/ACLs/TLS remains an explicit, undone production requirement — see `SAKAR_SECURITY_REQUIREMENTS.md` §10, which already specified exactly this gap and has been updated to reflect this pass's evidence.

## 8. Failure Handling

| Condition | Backend behavior |
|---|---|
| Malformed JSON / missing required envelope field | Rejected (`MALFORMED_MESSAGE`), logged, never crashes the listener thread |
| Unsupported `schemaVersion` | Rejected (`UNSUPPORTED_SCHEMA_VERSION`) |
| `timestamp` more than `sakar.mqtt.max-timestamp-skew-seconds` (default 300s) from server time | Rejected (`STALE_TIMESTAMP`) |
| Unknown `robotId` | Rejected (`UNKNOWN_ROBOT`) |
| Deactivated robot | Rejected (`UNAUTHORIZED_ROBOT`) |
| Topic org/site not matching the registered robot | Rejected (`TENANT_MISMATCH`) |
| Negative `sequence` | Rejected (`INVALID_SEQUENCE`) — sanity only, not strict monotonic-order enforcement (Master Requirements Part 15/23) |
| Payload exceeds `sakar.mqtt.max-payload-size-bytes` (default 64 KiB) | Rejected (`PAYLOAD_TOO_LARGE`) **before any JSON parsing is attempted** |
| Robot exceeds `sakar.mqtt.rate-limit-max-messages` within the configured window (default 120/60s) | Rejected (`RATE_LIMITED`) |
| Redelivered `messageId` | Idempotent no-op, still acknowledged as accepted |
| Any unexpected exception during ingestion | Caught, logged as `PROCESSING_FAILED`, never propagated to crash the MQTT callback thread |
| Broker unreachable at backend startup | Logged (`MQTT_CONNECT_FAILED`); Paho's `automaticReconnect` keeps retrying with bounded exponential backoff; the backend itself still starts normally |
| DB unavailable during ingestion | Propagates as an unhandled exception inside `route()`, caught by the outer handler and reported as `PROCESSING_FAILED` — not separately distinguished from other processing failures in this phase |

Every ACK published to `.../ack` carries only a stable rejection code (`MqttRejectionReason` name) — never a raw exception message, stack trace, or internal detail (Phase 3 Part 11).

## 9. Reconnection & Offline Behavior

- **Backend:** Paho's `automaticReconnect` (already present since Phase 1's scaffold); on `connectComplete(reconnect=true, ...)`, `MqttSubscriptionManager` re-issues every topic subscription (MQTT sessions here are `cleanSession=true`, so subscriptions do not survive a broker-side reconnect on their own).
- **Agent:** same library/pattern — `MqttConnectOptions.setAutomaticReconnect(true)` with a bounded max delay (`SakarMqttConfig.maxReconnectDelayMillis`, default 128s), giving Paho's own exponential backoff rather than a hand-rolled retry loop. Heartbeat/telemetry schedulers are independent of connection state — they keep calling `AgentMqttClient.publish*`, which transparently queues into a **bounded** (`BoundedOfflineQueue`, default capacity 200, drop-oldest) offline queue when disconnected, flushed in order on `connectComplete`.
- **Presence/LWT:** the agent publishes a retained `ONLINE` presence message on connect and sets an MQTT Last Will (retained `OFFLINE`) at connect time, so an unclean disconnect is detected by the broker/backend without waiting for a heartbeat timeout — the mechanism `SAKAR_ROBOT_PLATFORM_ARCHITECTURE.md` §4 recommends as primary.

## 10. WebSocket Integration

`RobotRealtimePublisher` publishes to the **existing, already-authorized** WebSocket transport (`WebSocketConfig`/`WebSocketAuthChannelInterceptor`, unchanged) at `/topic/organizations/{orgId}/robots/{robotId}/status` and `.../telemetry` after a successful heartbeat/telemetry/presence ingestion. No new authentication or authorization logic was added — this reuses the org-scoped destination pattern the interceptor already enforces at SUBSCRIBE time. Not a Web UI; backend real-time capability only.

## 11. Tests

- `MqttTopicResolverTest`, `AgentMqttTopicsTest` — topic build/parse, both sides.
- `MqttInboundMessageServiceTest` — the full ingestion pipeline (12 cases: acceptance, idempotency, unknown/deactivated/tenant-mismatch rejection, malformed envelope, stale timestamp, unsupported schema version, heartbeat/presence/event/error ingestion).
- `MqttInboundListenerTest` — JSON parsing, ACK publishing, malformed-topic/-payload handling (Mockito, no broker).
- `MqttGatewayServiceTest` — disabled-by-default behavior.
- `RobotCredentialControllerTest` — credential provisioning/rotation, permission and tenant checks.
- Agent side (`:api` module, plain JUnit, no Android SDK/broker required): `AgentMqttTopicsTest`, `BoundedOfflineQueueTest`, `MqttEnvelopeSerializationTest` (verifies Gson output matches the field names the backend's Jackson deserializer expects), `AgentMqttClientSafetyTest`, `HeartbeatSchedulerTest`, `TelemetrySchedulerTest`.

None of these tests require a running MQTT broker or a physical robot — see `SAKAR_PHASE_3_IMPLEMENTATION_REPORT.md` for exact pass counts.

## 12. Known Limitations

- **No physical C40 was connected.** Everything above is a software pipeline verified end-to-end at the code level (agent envelope -> backend ingestion -> database -> WebSocket), not against a real robot or even a real running MQTT broker in this session.
- **`MQTT SOFTWARE PIPELINE: IMPLEMENTED / TESTED` — `PHYSICAL ROBOT TELEMETRY: REQUIRES PHYSICAL C40 TEST.`** Do not conflate the two.
- Broker-side authentication/ACL/TLS are not implemented (§7).
- No rate limiting on MQTT ingestion.
- `agentId` is not itself authenticated or validated against a registry — only `robotId` is.
- Command dispatch, remote lock/unlock, and task/cleaning orchestration remain entirely out of scope, as instructed.
