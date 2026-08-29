# Phase 3 — Robot Communication / MQTT Implementation Report

**Status: SOFTWARE COMPLETE / PHYSICAL VALIDATION NOT PERFORMED.** This report is checked against the actual repository state produced by this phase — re-run the commands in §5 before trusting it if time has passed, per the root `README.md`'s own development rule.

## 1. Objective (as given)

Build the first real, Sakar-owned, end-to-end robot data path:

```
ONE ROBOT AGENT -> SECURE MQTT -> SAKAR CLOUD -> TELEMETRY INGESTION -> SAKAR DATABASE
```

Not the entire platform — see `SAKAR_MQTT_ARCHITECTURE.md` §1 for exact scope boundaries.

## 2. What Was Built

**Backend** (`backend/src/main/java/com/sakarrobotics/cloud/`):
- `mqtt/` — `MqttTopicResolver`/`MqttTopicKind`/`ParsedMqttTopic` (topic scheme), `MqttEnvelope`/`MqttMessageType`/`dto/*` (envelope + payloads), `MqttInboundMessage`/`MqttInboundMessageRepository` (idempotency ledger), `MqttDedupGuard`, `MqttInboundMessageService` (the fully-testable ingestion core), `MqttInboundListener` (Paho callback adapter), `MqttSubscriptionManager` (connects + subscribes at startup, gated on `sakar.mqtt.enabled`), `MqttLifecycleLogger` (SRELS integration), `MqttGatewayService` extended with `publish()`.
- `telemetry/` — `RobotStatusService` (upsert), `TelemetryIngestionService`, `HeartbeatService`, `dto/RobotStatusUpdate` — these three previously-schema-only tables (`robot_status`, `robot_telemetry`) now have a real write path.
- `srels/` — `RobotEventIngestionService`, `RobotErrorIngestionService` — `robot_events`/`robot_errors` now have a real write path.
- `robot/registry/` — `RobotCredentialService`, `dto/RobotMqttCredentialResponse`, and a new `POST /api/v1/robots/{id}/mqtt-credentials` endpoint — `robot_credentials` (previously unused by any code path) is now populated.
- `websocket/` — `RobotRealtimePublisher`, publishing onto the existing authenticated/authorized transport.
- One new migration: `V11__mqtt_inbound_messages.sql`. **No existing migration was modified.**

**Robot Agent** (`robot/SakarC40Agent/`):
- `api/` module (converted from an empty placeholder to the real integration surface it always documented itself as reserved for) — `mqtt/` package: `AgentIdentity`, `SakarMqttConfig`, `AgentMqttTopics`, `MqttEnvelope`/`MqttMessageType`/`dto/*`, `BoundedOfflineQueue`, `TelemetrySnapshotProvider` (interface), `AgentMqttListener` (interface), `AgentMqttClient` (the MQTT client itself), `HeartbeatScheduler`, `TelemetryScheduler`. Still plain `java-library` — no Android SDK dependency, so its tests run as plain JUnit.
- `app/` module — `SakarMqttConfigFactory` (BuildConfig → `SakarMqttConfig`, mirroring `SdkConnectionConfig.fromBuildConfig()`), `C40TelemetrySnapshotProvider` (the one place this phase reads `C40RobotController`, never `:sdk`/`com.keenon.*` directly), `SakarC40Application` wired to construct and start all of the above in `onCreate()` — no-op if unconfigured.
- `secrets.properties.example` extended with 7 new keys (broker URL, org/site/robot/agent identity, MQTT username/password) — same git-ignored-file pattern the Peanut SDK credentials already use.

## 3. Requirement-by-Requirement Status

| Requirement | Status |
|---|---|
| MQTT broker integration/foundation | IMPLEMENTED (extends the Phase 1 scaffold rather than duplicating it) |
| Secure MQTT configuration | PARTIALLY IMPLEMENTED — see `SAKAR_MQTT_ARCHITECTURE.md` §7 for the exact DEV/PROD-REQUIRED split |
| Robot/device identity | IMPLEMENTED (credential issuance + software-side robot/tenant verification); broker-side enforcement is a production requirement |
| Agent MQTT client | IMPLEMENTED |
| Cloud MQTT gateway | IMPLEMENTED |
| Robot connection lifecycle | IMPLEMENTED (connect, LWT-based offline detection, reconnect) |
| Heartbeat | IMPLEMENTED |
| Telemetry publish | IMPLEMENTED (from confirmed, typed `RuntimeSnapshot` fields only) |
| Telemetry ingestion | IMPLEMENTED |
| Database persistence | IMPLEMENTED |
| MQTT acknowledgement | IMPLEMENTED |
| Reconnection | IMPLEMENTED (Paho automatic reconnect, bounded exponential backoff, both ends) |
| Offline behavior | IMPLEMENTED (bounded offline queue, agent side) |
| Basic observability | IMPLEMENTED (SRELS `application_logs` integration) |
| Automated tests | IMPLEMENTED — see §4 |

Not implemented, as instructed: Web UI, Mobile app, remote lock, remote unlock, cleaning orchestration, full task engine, fleet dashboard, production deployment, physical C40 testing.

## 4. Test Results

**Backend** (`cd backend && ./mvnw clean test`, JDK 21):
```
Tests run: 59, Failures: 0, Errors: 0
BUILD SUCCESS
```
32 pre-existing tests (unmodified) + 27 new: `MqttTopicResolverTest` (5), `MqttGatewayServiceTest` (2), `MqttInboundListenerTest` (4), `MqttInboundMessageServiceTest` (12), `RobotCredentialControllerTest` (4).

`./mvnw package -DskipTests` also succeeds, producing `backend/target/sakar-cloud-backend-0.0.1-SNAPSHOT.jar` (unchanged from Phase 1's verified behavior).

**Robot Agent** (`cd robot/SakarC40Agent && ./gradlew :api:test`):
```
16 tests completed, 0 failed
BUILD SUCCESSFUL
```
`AgentMqttTopicsTest` (3), `BoundedOfflineQueueTest` (3), `MqttEnvelopeSerializationTest` (4), `AgentMqttClientSafetyTest` (3), `HeartbeatSchedulerTest` (2), `TelemetrySchedulerTest` (1).

**Whole agent project** (`./gradlew assembleDebug test`): `BUILD SUCCESSFUL` — the debug APK assembles with the new `:api` dependency wired into `:app`, and every module's test task (including the pre-existing, still-empty ones) passes.

None of the above required a running MQTT broker or a physical robot.

## 5. Build Verification Commands

```bash
# Backend
cd backend && ./mvnw clean test && ./mvnw package -DskipTests

# Robot Agent
cd robot/SakarC40Agent && ./gradlew assembleDebug test
```

## 6. Security Review

See `SAKAR_MQTT_ARCHITECTURE.md` §7 for the full table. Summary:

| Check | Status |
|---|---|
| No secrets committed | CONFIRMED — `secrets.properties` (real) is git-ignored; only `.example` files with blank/placeholder values were edited; `application.yml`/`application-test.yml` use only dev-default placeholders (pre-existing pattern, unchanged) |
| No credentials in logs | CONFIRMED — `MqttLifecycleLogger`/`SdkCallLogger` calls in this phase log only event codes, topics, and generic error messages; the raw MQTT username/password/robot secret is never passed to either logger |
| MQTT authentication (transport-level) | PARTIAL — client-side username/password wiring exists on both ends; broker does not enforce it (dev Mosquitto config, unchanged, `allow_anonymous true`) |
| TLS configuration | NOT IMPLEMENTED — `tcp://` only; REQUIRES PRODUCTION CONFIGURATION |
| Robot identity | IMPLEMENTED (credential issuance) + PARTIAL (broker-side enforcement missing) |
| Tenant isolation | IMPLEMENTED — software-side robot/org/site cross-check, independent of transport-level auth |
| Topic ACL assumptions | Documented, not enforced — REQUIRES PRODUCTION CONFIGURATION |
| Replay/duplicate handling | IMPLEMENTED — `mqtt_inbound_messages` unique constraint |
| Message validation | IMPLEMENTED — schema version, required fields, timestamp skew |
| Rate limiting | NOT IMPLEMENTED |
| Malformed payload handling | IMPLEMENTED — caught, rejected, never crashes the listener |
| Unauthorized robot publishing | IMPLEMENTED (software-side check) / PARTIAL (no broker-side prevention) |
| DB injection risks | Not applicable — all persistence goes through JPA parameterized queries, no string-built SQL was introduced |
| JSON parsing risks | Bounded by each library's own parser (Jackson 3 / Gson); no custom parsing was written; malformed input is caught and rejected, not allowed to throw uncaught |
| Oversized payloads | NOT IMPLEMENTED — no explicit message-size cap was added in this phase beyond Paho/Mosquitto's own defaults |
| Denial-of-service risks | NOT ADDRESSED beyond the above — no rate limiting, no payload size cap |
| Reconnect storms | MITIGATED — Paho's built-in bounded exponential backoff (`maxReconnectDelay`) on both ends, not a tight retry loop |

**This phase does not claim production-grade security.** Every "NOT IMPLEMENTED"/"REQUIRES PRODUCTION CONFIGURATION" row above is a real, undone gap, not a placeholder marked complete.

## 7. Multi-Robot Compatibility

Nothing in this phase hardcodes C40/Keenon into the communication layer:
- Topic scheme uses only UUIDs (`organizationId`/`siteId`/`robotId`) — no vendor/model reference.
- Envelope/payload shapes carry generic metric/event/error fields, not Keenon-specific ones.
- The agent-side `TelemetrySnapshotProvider` interface lives in `:api` with zero dependency on `:sdk`/`:robot`; the concrete C40-specific implementation (`C40TelemetrySnapshotProvider`) lives in `:app`, the composition root — a future second robot model's agent would implement the same interface differently, not modify `:api`.
- Backend ingestion (`MqttInboundMessageService`) operates purely on `Robot`/`robotId` — it never branches on `RobotModel`/`AdapterType`/manufacturer.
- The Robot Adapter abstraction (`KeenonRobotAdapter`/`SakarRobotAdapter`, Phase 1) is untouched by this phase; MQTT ingestion is a separate path from the adapter-based Keenon Cloud integration, as designed.

## 8. No Physical Robot Claim

Per instruction, this report does **not** claim "C40 telemetry verified" or "CleanBot telemetry physically verified." The correct, current status is:

```
MQTT SOFTWARE PIPELINE: IMPLEMENTED / TESTED
PHYSICAL ROBOT TELEMETRY: REQUIRES PHYSICAL C40 TEST
```

## 9. Known Limitations

- No MQTT broker was run in this session; all backend tests exercise `MqttInboundMessageService`/`MqttInboundListener` directly (by design — see their Javadoc). Genuine broker connectivity (agent → real Mosquitto → backend) was not exercised end-to-end over the network in this phase.
- No physical robot was connected; `C40TelemetrySnapshotProvider` was not exercised against a live `PeanutSdkBridge` connection.
- Broker-side authentication/ACL/TLS remain undone (see §6).
- `agentId` is unauthenticated/unvalidated.
- No rate limiting or payload-size capping.
- The Android build (`:app`) could not be end-to-end runtime-tested (no emulator/device in this environment) — verified only by successful compilation and `assembleDebug` packaging.

## 10. Next Recommended Step

Physical C40 validation (Master Requirements Part 38) plus a live network test of this MQTT pipeline against a running broker and the actual agent — the two things this phase's automated tests, by design, do not and cannot cover.
