# Master Engineering Knowledge Base — Sakar CleanBot5000Plus / SakarC40Agent

**This is a documentation consolidation, not an implementation change.** Nothing in this document
set modifies application behavior, UI, or robot configuration. It organizes and cross-references
everything discovered across every prior audit phase in this project, plus the pre-existing SDK/
security/architecture documents already in the repository. See
[`02_EVIDENCE_REGISTER.md`](02_EVIDENCE_REGISTER.md) for the itemized, ID-tagged version of every
finding referenced here.

## Status vocabulary (used throughout this entire document set)

| Status | Meaning |
|---|---|
| `CONFIRMED` | Directly verified from APK/source/AAR/hardware/runtime evidence. |
| `STATIC-ANALYSIS CONFIRMED` | Confirmed from decompiled APK/source but not live-tested. |
| `PHYSICAL-HARDWARE CONFIRMED` | Actually observed on physical hardware. |
| `RUNTIME VERIFIED` | Actually tested while running. |
| `INFERRED` | Reasonable architectural inference but not directly proven. |
| `UNKNOWN` | No sufficient evidence. |
| `NOT VERIFIED` | Expected/identified but requires physical robot testing. |
| `NOT FOUND` | An explicit search was performed and the capability/interface was not found. |
| `UNAVAILABLE` | Evidence indicates the capability is not exposed through the currently available public interface. |

`UNKNOWN` and `INFERRED` are never silently upgraded to `CONFIRMED` anywhere in this document set.

**A parallel status vocabulary** (`OFFICIAL DOCUMENTATION CONFIRMED`, `STATIC APK ANALYSIS
CONFIRMED`, `PHYSICAL HARDWARE CONFIRMED`, `RUNTIME VERIFIED`, `INFERRED`, `UNKNOWN`,
`NOT DOCUMENTED`) is used specifically in
[`09_KEENON_OFFICIAL_C40_DOCUMENTATION_AUDIT.md`](09_KEENON_OFFICIAL_C40_DOCUMENTATION_AUDIT.md),
to distinguish findings sourced from Keenon's own official customer-facing documentation from the
findings above. `NOT DOCUMENTED` there means "this official documentation pass looked and didn't
find it" — it is never converted into a claim about what the hardware does or doesn't do, and it
is a different statement than this table's own `UNKNOWN` or `NOT FOUND`.

---

## 1. Executive Summary

Sakar is building `SakarC40Agent`, an Android operator application for the Sakar CleanBot5000Plus,
a rebranded Keenon C40-family cleaning robot. Development has used the **vendor's licensed Peanut
SDK** (`peanut-sdk-release.aar`) plus **decompiled analysis of Keenon's own five separate
applications** (never a physical robot — that access has not yet been obtained). Five audit phases
established, in order: (1) which Keenon screens live in which of Keenon's own separate APKs, (2)
how Keenon's own mapping/SLAM feature actually works end-to-end at the software level, (3) that no
physical robot is reachable from this development environment, and — from the user's own direct
physical inspection of the CleanBot5000Plus hardware, reported in this session — (4) that the
robot's Android/application computer is an **RK3288 UIB board**, and (5) that **a separate,
distinct computer performs SLAM**, not the RK3288.

**The single most consequential technical finding across all phases:** the public, licensed Peanut
SDK does not expose live SLAM/mapping control or data (start/stop mapping, occupancy-grid
streaming, LiDAR, live pose, relocalization beyond one basic trigger). That entire capability lives
in Keenon's own separate "Robot Installation Assistant" APK, communicating with an onboard,
non-Android compute node over an undocumented internal WebSocket/rosbridge protocol. Sakar's own
`SakarC40Agent` already reflects this honestly: every real, simulated, local, gated, and
unavailable capability is disclosed to the operator via a `Capability` badge, with **no fabricated
SLAM, cleaning-hardware, or mapping functionality anywhere in the current build.**

---

## 2. Robot Hardware Architecture

**Status: PHYSICAL-HARDWARE CONFIRMED (existence of two separate computers), reported directly by
the user from physical inspection of the CleanBot5000Plus in this session — not independently
re-verified by decompiled evidence, and not yet documented with photographs, schematics, or a
network capture inside this repository.**

The robot is understood to contain (at minimum) **two distinct computers**:

1. **The Android/application computer** — an **RK3288 UIB board**, running the Android OS and
   hosting Keenon's own applications (`com.keenon.peanut.clean`, etc.) and, when installed,
   `SakarC40Agent`. See §3.
2. **A separate SLAM computer** — a distinct, non-Android compute node responsible for SLAM/
   mapping/localization, reached (per Phase 4's decompiled-APK evidence) over a LAN connection at a
   specific internal address. See §4.

This two-computer architecture is consistent with, and was independently corroborated by,
Phase 4's decompiled-APK finding that Keenon's Robot Installation Assistant app relays WebSocket
traffic to an external rosbridge endpoint rather than running SLAM in-process on the Android
device — **two separate lines of evidence (user's physical hardware observation, and static
analysis of Keenon's own app) point to the same conclusion independently**, which is why this is
recorded with high confidence even though neither line of evidence alone would be sufficient.

---

## 3. Android / Application Computer — RK3288 UIB

**Status: PHYSICAL-HARDWARE CONFIRMED for board identity/markings (user-reported physical
observation, this session); STATIC-ANALYSIS CONFIRMED for its role as the Android host (matches
every APK's manifest target and the SDK's own 32-bit-only native libraries).**

- **Board markings observed** (as reported by the user from physical inspection):
  - `CBA_RK3288_UIB_SUPER_V2.2`
  - `RK3288_UIB_DUAL_LVDS_V1.0_20230803`
- **Role:** hosts Android and every Keenon Android application (`com.keenon.peanut.clean`,
  `com.keenon.peanut.peanutservice`, `com.keenon.systemservice`, `com.keenon.krlog`,
  `com.keenon.remote_control_oray` — see §6) plus `SakarC40Agent` when installed.
- **Known interfaces (per board markings and the "DUAL_LVDS" naming):** dual LVDS display output is
  strongly implied by the board's own model string — **INFERRED** from the naming convention only,
  not confirmed by a pinout/schematic. See [`03_HARDWARE_ARCHITECTURE.md`](03_HARDWARE_ARCHITECTURE.md)
  for the full connector-by-connector treatment; every connector whose function is not independently
  confirmed is marked there as `UNKNOWN — REQUIRES PINOUT / SCHEMATIC / CONTINUITY TEST`, per this
  audit's explicit instruction not to guess pinouts.
- **What is confirmed from software evidence (cross-referencing the SDK study,
  `PEANUT_SDK_C40_TECHNICAL_STUDY.md` / `COMPATIBILITY_REPORT.md`):** the RK3288 SoC is a 32-bit
  ARM (Cortex-A17) part; the licensed Peanut SDK's native libraries ship **only** `jni/armeabi` and
  `jni/armeabi-v7a` — **no `arm64-v8a` build exists** — which is consistent with, though not proof
  of, the SDK having been built against this specific 32-bit-only board family.
  `DeviceEnvironmentInspector.isSupportedByNativeSdkLibs()` (already implemented in
  `SakarC40Agent`) flags this at runtime.
- **What remains unknown:** exact RAM/storage configuration, exact peripheral wiring (see §26 and
  `03_HARDWARE_ARCHITECTURE.md`), and the exact physical connection (protocol/medium) between this
  board and the separate SLAM computer (§4) — Phase 4's evidence describes the *software* protocol
  (WebSocket/rosbridge over IP) but not the *physical* link (dedicated Ethernet? shared switch?
  USB-Ethernet adapter?) between the two boards.

---

## 4. Separate SLAM Computer

- **Confirmed existence:** `PHYSICAL-HARDWARE CONFIRMED` (user-reported, this session) — a
  distinct computer beyond the RK3288 exists and performs SLAM.
- **Suspected role:** runs the actual SLAM/occupancy-grid computation, localization, and
  ROS-based navigation stack.
- **Expected ROS/SLAM responsibilities (per Phase 4 static analysis of the decompiled Robot
  Installation Assistant APK — `STATIC-ANALYSIS CONFIRMED`, not physically re-verified):** hosts a
  `rosbridge_websocket`-compatible server on port `9090`, plus a second, Keenon-customized bridge
  instance on port `9091` serving specific topics/services
  (`/switch_dest_floor_map`, `/republish_tfs`, `/scan_base_map`, `/motor_lock`,
  `/tf2_web_republishe`).
- **Known historical IP/interface information:** the address **`192.168.64.20`** appears
  independently in *two* separate pieces of static evidence: (a) Keenon's decompiled Robot
  Installation Assistant APK (`RosSocketClientManager.ADDRESS`), and (b) the licensed Peanut SDK's
  own compiled constant `PeanutConstants.REMOTE_LINK_PROXY` (`PEANUT_SDK_C40_API_MATRIX.md` row
  55; `PEANUT_SDK_C40_TECHNICAL_STUDY.md` §7). This cross-confirmation from two independently
  decompiled artifacts raises confidence that `192.168.64.20` is a real, meaningful address in
  Keenon's architecture — **but it is explicitly `STATIC-ANALYSIS CONFIRMED`, not
  `PHYSICAL-HARDWARE CONFIRMED` or `RUNTIME VERIFIED`.** Phase 5's live network test from this
  development machine found `192.168.64.20` completely unreachable (§9 / §21) — expected, since
  this machine is not on the robot's own internal network, and **not a contradiction** of the
  static finding.
- **What is NOT physically verified (explicitly, per this audit's own rule):** whether the real
  robot's SLAM computer actually listens at `192.168.64.20`, what OS/ROS distribution it runs, its
  board model, its physical connection to the RK3288, or whether any of Phase 4's topic/service
  names exist on a live system. All of this requires the physical robot audit in
  [`07_NEXT_PHYSICAL_ROBOT_AUDIT.md`](07_NEXT_PHYSICAL_ROBOT_AUDIT.md).
- **New physical observation (this session), `PHYSICAL-HARDWARE CONFIRMED`:** this same separate
  computer (referred to by the user as the "ROS/Robot Computer") is now directly observed to be
  connected to / responsible for: LiDAR, stereo vision, ultrasonic sensors, hub motors, actuators,
  water pumps, water-level sensors, Hall sensors, depth camera, two side-brush motors,
  mopping-brush motors, scrubber roller motor, vacuum motors, vSLAM, and multiple cameras (some
  USB-connected, some via an unspecified internal wired interface). Board model, CPU, OS, ROS
  version, CAN IDs, UART ports, GPIOs, USB device IDs, baud rates, ROS topic/service names, network
  IP, protocol, and wiring pinout are all explicitly **not** inferred from this observation and
  remain `UNKNOWN`/`NOT VERIFIED`. Full detail, including an explicitly-recorded (not
  silently-resolved) tension with the SDK's separate "SCM IoT" motor-controller finding (§5 below):
  [`03_HARDWARE_ARCHITECTURE.md`](03_HARDWARE_ARCHITECTURE.md) §2.1/§3.

Full detail: [`04_SLAM_INVESTIGATION_STATUS.md`](04_SLAM_INVESTIGATION_STATUS.md).

---

## 5. Overall Robot Architecture

```
┌─────────────────────────────┐        (undocumented internal protocol,
│  RK3288 UIB board            │         STATIC-ANALYSIS CONFIRMED only)
│  (Android / application)     │◄───────────────────────────────────────┐
│                               │                                        │
│  • com.keenon.peanut.clean    │                                        │
│  • com.keenon.peanut.peanutservice (Installation Assistant)            │
│  • com.keenon.systemservice   │                                        │
│  • com.keenon.krlog           │                                        │
│  • com.keenon.remote_control_oray                                      │
│  • SakarC40Agent (this project, via licensed Peanut SDK)               │
└─────────────────────────────┘                                        │
                                                                          ▼
                                                        ┌───────────────────────────────┐
                                                        │  Separate ROS/Robot Computer    │
                                                        │  (non-Android, PHYSICAL-        │
                                                        │   HARDWARE CONFIRMED to exist;  │
                                                        │   board/CPU/OS/ROS version      │
                                                        │   not physically verified)      │
                                                        │  • rosbridge (STATIC-ANALYSIS    │
                                                        │    CONFIRMED, ports 9090/9091)  │
                                                        │  • PHYSICAL-HARDWARE CONFIRMED   │
                                                        │    connections (this session):  │
                                                        │    LiDAR, stereo vision,        │
                                                        │    ultrasonic sensors, depth    │
                                                        │    camera, multiple cameras     │
                                                        │    (USB + internal wired),      │
                                                        │    hub motors, actuators,       │
                                                        │    water pumps, water-level     │
                                                        │    sensors, Hall sensors, two   │
                                                        │    side-brush motors, mopping-  │
                                                        │    brush motors, scrubber       │
                                                        │    roller motor, vacuum         │
                                                        │    motors, vSLAM                │
                                                        │  • buses/protocols/pinouts for  │
                                                        │    all of the above: UNKNOWN    │
                                                        └───────────────────────────────┘
```

**Relationship to the motor/motion-controller board below is explicitly unresolved, not decided:**
the new physical observation that the ROS/Robot Computer is "responsible for" hub motors and
actuators does not confirm whether the SCM-IoT-addressed motor controller (below) is a distinct
board in between, the same board under a different description, or absent — see
`03_HARDWARE_ARCHITECTURE.md` §2.1/§3.

Motor/motion-board communication (separate from the SLAM computer) is confirmed at the SDK level:
the licensed Peanut SDK's `MotorComponent.getState()` uses a raw "SCM IoT" protocol
(`SCMRequest(dev=0, topic=6)`) — a **third**, low-level communication channel to a motion/motor
controller board, distinct from both the Android app processor and the SLAM computer
(`STATIC-ANALYSIS CONFIRMED`, `PEANUT_SDK_C40_TECHNICAL_STUDY.md` §4).

---

## 6. Keenon Application Separation

**Status: STATIC-ANALYSIS CONFIRMED for all 5 apps (decompiled manifests/source; see Phase 3 —
`KEENON_APPLICATION_SEPARATION_MAP.md` — for the full evidence).** Summarized here:

| Package | Display name | Role | Standalone APK? |
|---|---|---|---|
| `com.keenon.peanut.clean` | 擎朗机器人 ("Keenon Robot") | Cleaning/operator app **+ Super User + hardware Debug**, all in one APK | Yes |
| `com.keenon.peanut.peanutservice` | "Robot Installation Assistant" | Chromium WebView shell hosting a bundled web SPA for map/elevator/network/business commissioning | Yes |
| `com.keenon.systemservice` | "Keenon Service" | Shared, unauthenticated MQTT/TTS/Voice backend other apps bind to via AIDL | Yes |
| `com.keenon.krlog` | "krlog" | Standalone, boot-autostarted device-log capture/export utility | Yes |
| `com.keenon.remote_control_oray` | "Remote Assistant" | White-labeled Oray/Sunlogin third-party remote-desktop SDK | Yes |

**Reference screens by APK** (from the reference-screenshot inventory,
`docs/ui/references/REFERENCE_SCREEN_INVENTORY.md`, cross-referenced against decompiled Activity
names in Phase 3):

- `com.keenon.peanut.clean`: Home, Cleaning (Start Cleaning), Manual Drive/Manual Push, Scheduled
  Cleaning (+ Edit Task), Teaching Mode (+ Teach Route), Settings (all sub-screens), Super User
  (all 5 sidebar sections), Robot Debugging (full dashboard + sub-tests).
- `com.keenon.peanut.peanutservice`: Robot Installation/Maps, Business Settings, Elevator/Remote
  Control/Scheduling Path.
- `com.keenon.systemservice`: Keenon Service (single reference screen).
- `com.keenon.krlog`: krlog (2 reference screens).
- `com.keenon.remote_control_oray`: Remote Assistant (2 reference screens).

None of these 5 apps is a module of another — each has its own package ID, launcher entry, and
version line. The only confirmed cross-app coupling is process-level: `peanut-clean` binds to
`systemservice`'s exported, unauthenticated `MqttApiService` via a bundled AIDL stub
(`com.keenon.aidl.mqtt.IMqttAidlApi.aidl`).

---

## 7. Sakar Application Boundaries

**Status: proposal only — nothing here has been implemented, per Phase 3's own explicit scope.**

| Sakar application | Status | Keenon-equivalent scope |
|---|---|---|
| `SakarC40Agent` (existing) | **Built, in active development** | `com.keenon.peanut.clean` (Cleaning + Super User + Debug) |
| Sakar Installation Assistant | **Proposed, not built** | `com.keenon.peanut.peanutservice` |
| Sakar Log Utility | **Proposed, not built** | `com.keenon.krlog` |
| Sakar Service (shared backend) | **Reserved, not needed yet** — no second Sakar app exists to share it with | `com.keenon.systemservice` |
| Sakar Remote Assistant | **Reserved, licensing-gated** — depends on acquiring a third-party remote-access SDK | `com.keenon.remote_control_oray` |

Full detail, including the significant open architectural question this raises (today,
`SakarC40Agent` holds `robot_installation_commissioning`, `maps_management`, and `logs_viewer` as
in-app ORPHANED features, whereas Keenon's own architecture keeps those in two entirely separate
APKs): [`SAKAR_APPLICATION_BOUNDARIES.md`](SAKAR_APPLICATION_BOUNDARIES.md) (Phase 3 deliverable,
unchanged by this consolidation) and §7 cross-reference in
[`05_KEENON_TO_SAKAR_ARCHITECTURE.md`](05_KEENON_TO_SAKAR_ARCHITECTURE.md).

---

## 8. Peanut SDK v1.3.0 / actual AAR findings

**Status: STATIC-ANALYSIS CONFIRMED (AAR decompiled with `javap`, cross-checked against vendor
docs and SampleApp source). Physical C40 tested: NO, per `COMPATIBILITY_REPORT.md`'s own explicit
statement, unchanged.**

- **Exact AAR identity:** `peanut-sdk-release.aar`, SHA-256
  `67a868f2317cb8e3cd095d771ae05d1acdf2a65b2fae68656cf0adfdbf4579cf` (identical copy in
  `peanut-sdk-v1.3.0/.../libs/` and `SakarC40Agent/sdk/libs/`).
- **Package/version:** `package="com.keenon.peanut"`, `android:versionName="1.5.0-bate1"`
  (note: vendor's own typo, "bate1" not "beta1", preserved verbatim).
- **Android SDK compatibility:** `minSdkVersion 19`, `targetSdkVersion 29`.
- **Native architectures:** `jni/armeabi`, `jni/armeabi-v7a` only — **no `arm64-v8a`**. This is a
  32-bit-ARM-only native library set.
- **No C40-specific identifier exists anywhere in the AAR** — the SDK is chassis-generic; "C40" is
  never named in the compiled bytecode.

**Capability areas (full detail in `PEANUT_SDK_C40_API_MATRIX.md`, reproduced/summarized per the
requested categories):**

| Area | Status | Key classes/notes |
|---|---|---|
| **MotorComponent** | STATIC-ANALYSIS CONFIRMED (exists); physical behavior UNKNOWN | `getStatus`, `enable(lock/unlock)`, `getHealth`, `getEncoder`, `getSpeed`, `manual`/`forward`/`backward`/`turnLeft`/`turnRight`, `getMaxSpeed`/`setMaxSpeed`, `getState` (raw SCM-IoT), `moveControl`. **No per-app authorization on `enable()`** — any app with a valid SDK license can lock/unlock the motor (flagged as a critical, unresolved finding in the SDK study). |
| **Navigation** | STATIC-ANALYSIS CONFIRMED (exists); physical behavior UNKNOWN | `NavigationComponent` (status/setTarget/pause/resume/stop/reset/speed/profile config) and a second, higher-level `PeanutNavigation` builder API. |
| **Map APIs** | STATIC-ANALYSIS CONFIRMED (exists, file-transfer level only) | `MapComponent` (upload/download/getMapInfo, byte-array transfer) and `MapManager` (USB import/export to ROS). **Does not expose live SLAM control or data** — see §10/§11 and Phase 4. |
| **Battery** | STATIC-ANALYSIS CONFIRMED (exists) | `BatteryComponent.getStatus/autoCharge/manualCharge/stopCharge/getChargeMatches`. |
| **Charging** | STATIC-ANALYSIS CONFIRMED (exists) | `PeanutCharger` builder (`performAction`, `ChargerInfo`). |
| **Sensors** | STATIC-ANALYSIS CONFIRMED (existence only, largely not deep-decompiled) | `com.keenon.sdk.sensor.*` packages: motor, battery, stm32, lidar, rfid, ota, door, light, plasma, uvlamp, gravity, vision, calibration, map, headmotor, jacking, emotion, callbell. |
| **Diagnostics** | STATIC-ANALYSIS CONFIRMED (exists) | `DeviceComponent` (`getAutoCheckStatus`, `getCheckRobotStatus`, `getRobotDevicesTreeStatus`, `checkRobot`), `ApiConstants.RobotCheck` named self-tests (`GAZER_CHECK`, `LIDAR_CHECK_RANGING`, `DEPTH_CHECK`, etc.). |
| **Telemetry** | STATIC-ANALYSIS CONFIRMED (exists) | `PeanutRuntime.getRuntimeInfo()` → `RuntimeInfo` (workMode, syncStatus, power, totalOdo, emergencyEnable/Open, motorStatus, robotArmInfo, robotStm32Info, robotIp, robotProperties, destList); generic pub/sub with 65 confirmed topic names. |
| **Communication** | STATIC-ANALYSIS CONFIRMED, local-only | `PeanutConstants`: `LOCAL_LINK_PROXY=127.0.0.1`, `REMOTE_LINK_PROXY=192.168.64.20`, `LOCAL_ETHER_IP=192.168.64.10`, ports CoAP `5683`, HTTP `34569`, WS `12386`, TFTP `9527`. `PushManager.getUrl()` connects only to a developer-configured local IP, never a hardcoded host. |
| **Cleaning capability** | **NOT FOUND** | No cleaning-hardware control API (brush/pump/fan/water/workstation) exists anywhere in the licensed AAR — confirmed absent, not merely undocumented. This is why `SakarC40Agent` simulates cleaning execution rather than pretending to control hardware. |
| **Workstation capability** | **NOT FOUND** | Same conclusion as Cleaning — no auto water-refill/drainage/docking-base API exists in the SDK. |
| **SLAM capability** | **UNAVAILABLE** (present only as file-transfer + one localization trigger) | `MapComponent`/`MapManager` (file-level only) and `PeanutRuntime.getInstance().location()` (documented as a SLAM power-on localization trigger). No start/stop mapping, no occupancy-grid stream, no LiDAR/pose stream. See §10/§11. |
| **Cloud/network findings** | STATIC-ANALYSIS CONFIRMED | Every hardcoded IP in the AAR is private (`127.0.0.1`/`192.168.64.20`/`192.168.64.10`); no cloud hostname, no public IP, no hardcoded HTTPS URL found anywhere in 1830 scanned classes (AAR + 3 bundled libraries). **Requires further scrutiny, not ruled out:** the OTA/firmware-update package's actual data source (local file vs. remote server) was not traced to completion. |

---

## 9. SDK vs. Internal vs. Cloud Capability Matrix

| Capability | Public Peanut SDK | Internal (Keenon-app-only) | Cloud |
|---|---|---|---|
| Motor status/read | Yes | — | — |
| Motor lock/unlock, manual drive | Yes (no per-app auth) | — | — |
| Navigation (go-to-point, pause/resume/stop) | Yes | — | — |
| Battery/charging | Yes | — | — |
| Map file transfer (upload/download/USB import-export) | Yes | — | — |
| SLAM start/stop, live occupancy grid, LiDAR, live pose during mapping | **No** | Yes (rosbridge to separate SLAM computer, §4) | — |
| `Mapping.db` (map/pose/elevator/gate/label/init_pose/dynamic_map_info) | **No** | Yes (Robot Installation Assistant's own Room DB) | — |
| MQTT/TTS/Voice | **No** | Yes (`com.keenon.systemservice`, exported+unauthenticated AIDL) | Partial — TTS uses Microsoft Cognitive Services + iFlytek cloud speech APIs |
| Elevator vendor IoT integration | **No** | — | Yes (`HttpElevatorApi` in `peanut-clean`, `/api/elevator/v2/...`, `/api/hotel/...`) |
| Cleaning-app cloud sync (map backup, marketing material) | **No** | — | Yes (`PeanutApi`, `/api/cleanapp/iot/...`, region-specific `console.peanut.keenonrobot.com`-style hosts) |
| Remote desktop/support | **No** | — | Yes (licensed Oray/Sunlogin backend, third-party, not Keenon's own) |
| Device-wide log capture | **No** | Yes (`com.keenon.krlog`, standalone) | — |

---

## 10. Mapping Reverse Engineering

**Status: STATIC-ANALYSIS CONFIRMED (Phase 4), not physically re-verified (Phase 5 found no
reachable robot).** Full detail: [`KEENON_MAPPING_DEEP_REVERSE_ENGINEERING.md`](KEENON_MAPPING_DEEP_REVERSE_ENGINEERING.md).

**Confirmed chain:**
```
Installation Assistant (com.keenon.peanut.peanutservice)
  → SimpleChromeActivity (Chromium WebView shell, launcher)
  → local HTTP :8080 (AndService, AndServer library)
  → Vue3 SPA (assets/ros/dist/, single effective route "/install")
  → WebSocket :8888 (WebServerSocket)
  → WebServerManager (dispatches by presence of an "id" field)
  → RosSocketClientManager (relays "id"-bearing frames)
  → rosbridge protocol
  → expected SLAM computer (192.168.64.20:9090 / :9091, STATIC-ANALYSIS CONFIRMED address only)
  → ROS/SLAM (algorithm itself: UNKNOWN, not present in any decompiled APK)
```

Frames *without* an `id` and with a `requestType` are routed instead to `WebSocketHelp`, which
implements the JS-facing `android.*` bridge (`postDBOperation`, `getRobotLockState`, etc.) — **not**
a Java `addJavascriptInterface` bridge (confirmed absent across all 5 decompiled APKs).

**`Mapping.db`** (Room/SQLite, on-device at `/sdcard/peanutservice/DBfile/Mapping.db`), 7 tables,
all confirmed by reading the Room entity classes and generated DAO SQL:

| Table | Key fields |
|---|---|
| `map` | `_id, name, type, floor, value, map_md5, floorInfo, buildingInfo` |
| `pose` | `_id, target_id, name, type, floor, phone, map_md5, elevator_id, position_x/y/z, orientation_x/y/z/w, floorInfo, buildingInfo, macAddress, disableOrientation, phoneStr` |
| `init_pose` | `_id, init_id, base_map, init_method, angleRange, confidence, penetrate, position_x/y/z, orientation_x/y/z/w` |
| `label` | `_id, label_id, base_map, floor (float), position_x/y/z, orientation_x/y/z/w` |
| `elevator` | `_id, elevator_id, robot_id, available_floor, transit_floor, opendoor_time` |
| `gate` | `_id, id, floor, mac_address` (BLE/Wi-Fi MAC-based beacon detection) |
| `dynamic_map_info` | `_id, floor, resolution, width, height, position_x/y/z, orientation_x/y/z/w` (ROS `OccupancyGrid`/`MapMetaData`-shaped) |

All 7 tables are written through a single generic dispatch path:
`postDBOperation` (WebSocket) → `WebSocketHelp` → `InformationSupport` → `DataBaseManager.OperationDB()`
→ `FactoryDao.createDao(tableName)` → per-table `*DaoHelp` (`insert/update/delete/select/selectAll/deleteAll`)
→ Room DAO → SQLite.

**WebSocket protocol:** home-grown JSON RPC (`{action, actionType, requestType, data}` for
bridge/DB calls; standard rosbridge `op`/`topic`/`service`/`id` JSON for ROS traffic, distinguished
purely by presence of an `id` field).

**Local mapping architecture:** entirely local — no cloud call is part of the map/pose/elevator/
gate/label write path. Live map rendering uses a custom WebGL (THREE.js) renderer for the
occupancy grid plus zrender (2D canvas) for vector overlays (virtual walls, gates, elevators,
VSLAM points). ARCore libraries are bundled for camera-based Visual SLAM (VSLAM) pose assistance.

---

## 11. Mapping Control APIs

| Operation | Classification |
|---|---|
| Map file upload/download (`MapComponent`), USB import/export (`MapManager`) | **Public Peanut SDK** |
| Basic localization trigger (`PeanutRuntime.getInstance().location()`) | **Public Peanut SDK** |
| SLAM start/stop (`ROSNAME.BUILDMAPTYPE` rosbridge service, `BuildMapLocalState` native event) | **Internal (undocumented rosbridge protocol)** |
| Live occupancy-grid/LiDAR/pose streaming | **Internal (rosbridge topics, e.g. `/scan_base_map`)** |
| `Mapping.db` CRUD (map/pose/elevator/gate/label/init_pose/dynamic_map_info) | **Internal (WebSocket `postDBOperation`)** |
| Rich manual/assisted relocalization | **Internal (`ROSNAME.CONFIGINITPOSE`)** |
| Exact `map.value` payload encoding (PNG? ROS `.pgm`/`.yaml`? custom binary-as-string?) | **Unknown interface** — not decoded in Phase 4 |
| Exact rosbridge topic backing `getRobotLocalPosition`/`getRobotPosition` in the JS | **Unknown interface** — not isolated in Phase 4 |

---

## 12. Live Map / Pose / LiDAR / ROS

**No topic/service/action below is claimed to exist on the physical robot. Everything in this
table is `STATIC-ANALYSIS CONFIRMED` (found as a literal string/constant in decompiled source) or
explicitly `NOT VERIFIED` (named in the task's own checklist, not found anywhere in decompiled
evidence, and requiring physical testing either way).**

| Capability | Evidence | Status | Source | Next verification |
|---|---|---|---|---|
| `/map` | Not found as a literal in decompiled JS/Java | NOT VERIFIED | — | Physical robot ROS introspection |
| `/map_metadata` | Not found as a literal | NOT VERIFIED | — | Physical robot |
| `/scan` | Not found as a literal (a *different*, Keenon-custom topic `/scan_base_map` was found instead) | NOT VERIFIED (standard name); STATIC-ANALYSIS CONFIRMED (custom name) | `RosSocketClientManager.topic_91` | Physical robot — determine if both exist, or only the custom one |
| `/tf` | Not found as a literal | NOT VERIFIED | — | Physical robot |
| `/tf_static` | Not found (a related but distinct topic `/tf2_web_republishe` was found) | NOT VERIFIED (standard name); STATIC-ANALYSIS CONFIRMED (related custom name) | `RosSocketClientManager.RECEIVE_TOPIC_91` | Physical robot |
| `/odom` | Not found as a literal | NOT VERIFIED | — | Physical robot |
| `/amcl_pose` | Not found as a literal; `init_pose` table fields (`confidence`, `penetrate`) suggest AMCL-style localization is plausible | NOT VERIFIED (INFERRED plausibility only) | `database/tables/InitPose.java` | Physical robot |
| `/initialpose` | Not found as a literal; `ROSNAME.CONFIGINITPOSE` → `/database/config_init_pose` (a different, custom name) found instead | NOT VERIFIED (standard name); STATIC-ANALYSIS CONFIRMED (custom name) | `index-32d09e94.js` `ROSNAME` table | Physical robot |
| `/cmd_vel` | Not found as a literal | NOT VERIFIED | — | Physical robot |
| SLAM-specific topics/services found | `/switch_dest_floor_map`, `/republish_tfs` (services, port 9091); `/scan_base_map`, `/motor_lock` (topics, port 9091); `/tf2_web_republishe` (received topic); `ROSNAME.BUILDMAPTYPE`, `ROSNAME.ELEVATORCONFIG`, `ROSNAME.VIRTUALWALLAUTOSAVE`, `ROSNAME.CONFIGINITPOSE` (services) | STATIC-ANALYSIS CONFIRMED | `RosSocketClientManager.java`, `index-32d09e94.js` | Physical robot — confirm these are live and unchanged in the current firmware version |
| Actions (ROS `actionlib`-style) | None found | NOT FOUND | Phase 4 full trace | Physical robot |
| Update frequency (any topic) | Not measured | UNKNOWN | — | Physical robot, live capture |
| Message types | `keenon_database_msgs/initPoseConfig` confirmed for `CONFIGINITPOSE`; others not confirmed | STATIC-ANALYSIS CONFIRMED (one), UNKNOWN (rest) | `index-32d09e94.js` | Physical robot |

---

## 13. Offline Mapping

**STATIC APK FINDING (Phase 4, not physically re-verified):**
- The entire `Mapping.db` read/write path is local SQLite + local WebSocket IPC — no cloud call
  observed in this path.
- The rosbridge control/data plane targets a fixed private LAN address
  (`192.168.64.20`) — no internet route involved in the protocol itself.
- `AndService`'s local HTTP server binds without restricting to loopback (`inetAddress(null)`) —
  reachable over LAN, not requiring internet.
- Optional cloud sync exists and is architecturally separate: region-specific console URLs
  (`console.peanut.keenonrobot.com`-style) used only for map-file backup via `axios.put`.

**PHYSICAL ROBOT VERIFIED:** **none of the above.** No live observation of the robot mapping with
or without internet connectivity has been made. This entire section remains a static-code
conclusion until a physical robot test is performed.

---

## 14. Cleaning System

- **Public SDK cleaning limitations:** `NOT FOUND` — no cleaning-hardware control API
  (brush/pump/fan/water/workstation) exists anywhere in the licensed `peanut-sdk-release.aar`
  (confirmed absent by full class enumeration, not merely undocumented).
- **Internal `CleanComponent` findings:** the licensed AAR does not contain a `CleanComponent`
  class at all — this was established in earlier project work (pre-dating the 5 audit phases in
  this consolidation) and is treated as an already-established fact here, unchanged.
- **Cleaning modes:** UI-only — `manual_cleaning_mode_selector` (Sweep & Mop / Water Suction /
  Sweep & Vacuum / Sweep & Push / Sweep) is local Compose state with **no backing hardware API**
  (`Capability.LOCAL`/`UNAVAILABLE`).
- **Schedule architecture:** LOCAL — Room-backed (`ScheduleRepository`/`RoomScheduleRepository`),
  no robot hardware involved.
- **Cloud APIs (Keenon's own, not Sakar's):** `PeanutApi` (`/api/cleanapp/iot/...`) in
  `peanut-clean` — confirmed to exist in Keenon's own app, **not used by `SakarC40Agent`**, which
  has no cloud cleaning-sync feature.
- **What Sakar currently implements:** `StartCleaningScreen` (SIMULATED session, time-based
  progress), `ScheduleListScreen`/`ScheduleEditScreen` (LOCAL, Room-backed).
- **What is simulated:** the entire cleaning execution lifecycle (start/pause/resume/stop/progress/
  cycles) — explicitly disclosed via `Capability.SIMULATED`, never presented as real hardware
  control.
- **What is unavailable:** any actual brush/pump/fan/water/workstation control — `NOT FOUND` in the
  SDK, so `Capability.UNAVAILABLE` throughout the UI.

---

## 15. Robot Debugging

- **Actual SDK-backed features:** Emergency Stop, IMU, Battery reads (`Capability.REAL`) — real
  Peanut SDK calls (`RuntimeInfo.isEmergencyEnable/isEmergencyOpen`, sensor reads).
- **Unavailable `CleanComponent` functions:** every brush/pump/fan/water/workstation row in the
  Debug dashboard is `Capability.UNAVAILABLE` — `CleanComponent` does not exist in the licensed SDK
  (confirmed absent, see §14).
- **Hardware verification status:** `NOT VERIFIED` — no physical C40/CleanBot5000Plus has been
  connected to any development environment this project has been built in
  (`COMPATIBILITY_REPORT.md`'s own opening statement, unchanged and still true as of this
  consolidation).

---

## 16. Manual Drive

- **Actual `MotorComponent` APIs (STATIC-ANALYSIS CONFIRMED to exist):** `getStatus`, `enable`
  (lock/unlock), `getHealth`, `getEncoder`, `getSpeed`, `manual`/`forward`/`backward`/`turnLeft`/
  `turnRight`, `getMaxSpeed`/`setMaxSpeed`, `getState` (raw SCM-IoT), `moveControl`.
- **Sakar integration:** `RobotMotionController` interface with two implementations —
  `RealMotorController` (wraps `C40RobotController`'s gated motor calls) and
  `SimulatedMotorController` (deterministic virtual robot state), selected once at
  `DefaultAppContainer` construction via `ManualDriveBackendMode` (default `SIMULATED`).
- **Simulation architecture:** `SimulatedMotorController` advances a virtual x/y/heading/encoder
  state on a 100ms tick while a direction is held, with zero real `MotorComponent` calls — verified
  via logcat in earlier project work (pre-dating this consolidation).
- **Safety gating:** `OperatingMode` (`DIAGNOSTIC_ONLY` default / `HARDWARE_TEST`) — real motor
  actuation is blocked (`ERROR_BLOCKED_BY_OPERATING_MODE`) unless `HARDWARE_TEST` is set, and
  **nothing in the codebase ever sets `HARDWARE_TEST`** (confirmed by the README's own explicit
  statement, unchanged).
- **What has and has not been physically verified:** **nothing** about real motor behavior has
  been physically verified — no physical robot connection has ever been established for this
  project (`COMPATIBILITY_REPORT.md` row "Motor enable/disable": `UNKNOWN`).

---

## 17. Charging / Battery

- `BatteryComponent` (`getStatus`, `autoCharge`, `manualCharge`, `stopCharge`, `getChargeMatches`)
  and the higher-level `PeanutCharger` builder are `STATIC-ANALYSIS CONFIRMED` to exist in the SDK.
- Sakar's `ChargingBridge.startCharging()` deliberately uses `manualCharge()` rather than
  `autoCharge(pile)` specifically to avoid guessing an unconfirmed pile-number parameter
  (`COMPATIBILITY_REPORT.md`).
- Physical charging behavior: `NOT VERIFIED`.

---

## 18. Sensors

`com.keenon.sdk.sensor.*` packages (motor, battery, stm32, lidar, rfid, ota, door, light, plasma,
uvlamp, gravity, vision, calibration, map, headmotor, jacking, emotion, callbell) are
`STATIC-ANALYSIS CONFIRMED` to exist as classes; most were **not deep-decompiled** (the SDK study
scoped its full-bytecode-depth analysis to motor/config/network/topic-name classes only — roughly
1350 of 1399 SDK classes were catalogued by signature only). Physical sensor behavior:
`NOT VERIFIED`.

---

## 19. Diagnostics

`DeviceComponent` (`getAutoCheckStatus`, `getCheckRobotStatus`, `getRobotDevicesTreeStatus`,
`checkRobot`) and `ApiConstants.RobotCheck` named self-tests (`ONLINE_CHECK`, `GAZER_CHECK`,
`LIDAR_CHECK_RANGING`, `LIDAR_CHECK_MATCHING`, `DEPTH_CHECK`, `GAZER_VERIFY`, `LIDAR_VERIFY`,
`DEPTH_VERIFY`, `UNION_CALIBRATION_VERIFY`) are `STATIC-ANALYSIS CONFIRMED` to exist. Sakar's own
`DeviceEnvironmentInspector` (Android/build info, network interfaces) and `SerialPortInspector`
(read-only `/dev/ttyS*` existence checks, never opens a port) are implemented and unrelated to the
SDK's own diagnostics surface.

---

## 20. Telemetry

`PeanutRuntime.getRuntimeInfo()` → `RuntimeInfo` (workMode, syncStatus, power, totalOdo,
emergencyEnable/Open, motorStatus, robotArmInfo, robotStm32Info, robotIp, robotProperties,
destList) is `STATIC-ANALYSIS CONFIRMED`. Sakar's `telemetry` module provides plain data classes
(`RuntimeSnapshot`, `HealthEvent`, `ConnectionStatus`, `RawSnapshot`) with no SDK/Android
dependency, consumed by `C40TelemetrySnapshotProvider` and the `:api` module's MQTT telemetry
publisher.

---

## 21. Network Architecture

- **SDK-confirmed local endpoints** (§8): `127.0.0.1`, `192.168.64.20`, `192.168.64.10`; ports
  `5683` (CoAP), `34569` (HTTP), `12386` (WebSocket), `9527` (TFTP).
- **Robot Installation Assistant-confirmed local endpoints** (§10): local HTTP `8080`, local
  WebSocket `8888` (bridge/relay) and `8899` (logging), outbound rosbridge to `192.168.64.20:9090`
  and `:9091`.
- **This development machine's own network** (Phase 5, live-tested): `192.168.1.0/24` (Wi-Fi),
  gateway `192.168.1.1` — **no route to `192.168.64.0/24` exists from here.** All 4 named ports on
  `192.168.64.20` returned `UNREACHABLE` (no response of any kind, not even connection-refused).
- **Conclusion:** `192.168.64.20` is real *inside Keenon's own architecture* (cross-confirmed by
  two independent decompiled artifacts, §4/§8) but has never been reached from any development
  environment this project has used.

---

## 22. Security Findings

**Findings are reverse-engineering observations, presented as documentation of what exists — not
operational attack instructions, and not a claim that any of this has been exploited.**

| Finding | Status | Source |
|---|---|---|
| `MotorComponent.enable()` (lock/unlock) has no per-app authorization — any app with a valid SDK license can call it | STATIC-ANALYSIS CONFIRMED | `PEANUT_SDK_C40_TECHNICAL_STUDY.md` §8/§12, `PEANUT_SDK_C40_API_MATRIX.md` row 60 |
| A local or remote (e.g. ADB) user who can launch any licensed-SDK app on the robot's Android device can call motor unlock regardless of `SakarC40Agent`'s own gating | STATIC-ANALYSIS CONFIRMED (reasoning from the above); real ADB exploitation **NOT VERIFIED/NOT ATTEMPTED** | `PEANUT_SDK_C40_TECHNICAL_STUDY.md` §12 |
| Bundled OkHttp client (`OkHttpFactory`) contains `TrustAllManager`/`TrustAllHostnameVerifier` — TLS certificate/hostname validation is disabled when this HTTP client is used | STATIC-ANALYSIS CONFIRMED | `PEANUT_SDK_C40_TECHNICAL_STUDY.md` §7 |
| `com.keenon.systemservice`'s `MqttApiService`/`TtsApiService`/`VoiceApiService` are `android:exported="true"` with no permission requirement — any app on the device can bind | STATIC-ANALYSIS CONFIRMED | Phase 3, `KEENON_APPLICATION_SEPARATION_MAP.md` §3 |
| OTA/firmware-update package's actual data source (local vs. remote) not fully traced — the single most likely unruled-out place a real external call could exist | STATIC-ANALYSIS CONFIRMED (as an open question, not a resolved finding) | `PEANUT_SDK_C40_TECHNICAL_STUDY.md` §7 |
| CORS behavior of any Keenon local HTTP server (e.g. `AndService` on port 8080) | **NOT FOUND** — no evidence located in any existing document; not checked in this consolidation pass | — |
| Wi-Fi credential exposure/storage | **NOT FOUND** — no evidence located in any existing document beyond the SDK's `RuntimeComponent.getRobotWifi()` API existing (reads the robot's own reported network identity; not a credential-disclosure finding) | `PEANUT_SDK_C40_TECHNICAL_STUDY.md` line 186 |
| SSH credential derivation | **NOT FOUND** — no evidence located in any existing document; `RemoteControl`'s `SshInfoActivity` (Phase 3) is known to exist but its credential mechanism was not traced | Phase 3 |
| ADB behavior beyond the motor-unlock implication above | **NOT FOUND** — no dedicated ADB security study exists in this repository | — |

These `NOT FOUND` rows are recorded explicitly rather than silently omitted, per this document
set's own rule that a search performed with no result is itself a finding.

---

## 23. Existing Sakar Architecture

```
SakarOperatorActivity (operator-ui)
    ↓
domain (use cases + repository interfaces + Capability-tagged models)
    ↓
data (real C40RobotController bridges, Room, DataStore) — DefaultAppContainer wires everything
    ↓
robot (C40RobotController — enforces OperatingMode) / sdk (only module allowed to import com.keenon.*)
    ↓
peanut-sdk-release.aar
    ↓
C40 robot firmware (not physically connected to any dev environment used so far)
```

Module table (`app`, `sdk`, `robot`, `navigation`, `charging`, `telemetry`, `diagnostics`,
`logging`, `ui`, `api`, `domain`, `data`, `operator-ui`, `virtual-agent`) — full descriptions in
`README.md` §"Module layout", unchanged and authoritative, not reproduced in full here to avoid
duplicating a document that already exists and is current.

**Every screen honestly discloses its `Capability`** (`REAL`/`GATED`/`SIMULATED`/`LOCAL`/
`UNAVAILABLE`) — this is the project's own load-bearing design principle, confirmed still in force
by reading `README.md` directly during this consolidation pass.

---

## 24. Current Implementation Status

- **Built and working (per `README.md`, `git`/module structure, not re-verified live in this
  consolidation pass):** Home, Start Cleaning (simulated), Scheduled Cleaning (local), Teaching
  Mode (local metadata), Manual Drive (simulated backend by default, real backend behind
  `OperatingMode` gate), Settings (mixed real/local/unavailable), Super User + Robot Debugging
  (real reads + unavailable actuator tests), Logs (real `SdkCallLogger`), Robot Installation/Maps
  screens (mostly unavailable, present but currently ORPHANED from navigation per the UI
  governance registries in `docs/ui/`).
- **`:api` module:** MQTT client (`AgentMqttClient`, Eclipse Paho) with presence/heartbeat/
  telemetry/event/error publishing and inbound `CommandDispatcher` — exists in the module
  structure and has its own test suite (`AgentMqttClientSafetyTest`,
  `PeanutSdkGoToPointExecutorTest`, `PeanutSdkReturnToDockExecutorTest`, etc.), confirming a
  real, tested Sakar-backend integration boundary already exists in code, gated the same way as
  everything else by `OperatingMode`.
- **`:virtual-agent` module:** a virtual/simulated robot command executor and engine
  (`VirtualRobotCommandExecutorTest`, `VirtualRobotEngineTest`, `VirtualRobotRegistryTest`,
  `VirtualAgentRealSdkIsolationTest`) — confirms Sakar has its own, separate simulation layer for
  testing without hardware, architecturally distinct from the operator-UI's own
  `SimulatedMotorController` (§16).
- **Not built:** Sakar Installation Assistant, Sakar Log Utility (standalone), Sakar Service, Sakar
  Remote Assistant (§7) — all proposal-only.

---

## 25. Current Documentation Inventory

Full index with per-file purpose/status/authority: [`08_DOCUMENTATION_INDEX.md`](08_DOCUMENTATION_INDEX.md).

---

## 32. Official Keenon C40 Documentation (New Evidence Source)

**Full detail:** [`09_KEENON_OFFICIAL_C40_DOCUMENTATION_AUDIT.md`](09_KEENON_OFFICIAL_C40_DOCUMENTATION_AUDIT.md).
This section summarizes only the headline points; the full document has the complete per-document
findings, the 25-item hardware table, the mapping/SLAM/cleaning extraction tables, and every
recorded conflict/refinement against prior APK-based findings.

- Keenon's official C40 documentation portal (`doc.keenonrobot.com/dashboard/en/cleans/c40`) was
  read directly in this session — a **third evidence source**, alongside decompiled-APK static
  analysis and physical hardware observation. 10 documents exist for C40 across 8 of the 11
  requested categories (3 categories — Merchant Quick Action Guide, Cloud Platform User Manual,
  Mobile app user manual — exist as empty categories with zero published documents,
  `NOT DOCUMENTED`).
- **New `OFFICIAL DOCUMENTATION CONFIRMED` fact directly relevant to §4/§18:** the robot "is
  equipped with LIDAR for positioning and navigation" (direct quote, official safety
  instructions) — the first non-APK, non-physical confirmation of LiDAR's existence and purpose.
- **New `OFFICIAL DOCUMENTATION CONFIRMED` fact refining §10's `INFERRED` "camera/VSLAM" finding:**
  official documentation names a distinct "Binocular Stereo Vision" sensor, used for elevator/
  label recognition — a refinement, not proof that it is the same code path as the ARCore-based
  VSLAM assistance found in Phase 4.
- **Important documented mismatch:** the one document filed under Keenon's own "Hardware
  Connection Diagram" category is a mechanical parts exploded-view diagram (18 labeled assemblies:
  fan, water tanks, pumps, brush motors, hub, chassis, etc.) — **not** an electrical or network
  interface diagram. No RK3288, SLAM computer, ROS, network topology, or internal interface of any
  kind is documented anywhere in official C40 material — this is a gap in the official
  documentation, not a contradiction of the APK-based/physical findings elsewhere in this document
  set, which remain unchanged.
- New operational facts with no prior source to compare against: maximum map area ≤20,000 m²;
  detailed passability specs (braking distance, min widths, climbing angles); a documented
  two-layer map model (base/application layer); a documented elevator-mapping labeling procedure;
  a documented manual relocalization-recovery procedure ("push to landmark/charging station to
  restore positioning"); a LAN-pairing requirement between the operator's phone and the robot for
  carpet-area remote-control mapping.
- **A severe, reproducible server-side reliability problem** affects the portal's large binary
  page-scan images specifically (confirmed via three independent HTTP mechanisms) — most of the
  ~185 total page-scan images across the 10 documents were not successfully retrieved or reviewed
  in this pass. Text/JSON content came back reliably every time. See `09` §0/§9 for the full,
  honest accounting of what was and wasn't reviewed.

---

## 33. Physical Hardware Photo Evidence — 2026-09-26

**Full detail:** [`10_PHYSICAL_HARDWARE_PHOTO_EVIDENCE.md`](10_PHYSICAL_HARDWARE_PHOTO_EVIDENCE.md)
(per-photo record) and [`03_HARDWARE_ARCHITECTURE.md`](03_HARDWARE_ARCHITECTURE.md) §6 (structured
inventory table + topology diagram). Nine photographs of the physical robot's internals were
reviewed directly this session. All of the following is `PHYSICAL-HARDWARE CONFIRMED` for the
component's existence and any directly-legible label; nothing beyond that is asserted.

- **RK3288 UIB:** unaffected by this photo set — no photo in this batch shows the RK3288 board
  itself; §3's existing content is unchanged.
- **Separate ROS/Robot computer:** a candidate physical match was found — an enclosure labelled
  **"ARM IPC"**, with its own carrier PCB, physically distinct from the RK3288. This is the
  strongest physical lead yet for the entity already documented (§4) as existing but otherwise
  unidentified — **it is not confirmed to be that entity**, only a strong candidate ("IPC"
  commonly = "Industrial PC," consistent with but not proof of a ROS-class computer).
- **Motor controller evidence:** a multi-section custom control PCB was found carrying labels
  "STEP MOTOR," "AIR PR GAUGE 1," and unexplained "102" markings — a plausible but unconfirmed
  candidate location for the SDK's already-documented SCM-IoT-addressed motor controller (§4/§5,
  E-016).
- **CAN evidence:** a wire bundle is explicitly labelled **"CAN."** This does not by itself
  resolve E-052 (whether the ROS/Robot Computer and the SCM-IoT motor controller are the same
  board, different boards, or connected via this bus) — recorded as open, not decided.
- **USB hub evidence:** a board labelled **"USB HUB V1.1"** has two labelled ports, "USB1" and
  "USB2" — a strong candidate for the already-reported "some cameras are USB-connected" finding
  (§4/§29 item 9), but which specific peripherals use which port is not traced.
- **Motor evidence:** a hub/gear motor is labelled **"YONGJIE MOTOR / BLFE15-55682 / 24V 350W"** —
  the first labelled confirmation of a hub motor's exact make/model/rating. A second connector,
  labelled consistent with **"BRUSH MOTOR HALL,"** confirms Hall-sensor feedback specifically on a
  brush motor.
- **Communication module evidence:** a board labelled **"USB LoRa"** carries a module marked
  **"E22-900T22S 1B"** (a commercially-cataloged LoRa RF module family) with an external antenna —
  a genuinely new finding with no prior mention anywhere in this project. Its actual use, if any,
  in this robot's system is `UNKNOWN`.
- **Charging-contact evidence:** two metal charging-contact plates and a separate small sensor PCB
  were photographed in a rear/underside bracket — the first direct photographic evidence of the
  charging interface's physical hardware (electrical spec still `NOT DOCUMENTED`/`UNKNOWN`).

**Nothing above resolves E-052, and nothing above should be read as doing so.** The topology
diagram in `03_HARDWARE_ARCHITECTURE.md` §6.1 records the open question explicitly, with every
link marked `UNKNOWN` except the two endpoints' own confirmed physical existence.

---

## 34. Software Architecture Preparation — 2026-09-26

**Full detail:** `docs/architecture/SAKAR_ROBOT_SOFTWARE_ARCHITECTURE.md` and
`docs/architecture/SAKAR_CAPABILITY_REGISTRY.md`.

`SakarC40Agent`'s codebase gained a `SakarRobotGateway` seam (`:domain`/`:data`) preparing for a
future real ROS/Robot Computer integration, without implementing any of it: two new capability
interfaces (`RobotMappingRepository`, `RobotTelemetryRepository`) and a minimal `RosRobotAdapter`
(connection state/lifecycle/capability-discovery only, no topics/services/ports, no hardcoded
`192.168.64.20`/`9090`/`9091`). Every new interface's only implementation returns an explicit
`NOT_AVAILABLE`/`NOT_VERIFIED` result, enforced at the type level by a new
`RobotCapabilityResult<T>` sealed class that makes it impossible to construct a "successful" result
tagged as unavailable, or vice versa. Nothing existing (`Capability`, any screen, any ViewModel, any
use case) was changed - this is purely additive. `SakarInstallationAssistant` remains
contracts-only, per this task's own explicit instruction not to create the second APK yet.

---

## 26. Hardware Replacement Investigation

**Status: investigation item only. ROCK 4D is explicitly NOT an approved replacement architecture
— this section documents open questions, not a decision.**

- **RK3288 UIB** (current, confirmed Android/application computer, §3): board markings
  `CBA_RK3288_UIB_SUPER_V2.2`, `RK3288_UIB_DUAL_LVDS_V1.0_20230803` (user-reported physical
  observation). 32-bit ARM (Cortex-A17); the licensed Peanut SDK's native libraries are
  32-bit-only (`armeabi`/`armeabi-v7a`, no `arm64-v8a`) — **this is a directly relevant compatibility
  fact for any replacement board**: if a candidate replacement (e.g. ROCK 4D, if it is an ARM64-only
  platform) cannot run 32-bit native libraries, the licensed Peanut SDK would not load at all,
  per `DeviceEnvironmentInspector.isSupportedByNativeSdkLibs()`'s own existing runtime check. This
  is `STATIC-ANALYSIS CONFIRMED` for the SDK's own native-library architecture; it is
  **`UNKNOWN`** whether ROCK 4D (or any specific candidate board) supports 32-bit ARM compatibility
  mode, because no ROCK 4D hardware has been investigated in this project.
- **Why ROCK 4D is NOT currently approved as a replacement:** no interface investigation has been
  performed at all. This is a name only, with zero hardware documentation in this repository.
- **Required interface investigation (none of the following exist in this repository yet — all
  `UNKNOWN — REQUIRES PINOUT / SCHEMATIC / CONTINUITY TEST` per this audit's own rule against
  guessing):**
  - Display/LVDS (the RK3288 board's own "DUAL_LVDS" naming implies dual-LVDS display output;
    whether a candidate replacement offers equivalent LVDS output, and at what timing/resolution,
    is unknown)
  - USB (port count, type, and role — host vs. device — on both the current board and any
    candidate, unknown)
  - Ethernet (presence/count/speed on both, unknown)
  - Touch (controller/protocol for the touchscreen, unknown)
  - Audio (codec/interface, unknown)
  - Power (input voltage/connector/regulation requirements, unknown)
  - Robot-specific connectors (any connector on the RK3288 board whose function is not
    independently confirmed — see `03_HARDWARE_ARCHITECTURE.md` for the explicit
    connector-by-connector treatment)
  - SLAM communication (the physical medium connecting the RK3288 to the separate SLAM computer,
    §4 — Phase 4 confirms the *software* protocol (WebSocket/rosbridge over IP) but not the
    *physical* link; a replacement board would need to replicate whatever that physical link
    actually is, which is unknown)
  - **The ROS/Robot Computer's own, much larger connection surface** (§4, §29 item 9): LiDAR,
    stereo vision, ultrasonic sensors, depth camera, multiple cameras (USB + internal wired), hub
    motors, actuators, water pumps, water-level sensors, Hall sensors, side-brush motors,
    mopping-brush motors, scrubber roller motor, vacuum motors. This is now `PHYSICAL-HARDWARE
    CONFIRMED` to exist, but every bus/protocol/connector for every item on this list is
    `UNKNOWN`. **Any replacement scope that only addresses the RK3288 (display/network/SDK
    compatibility) does not address this larger surface at all** — replacing the ROS/Robot
    Computer itself, as opposed to just the RK3288, is a substantially bigger undertaking than
    this section previously described.

---

## 27. Physical Robot Investigation Plan

Full checklist: [`07_NEXT_PHYSICAL_ROBOT_AUDIT.md`](07_NEXT_PHYSICAL_ROBOT_AUDIT.md).

---

## 28. Known Unknowns

Full list: [`06_UNVERIFIED_ITEMS.md`](06_UNVERIFIED_ITEMS.md).

---

## 29. Confirmed Facts

(Repeating, for a single-glance summary, only what is `CONFIRMED`/`STATIC-ANALYSIS CONFIRMED`/
`PHYSICAL-HARDWARE CONFIRMED` elsewhere in this document — see the relevant section for full
evidence, not re-cited here to avoid duplication drift.)

1. RK3288 UIB is the Android/application computer (§3) — PHYSICAL-HARDWARE CONFIRMED (board
   markings) + STATIC-ANALYSIS CONFIRMED (role).
2. A separate computer performs SLAM (§4) — PHYSICAL-HARDWARE CONFIRMED (existence) +
   STATIC-ANALYSIS CONFIRMED (expected role/protocol).
3. Keenon's 5 applications are genuinely separate APKs (§6) — STATIC-ANALYSIS CONFIRMED.
4. The public Peanut SDK does not expose live SLAM control/data (§8/§11) — STATIC-ANALYSIS
   CONFIRMED.
5. `Mapping.db` and its 7 tables exist inside the Robot Installation Assistant APK (§10) —
   STATIC-ANALYSIS CONFIRMED.
6. `CleanComponent`/cleaning-hardware control is absent from the licensed AAR (§14) — STATIC-
   ANALYSIS CONFIRMED (NOT FOUND after full search).
7. No physical robot has been reached from any development environment used by this project
   (§21, Phase 5) — RUNTIME VERIFIED (the negative network test itself was actually run).
8. `192.168.64.20` is not the current physical robot IP as verified by this project — it is a
   value found in two independently decompiled static artifacts, unconfirmed live.
9. The separate ROS/Robot Computer (§2/§4, same entity as "the SLAM computer") is physically
   connected to / responsible for LiDAR, stereo vision, ultrasonic sensors, hub motors, actuators,
   water pumps, water-level sensors, Hall sensors, depth camera, two side-brush motors,
   mopping-brush motors, scrubber roller motor, vacuum motors, vSLAM, and multiple cameras (some
   USB, some internal-wired) — PHYSICAL-HARDWARE CONFIRMED (user-reported, this session). Its
   board model, CPU, OS, ROS version, buses, protocols, and pinouts are NOT part of this
   confirmation and remain UNKNOWN/NOT VERIFIED.

---

## 30. Do Not Assume List

1. Do not assume `192.168.64.20` is the current physical robot's SLAM computer address — it is a
   static-analysis finding only.
2. Do not assume any ROS topic/service name from Phase 4 exists unchanged on a live robot.
3. Do not assume the RK3288 board is the SLAM computer — it is confirmed to be the Android/
   application computer; the SLAM computer is a separate, physically distinct entity.
4. Do not assume ROCK 4D is pin-compatible, protocol-compatible, or even ARM32-compatible with the
   current board until an actual interface investigation is performed.
5. Do not assume the public Peanut SDK can be used to build live mapping — it cannot, per §8/§11.
6. Do not assume mapping is cloud-dependent, or that it is definitely cloud-independent, on a real
   robot — only the static-code path has been examined (§13).
7. Do not assume any connector on the RK3288 board does what its position/silkscreen suggests
   without a pinout, schematic, or continuity test (`03_HARDWARE_ARCHITECTURE.md`).
8. Do not convert any `INFERRED` or `UNKNOWN` item in this document set into `CONFIRMED` without
   new, explicitly cited evidence.
9. Do not assume the ROS/Robot Computer's newly-confirmed connection to hub motors/actuators means
   the separately-documented SCM-IoT motor-controller board (§3 of `03_HARDWARE_ARCHITECTURE.md`)
   is the same board, a different board, or does not exist — this relationship is explicitly
   unresolved (§4/§5 above, `03_HARDWARE_ARCHITECTURE.md` §2.1/§3).
10. Do not assume any board model, CPU, OS, ROS version, CAN ID, UART port, GPIO, USB device ID,
    baud rate, ROS topic/service name, network IP, protocol, or wiring pinout for the ROS/Robot
    Computer's newly-confirmed connections (§4/§29 item 9) — none of these were reported and none
    are guessed anywhere in this document set.

---

## 31. Next Engineering Steps

1. Obtain physical or remote network access to an actual Sakar CleanBot5000Plus unit — the single
   blocking prerequisite for nearly every open item in this document set (see
   `07_NEXT_PHYSICAL_ROBOT_AUDIT.md` for the exact, read-only checklist to run once access exists).
2. Photograph, and if possible obtain schematics for, the RK3288 UIB board and the separate SLAM
   computer, to replace `PHYSICAL-HARDWARE CONFIRMED (user-reported)` with in-repository, citable
   evidence (`03_HARDWARE_ARCHITECTURE.md`).
3. Do not begin any Installation Assistant, mapping, SLAM, or hardware-replacement implementation
   work until (1) and (2) above have produced physically-verified evidence — this consolidation
   task's own explicit scope stops at documentation.
