# SakarC40Agent — Phase 0 Validation Report

**Naming note:** `SakarC40Agent` is the current concrete implementation of the generic **Sakar Robot Agent** role, built for the first product, **Sakar CleanBot 5000 Plus**, against the Keenon C40 / C40 S reference hardware — see [`docs/architecture/SAKAR_ROBOT_PLATFORM_NAMING_AND_MODEL_STRATEGY.md`](../architecture/SAKAR_ROBOT_PLATFORM_NAMING_AND_MODEL_STRATEGY.md).

**Status:** describes the CURRENT state only. Compares the existing `SakarC40Agent` implementation (copied under `robot/SakarC40Agent`) against the approved documentation (`docs/requirements/`, `docs/security/`, `docs/architecture/`, `docs/api/`). No new feature was implemented. No physical C40 was connected. No Peanut SDK file was modified.

**Companion documents:** `docs/architecture/SAKAR_C40_AGENT_ARCHITECTURE.md` (current architecture), `docs/security/SAKAR_C40_AGENT_SECURITY_AUDIT.md` (current security findings).

---

## 1. Build Result

| Item | Result |
|---|---|
| Command | `./gradlew clean assembleDebug --no-daemon` |
| Outcome | **BUILD SUCCESSFUL** (19s; 212 actionable tasks, 201 executed, 11 up-to-date) |
| Compilation errors | **None.** |
| Warnings | One deprecation notice in `ui/.../MainActivity.java` (uses/overrides a deprecated Android API — not identified further in this pass, non-blocking); one native-library notice ("Unable to strip the following libraries, packaging them as they are: `libkeenon_serial.so`" — expected/benign for a prebuilt vendor `.so` without debug-symbol metadata, does not affect functionality) |
| Artifact produced | `robot/SakarC40Agent/app/build/outputs/apk/debug/app-debug.apk` |
| Environment issues encountered (not code defects) | (1) The shell's `JAVA_HOME` environment variable was pre-set to an invalid path (`...\jdk-17\bin`, missing the JDK root); worked around by exporting a corrected `JAVA_HOME` for the build command only — no system configuration was changed. (2) `local.properties` (machine-specific, git-ignored) did not exist in the fresh copy and was recreated locally with the same Android SDK path already installed on this machine — required for any Android Gradle build, not a project defect. |
| Compilation fixes applied to the Agent's own source | **None required.** The existing code compiled and packaged successfully with zero modifications. |

**Conclusion: no genuine compilation or integration problem exists in the current `SakarC40Agent` codebase.**

## 2. Gradle Module Inventory

| Module | Type | Purpose |
|---|---|---|
| `:app` | Android application | Application shell, manifest merge point, `SakarC40Application` lifecycle |
| `:sdk` | Android library | Sole Peanut SDK integration point (`PeanutSdkBridge`) |
| `:robot` | Android library | `C40RobotController` — the safety-gating façade |
| `:navigation` | Android library | Thin navigation pass-through (`NavigationBridge`) |
| `:charging` | Android library | Thin charging pass-through (`ChargingBridge`) |
| `:telemetry` | Java library | Pure data classes, no SDK imports |
| `:diagnostics` | Android library | Device/environment/serial-port read-only inspection |
| `:logging` | Java library | `SdkCallLogger` in-memory ring buffer |
| `:ui` | Android library | `MainActivity` diagnostic dashboard |
| `:api` | Java library | Empty placeholder for a future Sakar Backend integration surface |

All 10 modules built successfully; full detail in `docs/architecture/SAKAR_C40_AGENT_ARCHITECTURE.md`.

## 3. Peanut SDK Integration — Current State

Confirmed unchanged from `PEANUT_SDK_C40_TECHNICAL_STUDY.md` and `COMPATIBILITY_REPORT.md`: the vendored `peanut-sdk-release.aar` (SDK version `1.5.0-bate1`) is resolved via a `flatDir` repository, every `com.keenon.*` reference is confined to `PeanutSdkBridge.java`, and every SDK method this bridge calls was independently re-confirmed present in the compiled AAR by the earlier `javap`-based technical study. Nothing in this phase required re-decompiling the AAR — it was not touched, and its identity (SHA-256 `67a868f2317cb8e3cd095d771ae05d1acdf2a65b2fae68656cf0adfdbf4579cf`) was already verified unchanged during the earlier workspace-migration task.

## 4. C40 Capability Status

Per-capability classification, using the required vocabulary. Nothing below claims physical C40 behavior merely because an SDK API exists or because this project's code compiles.

| Capability | SDK API wired into this codebase? | Classification | Notes |
|---|---|---|---|
| SDK initialization | Yes — `PeanutSdkBridge.init()` | `CONFIRMED` (as a software call path); physical success on a real C40 `REQUIRES PHYSICAL C40 TEST` | Two-step init pattern matches the vendor's own sample app exactly |
| SDK connection / runtime start | Yes — `PeanutSdkBridge.startRuntime()` | Same as above | Only invoked after SDK init succeeds |
| Runtime status | Yes — `getRuntimeSnapshot()` | `CONFIRMED` (API); values `REQUIRES PHYSICAL C40 TEST` | 12-field snapshot, all fields pass through unparsed |
| Battery | Yes — `queryBatteryStatus()` | `CONFIRMED` (API); real value `REQUIRES PHYSICAL C40 TEST` | Response is an opaque, unparsed string |
| Charging (start/stop) | Yes — `startManualCharge()`/`stopCharge()`, gated behind `HARDWARE_TEST` (never reachable in this build) | `CONFIRMED` (API); physical effect `REQUIRES PHYSICAL C40 TEST` | `autoCharge(pile)` deliberately not wired in — pile number `UNKNOWN` |
| Motor status/health (read) | Yes — `queryMotorStatus()`/`queryMotorHealth()` | `CONFIRMED` (API); real value `REQUIRES PHYSICAL C40 TEST` | Read-only, ungated |
| Motor lock/unlock (actuation) | **No — not called anywhere in this codebase** | SDK-level capability `CONFIRMED` to exist (per `PEANUT_SDK_C40_TECHNICAL_STUDY.md`'s bytecode analysis); this project does not invoke it; physical behavior **`REQUIRES PHYSICAL C40 TEST`** regardless | Not production-ready by definition — see `docs/security/SAKAR_SECURITY_REQUIREMENTS.md` Section 6 |
| Navigation (target/pause/resume/stop, read status) | Yes, all gated behind `HARDWARE_TEST` (never reachable) except status read (ungated) | `CONFIRMED` (API); physical behavior `REQUIRES PHYSICAL C40 TEST` | Target-point IDs are never invented by this code |
| Position | Yes — `queryRobotPosition()`, explicitly labeled `UNCONFIRMED` in the source itself | `UNKNOWN` / `REQUIRES PHYSICAL C40 TEST` | Consistent with the Keenon Cloud audit, which found this endpoint returns empty for the current fleet |
| Maps | **No — `MapComponent`/`MapManager` not wired into this codebase at all** | `UNKNOWN` — out of scope for this POC | Documented in `COMPATIBILITY_REPORT.md` as intentionally excluded |
| Errors / health events | Yes — `PeanutRuntime.Listener.onHealth()` surfaced raw | `CONFIRMED` (API delivers something); schema `UNKNOWN`, real content `REQUIRES PHYSICAL C40 TEST` | Not parsed into a typed model anywhere |
| Events | Yes — `PeanutRuntime.Listener.onEvent()` surfaced raw | Same as above | |
| Telemetry (on-device only) | Yes — `RuntimeSnapshot`/`HealthEvent` | `CONFIRMED` as a local capability; forwarding to any backend does not exist | No cloud path — expected at this phase |
| Logging (on-device diagnostic only) | Yes — `SdkCallLogger` | `CONFIRMED` — implemented and working (verified by the successful build and source read) | Not the SRELS system from the master requirements; a different, simpler thing |
| Diagnostics | Yes — `DeviceEnvironmentInspector`, `SerialPortInspector` | `CONFIRMED` — implemented, read-only, safe | |

## 5. Comparison Against Approved Documentation

| Master requirements expectation | Current `SakarC40Agent` state | Gap type |
|---|---|---|
| Part 7.C: telemetry forwarding to Sakar Cloud | Does not exist — telemetry stays on-device | Expected gap, Phase 1+ work |
| Part 12 (SRELS): six-category structured logging | Does not exist — only the simpler `SdkCallLogger` ring buffer | Expected gap, Phase 4 work |
| Part 20: signed/expiring/nonce-protected command envelope | Does not exist — there is no command channel to a backend yet | Expected gap, Phase 8 work |
| Part 21: Android Keystore, device identity, kiosk/device-owner enforcement | Does not exist (Security Audit findings F2, F3, F7) | Expected gap, flagged for a future security-hardening phase |
| Part 11/38: ten-condition physical lock validation | Not applicable yet — no lock/unlock actuation code exists to test | Cannot be attempted before the actuation code itself is built, and even then requires physical hardware access this phase did not have |
| `OperatingMode.DIAGNOSTIC_ONLY` default-safe posture | **Matches the master requirements' safety-first principle exactly** — no actuation is reachable without an explicit, currently-unwired mode change | Positive finding, no gap |

**No architectural inconsistency was found between the existing codebase and the approved documentation.** Every gap identified above is an *absence* of not-yet-built future-phase functionality, consistent with what the master requirements themselves describe as future work — not a contradiction between what exists and what was approved.

## 6. Unsupported Assumptions About C40 Hardware — Checked For, None Found

The codebase was specifically checked for any assumption that a physical C40 supports a capability merely because the SDK exposes it. None was found:
- `queryRobotPosition()` is explicitly commented `UNCONFIRMED on the physical C40` at both the bridge and controller level, and the UI prefixes its display with `[UNCONFIRMED API on C40]`.
- `ChargingBridge` explicitly refuses to call `autoCharge(pile)` because the pile number is unconfirmed.
- `NavigationBridge` never invents a target-point ID.
- `COMPATIBILITY_REPORT.md` itself states, in its own words, "Nothing in this report is marked `CONFIRMED`," and every SDK capability in its table is graded `LIKELY` or `UNKNOWN` against physical C40 behavior — never `CONFIRMED`.

## 7. Physical Tests Required (before any capability above may be re-graded)

Full test procedures already exist in `docs/requirements/SAKAR_ROBOT_PLATFORM_MASTER_REQUIREMENTS.md` Part 38. Restated as a checklist against this specific codebase:
1. Confirm which `LinkType` the physical C40 actually requires (`DEFAULT`/`COM`/`COM_COAP`/`COAP`/`HTTP`).
2. Confirm `PeanutSDK.init()` succeeds against the real robot.
3. Confirm `PeanutRuntime.start()` produces real heartbeat/health content.
4. Confirm battery/motor-status/runtime-info values are sane and stable.
5. Confirm (or refute) that `queryRobotPosition()` returns usable data.
6. **Before any future phase wires up `MotorComponent.enable()`:** the full ten-condition lock/unlock validation plan (Part 38.B) — none of which has been attempted, because the actuation code does not exist in this build.

## 8. Network Tests Required

None specific to this codebase's current state — it makes no network calls of any kind today. The previously-identified Keenon network test requirement (stock app traffic, OTA subsystem traffic — `docs/security/SAKAR_SECURITY_REQUIREMENTS.md` Section 9.1) remains open and is unaffected by this phase's findings.

## 9. Vendor-Dependent Items

- Which `LinkType` the C40 requires — `REQUIRES VENDOR SUPPORT` or physical trial-and-error on real hardware; Keenon does not document this per the earlier SDK study.
- The meaning of several undocumented `RuntimeInfo`/health-event fields — `REQUIRES VENDOR SUPPORT` to fully resolve; treated as opaque pass-through data in this codebase, which is the correct posture until then.
- Whether the stock Keenon application can be safely co-installed without link contention — `COMPATIBILITY_REPORT.md` already flags this as a real risk; this codebase's `SerialPortInspector` was deliberately designed to never claim a port for exactly this reason.

## 10. Summary Classification Table

| Category | Status |
|---|---|
| Compilation | `CONFIRMED` — builds successfully, unmodified |
| Architecture vs. approved docs | `CONFIRMED` — no inconsistency found |
| Security posture at this phase | Findings documented, not yet remediated — see Security Audit |
| C40 physical behavior (all capabilities) | `REQUIRES PHYSICAL C40 TEST` — unconditionally, for every capability, per project convention |
| Keenon network behavior | `REQUIRES NETWORK TEST` — unchanged from prior phases |
| Vendor-specific unknowns | `REQUIRES VENDOR SUPPORT` — LinkType, undocumented fields, co-installation safety |
| Production readiness (any actuation capability, including lock/unlock) | **Not production-ready. Not claimed to be.** |
