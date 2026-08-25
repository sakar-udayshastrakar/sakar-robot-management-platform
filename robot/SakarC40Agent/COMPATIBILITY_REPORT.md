# Keenon C40 Compatibility Report

Status as of this build: **no physical Keenon C40 has been connected to any
development environment this project has been built in.** Every row below
is classified according to that reality.

## Classification legend

| Classification | Meaning |
| --- | --- |
| `CONFIRMED` | Tested successfully on a physical C40. |
| `LIKELY` | The API exists in the compiled SDK, appears generic (not tied to a specific chassis model), but has not been tested on a C40. |
| `UNKNOWN` | Insufficient evidence either way. |
| `NOT SUPPORTED` | A verified limitation. |

**Nothing in this report is marked `CONFIRMED`.**

## How this was verified

`peanut-sdk-v1.3.0/peanut-sdk-v1.3.0/peanut-sdk-v1.3.0/libs/peanut-sdk-release.aar`
was inspected directly:

- `AndroidManifest.xml` inside the AAR: `package="com.keenon.peanut"`,
  `android:versionName="1.5.0-bate1"`, `minSdkVersion 19`, `targetSdkVersion 29`.
- `jni/` contains only `armeabi/` and `armeabi-v7a/` - **no `arm64-v8a`**.
- `classes.jar` was unzipped and every class/method cited below was
  confirmed with `javap -p` against the actual compiled bytecode (not
  assumed from documentation).
- The vendor's own `SampleApp` source (`app/src/main/java/com/keenon/...`)
  was read to confirm real call patterns (e.g. two-step init, builder
  APIs).
- The Chinese/English developer `.docx` documents in `doc/` were **not**
  used as the source of truth for API signatures; the compiled AAR was
  authoritative in every case, per instructions. No SDK/AAR discrepancy
  was found that needed reconciling - the sample app code matches the
  decompiled bytecode exactly everywhere it was checked.
- No C40-specific class, string constant, or model identifier exists
  anywhere in the AAR. The SDK is chassis-generic; C40 is never named.

## Feature compatibility table

| Feature | SDK API (verified to exist) | C40 Status | Evidence | Notes |
| --- | --- | --- | --- | --- |
| SDK initialization | `PeanutSDK.getInstance().init(Context, ErrorListener)` | LIKELY | Method confirmed via `javap`; used by wrapped `PeanutSdkBridge.init()` | Two-step: init must succeed (`errorCode == PeanutSDK.SDK_INIT_SUCCESS`) before starting the runtime. |
| Runtime connection | `PeanutRuntime.getInstance().start(Listener)` | LIKELY | Confirmed via `javap`; separate call from SDK init, confirmed in vendor SampleApp `KeenonApiDemoMain` | Not chassis-specific in the bytecode. |
| Runtime status / info | `PeanutRuntime.getInstance().getRuntimeInfo()` → `RuntimeInfo` (workMode, syncStatus, power, totalOdo, emergencyEnable/Open, motorStatus, robotArmInfo, robotStm32Info, robotIp, robotProperties, destList) | LIKELY | Confirmed via `javap` | Populated by internal scheduled sync tasks after `start()`; no request/response shape is invented here. |
| Battery status | `PeanutSDK.getInstance().battery().getStatus(IDataCallback)` | LIKELY | `BatteryComponent` confirmed via `javap` | Response is an opaque `String`; schema not published, not parsed. |
| Motor status | `PeanutSDK.getInstance().motor().getStatus(IDataCallback)` | LIKELY | `MotorComponent` confirmed via `javap` | |
| Motor enable/disable | `MotorComponent.enable(IDataCallback, int)` with `ApiConstants.MOTOR_ENABLE_UNLOCK` / `MOTOR_ENABLE_LOCK` | UNKNOWN | Method + constants confirmed via `javap`, used in vendor sample's `MotorDemo` | **Not wired into `C40RobotController`** - motor enable/disable is an actuation call and is out of scope for `DIAGNOSTIC_ONLY` mode. |
| Position | `PeanutSDK.getInstance().runtime().getRobotPosition(IDataCallback)` on `RuntimeComponent` | UNKNOWN | Method confirmed to exist via `javap` | Explicitly called out as UNCONFIRMED per project instructions - existence of the method is not evidence it behaves correctly on a C40. |
| Navigation target | `PeanutSDK.getInstance().navigation().setTarget(IDataCallback, int)` on `NavigationComponent`; also `PeanutNavigation.Builder` (higher-level, used by vendor's `NavigationDemo`/`NavManager`) | UNKNOWN | Both confirmed via `javap` | Target-point IDs are per-map and are never guessed by this app - see README "required target-point table". |
| Navigation pause | `NavigationComponent.pause(IDataCallback)` / `PeanutNavigation.setPilotWhenReady(false)` | UNKNOWN | Confirmed via `javap` | |
| Navigation resume | `NavigationComponent.resume(IDataCallback)` / `PeanutNavigation.setPilotWhenReady(true)` | UNKNOWN | Confirmed via `javap` | |
| Navigation stop | `NavigationComponent.stop(IDataCallback)` / `PeanutNavigation.stop()` | UNKNOWN | Confirmed via `javap` | |
| Navigation status | `NavigationComponent.getStatus(IDataCallback)`; topic `TopicName.NAVIGATION_STATUS` | UNKNOWN | Confirmed via `javap` | |
| Map | `PeanutSDK.getInstance().map()` → `MapComponent` (upload/download/request); `MapManager` (import/export to ROS) | UNKNOWN | Confirmed via `javap` | **Not wired into `C40RobotController`** - out of scope for this POC. |
| Charging (start) | `BatteryComponent.manualCharge(IDataCallback)` / `.autoCharge(IDataCallback, int pile)`; also standalone `PeanutCharger` builder | UNKNOWN | Confirmed via `javap`; `autoCharge` takes a pile number this project refuses to guess | `ChargingBridge.startCharging()` uses `manualCharge()` specifically to avoid guessing a pile id. |
| Charging (stop) | `BatteryComponent.stopCharge(IDataCallback)` | UNKNOWN | Confirmed via `javap` | |
| Charging status | `BatteryComponent.getChargeMatches(IDataCallback)`; topic `TopicName.CHARGE_MATCH_TIMES` | UNKNOWN | Confirmed via `javap` | |
| Emergency state | `RuntimeInfo.isEmergencyEnable()` / `isEmergencyOpen()`; `PeanutRuntime.setEmergencyEnable(boolean)` | UNKNOWN | Confirmed via `javap` | Only the read-only getters are wired in; `setEmergencyEnable` is deliberately not called anywhere in this app. |
| Health / errors | Topic `TopicName.RUNTIME_HEALTH`; `PeanutRuntime.Listener.onHealth(Object)` / `onHeartbeat(Object)` | LIKELY | Confirmed via `javap` | Surfaced as raw content in the diagnostic UI, not parsed into a schema. |
| Doors | `PeanutSDK.getInstance().door()` → `DoorComponent` (open/close/getState/getAllLockStatus/getALlDoorStatus); also `PeanutDoor` gating manager with `Faults`/`GatingType`/`GatingState` | UNKNOWN | Confirmed via `javap` | **Not wired into `C40RobotController`** - the C40 is a delivery/base chassis; doors were not in the requested controller surface. Documented here only because the compatibility report is asked to cover it. |

## Important C40 limitations (explicit)

1. The SDK contains no explicit "C40" string, class, or model identifier anywhere in the compiled AAR - it is a generic Peanut-chassis SDK.
2. C40 compatibility has not been physically confirmed for any API in this table.
3. `AppId`/`Secret` are required by `PeanutConfig.Config.setAppId/setSecret` and must come from Keenon for your account - they are never hardcoded in this repo (see `secrets.properties.example`).
4. Target-point IDs must not be guessed; they must come from the C40's own configured map/point table.
5. The link type (`DEFAULT`/`COM`/`COM_COAP`/`COAP`/`HTTP`) must be determined on the actual C40; this build defaults to `DEFAULT` only because nothing is confirmed.
6. `RuntimeComponent.getRobotPosition()` exists in the compiled SDK but its behavior on a C40 is unconfirmed.
7. Pause/resume navigation behavior must be tested on the actual C40 before any automated use.
8. The SDK's native libraries (`jni/armeabi`, `jni/armeabi-v7a`) are 32-bit ARM only - there is no `arm64-v8a` build. A 64-bit-only Android device may not be able to load them; `DeviceEnvironmentInspector.isSupportedByNativeSdkLibs()` flags this at runtime.
9. Existing Keenon software running on the C40 tablet may already hold the SDK's link (serial port, IP, or AppId session) - installing this agent alongside it may conflict.
10. This app never force-stops, disables, or interferes with any existing Keenon application, and never opens/claims a serial port - `SerialPortInspector` only calls `File.exists()`.
