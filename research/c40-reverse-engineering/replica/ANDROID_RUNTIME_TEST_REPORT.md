# Android Runtime Test Report — C40 Replica Apps

**Date:** 2026-10-25
**Scope:** Actual on-emulator runtime testing of both Sakar Android apps (install, launch, interact, screenshot, Logcat) — not just compile/unit tests.
**Baseline commit:** `421af72` on `dev` (plus this session's uncommitted domain additions). Nothing was committed or pushed for this pass.

## 1. Tooling / "Android Studio" status (honest)

I do **not** have GUI control of the Android Studio IDE, so I did not click through the Android Studio window. I accomplished the real goal — running both apps on an emulator and interacting with them — using the project's own toolchain, which is equivalent to what Android Studio's Device Manager + Run would do:
- **Emulator:** started the **existing** `Medium_Tablet` AVD via the Android SDK `emulator` binary (the same AVD Android Studio's Device Manager lists). No new AVD was created and no system image was downloaded.
- **Build/install/run:** the project Gradle wrapper (via the robot-dev MCP `gradle_build` and direct `./gradlew`), `adb install`, and `adb am start` / robot-dev MCP `launch_app`.
- **Interaction:** robot-dev MCP device tools (`device_tap`, `device_tap_text`, `device_ui_dump`, `device_screenshot`) + `adb shell input`.
- **Toolchain:** JDK 21, Gradle 8.7, Android SDK, adb 1.0.41 (from `mcp_server_info`).

## 2. Emulator / device

| | |
|---|---|
| AVD | `Medium_Tablet` (only AVD present) |
| Serial | `emulator-5554` |
| Android | 10 (SDK 29), x86_64 |
| Screen | **2560 × 1600 @ 320 dpi** (tablet, landscape) — matches the apps' target |
| Boot | clean boot, `sys.boot_completed=1` |

No physical device was connected (`adb devices` empty before start) — expected; there is no robot/tablet attached.

## 3. App install / launch status

| App | Build | Install | Launch | Result |
|---|---|---|---|---|
| **SakarC40Agent** | `:app:assembleDebug` BUILD SUCCESSFUL | `adb install -r` Success | `SakarOperatorActivity` foreground (pid 5966) | ✅ runs |
| **SakarRobotInstallationAssistant** | `:app:assembleDebug` BUILD SUCCESSFUL (own Gradle root, JDK 21) | `adb install -r` Success | `MainActivity` foreground (pid 8388) | ✅ runs |

Both were tested **separately** on the same emulator.

## 4. Screens / workflows tested (actually opened + verified)

Screenshots are under `robot/.mcp-artifacts/` (git-ignored). Each was visually inspected.

### SakarC40Agent (cleaning HMI)
| Screen / workflow | Verified | Screenshot |
|---|---|---|
| Home / dashboard | Sakar branding, honest header "Offline · Battery UNAVAILABLE · v0.1.0-poc", "Not connected — the onboard SDK could not initialize" banner, 4 quick actions | `device-screenshot-1792903659445.png` |
| PIN gate (Start Cleaning) | "Please enter PIN" keypad; any-4-digit placeholder accepted | `…723561.png` |
| Start Cleaning | DEMO/SIMULATED banner, SIMULATED chip, 5 C40 modes (Sweep/Sweep Mop/Water Suction/Sweep Vacuum/Sweep Push), intensity, cleaning cycles | `…748337.png` |
| Cleaning execution | Progress "WATER SUCTION · STANDARD · 1% · Elapsed/Remaining (simulated estimate)", **Start → Pause → Resume → Stop** all worked, SIMULATED chip | `…767970.png` |
| Settings (list) | Consumables, Cleaning Data, Workstation, Charging, Wi-Fi/Network, Display, Sound, Screen Lock | `…829947.png` |
| Sound / voice settings | Music Volume, **Voice Prompt Volume (80)**, Music/Task DND toggles | `…848146.png` |
| Manual Drive | mode/intensity selector; unsupported modes greyed (honest gating), movement toggle | `…883779.png` |
| Super User PIN (long-press logo) | "Super User Access" keypad | `…919247.png` |
| Super User home → Network | reference-matching sidebar (Network, Resource Mgmt, General, Robot Debugging, System Settings); Wi-Fi shows real `AndroidWifi` "Connection successful" | `…982623.png` |
| Robot Debugging menu | Robot Debugging, Diagnostics Overview (read-only), Maps (read-only), Return-to-charge/Wash-pressure/ROS-connection tests | `…005040.png` |

Not triggered (safety): the "Return to charge stress test", "Wash Pressure Test", and "Industrial computer/ROS connection test" actions (actuation-type) — menu verified, not executed.

### SakarRobotInstallationAssistant (mapping)
| Screen / workflow | Verified | Screenshot |
|---|---|---|
| Installation Home | "SIMULATED BACKEND - no robot is connected. Everything shown is demo data.", "Hardware integration: not implemented and disabled", 7 cards | `…150751.png` (loaded) |
| Mapping Workspace (idle) | occupancy "Simulated map canvas" + legend (Not scanned/Free/Wall/Robot), **Floor selector (Ground/First)**, Idle session, "Start simulated mapping" | `…179893.png` |
| Mapping (running) | **occupancy grid rendering** with free space + walls + orange robot position, progress bar "45% scanned · No robot is mapping", Pause/Finish/Cancel; floor selector disabled mid-session | `…207639.png` |
| Mapping (finished) | 100% scanned, full floor-plan grid, "Finished (simulated)", Start again/Discard, name field + "Save simulated map" | `…239377.png` |

**World-to-pixel coordinate transform:** the simulated canvas renders occupancy cells directly; the standard ROS transform util added this session (`MapCoordinateTransform`, `:domain`) is unit-tested but is **not** wired to this simulated canvas (it is the building block for a real backend-PNG map — see COVERAGE §4). Verified by unit test, not by this runtime path.

## 5. Logcat findings

- **SakarC40Agent:** no crashes. Only: `PeanutSDK [#E_203002#]` (SDK cannot initialize with no robot → drives the honest "Not connected" banner) and a generic ART `runtime_flags: 0x8000` warning. No `AndroidRuntime`/FATAL, no app exceptions.
- **SakarRobotInstallationAssistant:** no crashes (pid alive throughout). Only the generic ART `runtime_flags` warning. The `Bundle` `ClassCastException` lines in the system log are Android framework-internal (pid 6696, system process), not the app. Cold start measured `Displayed MainActivity: +8s312ms`.

## 6. Bugs discovered / fixes made

- **No reproducible application-side bug was found.** Both apps launch, navigate, and render correctly with honest simulated/offline labeling and correct tablet-landscape layout; no blank screens (after load), navigation failures, missing assets, overflow/clipping, or runtime exceptions.
- **Observation (not a defect, no fix):** the installation assistant has a slow (~8 s) **cold start** that shows a white frame before the first Compose frame (ProfileInstaller + first-frame compile on a cold emulator). The first screenshot caught that white frame; a second capture after load showed the full Home. This is normal Android cold-start behavior, not a code defect, so **no code change was made**. (Possible future nicety: a lightweight splash/placeholder during cold start — optional, not required.)
- **No source files were modified in this pass.**

## 7. Build & test results (this pass)

- `SakarC40Agent/:app:assembleDebug` — BUILD SUCCESSFUL; installed + ran.
- `SakarRobotInstallationAssistant/:app:assembleDebug` — BUILD SUCCESSFUL; installed + ran.
- (Unit tests from the prior pass remain green: SakarC40Agent `run_unit_tests` BUILD SUCCESSFUL incl. new domain tests; installation assistant `:core:test` + `:app:testDebugUnitTest` BUILD SUCCESSFUL.)
- Instrumentation (`androidTest`) UI tests were **not** run this pass — the apps were exercised interactively instead.

## 8. Remaining hardware/backend-dependent limitations

- No physical C40 / ROS computer → the `PeanutSDK` cannot initialize (C40 app correctly shows "Not connected"); all robot telemetry, actuation, localization, and live mapping remain untestable on the emulator (and are fail-closed / simulated by design).
- Real occupancy-map rendering from a synced backend map (vs the local simulated canvas) needs a real map — see `COVERAGE_AND_GAP_REPORT.md` §4 and `RUNTIME_VERIFICATION_CHECKLIST.md`.
- Exact visual parity with the original vendor app is out of scope (Sakar-branded) and unverifiable without original-robot reference screenshots.
- The emulator `Medium_Tablet` is left running; nothing was committed or pushed.
