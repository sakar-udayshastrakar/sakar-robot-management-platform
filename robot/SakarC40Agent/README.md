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

**Current scope is diagnostic-only.** There is no physical C40 available
in any environment this was built in, so the app defaults to
`OperatingMode.DIAGNOSTIC_ONLY` and only performs read-only status
queries. See `COMPATIBILITY_REPORT.md` for exactly what has and has not
been verified.

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
| `api` | Placeholder for the future Sakar Backend integration boundary. No networking code - see `api/README.md`. |

## Safety: operating modes

`C40RobotController` has two modes:

- `DIAGNOSTIC_ONLY` (default, and the only mode the UI can reach): status/telemetry queries only. `goToPoint`, `pauseNavigation`, `resumeNavigation`, `stopNavigation`, `startCharging`, and `stopCharging` all immediately return `ERROR_BLOCKED_BY_OPERATING_MODE` without touching the SDK.
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
