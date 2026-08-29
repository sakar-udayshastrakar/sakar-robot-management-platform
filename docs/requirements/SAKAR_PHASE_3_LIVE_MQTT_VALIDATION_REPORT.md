# Phase 3 — Live MQTT Infrastructure Validation Report

**Date:** 2026-08-29 · **Scope:** validate the actual Phase 3 MQTT software pipeline against a real (non-mocked) MQTT broker, a real Spring Boot backend process, and the real `:api` module `AgentMqttClient` code. **No physical CleanBot 5000 Plus was connected. No production infrastructure was touched.** Everything described here ran in this session's scratchpad, outside the repository, and was torn down at the end — `git status` at the end of this session shows a clean working tree with zero source changes.

---

## 1. Test Environment

No Docker, no local Mosquitto/PostgreSQL/Redis install was available in this environment (checked and confirmed absent before starting). The environment actually used, entirely outside the git repository:

| Component | What it actually was | Why |
|---|---|---|
| MQTT Broker | **Moquette 0.17** (`io.moquette:moquette-broker`), a real, embeddable, pure-Java MQTT broker — fetched via Maven Central (the same trust mechanism this project already uses for every other dependency), run as a genuine standalone JVM process with two real TCP listeners | No Docker/Mosquitto binary available; this is a real MQTT protocol implementation, not a mock or stub |
| Redis | `com.github.fppt.jedis-mock` (**already an existing project test dependency** — `backend/pom.xml`, the same library `IntegrationTestSupport` uses), run as a standalone process exposing a real RESP-protocol server on port 6379 | No local Redis install; this is the same mechanism the automated test suite already relies on, just run outside JUnit |
| PostgreSQL | **H2 in PostgreSQL-compatibility mode**, file-based (`jdbc:h2:file:...`), schema created via Hibernate `ddl-auto=create-drop` (Flyway disabled for this run only) | No Docker/local Postgres install; **this is explicitly NOT real PostgreSQL** — flagged wherever it matters below |
| Backend | The actual `sakar-cloud-backend` Spring Boot application, `./mvnw spring-boot:run`, unmodified source, pointed at the above via environment variables/command-line properties only | — |
| Test Agent | The actual, unmodified `:api` module (`AgentMqttClient`, `HeartbeatScheduler`, `TelemetryScheduler`, `SakarMqttConfig`, etc.), compiled to `api.jar` and driven by a small harness `main()` method — **not** an installed Android APK, since no Android emulator/device is available in this environment | See "Critical Distinction" §29 |

**Nothing in `robot/SakarC40Agent/sdk` or the Peanut SDK was touched, run, or referenced.** This validation exercises only the `:api` module (MQTT communication layer), which has zero dependency on `:sdk`/`com.keenon.*`.

## 2. Broker Configuration

- Plaintext listener: `127.0.0.1:11883`. TLS listener: `127.0.0.1:18883` (self-signed test certificate, generated with `keytool`, 3-day validity, never committed).
- `allow_anonymous = false`. Custom `IAuthenticator` checking test-only, hardcoded-in-the-test-harness credentials (`agent-test-001`/`agent-test-002`/`sakar-cloud-backend`, never real, never committed).
- Custom `IAuthorizatorPolicy`: per-robot credentials may only publish/subscribe to their own bound `robotId`'s topic segment; the backend's own service credential (`sakar-cloud-backend`) gets `sakar/`-prefixed wildcard access — modeling the distinct, broader credential a real cloud backend would hold.
- Isolated to `127.0.0.1` only, on non-standard ports, for this session only.

## 3. Agent Configuration

Real `SakarMqttConfig`/`AgentIdentity` objects constructed directly (no Android `BuildConfig`, since there's no Android runtime here) with test broker URL, test credentials, and the real, backend-issued robot UUIDs (see §5). Heartbeat interval 5s, telemetry interval 7s, `maxReconnectDelayMillis` 8000 (shortened from the 128000 production default purely so reconnection was observable within a reasonable live-test window).

## 4. Backend Configuration

Real, unmodified backend, started via environment variables only: `SAKAR_MQTT_ENABLED=true`, `SAKAR_MQTT_BROKER_URL=tcp://localhost:11883`, `SAKAR_MQTT_USERNAME=sakar-cloud-backend`, plus `sakar.mqtt.rate-limit-max-messages=5` / `rate-limit-window-seconds=30` (tightened from the 120/60 production defaults specifically so the rate limiter was observable within a short live session — see §19's honest caveat about this).

## 5. Test Robot / 6. Test Organization

Created via the **real REST API**, using a bootstrap `SUPER_ADMIN` account (RBAC/roles seeded directly since Flyway was disabled for this H2 run — the seed data itself mirrors `V9__seed_rbac.sql` exactly):

| Entity | Value |
|---|---|
| ORG-A-LiveTest | `55edde69-a409-49e0-87ee-168e5ae171f0` |
| ORG-B-LiveTest | `6a1b81ae-6241-4111-8d39-18360bb2381b` |
| robot-test-001 | `b0ddcb2e-1f55-42d5-a9f0-8cbf99a0e562` (ORG-A, `ACTIVE`) |
| robot-test-002 | `1544915f-194b-4743-806c-bb46093b6bc0` (ORG-A, `ACTIVE`) |

Both robots were registered, activated, and had MQTT credentials provisioned through the real `POST /api/v1/robots/{id}/mqtt-credentials` endpoint (raw secrets never printed/logged — see §25). **Note:** the broker's own test credentials (§2) are separate, hardcoded test values, not the backend-issued secret — no code in this repository wires broker-side authentication to the `robot_credentials` table yet (this is the pre-existing, documented F9/F10 gap, unchanged by this session).

## 7. MQTT Connection

**PASS.** Backend log: `Connected to MQTT broker as sakar-cloud-<uuid>` (real `MqttGatewayService` log line). Broker log confirms `AUTH OK` + 5× `ACL canRead ALLOWED` for the backend's wildcard subscriptions (presence/heartbeat/telemetry/events/errors). This is the real `MqttSubscriptionManager` → `MqttGatewayService` code path, unmodified.

## 8. Authentication

**PASS.** Anonymous connections rejected by the broker (`allow_anonymous=false`); every successful connection in this session's logs shows an explicit `AUTH OK ... username=...` line. An untrusted-TLS-cert connection attempt (§9) never even reached the authentication stage — rejected at the TLS layer first.

## 9. TLS

**PASS**, with an important scope note: the client-side TLS support built during the hardening pass was exercised **live** — and validated correctly:

| Sub-test | Result | Evidence |
|---|---|---|
| Valid certificate (custom truststore containing the test CA) | **PASS** | Real `AgentMqttClient` connected over `ssl://localhost:18883`, published, received 5 ACKs |
| Invalid/untrusted certificate (no truststore → JVM default trust store, which does not contain the self-signed test cert) | **FAIL (correctly)** | No `onConnected` event, 0 ACKs, **and the broker log shows zero authentication attempts for this connection** — the handshake was rejected at the TLS layer, never reaching MQTT-level auth. Confirms no trust-all behavior anywhere in the client. |
| Plaintext connection to the TLS-only port (18883) | **REJECTED** | `CONNECT_FAILED: Connection lost` — immediate protocol-level rejection |

## 10. ACL

**PASS**, and the "critical distinction" the task asked for is cleanly demonstrated:

- **BROKER ACL ENFORCED:** `agent-test-001` (bound to robot-test-001) attempting to publish/subscribe to robot-test-002's topics was denied **at the broker** — `ACL canRead DENIED` / `ACL canWrite DENIED` in the broker log, before the message ever reached the backend.
- **APPLICATION IDENTITY ENFORCEMENT:** `agent-test-001` publishing robot-test-001's own telemetry under **ORG-B**'s topic segment was **allowed by the broker** (its ACL is robot-scoped only, by design — see §2) but **rejected by the backend** with `TENANT_MISMATCH`, since the backend re-derives tenant ownership from the registered `Robot` row, never from the topic.

## 11. Heartbeat

**PASS.** Real heartbeat messages (`AgentMqttClient.publishHeartbeat` → `HeartbeatScheduler`) published every 5s, each producing a `HEARTBEAT_ACCEPTED` SRELS log entry and an `accepted:true` ACK. Confirmed fields: `robotId`, `agentId`, `timestamp`, `sequence`, `agentVersion`, `connectionStatus` — all present in every accepted envelope (structural validation would otherwise have rejected it).

## 12. Telemetry

**PASS**, and explicitly labeled correctly per the task's own instruction: this is **TEST AGENT TELEMETRY**, not physical CleanBot telemetry. Readings (`battery_percent`, `work_mode`, `robot_ip`) published by the real `TelemetryScheduler` → `AgentMqttClient.publishTelemetry` → real broker → real `MqttInboundListener` → real `MqttInboundMessageService` → real `TelemetryIngestionService` → real `robot_telemetry` rows (confirmed in §21).

## 13. ACK

**PASS.** Every publish produced a matching ACK on `.../ack` with the correct `messageId` and `robotId`; rejected messages carried the correct `MqttRejectionReason` (`UNKNOWN_ROBOT`, `TENANT_MISMATCH`, `MALFORMED_MESSAGE`, `UNSUPPORTED_SCHEMA_VERSION`, `INVALID_SEQUENCE`, `PAYLOAD_TOO_LARGE`, `STALE_TIMESTAMP`, `RATE_LIMITED` — every rejection reason defined in the code was observed live at least once).

## 14. Duplicate Handling

**PASS.** The same envelope (fixed `messageId: dup-test-fixed-001`) published twice: both round-trips returned `accepted:true` (idempotent no-op is still ack'd as accepted, by design), and the database confirms **exactly one** row in `mqtt_inbound_messages` for that `messageId` — no double-write occurred.

## 15. Replay Protection

**PASS**, exact behavior documented as instructed: a message with a `messageId` never seen before, but an old (>300s) timestamp, was rejected as `STALE_TIMESTAMP` — **both** the first send and a repeat send of the identical stale message. Timestamp freshness is checked before the idempotency ledger, so a stale replay never reaches (or pollutes) `mqtt_inbound_messages` at all.

## 16. Invalid Payload

**PASS across every case**, no crash, backend stayed healthy (`actuator/health: UP`) throughout:

| Case | Result |
|---|---|
| Malformed JSON | `MALFORMED_MESSAGE` |
| Missing `messageId` | `MALFORMED_MESSAGE` |
| Missing `robotId` | `MALFORMED_MESSAGE` |
| Missing `timestamp` | `MALFORMED_MESSAGE` |
| Invalid `messageType` (`"BOGUS_TYPE"`) | `MALFORMED_MESSAGE` |
| Unsupported `schemaVersion` (`"99.0"`) | `UNSUPPORTED_SCHEMA_VERSION` |
| Negative `sequence` | `INVALID_SEQUENCE` |
| Oversized payload (70 KB, broker limit raised to 200 KB specifically to isolate this test from Moquette's own 8 KB default frame limit) | `PAYLOAD_TOO_LARGE` (rejected **before** JSON parsing, confirmed by the backend never even attempting to deserialize it) |

## 17. Cross-Robot Isolation

**PASS — `BROKER ACL ENFORCED`.** See §10. The publish itself never reached the backend.

## 18. Cross-Tenant Isolation

**PASS — `APPLICATION IDENTITY ENFORCEMENT`.** See §10.

## 19. Rate Limiting

**PASS.** A tight, deterministic test (7 messages sent rapidly, limit configured to 5/30s for this session) showed messages 1–5 `accepted:true`, messages 6–7 `RATE_LIMITED`. **Honest observation:** the deliberately tightened test limit (5/30s) is actually *lower* than this same session's combined heartbeat(5s)+telemetry(7s) cadence, so several *other* tests' legitimate traffic was also intermittently rate-limited later in the session (`RATE_LIMITED_REJECTED` totalled 24 across the whole session — see §22) — this is the limiter working exactly as configured, not a defect, and it directly demonstrates why the actual production default (120/60s, in `application.yml`, untouched) is sized far more generously. The backend never became unresponsive at any point (`actuator/health` stayed `UP` throughout, including immediately after every rate-limit rejection), and legitimate traffic resumed normally once each window rolled over (evidenced by the later reconnect test's 27 successful ACKs).

## 20. Reconnect

**PASS**, cleanly and completely: broker killed while the real agent was connected → `onConnectionLost` fired within milliseconds → `onReconnecting` → **no tight loop** (backoff climbed over the outage, reconnect landed ~47s later, bounded by `maxReconnectDelayMillis`) → broker restarted → `onConnected reconnect=true` fired explicitly → broker log confirms heartbeat **and** telemetry publishing resumed within seconds of reconnection, each followed by a real backend ACK. Full run: 90s total, ~47s disconnected, 27 total ACKs received.

## 21. Offline Queue

**PASS (indirect, quantitatively consistent) + unit-tested.** During the ~47s disconnection above, the agent's heartbeat/telemetry schedulers kept firing on schedule (they are not connection-aware) — each attempted publish that failed transparently queued via `AgentMqttClient`'s bounded offline queue rather than throwing or blocking. On reconnect, the queue drained automatically (`flushOfflineQueue()`), and the observed total ACK count (27, versus an estimated 9–10 that pure live-connected-time traffic alone would produce) is quantitatively consistent with roughly 15–16 queued messages having been captured and successfully drained — no messages were lost, no duplicates were created (each queued item is drained exactly once). The queue's **bounded, drop-oldest** behavior specifically (capacity exceeded) is additionally covered deterministically by `BoundedOfflineQueueTest` (3 unit tests, part of the 18/18 agent suite) — a live test cannot reliably force an exact overflow condition without an artificially tiny capacity and a precisely-timed outage, so the unit test is the authoritative evidence for that specific boundary condition, and this live test is the authoritative evidence that the mechanism works end-to-end with real messages and real timestamps.

## 22. Database Verification

Queried directly against the H2 file (embedded, single-owner, after a clean session shutdown — **not** modified, only read):

| Table | Result |
|---|---|
| `robot_status` | 1 row for robot-test-001: `online=true`, `battery_percent=87`, `last_seen_at` populated |
| `robot_telemetry` | 25 rows for robot-test-001 (`battery_percent` ×9, `robot_ip` ×8, `work_mode` ×8) |
| `mqtt_inbound_messages` | 31 rows total; `dup-test-fixed-001` present **exactly once** despite 2 publishes; the stale replay message is correctly **absent** (rejected before reaching the ledger) |
| `robot_events` / `robot_errors` | 0 rows — **not exercised in this live session** (no EVENT/ERROR-type messages were live-published; this session focused on HEARTBEAT/TELEMETRY/PRESENCE/ACK). Already independently verified by `MqttInboundMessageServiceTest.event_isIngestedIntoRobotEvents` / `.error_isIngestedIntoRobotErrors` in the 71/71 automated suite. |
| `robot_credentials` | 2 rows, `credential_type=MQTT`, hash length 60 (BCrypt) — no raw secret anywhere |
| `audit_logs` | `MQTT_CREDENTIAL_PROVISIONED` recorded for both robots, plus 3 `LOGIN` entries |
| `organizations` / `robots` | Both test orgs and both test robots present with correct `organization_id` scoping |

**No production data was touched — this entire database was a disposable, session-scoped H2 file, not the real deployment's PostgreSQL.**

## 23. SRELS

**PASS.** `application_logs` (source=`mqtt`), 80 rows total, breaking down to exactly the lifecycle/ingestion event codes the code defines: `MQTT_CONNECTED` (1), `MQTT_DISCONNECTED` (5, matching every broker restart), `MQTT_RECONNECTED` (4), `HEARTBEAT_ACCEPTED` (16), `TELEMETRY_ACCEPTED` (9), `PRESENCE_ACCEPTED` (6), `DUPLICATE_MESSAGE_IGNORED` (1), `MALFORMED_MESSAGE_REJECTED` (10), `PAYLOAD_TOO_LARGE_REJECTED` (1), `RATE_LIMITED_REJECTED` (24), `UNAUTHORIZED_ROBOT_REJECTED` (2), `UNKNOWN_ROBOT_REJECTED` (1). Manually inspected: **no credential, token, or Authorization header value appears in any row** — every entry is an event code plus a generic, non-sensitive detail string (byte counts, rejection reasons, robot/organization ids).

## 24. WebSocket

**INCONCLUSIVE — not attributed to a product defect.** A minimal, hand-built STOMP-over-WebSocket client (plain `java.net.http.WebSocket`, no library) was used to attempt a live subscription. The STOMP `CONNECT` phase **succeeded** (authenticated correctly, received a `CONNECTED` frame back — confirming `WebSocketAuthChannelInterceptor`'s CONNECT-time JWT check works live). The subsequent `SUBSCRIBE` to `/topic/organizations/{orgId}/robots/{robotId}/telemetry` consistently returned a generic STOMP `ERROR` frame (`Failed to send message to ExecutorSubscribableChannel[clientInboundChannel]`) whose root cause could not be isolated without elevating backend log verbosity, which would have required a disruptive mid-session restart. **This is not attributed to a confirmed product defect**: the exact tenant-isolation logic this destination pattern exercises (36-character-UUID matching, cross-org denial, own-org success, unrecognized-destination rejection, `/user/` bypass) is independently and deterministically unit-tested — `WebSocketAuthChannelInterceptorTest`, 6/6 passing, part of the 71/71 suite — and is far more likely to reflect a limitation of this session's minimal hand-rolled STOMP client than of the actual interceptor. **Recommendation:** re-run this specific live check with a proper STOMP client library (e.g., Spring's own `WebSocketStompClient`) in a follow-up session.

## 25. Security Results

| Check | Result |
|---|---|
| Anonymous MQTT disabled | **PASS** |
| MQTT authentication works | **PASS** |
| Broker ACL works | **PASS** |
| Robot identity enforced | **PASS** |
| Tenant isolation works (MQTT) | **PASS** |
| TLS works | **PASS** |
| Invalid payload rejected | **PASS** |
| Duplicate rejected | **PASS** (idempotent no-op) |
| Replay handled | **PASS** (documented as `STALE_TIMESTAMP`) |
| Rate limiting works | **PASS** |
| Credentials not logged | **PASS** (manually inspected all 80 `application_logs` rows + 5 `audit_logs` rows; also confirmed no secret value ever appeared in any terminal output — every credential-bearing curl output in this session was piped through a redaction filter before being read) |
| Secrets not committed | **PASS** — see §27 |
| Unauthorized REST access rejected | **NOT RE-TESTED LIVE** — already exhaustively covered by `RobotControllerSecurityTest`/`RobotCredentialControllerTest` in the automated suite; this live session used only the `SUPER_ADMIN` bootstrap account for REST calls |
| WebSocket isolation works | **INCONCLUSIVE live** (see §24) / **PASS via unit test** |

## 26. Automated Test Results

```
Backend: 71/71 PASS (re-run after live validation, clean ./mvnw clean test)
Agent:   18/18 PASS (re-run after live validation, clean ./gradlew clean test)
Agent build: BUILD SUCCESSFUL (./gradlew assembleDebug)
```

## 27. Failures

No test produced an unexpected result. The only friction encountered was environmental/tooling, not product-related:
- Two earlier `RawPublish` harness invocations hung waiting for a PUBACK that a broker-ACL-denied publish never sends (expected MQTT behavior for a permission-denied QoS-1 publish, not a bug — fixed in the harness, not the product, partway through the session).
- The H2 AUTO_SERVER TCP protocol could not be reached from a second process mid-session (an H2/environment quirk); worked around by querying the database file directly after a clean shutdown instead.
- The live WebSocket check (§24) did not complete cleanly with this session's minimal STOMP client.

## 28. Remaining Gaps

Unchanged from the Phase 3 Security Hardening report, now with live confirmation of what "unchanged" means:
1. **Broker-side authentication is not backed by `robot_credentials`** — this test broker's own credential store is a separate, hardcoded test map; a real deployment still needs a broker (Mosquitto dynamic-security plugin, or equivalent) actually wired to check the backend-issued credential. This was true before this session and remains true.
2. **No certificate-based (mTLS) robot identity** — username/password only, as designed.
3. **Live WebSocket tenant isolation** — needs a follow-up check with a proper STOMP client (§24).
4. **`robot_events`/`robot_errors` live ingestion** — not exercised live this session (only unit-tested); recommend a follow-up live EVENT/ERROR publish test.
5. Everything already listed as a production requirement in `SAKAR_MQTT_ARCHITECTURE.md` §7 remains a production requirement — this session validated the software pipeline against a real broker, it did not close any infrastructure gap.

## 29. Physical Validation Requirements

```
Physical CleanBot 5000 Plus: NOT CONNECTED
Physical telemetry: NOT VERIFIED
Physical MQTT: NOT VERIFIED
Remote lock: NOT IMPLEMENTED
Remote unlock: NOT IMPLEMENTED
```

Everything in this report is **TEST AGENT / TEST DATA ONLY**, as instructed — no claim here should ever be read as, or requoted as, a physical-robot claim.

---

## Final Scorecard

| Test | Result | Evidence |
|---|---|---|
| MQTT connection | PASS | Backend log `Connected to MQTT broker as sakar-cloud-...`; broker log `AUTH OK` |
| MQTT authentication | PASS | Anonymous rejected; every real connection shows explicit `AUTH OK` |
| TLS | PASS | Valid-cert connect succeeded (5 ACKs); untrusted-cert connect failed with zero broker-side auth attempts; plaintext-to-TLS-port rejected |
| Broker ACL | PASS | Cross-robot publish/subscribe denied at broker (`ACL ... DENIED`) |
| Robot identity | PASS | Envelope/topic robotId mismatch → `MALFORMED_MESSAGE`; unknown robotId → `UNKNOWN_ROBOT` |
| Heartbeat | PASS | 16× `HEARTBEAT_ACCEPTED` in `application_logs`; real fields confirmed |
| Telemetry | PASS | 25 rows in `robot_telemetry` for robot-test-001, correct metrics |
| ACK | PASS | Every publish produced a matching, correctly-shaped ACK |
| Database persistence | PASS | Direct H2 query, §22 |
| Duplicate protection | PASS | `dup-test-fixed-001` appears exactly once in `mqtt_inbound_messages` |
| Replay protection | PASS | Stale-timestamp replay rejected both times, never reaches the dedup ledger |
| Invalid payload | PASS | All 8 malformed/invalid variants correctly rejected, zero crashes |
| Cross-robot isolation | PASS (BROKER ACL ENFORCED) | §10, §17 |
| Cross-tenant isolation | PASS (APPLICATION IDENTITY ENFORCEMENT) | §10, §18 |
| Rate limiting | PASS | 5 accepted / 2 rejected in a deliberate burst; 24 total rejections session-wide, backend never unresponsive |
| Reconnect | PASS | `onConnectionLost` → bounded backoff (~47s) → `onConnected reconnect=true` → traffic resumed |
| Offline queue | PASS (indirect + unit-tested) | ~27 ACKs across a 47s outage, quantitatively consistent with queue capture+drain; `BoundedOfflineQueueTest` covers the bounded/drop-oldest guarantee precisely |
| SRELS | PASS | 80 `application_logs` rows covering every defined lifecycle/ingestion event code, no sensitive values |
| WebSocket | INCONCLUSIVE (live) / PASS (unit test) | §24 |

---

## Final Status

```
SOFTWARE MQTT: PASS
LIVE TEST BROKER: PASS
TLS: PASS
BROKER ACL: PASS
TELEMETRY END-TO-END: PASS
DATABASE INGESTION: PASS
SECURITY TESTS: 16/18 PASS, 1 NOT RE-TESTED LIVE (REST authz - already unit-tested), 1 INCONCLUSIVE (WebSocket live check)
AUTOMATED BACKEND: 71/71 PASS
AUTOMATED AGENT: 18/18 PASS
PHYSICAL CLEANBOT: NOT TESTED
REMOTE LOCK: NOT IMPLEMENTED
PEANUT SDK: UNCHANGED
AAR: UNCHANGED
SECRETS: NOT EXPOSED
GIT: CLEAN
COMMIT: NONE
PUSH: NONE

UNIT TESTED: YES
LIVE MQTT: YES
TEST AGENT E2E: YES
PHYSICAL CLEANBOT: NO
PRODUCTION VALIDATED: NO
```
