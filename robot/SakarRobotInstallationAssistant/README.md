# Sakar Robotics Robot Installation Assistant

A tablet-first Android app that guides a technician through a robot installation workflow.
**Release 0.2.0-sim runs entirely on a simulated backend.** It never talks to a robot, a ROS computer
or any network, and every screen shows a SIMULATED BACKEND banner. Reports, backups and maps it produces
are demo data, not evidence of a real installation, and no map is claimed to be compatible with any
vendor (Keenon or otherwise) map format.

- Application ID: `com.sakarrobotics.installationassistant` (separate from SakarC40Agent)
- Version: `0.2.0-sim` (versionCode 2)
- Status: software-only; **physical robot integration is not enabled and not implemented.**

## What it does

| Screen | Purpose |
|---|---|
| Home | Seven tiles: New Installation, Mapping & Maps, Sensor Diagnostics, Floor Management, Installation Checklist, Installation Report, Settings & About |
| Installation Wizard | 7 steps: overview, system readiness, sensor diagnostics, mapping setup, map & floor configuration, validation, completion report. Next is gated with a visible reason. Progress is saved and resumes at the first incomplete step. |
| Mapping Workspace | Simulated grid canvas; start / pause / resume / finish / cancel (cancel and discard ask for confirmation); naming and saving maps; legend; floor selection. Leaving the screen or backgrounding the app pauses the simulation. |
| Floor & Map Management | Up to 20 floors; add / rename / delete (with confirmation); per-map preview, rename, delete. Names are trimmed, whitespace-collapsed and compared case-insensitively. |
| Sensor Diagnostics | Simulated LiDAR, depth/stereo camera, ultrasonic, IMU, localization and mapping-service rows, each tagged SIMULATED, with troubleshooting text; Healthy / Degraded demo scenarios |
| Checklist | 8 default items with explicit states; Blocked and N/A require a note |
| Installation Report | Local report with installation ID, floors, simulated mapping status, completion status, checklist results, warnings, timestamp and a SIMULATION ONLY notice. **Export as PDF and share** through the Android share sheet; copy as text; delete with confirmation. |
| Settings & About | App name, version, simulation mode and a clear "physical robot integration is not yet enabled" notice. **Backup export / restore** (versioned JSON, Storage Access Framework) and **Reset demo data** (with confirmation). |

### PDF report
`SIMULATION ONLY — NOT VALIDATED ON PHYSICAL ROBOT` is drawn in a banner at the top of every page and the full
simulation notice in the footer. The PDF is built with the platform `PdfDocument` (no third-party library, no
network), written to the app's private cache, and shared through a `FileProvider` URI, so no storage permission
is needed and only the `reports/` cache folder can ever be shared. It contains no device identifiers, accounts
or credentials. Generation and share errors are shown as messages, not crashes.

### Backup and restore
Backups are a versioned JSON file (`formatVersion` 1) chosen through the system file picker. They hold floors,
simulated-map metadata and previews, the current installation (wizard progress and checklist) and saved reports.
Restoring validates the **whole** file before any change is made and refuses malformed, truncated, oversized,
non-text, incompatible (newer-version) or inconsistent files with an explanation. A valid file can then be
**merged** (adds what is missing, matches floors by id or name, skips duplicates, never overwrites; an installation
in progress is kept) or used to **replace** all data (a second confirmation lists what will be deleted). Maps in
a backup are Sakar demo occupancy grids and are **not** compatible with any robot map format.

## Architecture

Two Gradle modules, so the logic is testable without Android:

- `:core` (pure Kotlin/JVM): models; repository interfaces (`InstallationRepository`, `MappingRepository`,
  `FloorRepository`, `DiagnosticsRepository`, `InstallationReportRepository`, `BackupRepository`) and their
  simulated/local implementations; wizard, validation and report logic; the platform-independent report
  document model; the backup format, parser and service; a persistence monitor; and the disabled
  `RobotHardwarePort` seam. Persistence is a small `KeyValueStore` (JSON via Gson).
- `:app` (Android, Jetpack Compose Material 3): theme and reusable components, navigation, one screen +
  ViewModel per feature, the PDF renderer, exporter and sharer, and the file-backed store.

UI depends only on repository interfaces through `AssistantServices`, the manual composition root.

Reliability design: storage failures never crash the app: they surface in a dismissible banner on every
screen while the in-memory state stays usable; corrupt stored data falls back to defaults; the mapping timer is
stopped when the workspace is left, when the activity stops and when services are closed.

### Language and UI framework decision
Kotlin and Jetpack Compose (Material 3). The target is an Android tablet and the existing Sakar Android app is
Kotlin, so tooling and SDK levels (minSdk 21) match; Compose makes the tablet-responsive layouts and reusable
orange components compact; and the pure-Kotlin `:core` module gives fast JUnit tests with a virtual-time mapping
simulation. No DI framework was added: a hand-written composition root is enough.

## Build and test

JDK 17 is required (set `JAVA_HOME` to it).

```bash
./gradlew :core:test                        # JVM unit tests (core)
./gradlew :app:testDebugUnitTest            # JVM unit tests (file store)
./gradlew :app:assembleDebug                # debug APK -> app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:connectedDebugAndroidTest    # UI + PDF tests; run only against an emulator
```

Run instrumentation tests only with a single local emulator attached (`adb devices`); never against a robot
tablet. Test results for the current release are in [docs/PHASE2_REPORT.md](docs/PHASE2_REPORT.md).

## Safety
No permissions are declared (no INTERNET, no storage), `allowBackup` is false, and the app contains no network,
ROS, ADB or SSH code. `RobotHardwarePort` has a single disabled implementation that nothing wires in.
See [docs/FUTURE_HARDWARE_INTEGRATION.md](docs/FUTURE_HARDWARE_INTEGRATION.md) for what must exist before any
real-hardware integration is considered.

## Known limitations
- Everything robot-dependent is simulated; nothing here has been validated on a physical robot.
- An interrupted mapping session is paused but not restored after the app process is killed (saved maps are).
- The wizard reopens at the first incomplete step, not at the exact step the user last viewed.
- Backups are not encrypted; they contain demo data only.
- PDF layout is fixed A4 with the platform default sans-serif font; very long single-line notes are wrapped.
- Reports are capped at the 20 most recent.

## Next phase: ROS integration (not started)
Defined in [docs/FUTURE_HARDWARE_INTEGRATION.md](docs/FUTURE_HARDWARE_INTEGRATION.md): vendor authorization and
an approved interface contract, a firmware compatibility matrix, authentication and transport security, an
operator-confirmed safety gate, a real `RobotHardwarePort` implementation in its own module, an un-fakeable
REAL/SIMULATED indicator driven by the active port, contract tests shared by simulated and real ports, and a
hardware-in-the-loop test plan.
