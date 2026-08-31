# SakarC40Agent

A native Android proof-of-concept bridge between the Keenon C40 delivery
robot and future Sakar software, built on top of Keenon's Peanut SDK
(v1.3.0 distribution, compiled SDK version `1.5.0-bate1`).

This module is the current concrete implementation of the generic **Sakar Robot Agent** role for the first product, **Sakar CleanBot 5000 Plus** — see [`docs/architecture/SAKAR_ROBOT_PLATFORM_NAMING_AND_MODEL_STRATEGY.md`](../../docs/architecture/SAKAR_ROBOT_PLATFORM_NAMING_AND_MODEL_STRATEGY.md).

## Purpose

This app talks to the C40's onboard Peanut SDK and exposes a clean,
modular Java API (`C40RobotController`) that later Sakar software can
build on, without any module outside `:sdk` ever importing a
`com.keenon.*` class directly.

**Current scope is diagnostic-only for physical robot actions.** There is
no physical C40 available in any environment this was built in, so the
app defaults to `OperatingMode.DIAGNOSTIC_ONLY` and only performs
read-only status queries via the Peanut SDK. See `COMPATIBILITY_REPORT.md`
for exactly what has and has not been verified.

**Roadmap Phase 7 addition:** the app now also consumes remote commands
over MQTT from the Sakar Cloud backend (`CommandDispatcher`, in `:api`,
routing by command type via `CompositeRobotCommandExecutor`) and reports
RECEIVED/EXECUTING/COMPLETED/DISPATCHED/FAILED/TIMEOUT lifecycle results
back. Two command types are wired, with two very different executors:

- `START_TASK` → `SimulatedRobotCommandExecutor` (in `:app`), an
  **explicitly-labeled software placeholder that never calls the Peanut
  SDK and never touches the robot**. This is not a shortcut: the
  officially-distributed Peanut SDK v1.3.0 AAR this app bundles has **no
  cleaning-control API at all** (confirmed by decompiling every class in
  the AAR — no `CleanComponent`, no `com.keenon.sdk.robot.api` /
  `com.keenon.sdk.coapapi` packages).
- `RETURN_TO_DOCK` → `PeanutSdkReturnToDockExecutor` (in `:api`) →
  `RealReturnToDockGateway` (in `:app`) → `C40RobotController.returnToDock()`
  — a **REAL** call to the officially-distributed SDK's
  `BatteryComponent.autoCharge(IDataCallback, int)` (re-verified this pass
  by fresh `javap` decompilation; internally `@CoapCommond(path="/charge/auto")`).
  This one goes through the exact same `OperatingMode.HARDWARE_TEST` guard
  as `startCharging`/`stopCharging` — it is never called unless that mode
  is deliberately, supervisedly enabled (see "C40 hardware test procedure"
  below). Its success signal is reported as `DISPATCHED`, not `COMPLETED`
  — the SDK callback only confirms the local interface *accepted* the
  request, never that the robot physically reached a dock or began
  charging.

See `../../ROBOT_AGENT_COMMAND_LOOP_INVESTIGATION_AND_DESIGN.md` (§§1-21
for `START_TASK`, §22 for `RETURN_TO_DOCK`) for the full investigation and
the business decision still open before cleaning gets a real executor.

**Roadmap addition (unreleased C40 S reverse-engineering pass):** the
diagnostic dashboard now also queries raw sensor data and map metadata,
none of which were previously wired into this project even though both
were already present in the bundled AAR:

- `C40RobotController.getLidar/getDepth/getSonar/getImu()` → `PeanutSdkBridge`
  → `com.keenon.sdk.api.SensorLidarApi/SensorDepthApi/SensorSonarApi/SensorImuApi`
  (public, standalone classes confirmed present via `javap` - **not** the
  same package as the internal-only `com.keenon.sdk.coapapi.api.sensor.*`
  classes found bundled inside Keenon's own C40 S apps). One-shot
  `send()` queries only; `observe()` (continuous subscription) exists on
  each class but is intentionally not wired yet.
- `C40RobotController.getMapInfo()/downloadMap()` → `MapComponent.getMapInfo`/`downloadOpt`
  - read-only, no operating-mode guard, shown on the diagnostic screen.
- `C40RobotController.uploadMap(byte[], callback)` → `MapComponent.uploadOpt`
  - writes to the robot, so it is gated by `OperatingMode.HARDWARE_TEST`
  exactly like `goToPoint`/`startCharging`/`returnToDock`, and (like
  those) has no UI control on this diagnostic-only screen. `PeanutSdkBridge.uploadMap`
  rejects `null`/empty map data before ever calling the SDK (`SdkCallback.onError`
  with `PeanutSdkBridge.ERROR_INVALID_MAP_DATA`), rather than relying on
  Keenon's `MapDownloadOptApi.CoapParams()` silently degrading a null
  payload to an empty request body. Also documented in
  `PeanutSdkBridge.java`: Keenon's own `MapUploadOptApi`/`MapDownloadOptApi`
  internal class names are reversed relative to what they do (verified by
  bytecode - `downloadOpt` calls the class named "Upload", `uploadOpt`
  calls the class named "Download") - this bridge's read/gated-write split
  is based on the verified payload behavior, not the vendor names.

**Status:**
- Map/Sensor integration: **CODE VERIFIED** (every method traced to the
  actual bundled AAR via `javap`, `:sdk`/`:robot`/`:ui`/`:app` build clean).
- Software tests: **SOFTWARE TEST VERIFIED** - `robot/src/test/java/.../C40RobotControllerTest.java`
  covers `uploadMap`'s gating (null/empty data rejected before the SDK is
  touched; `OperatingMode.DIAGNOSTIC_ONLY` blocks before the SDK is
  touched; `OperatingMode.HARDWARE_TEST` with valid data reaches the real
  `PeanutSDK` singleton - which then throws in this pure-JVM test because
  it was never initialized, and that exception is itself the proof the
  guard let the call through). No Android runtime, no physical robot.
- Physical robot: **NOT PERFORMED.** None of the above has been exercised
  against a physical C40 S.

See
`../../C40_S_LS_M014C00_RW_F00_V246_ROS_INTERFACE_ANALYSIS.md` section 18
for the full evidence trail (fresh `javap` extraction of this project's
own vendored `peanut-sdk-release.aar`) that motivated this addition.

## Architecture

Today:

```text
Keenon C40
    |
    v
SakarC40Agent   (this project)
    |
    v
Peanut SDK      (peanut-sdk-release.aar, vendored read-only)
    |
    v
C40 Robot Controller  (com.keenon.* chassis firmware, on the robot)
```

Later (not built yet - out of scope for this project):

```text
Sakar Web Dashboard
        |
        v
Sakar Backend
        |
        v
SakarC40Agent
        |
        v
Peanut SDK
        |
        v
C40
```

### Module layout

| Module | Role |
| --- | --- |
| `app` | Application shell: `SakarC40Application` builds the one `C40RobotController` for the process. |
| `sdk` | The **only** module allowed to import `com.keenon.*`. Wraps `PeanutSDK`/`PeanutRuntime`/component classes behind decoupled types (`SdkConnectionConfig`, `SdkCallback`, `PeanutSdkBridge`). |
| `robot` | `C40RobotController` - the main abstraction the rest of the app uses. Enforces `OperatingMode` (see Safety below). |
| `navigation` | Thin wrapper over `sdk`'s navigation pass-throughs. Not safety logic - `robot` decides when it's callable. |
| `charging` | Thin wrapper over `sdk`'s charging pass-throughs. Same note as above. |
| `telemetry` | Plain data classes (`RuntimeSnapshot`, `HealthEvent`, `ConnectionStatus`, `RawSnapshot`) shared across modules. No SDK or Android dependency. |
| `diagnostics` | `DeviceEnvironmentInspector` (Android/Build info, network interfaces) and `SerialPortInspector` (read-only `/dev/ttyS*` existence checks). |
| `logging` | `LogEntry` + `SdkCallLogger`: the ring buffer behind the on-screen raw SDK log. No SDK or Android dependency. |
| `ui` | `MainActivity` - the diagnostic dashboard, and the app's only screen. |
| `api` | The Sakar Backend integration boundary: MQTT client (`AgentMqttClient`, Eclipse Paho), presence/heartbeat/telemetry/event/error publishing, and (Phase 7) inbound command consumption (`CommandDispatcher`) + lifecycle result reporting. Never imports `com.keenon.*` - see `api/README.md`. |

## Safety: operating modes

`C40RobotController` has two modes:

- `DIAGNOSTIC_ONLY` (default, and the only mode the UI can reach): status/telemetry queries only. `goToPoint`, `pauseNavigation`, `resumeNavigation`, `stopNavigation`, `startCharging`, `stopCharging`, and (Phase 7) `returnToDock` all immediately return `ERROR_BLOCKED_BY_OPERATING_MODE` without touching the SDK. This is also what blocks the MQTT-triggered `RETURN_TO_DOCK` command's real executor from ever reaching the robot in this build.
- `HARDWARE_TEST`: unlocks the actuation methods above. **Nothing in this codebase ever sets this mode**; it exists as a documented seam for a future, explicitly supervised on-robot test session.

`connect()`/`disconnect()` are always allowed in either mode - they only open/close the SDK link, they do not move anything.

## SDK integration

- The AAR lives at `sdk/libs/peanut-sdk-release.aar`, copied read-only from `peanut-sdk-v1.3.0/.../libs/peanut-sdk-release.aar` (original untouched).
- `sdk`'s `build.gradle` also declares `org.slf4j:slf4j-api` and `com.google.code.gson:gson`, matching the vendor's own `SampleApp` dependencies (required by the SDK's bundled CoAP libraries).
- Every SDK call is logged through `SdkCallLogger` (timestamp, API, request, response, success/error, error code, error message) and shown live in the UI's scrolling raw log.

## Configuration

Credentials are never hardcoded. Copy the example file and fill in real values:

```
cp secrets.properties.example secrets.properties
```

```properties
APP_ID=
APP_SECRET=
LINK_TYPE=DEFAULT   # DEFAULT | COM | COM_COAP | COAP | HTTP - unconfirmed for C40, see COMPATIBILITY_REPORT.md
LINK_HOST=
LINK_PORT=0
```

`secrets.properties` is git-ignored. `sdk`'s `build.gradle` reads it and generates `BuildConfig.APP_ID` / `APP_SECRET` / `LINK_TYPE` / `LINK_HOST` / `LINK_PORT`, which `SdkConnectionConfig.fromBuildConfig()` reads. If the file is absent, the project still compiles with empty placeholders (required for this diagnostic build, which has no physical robot to test against).

### Required target-point table

Not applicable yet - no navigation target IDs have been confirmed against
a real C40 map. Do not invent point IDs. When a physical robot and map are
available, record the confirmed point IDs here before using `goToPoint()`.

### CPU / ABI requirements

The SDK's native libraries only ship `armeabi` and `armeabi-v7a` (no
`arm64-v8a`) - see `COMPATIBILITY_REPORT.md`. The diagnostic screen's
"Native SDK libs supported" line reports whether the current device's
supported ABIs include one of these.

## Build

```
cd SakarC40Agent
./gradlew assembleDebug
```

Requires an Android SDK (platform 34, build-tools) available via
`local.properties` (`sdk.dir=...`) or the `ANDROID_HOME`/`ANDROID_SDK_ROOT`
environment variable. See the build log at the end of this document /
the assistant's final report for what was actually run and verified in
this environment.

### APK location

`app/build/outputs/apk/debug/app-debug.apk`

### ADB install

```
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Diagnostic test procedure (no robot required)

1. Install and launch the app.
2. The "Device info", "Serial ports", and app/SDK version fields populate immediately - these need no SDK connection.
3. Tap **Connect**. This calls `PeanutSDK.init()` then `PeanutRuntime.start()`. Watch the raw SDK log for the result.
4. Tap **Refresh diagnostics** to query battery, motor status/health, and (unconfirmed) position.
5. Confirm the app never becomes unresponsive and never issues any motion/charge command - there is no button that can.

## C40 hardware test procedure (requires physical robot, supervised)

This build does not perform any hardware test automatically. Before ever
switching `C40RobotController` into `HARDWARE_TEST` mode on a real C40:

1. Confirm the correct `LINK_TYPE`/`LINK_HOST`/`LINK_PORT` for that specific robot (do not assume).
2. Confirm the AppId/Secret registered for that robot/fleet.
3. Confirm the robot's E-stop is reachable and tested independently of this app.
4. Confirm you are not conflicting with the stock Keenon application already running on the tablet.
5. Only then, in a code change reviewed separately from this diagnostic build, wire an explicit, operator-confirmed path to `setOperatingMode(HARDWARE_TEST)`.

## Known limitations

See `COMPATIBILITY_REPORT.md` for the full, evidence-backed table. In short: nothing here has been confirmed against a physical C40; the SDK is chassis-generic and never names the C40 anywhere in its compiled code.
