# C40 Android Replica — Implementation Status

**Date:** 2026-10-25 (continuation iteration 2)
**Companion to:** `COVERAGE_AND_GAP_REPORT.md`, `RUNTIME_VERIFICATION_CHECKLIST.md`, `REMAINING_UNKNOWN_ITEMS.md`.

## What this iteration did (safe, evidence-backed, no guessed UI)

Per the project's reference-first rule (no screen built without an original-robot reference) and the
"do not invent" rule, this pass added only non-visual, evidence-grounded capabilities + tests, and
ran the builds/tests for **both** apps (the mapping app's tests had never been run before).

### Implemented
| Item | Where | Notes |
|---|---|---|
| `VoiceService` abstraction + `MockVoiceService` + voice event catalog | `SakarC40Agent/domain/.../voice/` | Phase-4 request. Labeled mock: resolves announcement text only, **no audio, no robot contact**; honours mute/volume. Sakar-authored event text (original vendor text is UNKNOWN). Wired into `AppContainer` + `DefaultAppContainer`. |
| Map coordinate transform (`MapCoordinateTransform` + `OccupancyGridInfo`) | `SakarC40Agent/domain/.../map/` | Phase-3 gap (coverage §4). Standard ROS/cartographer world↔pixel transform for a real-map renderer; documented as the standard convention, **not** the original OpenLayers internals. Pure logic, no fabricated map data. |
| Mapping-app tests executed | `SakarRobotInstallationAssistant` | Previously un-run; now green (see below). |

### Not implemented this pass (and why — unchanged from coverage §7)
- Localization/relocation screen, map-edit parity, elevator/dispatch, audio/voice **UI** screens beyond existing Sound settings — blocked on **original-robot reference screenshots** (project rule forbids guessing layouts).
- Real occupancy renderer wired to a live map — blocked on a **synced real map** to verify against (the transform util above is the ready building block).
- Real localization / actuation / live ROS — blocked on **physical robot** (`NotAvailableMappingRepository`/`SakarRobotAdapter` stay fail-closed by design).

## Files created
- `SakarC40Agent/domain/src/main/kotlin/com/sakarrobotics/c40agent/domain/voice/VoiceService.kt`
- `SakarC40Agent/domain/src/main/kotlin/com/sakarrobotics/c40agent/domain/voice/MockVoiceService.kt`
- `SakarC40Agent/domain/src/main/kotlin/com/sakarrobotics/c40agent/domain/map/MapCoordinateTransform.kt`
- `SakarC40Agent/domain/src/test/kotlin/com/sakarrobotics/c40agent/domain/voice/VoiceServiceTest.kt`
- `SakarC40Agent/domain/src/test/kotlin/com/sakarrobotics/c40agent/domain/map/MapCoordinateTransformTest.kt`
- `research/c40-reverse-engineering/replica/IMPLEMENTATION_STATUS.md` (this file)

## Files modified
- `SakarC40Agent/domain/src/main/kotlin/com/sakarrobotics/c40agent/domain/di/AppContainer.kt` — added `voiceService` member.
- `SakarC40Agent/data/src/main/java/com/sakarrobotics/c40agent/data/di/DefaultAppContainer.kt` — provide `MockVoiceService`.
- `research/c40-reverse-engineering/replica/COVERAGE_AND_GAP_REPORT.md`, `REMAINING_UNKNOWN_ITEMS.md` — dated update sections.

No existing screens were modified; no original research/APKs touched; no commit/push performed.

## Tests executed & build results (actual)
- **SakarC40Agent** — robot-dev MCP `run_unit_tests` (project-wide `testDebugUnitTest` + `test`): **BUILD SUCCESSFUL in 13s, exit 0.** `:domain:test` ran the new `VoiceServiceTest` (4) + `MapCoordinateTransformTest` (5); `:data` recompiled and tested with the new DI member; full app compiles.
- **SakarRobotInstallationAssistant** — `./gradlew :core:test :app:testDebugUnitTest` (JDK 21): **BUILD SUCCESSFUL in 40s, exit 0** (core unit suite + app unit test). Instrumentation `androidTest` UI tests were **not** run (require a device/emulator — documented limitation).

## Screens: complete / partial (summary; full matrix in coverage §2–§3)
- **Complete (live/simulated, built):** Home, Start Cleaning + mode/intensity (5 C40 modes), Schedule, Teach Route, Diagnostics, Super User + hardware Debug, most Settings (incl. Sound: music/voice volume + DND), Maps zone-sketch; mapping app: home/wizard/scan/floors/checklist/report.
- **Partial:** map-edit parity (rooms/virtual-walls), audio/voice announcement playback (now has a mock service; no UI playback added), occupancy render from a real backend map (transform util ready).
- **Blocked (hardware/reference/decision):** localization screen, elevator/dispatch, live ROS, real actuation, exact visual parity.

## Remaining hardware-dependent tasks
Live ROS verification, real localization/pose, real map sync + render verification, all robot actuation, and original-robot reference screenshots for the UNCONFIRMED screens. See `RUNTIME_VERIFICATION_CHECKLIST.md`.
