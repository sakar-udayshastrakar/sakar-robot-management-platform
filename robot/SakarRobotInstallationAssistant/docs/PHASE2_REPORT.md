# Phase 2 - implementation and verification report

Version `0.2.0-sim` (versionCode 2) of the Sakar Robot Installation Assistant. Software-only: no robot, ROS
computer, robot network or physical device was contacted. Verified on the local `Medium_Tablet` emulator
(Android 10, `emulator-5554`, the only device attached).

## What was added

| Area | Result |
|---|---|
| A. PDF report export | A4 PDF built with the platform `PdfDocument`. `SIMULATION ONLY — NOT VALIDATED ON PHYSICAL ROBOT` banner and the full notice footer on **every** page; installation ID, report ID, date/time, completion status, floors and simulated maps, simulated diagnostics, checklist results (with notes) and outstanding warnings. Written to the private cache, shared through a `FileProvider` URI and the Android share sheet. Errors become visible messages. No device identifiers or credentials. |
| B. Backup / restore | Versioned JSON (`formatVersion` 1) through the Storage Access Framework (`CreateDocument` / `OpenDocument`). Whole-file validation before any change; merge (default, never overwrites, no duplicates) or replace (second confirmation). States clearly that maps are not Keenon / robot-map compatible. |
| C. Workflow reliability | Persistence-failure monitor with a banner on every screen; atomic file writes that now report failures; name normalisation (trim, collapse whitespace, case-insensitive duplicates); floor limit (20) and map limit (200); confirmations for cancel / discard / restart of a scan; simulation timer stops when the workspace is left, the activity stops or services close. |
| D. Simulation | Pause / resume / finish / cancel unchanged and covered by timer-cleanup tests; completion status added to reports; all diagnostics still labelled SIMULATED; `RobotHardwarePort` untouched and unwired. |
| E. Settings & About | New screen: app name, version, SIMULATED mode, "physical robot integration is not yet enabled" notice, backup export / restore, Reset demo data (confirmation lists exactly what is deleted). |
| F. UI | Report actions moved above the long report body (they were below the fold); aligned About rows; Settings tile on Home; consistent orange theme and 48 dp targets retained. No redesign. |

## Test results (run on the final build)

| Suite | Result |
|---|---|
| `:core:test` (JVM) | **122 passed, 0 failed** (39 from Phase 1 + 83 new) |
| `:app:testDebugUnitTest` (JVM) | **8 passed, 0 failed** |
| `:app:connectedDebugAndroidTest` (emulator) | **65 passed, 0 failed, 0 skipped** |

New and changed coverage:
- **Report document / PDF content:** `ReportDocumentTest` (7), `PdfExportTest` (11: real PDF header, page count via the system `PdfRenderer`, banner painted, banner and "Page n of N" on every page of a 120-item report, unbreakable long word, old reports without completion status, file replacement and cache pruning, unusable folder, FileProvider serves only the reports folder and refuses private data).
- **Backup:** `BackupExportTest` (2), `BackupRestoreTest` (10: round trip, restart, merge twice without duplicates, merge never overwrites, name-matched floors, installation kept, replace stops a running session), `BackupValidationTest` (44 malformed, truncated, oversized, deeply nested, wrong-type, duplicate, out-of-range, unknown-enum and newer-version files; each must leave all data unchanged).
- **Restore / export through the app:** `SettingsAndBackupUiTest` (17) with a scripted picker and real `ContentResolver` I/O.
- **Persistence and errors:** `PersistenceErrorTest` (5), `WorkflowPersistenceTest` (2), `FileKeyValueStoreTest` (8, JVM), `WorkflowRestartUiTest` (3: configuration change and full restart through the real file store), `PersistenceErrorUiTest` (1), `DocumentIoTest` (6).
- **Invalid input / duplicates / limits:** `InputValidationTest` (6) and UI dialog tests.
- **Destructive actions:** reset, restore-replace, start over, cancel / discard / restart scan, delete floor, delete map, delete report, floor-with-maps refusal (`DestructiveActionsUiTest` 4, `WorkflowControlUiTest` 6, plus Settings tests).
- **Timer cleanup:** `SimulationTimerCleanupTest` (5, virtual time) and UI tests for leaving the screen and stopping the activity.
- **Settings / reset:** `ResetDemoDataTest` (2) and Settings UI tests.
- **Share sheet:** `ReportPdfUiTest` (4) including the real system chooser opening for a real FileProvider URI.

Failures found and fixed during the work (none weakened an assertion):
- First UI run: 10 failures, all in the test code (scroll on non-scrollable nodes, text asserted on a container, 15 ms simulation tick finishing before a click, rename text inserted at cursor 0).
- The new Export / Copy / Delete buttons sat below the long report; moved above it (a real usability fix). Tests that waited for them exposed this.
- The chooser test initially detected the share sheet by activity state; changed to the foreground window package, and the chooser is always dismissed in a `finally`.
- Core: two test-setup mistakes (a mutated file kept a stale floor id; sequential test IDs made both installation IDs equal).

## Manually verified on the emulator
Installed the debug APK on `emulator-5554` only and checked, with screenshots:
- Home with the Settings tile; Settings & About; reset and restore confirmations.
- **Real Android system file pickers**: Export saved a file into Downloads; Restore opened it, validated it and showed the summary dialog.
- **Real share sheet** for the generated PDF (`content://…fileprovider/…` URI). Nothing was actually sent anywhere.
- Mapping cancel confirmation; leaving a running scan pauses it (37%) and the progress does not advance while away.
- PDF pages rendered by the device's `PdfRenderer` (single and multi-page).
- Logcat: 0 `FATAL EXCEPTION` entries, 0 app entries in the crash buffer.
- One Google Play Protect "send app for security check" prompt appeared on install; **"Don't send"** was chosen.

Screenshots: `docs/screenshots/` - `home`, `settings`, `report`, `restore-dialog`, `reset-dialog`, `share-sheet`, `system-save-picker`, `mapping-cancel-confirm`, `mapping-paused-on-leave`, `pdf-report-page1`, `pdf-report-page2-multipage` (new/changed) and `wizard`, `mapping`, `floors`, `diagnostics` (Phase 1).

## APK
`D:\Sakar Robotics Projects\sakar robotics web\robot\SakarRobotInstallationAssistant\app\build\outputs\apk\debug\app-debug.apk` (15,608,926 bytes, debug-signed, installed on the emulator).

## Files changed

Core (`core/src/main/kotlin/.../core/`): **new** `storage/PersistenceMonitor.kt`, `logic/ReportDocument.kt`, `backup/BackupModels.kt`, `backup/BackupParser.kt`, `backup/BackupService.kt`; **changed** `AssistantServices.kt`, `model/Outcome.kt`, `model/Models.kt`, `data/FloorAndMapData.kt`, `data/InstallationData.kt`, `data/SimulatedDiagnostics.kt`, `data/SimulatedMappingRepository.kt`, `logic/ReportBuilder.kt`, `repository/Repositories.kt`.

App (`app/src/main/`): **new** `export/PdfReportRenderer.kt`, `export/ReportExporter.kt` (also `ReportSharer`), `export/DocumentIo.kt`, `ui/settings/SettingsScreen.kt`, `res/xml/file_paths.xml`; **changed** `AssistantApp.kt`, `MainActivity.kt`, `AndroidManifest.xml` (FileProvider only; still no permissions), `data/FileKeyValueStore.kt`, `ui/Navigation.kt`, `ui/home/HomeScreen.kt`, `ui/mapping/MappingScreen.kt`, `ui/report/ReportScreen.kt`, `ui/components/Components.kt`.

Tests: **new** `core/src/test/.../ReportDocumentTests.kt`, `BackupTests.kt`, `ReliabilityTests.kt`; `app/src/test/.../FileKeyValueStoreTest.kt`; `app/src/androidTest/.../UiTestBase.kt`, `SettingsAndBackupUiTest.kt`, `WorkflowReliabilityUiTest.kt`, `PdfAndIoTest.kt`; **changed** `AssistantUiTest.kt` (cancel confirmation, Settings tile).

Docs: `README.md` (rewritten), this report, new screenshots.

## Build configuration changes
Only `app/build.gradle`: `versionCode 2`, `versionName "0.2.0-sim"`, `testImplementation junit:4.13.2` (JVM tests of the file store) and `androidTestImplementation gson:2.10.1` (parse exported backups in tests). `settings.gradle`, the root `build.gradle`, `gradle.properties`, `local.properties` and the wrapper are unchanged. Nothing outside this project was modified.

## Known limitations
- Everything robot-dependent is simulated and has not been validated on a physical robot.
- Tested on one emulator (Android 10 / API 29, 2560x1600 tablet). minSdk 21 and phone-sized screens are not exercised. No TalkBack run; accessibility was reviewed via content descriptions and 48 dp targets only. `lint` was not run.
- An interrupted mapping session is paused, but is **not** restored after the app process is killed (saved maps are).
- The wizard reopens at the first incomplete step, not the last step viewed.
- A simulated scan keeps running while a confirmation dialog is open (it is a dialog over a live simulation).
- Each generated PDF is about 370 KB because the platform embeds the font; fine for sharing, but not minimal.
- Backups and PDFs are not encrypted or signed; they contain demo data only.
- Merge keeps an in-progress installation and only notes that the backup's differing installation was skipped; use Replace to restore it.
- The system share sheet and pickers are driven by the OS; their appearance varies by device.
- A scripted picker is used in automated tests; the real pickers were verified manually only.

## What remains for the future ROS integration phase (not started)
Unchanged from `docs/FUTURE_HARDWARE_INTEGRATION.md`: written vendor authorization and an approved interface contract; firmware compatibility matrix; authentication and transport security; operator-confirmed safety gating and an emergency-stop path; a real `RobotHardwarePort` implementation in a separate module behind a build flag; an un-fakeable REAL/SIMULATED indicator driven by the active port; shared contract tests for simulated and real ports; a hardware-in-the-loop test plan on a non-customer robot; and a decision on any real map format, with a separate conversion layer if one is ever authorised (the current backup format must not be presented as one).

## Git
No commits, resets, cleans or stashes. Status equals the Phase 1 baseline plus the untracked project folder; the tracked-diff hash is unchanged at `9c1eaa81cebf7105`; SakarC40Agent is untouched.
