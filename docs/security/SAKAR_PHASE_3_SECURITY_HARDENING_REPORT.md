# Phase 3 — Security Review & Hardening Report

**Date:** 2026-08-29 · **Scope:** the Phase 3 MQTT software pipeline only (backend `mqtt`/`telemetry`/`srels`/`robot.registry` credential code, agent `:api` MQTT client) · **Status: SOFTWARE HARDENED. LIVE BROKER: NOT YET VERIFIED. PHYSICAL CLEANBOT: NOT TESTED. PRODUCTION CERTIFIED: NO.**

This report governs every status claim below: a finding marked "fixed" or "hardened" means *fixed in software and covered by an automated test that runs with no broker and no robot* — never more than that. Where a gap can only be closed by broker infrastructure or physical hardware, it is marked `REQUIRES BROKER CONFIGURATION` or `REQUIRES PHYSICAL C40 TEST` and is **not** claimed as resolved.

---

## 1. Executive Summary

Nothing CRITICAL was found. 5 HIGH and 5 MEDIUM findings were identified against the actual Phase 3 code (not against documentation); all 5 HIGH and 3 of 5 MEDIUM were fixed in this pass with new automated tests. The remaining 2 MEDIUM findings (broker anonymous access, no broker-side ACL) are infrastructure/broker configuration, explicitly out of this repository's reach, and are documented as such rather than claimed fixed. No secrets were found in the repository. The Peanut SDK and its `.aar` are byte-for-byte unchanged. No lock/unlock, movement command, or Web/Mobile/Fleet code was added, per the task's explicit boundaries.

## 1.5 Threat Model

| Threat | Attack path | Impact | Current protection | Remaining gap | Hardening action (this pass) |
|---|---|---|---|---|---|
| **A. Internet attacker** | Reaches the MQTT broker port or the backend's public REST API directly | Could flood the broker, attempt credential guessing, or probe the API | REST: JWT auth + RBAC on every non-public endpoint (Phase 1, unchanged). MQTT: none at the broker (dev config) | Broker has no network-level access control | Documented as `REQUIRES BROKER CONFIGURATION` (firewalling/VPC placement is also out of this repo's reach) |
| **B. Compromised robot** | A real, registered robot's onboard agent is compromised (malware, physical tampering) | Attacker can publish arbitrary telemetry/events/errors as that robot, or flood it | Rate limit (F1) bounds flood impact; message content is still constrained to the robot's own registered org/site | Cannot revoke the robot's *transport-level* MQTT access without broker support; software revocation (F5) only removes the backend's own credential-hash record | Rate limiting added (F1); revoke endpoint added (F5) as the software half |
| **C. Compromised Robot Agent (software-only, e.g. a malicious dependency)** | A compromised library or supply-chain attack inside `:api`/`:sdk` | Could exfiltrate the MQTT credential from `BuildConfig`, or publish malicious payloads | Gson CVE fixed (F3); `PeanutSdkBridge` chokepoint still confines all `com.keenon.*` access to one file, unaffected by MQTT changes | No Android Keystore isolation for the MQTT credential (documented, §9) | Dependency CVE fixed (F3) |
| **D. Unauthorized Sakar user** | A valid but under-privileged user account | Attempts to view/configure robots or issue credentials beyond their role | RBAC (`ROBOT_CONFIGURE` etc.), tenant scoping via `TenantAccessGuard` (Phase 1, unchanged) | None new identified | Verified via `RobotCredentialControllerTest`'s viewer-role cases |
| **E. Compromised MQTT credential** | An issued robot credential (username/password) is stolen | Attacker could authenticate as that robot to the broker (once the broker enforces credentials at all) | Credential is BCrypt-hashed at rest; rotation and revocation both exist (F5) | Broker doesn't check the credential today, so "compromise" and "no credential at all" currently have the same practical effect at the transport layer — mitigated at the message-content layer only (org/site/status cross-check) | Revocation + audit logging (F4/F5) |
| **F. Malicious MQTT client (not a registered robot)** | Any client that can reach the broker publishes to an arbitrary/guessed topic | Could inject fake telemetry for a real robotId, or flood the ingestion pipeline | Envelope `robotId` must match topic `robotId`; robot must exist, be active, and match the topic's org/site; rate limit and size cap bound flood impact | Nothing prevents the connection itself without broker ACLs | F1/F2 (flood/size), pre-existing identity/tenant checks (re-verified) |
| **G. Cross-tenant attacker** | A user or robot from Organization A attempts to reach Organization B's data | Cross-tenant data exposure or spoofed telemetry | `TenantAccessGuard` (REST), topic/registry cross-check (MQTT), `WebSocketAuthChannelInterceptor` (WebSocket, now tested — F8) | None new identified across the surfaces this phase touches | New WebSocket test suite (F8) |
| **H. Replay attacker** | Captures a valid TELEMETRY/HEARTBEAT/ACK message and re-sends it | Could re-inject stale data as if current | `messageId`-keyed idempotency ledger (`mqtt_inbound_messages`, pre-existing); timestamp-skew rejection bounds how stale a "fresh-looking" replay can be | No cryptographic signing — a replay within the timestamp/dedup window using a *fresh* messageId+timestamp (i.e., a true man-in-the-middle re-signing scenario) is not distinguishable from a genuine message without TLS+broker auth | **MESSAGE SIGNING: NOT IMPLEMENTED** — see §4; TLS (F7) is the mitigation for in-transit tampering, not a substitute for message signing |
| **I. Message tampering attacker** | Modifies a message in transit | Corrupted/falsified telemetry accepted as genuine | TLS (F7, when enabled) protects transport integrity; schema/type validation rejects structurally invalid results | No per-message signature independent of transport — see H | Same as H: TLS is necessary but explicitly not sufficient; documented, not solved, this pass |
| **J. Database attacker** | Direct or exfiltrated PostgreSQL access | Full data exposure | Unchanged from Phase 1 (private-network-only DB, no public exposure by design) — Phase 3 added no new exposure path | Unchanged | None needed this pass — reviewed, no regression |
| **K. Insider with excessive privileges** | A Sakar staff account with `ROBOT_CONFIGURE` misuses credential issuance | Could provision/rotate/revoke credentials for robots they're not operationally responsible for (but are tenant-authorized for) | Audit logging now records every provision/rotate/revoke action (F4/F5) | No anomaly detection/alerting on unusual credential-issuance patterns (out of scope — no alerting system exists anywhere in the platform yet) | Audit logging added (F4/F5) — detection, not prevention |

## 2. Security Findings (Baseline, Before This Pass)

| # | Finding | Area | Severity |
|---|---|---|---|
| F1 | No per-robot rate limit on MQTT ingestion — a single robot (or spoofed robotId) could publish unboundedly | MQTT ingestion | HIGH |
| F2 | No maximum payload size — an oversized MQTT message would be handed straight to the JSON parser | MQTT ingestion | HIGH |
| F3 | Agent's JSON library (Gson 2.8.6) carried a known, high-severity CVE (CVE-2022-25647, deserialization of untrusted data, CVSS 7.7) | Robot Agent dependency | HIGH |
| F4 | MQTT credential provisioning/rotation was not audit-logged — no accountability trail for who issued or rotated a robot's credential | Credential endpoint | HIGH |
| F5 | No credential revocation capability existed at all (only provision/rotate) | Credential lifecycle | HIGH |
| F6 | `sequence` accepted any `long`, including negative values, with no sanity check | MQTT envelope validation | MEDIUM |
| F7 | Neither backend nor agent MQTT client had any TLS configuration surface — only plaintext `tcp://` was wired, undocumented whether `ssl://` even worked | MQTT transport | MEDIUM |
| F8 | Zero test coverage existed for `WebSocketAuthChannelInterceptor`'s tenant-isolation logic (a Phase 1 gap, surfaced because Phase 3 is the first code to actually publish real WebSocket traffic through it) | WebSocket | MEDIUM |
| F9 | The dev MQTT broker (`backend/docker/mosquitto.conf`) accepts anonymous, plaintext connections | Broker infrastructure | MEDIUM |
| F10 | No broker-side topic ACL exists — nothing at the broker prevents a connected client from publishing/subscribing to another robot's topic | Broker infrastructure | MEDIUM |
| F11 | `robot_events.payload`/`application_logs.context` are unbounded `TEXT` columns | Database | LOW |
| F12 | `android:exported="true"` on `MainActivity` | Android Agent | INFO (reviewed, not a defect — required for a LAUNCHER activity) |
| F13 | Eclipse Paho 1.2.5 (both ends) | Dependency | INFO (already the latest release in this line; the known CVE-2019-11777 was fixed in 1.2.1) |

## 3. Fixes Applied

| # | Fix | Files | Test Coverage |
|---|---|---|---|
| F1 | `MqttRateLimiterService` — Redis-backed, per-robot, fixed-window (120 msg/60s default, configurable), checked before any DB lookup | `mqtt/MqttRateLimiterService.java`, wired into `MqttInboundMessageService` | `MqttInboundMessageServiceTest.exceedingThePerRobotRateLimit_isRejected` |
| F2 | Hard payload-size cap (64 KiB default, configurable), enforced **before** JSON parsing | `mqtt/MqttInboundListener.java`, `MqttProperties.maxPayloadSizeBytes` | `MqttInboundListenerTest.oversizedPayload_isRejectedWithoutEverParsingItOrCallingTheInboundService` |
| F3 | Gson bumped 2.8.6 → 2.8.9 in `:api` (the fix version, same 2.8.x line); `:sdk`'s own 2.8.6 pin deliberately left untouched (vendor/Peanut-SDK-adjacent) | `robot/SakarC40Agent/api/build.gradle` | `:api:test` (18/18) + `assembleDebug` post-bump |
| F4 | `AuditService.record(...)` called on every provision/rotate/revoke, logging the action only, never the raw secret | `robot/registry/RobotCredentialService.java` | `RobotCredentialControllerTest.provisioning_isAuditLoggedWithoutTheRawSecretAnywhereInTheAuditTrail` |
| F5 | New `DELETE /api/v1/robots/{id}/mqtt-credentials` — deletes the stored hash, audit-logged; same RBAC/tenant checks as provisioning | `robot/registry/RobotCredentialService.java`, `RobotController.java` | `RobotCredentialControllerTest.revoking_deletesTheStoredCredentialAndIsAuditLogged`, `.revoking_withoutRobotConfigurePermission_isForbidden` |
| F6 | Reject `sequence < 0` as `INVALID_SEQUENCE` (sanity only — not strict monotonic-order enforcement, which would incorrectly reject legitimate QoS-1 out-of-order delivery) | `mqtt/MqttInboundMessageService.java`, `MqttRejectionReason.INVALID_SEQUENCE` | `MqttInboundMessageServiceTest.negativeSequence_isRejectedAsInvalidSequence` |
| F7 | Both backend and agent now explicitly support `ssl://` broker URLs (Paho's own default, real-CA-validated TLS — never trust-all) plus optional private-CA truststore config (`MqttProperties`/`SakarMqttConfig` `tlsTrustStorePath`/`Password`) | `mqtt/MqttGatewayService.java`, `api/mqtt/AgentMqttClient.java`, `SakarMqttConfig.java` | `AgentMqttClientSafetyTest` (2 new cases); **not** run against a real TLS-terminating broker — `REQUIRES NETWORK TEST` |
| F8 | New unit test suite exercising `WebSocketAuthChannelInterceptor` directly (no live socket) | — (test-only) | `WebSocketAuthChannelInterceptorTest` (6 cases) |
| F9 | Not fixed — documented. `backend/docker/mosquitto.conf` is unmodified; its header comment already correctly states "Production requires TLS, per-robot credentials, and topic ACLs — none of that is configured here." | — | `REQUIRES BROKER CONFIGURATION` |
| F10 | Not fixed — documented, same reason as F9. The software-side equivalent (message-content robot/tenant cross-check) was already implemented in the original Phase 3 pass and remains the actual current defense | — | `REQUIRES BROKER CONFIGURATION` |
| F11 | Not fixed — the new payload-size cap (F2) bounds this indirectly (nothing can write more than ~64 KiB worth of content per message anymore); a DB-level `CHECK` constraint was considered and rejected as out of scope ("do not rewrite old migrations" and no new migration is genuinely required for this) | — | Indirect, via F2's tests |
| F12 | Not fixed — reviewed and confirmed correct. A `LAUNCHER`-intent-filtered activity must be `exported="true"` on modern Android; setting it `false` would break the app's ability to launch | — | Manual review only |
| F13 | No action — already the safe version | — | — |

## 3.5 Replay Protection & Message Signing — Explicit Statement

Software-side replay protection **is** implemented: the `mqtt_inbound_messages` unique constraint on `(robot_id, message_id)` makes a captured-and-replayed TELEMETRY/HEARTBEAT message a no-op rather than a duplicate write, and the timestamp-skew check (both directions) bounds how old a "fresh-looking" replay can appear to be. This is bounded, persisted deduplication, not unbounded storage — `mqtt_inbound_messages` is an append-only ledger, same retention posture as every other append-only table in the schema.

```
MESSAGE SIGNING: NOT IMPLEMENTED
```

TLS (F7) protects a message against tampering **in transit** once enabled and once a broker actually terminates it. It does **not** provide end-to-end message signing — nothing in this pipeline lets the backend cryptographically verify that a given envelope's contents actually originated from the specific robot's private key, independent of the transport. That is a distinct, larger piece of work (`SAKAR_ROBOT_PLATFORM_API_SPEC.md` §2.11 already anticipates it for *commands* specifically) and is explicitly **not implemented** for telemetry/heartbeat/event/error messages in this phase. Documented here as a production hardening requirement, not pretended to be covered by TLS alone.

## 4. Remaining Risks

See `SAKAR_SECURITY_RISK_REGISTER.md` R08 (updated), R21 (new), R22 (new). In order of severity:

1. **Broker does not enforce anything** (F9/F10, register R08) — the single largest remaining gap. Every software-side identity/tenant check in this pipeline is defense-in-depth on top of a broker that currently trusts every connection unconditionally. **REQUIRES BROKER CONFIGURATION** (Mosquitto password file or dynamic-security plugin, per-client ACLs, TLS listener) before any production claim.
2. **No live TLS validation** — the client-side `ssl://` support (F7) has never been exercised against an actual TLS-terminating broker in this session. **REQUIRES NETWORK TEST.**
3. **No certificate-based (mTLS) robot identity** — username/password only; certificate auth remains a documented future posture (`SAKAR_SECURITY_REQUIREMENTS.md` §10), not attempted this phase.
4. **Rate limit / payload cap are software-only** — a broker with no size/rate limits of its own could still be flooded at the transport level before a message ever reaches the backend's Redis-backed limiter. Defense-in-depth, not a complete DoS solution.
5. **`agentId` remains unauthenticated** — recorded for observability only, as documented since the original Phase 3 pass; unchanged by this hardening pass.

## 5. MQTT Security

See §3/§4 above and the updated `SAKAR_SECURITY_REQUIREMENTS.md` §10 table (per-control DEVELOPMENT/DESIGN-COMPLETE/REQUIREMENT split) and `SAKAR_MQTT_ARCHITECTURE.md` §7.

## 6. Robot Identity

Unchanged in design from the original Phase 3 pass, re-verified this pass:
- `robotId`/`agentId`/`organizationId`/`siteId` — `robotId` is the only value ever trusted for authorization; `agentId` is observability-only; `organizationId`/`siteId` are read from the topic as a *claim*, then cross-checked against the registered `Robot` row (`MqttInboundMessageService`) — a mismatch is rejected as `TENANT_MISMATCH`. This was tested before and remains tested (`topicOrganizationNotMatchingTheRegisteredRobot_isRejectedAsTenantMismatch`).
- A robot cannot publish telemetry for another robot in software: the envelope's `robotId` must match the topic's `robotId` (`envelopeRobotIdNotMatchingTopicRobotId_isRejectedAsMalformed`), and the topic's org/site must match the *registered* robot, not whatever the publisher claims.
- What remains unenforced is **which physical/network client is allowed to publish to that topic at all** — that is the broker's job, and the broker does not do it yet (F9/F10).

## 7. Tenant Isolation

Verified across every surface named in the task:
- **REST APIs:** `RobotControllerSecurityTest`, `RobotCredentialControllerTest.cannotProvisionCredentialsForARobotInAnUnrelatedOrganization` (pre-existing + Phase 3).
- **MQTT ingestion/telemetry/events/errors:** `MqttInboundMessageServiceTest.topicOrganizationNotMatchingTheRegisteredRobot_isRejectedAsTenantMismatch` — one check, applied identically before any message type is routed, so it covers telemetry/events/errors/heartbeat/presence uniformly.
- **Robot credentials:** `RobotCredentialControllerTest` (cross-tenant provisioning and revocation both denied).
- **WebSocket:** new `WebSocketAuthChannelInterceptorTest` (F8 fix) — subscribing to another organization's destination is denied by `TenantAccessGuard`, verified directly.
- **Database queries:** unchanged from Phase 1 — every tenant-scoped repository method still requires an explicit organization-id filter (`TenantAccessGuard`'s existing chokepoint); Phase 3 added no new unscoped query path.
- **Commands:** N/A — no command dispatch exists in this phase.

`organizationId`/`siteId` supplied by any client (topic, payload, or otherwise) is never trusted as the source of truth anywhere in this codebase — ownership is always re-derived from the authenticated user (REST) or the registered `Robot` row (MQTT).

## 8. API Security

`POST`/`DELETE /api/v1/robots/{id}/mqtt-credentials`:

| Check | Status |
|---|---|
| Authentication | Required (JWT, same as every non-public endpoint) |
| RBAC | `ROBOT_CONFIGURE` required — `VIEWER`/read-only roles get `403` (tested) |
| Organization/robot ownership | `robotService.getAccessibleOrThrow` before any credential operation — cross-org requests get `404 ROBOT_NOT_FOUND`, not `403` (consistent with the platform's existing anti-enumeration convention) |
| Input validation | Path variable only (`UUID id`), validated by Spring's own UUID conversion |
| IDOR protection | Covered by the ownership check above |
| Rate limiting | Not added to this endpoint specifically — the existing global authentication requirement plus RBAC is the primary control; a dedicated per-endpoint rate limit was judged out of scope for this pass (no evidence of abuse risk beyond what login rate limiting already covers for account-level brute force) |
| Request size limits | Not applicable — no request body |
| Error handling | Routes through the existing `GlobalExceptionHandler` — no stack trace, SQL, or internal path ever reaches the response |
| Audit logging | **Added this pass** (F4/F5) |

Tested across unauthenticated (implicit — no token, standard Spring Security 401), `VIEWER` (read-only), and cross-tenant `ORG_ADMIN` — all get exactly the access level expected, no more.

## 9. Agent Security

- `secrets.properties`/`.example`: unchanged pattern from Phase 3's original pass — real file git-ignored, `.example` carries only blank/placeholder values, re-verified this pass (`git diff` shows no new secret material).
- `BuildConfig`: MQTT fields (`SAKAR_MQTT_*`) sourced from `secrets.properties`, same mechanism as the Peanut SDK's own `APP_ID`/`APP_SECRET` — no hardcoding.
- `AndroidManifest.xml`: reviewed (F12) — `MainActivity` export is correct/required; no exported services/receivers/providers exist anywhere in the project; `allowBackup="false"` unchanged from Phase 0.
- Credential storage: the MQTT password lives only in `secrets.properties` (dev) → `BuildConfig` (compile-time constant baked into the APK) → `SakarMqttConfig` in memory. **Android Keystore is not used to store it** — this matches the pre-existing pattern for the Peanut SDK's own `APP_SECRET`. **Determination for this pass:** implementing Keystore-backed secure storage for the MQTT credential now would be scope creep beyond "harden the current phase" (it would require a provisioning-flow redesign — fetching the credential at first-run and persisting it in the Keystore, rather than baking it into the APK at build time) and is documented here as a **production requirement**, not implemented.
- Logs: verified (`grep`) that `mqttPassword`/`SAKAR_MQTT_PASSWORD` is never passed to `SdkCallLogger` or any print statement anywhere in the agent codebase — only used at `MqttConnectOptions.setPassword(...)`.
- Debug/release: no behavioral difference introduced by Phase 3 or this hardening pass; `minifyEnabled false` in both build types is a pre-existing Phase 0 choice, unchanged.

## 10. Database Security

| Table | Constraints/indexes | Reviewed finding |
|---|---|---|
| `mqtt_inbound_messages` | Unique `(robot_id, message_id)`, FK to `robots` `ON DELETE CASCADE`, index on `(robot_id, received_at)` | No change needed — correct as designed |
| `robot_telemetry`, `robot_events`, `robot_errors`, `robot_status` | Unchanged (Phase 1/3 schema) | No new migration required |
| `robot_credentials` | Unique `robot_id`, FK to `robots` `ON DELETE CASCADE` | Revocation (F5) uses a plain `DELETE` on this table — no new column/migration needed |

**No existing migration was modified or rewritten.** No new migration was added in this pass (F1/F2's config is application-level, not schema).

## 11. Logging Security

Grepped the full diff and all Phase 3 code for `password=`, `secret=`, credential variables, JWTs, and Authorization headers reaching any logger: none found. `MqttLifecycleLogger`/`AuditService` calls introduced by this pass (F1's `RATE_LIMITED_REJECTED`, F2's `PAYLOAD_TOO_LARGE_REJECTED`, F4/F5's `MQTT_CREDENTIAL_*`) carry only event codes, robot/organization ids, and generic detail strings (byte counts, rejection reasons) — never a raw payload, credential, or token. `RobotCredentialControllerTest.provisioning_isAuditLoggedWithoutTheRawSecretAnywhereInTheAuditTrail` asserts this directly against persisted rows, not just code inspection.

## 12. Test Results

**Backend** (`cd backend && ./mvnw clean test`):
```
Tests run: 71, Failures: 0, Errors: 0
BUILD SUCCESS
```
59 pre-existing (32 Phase 1 + 27 original Phase 3) + **12 new this hardening pass**: `MqttInboundMessageServiceTest` (+2: negative sequence, rate limit), `MqttInboundListenerTest` (+1: oversized payload), `RobotCredentialControllerTest` (+3: audit trail, revoke, revoke-forbidden), `WebSocketAuthChannelInterceptorTest` (+6, new class).

`./mvnw package -DskipTests` succeeds, producing `backend/target/sakar-cloud-backend-0.0.1-SNAPSHOT.jar`.

**Robot Agent** (`cd robot/SakarC40Agent && ./gradlew :api:test`):
```
18 tests completed, 0 failed
BUILD SUCCESSFUL
```
16 pre-existing + 2 new (`AgentMqttClientSafetyTest`: ssl:// with default trust store, ssl:// with custom trust store path).

`./gradlew assembleDebug test` (whole agent project): `BUILD SUCCESSFUL`.

## 13. Secret Scan

Scanned the full diff and every new/modified file for `.env`, `secrets.properties`, `local.properties`, `*.jks`/`*.keystore`/`*.pem`/`*.key`/`*.p12`, `*.aar`/`*.apk`/`*.aab`, and source patterns `password=`/`secret=`/`apiKey=`/`accessToken=`/`clientSecret=`/`privateKey=`. **No secrets found.** The only file matching a sensitive-pattern name (`secrets.properties.example`) contains only blank values and a dev-default `tcp://localhost:1883` broker URL, consistent with the original Phase 3 pass. No `.aar`/`.apk`/`.aab`/`.jks`/`.keystore`/`.pem`/`.key`/`.env`/`local.properties`/`secrets.properties` is tracked or newly added — confirmed via both `git ls-files` and `git status --porcelain` pattern matching.

## 14. Peanut SDK Integrity

- **Peanut SDK: UNCHANGED.** `peanut-sdk-release.aar` SHA-256 (`67a868f2317cb8e3cd095d771ae05d1acdf2a65b2fae68656cf0adfdbf4579cf`) identical to the value recorded in the original Phase 3 audit.
- `robot/SakarC40Agent/sdk/build.gradle`: unmodified this pass.
- `backend/src/main/java/.../security/SecurityConfig.java`: unmodified this pass (verified via `git diff --stat` — empty).
- `backend/docker/mosquitto.conf`: unmodified this pass.
- **No `com.keenon.*` import exists outside `PeanutSdkBridge.java`** (verified by `git grep '^import com.keenon'` across the whole `robot/SakarC40Agent` tree) — the only non-code hits are Javadoc/README prose mentioning the class name, not actual imports.

## 15. Physical Validation Status

```
Physical CleanBot 5000 Plus: NOT TESTED
Physical telemetry: NOT VERIFIED
Physical MQTT communication: NOT VERIFIED
Remote lock: NOT IMPLEMENTED
Remote unlock: NOT IMPLEMENTED
```
Unchanged from the original Phase 3 report — nothing in this hardening pass touched, connected to, or tested against a physical robot or a live broker.

## 16. Production Readiness

**NO.** Software hardening in this pass closes 5 HIGH and 3 MEDIUM findings entirely within the repository's reach. It does **not** close: broker-enforced authentication/ACL/TLS (F9/F10 — infrastructure), live TLS validation (needs a real broker), or Android Keystore-backed credential storage (documented as a production requirement, not implemented — see §9). None of these can be closed by source-code changes alone; all require either broker configuration outside this repository or a physical/network test this task explicitly did not authorize.

## 17. Remaining Recommendations

Highest-priority next steps, in order:
1. **Broker configuration** (Mosquitto dynamic-security plugin or password file + per-client ACLs + TLS listener) — this alone would close F9/F10/R08's remaining half and is pure infrastructure work, no application code change needed.
2. **Live network test** of the TLS path (F7) against a real `ssl://` broker, and of the rate-limit/payload-cap behavior (F1/F2) under actual flood conditions.
3. **Physical C40 validation** (Master Requirements Part 38) — unchanged priority from the original Phase 3 report, still gating any physical-telemetry or lock/unlock claim.

---

## Final Security Scorecard

| Area | Before | After | Status | Residual Risk |
|---|---|---|---|---|
| Authentication (REST) | Implemented (Phase 1) | Unchanged | DESIGN COMPLETE | None new |
| Authorization (RBAC) | Implemented (Phase 1) | Unchanged + new credential-revoke check | DESIGN COMPLETE | None new |
| Robot identity | Software cross-check only | Unchanged (re-verified) | DESIGN COMPLETE | Broker doesn't enforce transport-level identity |
| Tenant isolation | REST + MQTT covered | + WebSocket now test-covered (F8) | DESIGN COMPLETE | None new for covered surfaces |
| MQTT TLS | Not configured at all | `ssl://` supported, real CA validation, optional custom truststore | DESIGN COMPLETE | Never run against a live broker |
| MQTT ACL | Not implemented | Unchanged (software cross-check only) | REQUIREMENT | REQUIRES BROKER CONFIGURATION |
| Credential management | Provision/rotate only, unaudited | + revoke, + audit logging (F4/F5) | DESIGN COMPLETE | Broker doesn't check credential at CONNECT |
| Replay protection | messageId dedup only | Unchanged (re-verified sound) | DESIGN COMPLETE | None new |
| Payload validation | Schema/timestamp only | + size cap, + sequence sanity (F2/F6) | DESIGN COMPLETE | None new |
| Rate limiting | None | Per-robot Redis limiter (F1) | DESIGN COMPLETE | Broker itself has no limiter |
| Offline queue | Bounded (200, drop-oldest) | Unchanged (re-verified) | DESIGN COMPLETE | None new |
| Reconnect | Bounded backoff (Paho) | Unchanged (re-verified) | DESIGN COMPLETE | None new |
| Logging | No sensitive-value leaks found | + new events, still no leaks (verified) | DESIGN COMPLETE | None new |
| WebSocket | Implemented, untested | Now unit-tested (F8) | DESIGN COMPLETE | None new |
| Database | Sound schema, no new migration needed | Unchanged | DESIGN COMPLETE | Unbounded TEXT columns (F11, low, indirectly bounded by F2) |
| Android Agent | Secrets via BuildConfig, no Keystore | Unchanged; exported-component review done (F12, no issue) | PARTIAL | No Keystore-backed credential storage (documented production requirement) |
| Secret management | Clean | Clean (re-scanned) | DESIGN COMPLETE | None |
| Audit logging | Present for auth; absent for credentials | + credential provision/rotate/revoke (F4/F5) | DESIGN COMPLETE | None new |

---

## Final Report Fields

```
SOFTWARE HARDENED: YES
LIVE BROKER: NOT YET VERIFIED
PHYSICAL CLEANBOT: NOT TESTED
PRODUCTION CERTIFIED: NO

Backend tests: 71/71 PASS
Phase 3 tests (backend, cumulative): 39/39 PASS (27 original + 12 hardening)
Agent tests: 18/18 PASS
Backend build: SUCCESS
Agent build: SUCCESS

Security critical findings: 0
High findings: 5 (5 fixed)
Medium findings: 5 (3 fixed, 2 documented as REQUIRES BROKER CONFIGURATION)
Low findings: 1 (documented, indirectly mitigated)

Secrets exposed: NO
Peanut SDK modified: NO
AAR modified: NO
Physical CleanBot tested: NO
MQTT live broker tested: NO
Remote lock implemented: NO
```
