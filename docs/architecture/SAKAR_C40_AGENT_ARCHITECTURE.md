# SakarC40Agent — Current Architecture (Phase 0)

**Naming note:** `SakarC40Agent` is the current concrete implementation of the generic **Sakar Robot Agent** role, built for the first product, **Sakar CleanBot 5000 Plus**, against the Keenon C40 / C40 S reference hardware. This document intentionally keeps the concrete module name throughout, since it is a snapshot of *this specific codebase's* current state, not a generic architecture description — see [`SAKAR_ROBOT_PLATFORM_NAMING_AND_MODEL_STRATEGY.md`](SAKAR_ROBOT_PLATFORM_NAMING_AND_MODEL_STRATEGY.md) for the platform-wide naming strategy.

**Status:** describes the CURRENT state of the codebase only, as of Phase 0 validation. No feature was added while producing this document. Source inspected: `robot/SakarC40Agent` (a copy of the original project, verified byte-identical for the vendored `.aar` and unmodified for every source file). External Peanut SDK reference: `D:\Sakar Robotics Projects\peanut-sdk-v1.3.0` — read-only, not modified, not moved.

---

## 1. Module Graph

10 Gradle modules, 8 of them Android library modules, 2 pure-Java modules:

```
:app  (com.android.application)
  |-- :sdk         (com.android.library)  <-- the ONLY module that imports com.keenon.* types
  |-- :robot       (com.android.library)
  |-- :ui          (com.android.library)
  `-- :logging     (java-library)

:robot
  |-- :sdk
  |-- :navigation  (com.android.library)
  |-- :charging    (com.android.library)
  |-- :telemetry   (java-library)
  `-- :logging

:ui
  |-- :robot
  |-- :sdk
  |-- :diagnostics (com.android.library)
  |-- :telemetry
  `-- :logging

:navigation --> :sdk, :logging
:charging   --> :sdk, :logging
:sdk        --> :logging, :telemetry  (+ the vendored peanut-sdk-release.aar, flatDir-resolved)
:diagnostics --> (no module deps; :ui depends on it directly)
:api        (java-library) --> (no deps; empty marker interface, unused by any other module)
```

**Isolation invariant (verified by direct source read, not by claim):** every `com.keenon.*` import in this entire codebase is confined to exactly one file — `sdk/src/main/java/com/sakarrobotics/c40agent/sdk/PeanutSdkBridge.java`. No other module, including `:ui`, ever imports a Peanut SDK type directly. `CONFIRMED`.

## 2. Layered Runtime Architecture

```
MainActivity (:ui)
      |
      v
C40RobotController (:robot)   <-- single safety gate: OperatingMode
      |         |
      v         v
NavigationBridge  ChargingBridge   (:navigation, :charging — thin pass-throughs, no safety logic of their own)
      |         |
      v         v
   PeanutSdkBridge (:sdk)     <-- single chokepoint to the vendor SDK
      |
      v
Peanut SDK (external .aar, vendored at sdk/libs/peanut-sdk-release.aar)
      |
      v
C40 (local link — CONFIRMED private-only per PEANUT_SDK_C40_TECHNICAL_STUDY.md)
```

Cross-cutting: `SdkCallLogger` (`:logging`) records every SDK call's request/response/error, feeding both the on-screen raw log (`MainActivity`) and, in principle, any future SRELS forwarding. `telemetry` (`:telemetry`) holds pure data classes (`RuntimeSnapshot`, `HealthEvent`, `ConnectionStatus`, `RawSnapshot`) with zero SDK imports, used to move data across module boundaries without leaking `com.keenon.*` types upward.

## 3. Peanut SDK Integration

| Aspect | Current state |
|---|---|
| SDK version | `1.5.0-bate1` (from the AAR's own `AndroidManifest.xml`, confirmed by unzipping it — not the "v1.3.0" marketing label) |
| Dependency mechanism | `implementation(name: 'peanut-sdk-release', ext: 'aar')` inside `:sdk`, resolved via a root-level `flatDir { dirs "${rootProject.projectDir}/sdk/libs" }` repository declared for `allprojects` — required because AGP will not bundle a direct local `.aar` `files()` dependency into another library module |
| Additional required dependencies | `org.slf4j:slf4j-api:1.7.25`, `com.google.code.gson:gson:2.8.6` — both confirmed required by the SDK's bundled CoAP (Californium) libraries, per the vendor's own `SampleApp/app/build.gradle` |
| Initialization pattern | Two separate steps, exactly matching the vendor's own `SampleApp` (`KeenonApiDemoMain`): (1) `PeanutSDK.getInstance().init(context, ErrorListener)`, then (2) only on success, `PeanutRuntime.getInstance().start(Listener)` |
| Configuration | `PeanutConfig.getConfig().setLinkType(...).enableLog(true).setLogLevel(Log.DEBUG).setAppId(...).setSecret(...).enableUMLog(false)`, then either `.setLinkCOM(host)` (if `LinkType.COM`) or `.setLinkIP(host)` + optional `.setLinkPort(port)` |
| Link type in use | `SdkLinkType.DEFAULT` unless overridden via `secrets.properties` — **`UNKNOWN` which value the physical C40 actually needs; `REQUIRES PHYSICAL C40 TEST`** |
| Actuation calls wired in | Only `startManualCharge`, `stopCharge`, `setNavigationTarget`, `pauseNavigation`, `resumeNavigation`, `stopNavigation` — each gated by `C40RobotController.guard()` |
| Actuation calls NOT wired in anywhere | `MotorComponent.enable()` (motor lock/unlock), `BatteryComponent.autoCharge()` (deliberately avoided — requires a charging-pile number nothing in this project has confirmed), any door/map/light/disinfect component | 

## 4. Configuration Handling

- `secrets.properties` (git-ignored, not present in this copy — only `secrets.properties.example` exists) supplies `APP_ID`, `APP_SECRET`, `LINK_TYPE`, `LINK_HOST`, `LINK_PORT` to `:sdk`'s `build.gradle`, which generates `BuildConfig` fields from them.
- If `secrets.properties` is absent, every field falls back to a safe default (`''` for strings, `'DEFAULT'` for `LINK_TYPE`, `0` for `LINK_PORT`) — **confirmed by this Phase 0 build**, which succeeded with no `secrets.properties` present.
- `SdkConnectionConfig.fromBuildConfig()` is documented as "the only supported way to obtain a config in app code" — no code path constructs credentials inline. `CONFIRMED` by source read.
- `local.properties` (machine-specific `sdk.dir`) was not part of the copy (correctly git-ignored) and was recreated locally, pointing at the same Android SDK path (`C:\Users\sakar\AppData\Local\Android\Sdk`) already installed on this machine, purely to make Phase 0's build attempt possible.

## 5. Logging

`SdkCallLogger` (`:logging`) is a synchronized, in-memory ring buffer (max 500 entries) of every SDK call's `(api, request, response|error)`, with a listener mechanism `MainActivity` uses to append entries to an on-screen scrolling raw log in real time. It is the **only** place SDK call bookkeeping happens — confirmed no module logs SDK activity ad-hoc through `android.util.Log` directly for this purpose. There is no persistence (nothing survives a process restart) and no forwarding anywhere yet — this is a pure on-device diagnostic aid today, not the SRELS system specified in the master requirements (Part 12), which does not exist in this codebase yet.

## 6. Telemetry

`:telemetry` module: `RuntimeSnapshot` (12-field mirror of the SDK's `RuntimeInfo`: workMode, syncStatus, power, totalOdo, emergencyEnable/Open, motorStatus, robotArmInfo, robotStm32Info, robotIp, robotProperties, destList), `HealthEvent` (kind: EVENT/HEALTH/HEARTBEAT + raw content + timestamp), `ConnectionStatus` (DISCONNECTED/CONNECTING/CONNECTED/INIT_FAILED — an app-local enum, not an SDK type), `RawSnapshot` (wraps an unparsed SDK response string, since response schemas are largely undocumented). All of this data stays on-device; there is no telemetry-forwarding capability to any backend yet.

## 7. Diagnostics

`:diagnostics` module: `DeviceEnvironmentInspector` (read-only `android.os.Build` + `java.net.NetworkInterface` introspection — Android version, manufacturer/model, CPU ABI, whether the running device's ABI is compatible with the SDK's 32-bit-only native libraries, and a list of network interfaces/addresses already bound to the device) and `SerialPortInspector` (checks `/dev/ttyS1`, `/dev/ttyS2`, `/dev/ttyS3` for existence only via `File.exists()` — never opens, reads, or writes to them).

## 8. Navigation & Charging Bridges

Both `NavigationBridge` and `ChargingBridge` are deliberately thin — they hold no safety logic of their own and forward directly to `PeanutSdkBridge`. `ChargingBridge.startCharging()` calls `manualCharge()`, not `autoCharge(pile)`, specifically because the charging-pile number is unconfirmed for the C40 and the project's own convention is to never guess such values. `NavigationBridge` never invents a target-point ID — callers must supply one from a real, confirmed map/point table.

## 9. Robot Controller (`C40RobotController`)

The sole safety gate in the entire codebase. Field `operatingMode` defaults to, and in this build can only ever be, `OperatingMode.DIAGNOSTIC_ONLY` — `setOperatingMode(HARDWARE_TEST)` exists as a method but is never called anywhere in the source tree (`CONFIRMED` by exhaustive grep). Every action that could move the robot, touch the motor, or start/stop charging (`goToPoint`, `pause/resume/stopNavigation`, `start/stopCharging`) is routed through a private `guard()` method that short-circuits to error `-1001` unless `operatingMode == HARDWARE_TEST`. Read-only queries (`getBattery`, `getPosition`, `getMotorStatus`, `getMotorHealth`, `getRuntimeInfo`, `getHealth`, `getHeartbeat`) are **not** gated — they are always callable, consistent with them being non-actuating.

## 10. Application Lifecycle

`SakarC40Application.onCreate()` builds a `SdkConnectionConfig` via `fromBuildConfig()`, constructs a single `C40RobotController`, and stores it in a static `C40RobotControllerHolder` — the SDK itself is **not** initialized at application startup; `PeanutSDK.init()` only runs when `MainActivity`'s connect button is pressed (`C40RobotController.connect()`). This means the app can launch and be inspected even with no robot/SDK link present at all, which is why Phase 0's build/inspection did not require a physical C40.

## 11. Android Permissions (declared, current state)

| Permission | Declared in | Justified by code? |
|---|---|---|
| `INTERNET` | `app`, `sdk` | Not yet exercised — no networking code exists anywhere in this codebase today (verified by grep: no `OkHttp`/`Retrofit`/`HttpURLConnection`/raw sockets/`http://` literal found anywhere); the Peanut SDK itself may use it internally for its local CoAP/HTTP/WS link types (Part 10 of the master requirements) |
| `ACCESS_NETWORK_STATE` | `app`, `sdk`, `diagnostics` | Consistent with `DeviceEnvironmentInspector.getNetworkInterfaceSummaries()` and whatever internal connectivity checks the SDK performs |
| `READ_PHONE_STATE` | `app` | **No code anywhere in this repository uses `TelephonyManager` or reads any phone-state/IMEI value** (verified by grep). See `SAKAR_C40_AGENT_SECURITY_AUDIT.md` for this finding — not fixed in this phase per the "document, don't fix" instruction. |

## 12. What Does Not Exist Yet (by design, this phase)

No REST/MQTT/WebSocket client of any kind. No cloud/backend integration beyond the `:api` module's single empty marker interface (`SakarBackendApi`, explicitly documented as "NOT IMPLEMENTED"). No SRELS logging system (only the in-memory `SdkCallLogger`). No device/agent identity mechanism. No Android Keystore usage. No kiosk/device-owner enforcement. No remote lock/unlock command path. All of this is expected and correct for Phase 0 — the master requirements' Parts 8, 12, 15, 20–24 describe a *future* state, not this codebase's current state.
