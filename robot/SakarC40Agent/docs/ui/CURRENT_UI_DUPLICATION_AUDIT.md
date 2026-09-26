# Current UI Duplication Audit

**Read-only. No code was changed to produce this document, and nothing is deleted or disabled as
a result of it.** Every claim below was verified by reading the current source (`Grep`/`Read`),
not assumed from memory - see the inline evidence under each item.

Date: 2026-09-25

---

### 1. Home "Super User →" text button

- **FEATURE:** `super_user_authentication` (entry point)
- **OWNER:** `HomeScreen.kt` (`HomeTopBar`, `TextButton(onClick = onOpenSuperUser)`)
- **ROUTE:** navigates to `SUPER_USER_LOGIN`
- **ACCESS PATH:** Home → top-right "Super User →" text button
- **DUPLICATE?** YES - reaches the identical screen/route as item 2 (long-press logo)
- **REFERENCE MATCH?** UNCONFIRMED - no vendor screenshot or user statement confirms the real
  robot's Home screen shows a visible Super User button. This button pre-dates the discovery (this
  session, item 2) that the real entry point is a hidden long-press gesture.
- **ACTION:** **Requires user decision.** Two real, independently-working entry points to the same
  feature now exist. Per the stated governance rule ("multiple legitimate access paths are allowed
  only when the reference UI requires them"), this one path is not reference-confirmed and is the
  candidate for removal - but it is not removed here, since removing it is a product decision, not
  a mechanical duplicate-cleanup.

### 2. Long-press SAKAR ROBOTICS logo

- **FEATURE:** `super_user_authentication` (entry point)
- **OWNER:** `HomeScreen.kt` (`HomeTopBar`, `Image(...).pointerInput { detectTapGestures(onLongPress = ...) }`)
- **ROUTE:** navigates to `SUPER_USER_LOGIN`
- **ACCESS PATH:** Home → long-press the SAKAR ROBOTICS wordmark (top-left)
- **DUPLICATE?** YES (of item 1, see above) - same route, same screen
- **REFERENCE MATCH?** USER-STATED real-robot behavior (added this session specifically because
  the user reported this as how the real robot exposes Super User).
- **ACTION:** Keep. This is the reference-confirmed path; item 1 is the one in question.

### 3. Super User sidebar

- **FEATURE:** `super_user_home_shell`
- **OWNER:** `SuperUserHomeScreen.kt`
- **ROUTE:** `SUPER_USER_HOME`
- **ACCESS PATH:** Super User authentication success
- **DUPLICATE?** NO - single implementation, rebuilt this session to replace the old flat-list
  version (not left as a second copy; the old version's source was overwritten, not duplicated).
- **REFERENCE MATCH?** CONFIRMED - matches 5 vendor reference screenshots (sidebar icons/colors,
  section order, selected-state styling).
- **ACTION:** None. Note: the sidebar has a second group of items (Manual Drive Simulation, Logs,
  Robot Information, Robot Installation, Maps) below a divider that is **not** in the reference
  screenshots - see items 7, 13, 14, 15, 16 below for each one individually.

### 4. Manual Drive

- **FEATURE:** `manual_cleaning_mode_selector` + `manual_drive_motion_control`
- **OWNER:** `ManualDriveScreen.kt`
- **ROUTE:** `MANUAL_DRIVE`
- **ACCESS PATH:** Home → Manual Drive card, **and** Super User → Manual Drive Simulation (item 7)
- **DUPLICATE?** NO - both paths reach the exact same screen/route/ViewModel. This is the
  "same owner, multiple entry points" pattern the governance rules explicitly allow, not a
  duplicate implementation.
- **REFERENCE MATCH?** CONFIRMED for the mode/intensity selector (vendor "Hand Cleaning"
  reference); the movement-controls dialog was an explained, additive UI change (see
  `UI_CHANGE_LEDGER.md`) since the vendor reference screen itself has no movement controls at this
  checkpoint.
- **ACTION:** None.

### 5. Cleaning modes (Sweep & Mop / Water Suction / Sweep & Vacuum / Sweep & Push / Sweep)

- **FEATURE:** part of `manual_cleaning_mode_selector`
- **OWNER:** `ManualDriveScreen.kt` (mode-card grid)
- **ROUTE:** `MANUAL_DRIVE`
- **ACCESS PATH:** Home → Manual Drive (and Super User → Manual Drive Simulation, same screen)
- **DUPLICATE?** NO - verified by `Grep` for `CleaningMode`/`SWEEP`/`WATER_SUCTION` across
  `operator-ui/.../superuser/`: **zero matches**. These five modes exist in exactly one place.
- **REFERENCE MATCH?** CONFIRMED (vendor "Hand Cleaning" reference).
- **ACTION:** None. See item 6 for the specific claim (from the task that requested this audit)
  that cleaning controls were repeated inside Super User.

### 6. "Super User duplicate cleaning controls"

- **FEATURE:** N/A - **this specific duplication was checked for and not found.**
- **OWNER:** N/A
- **ROUTE:** N/A
- **ACCESS PATH:** N/A
- **DUPLICATE?** **NO, per direct source verification.** `SuperUserResourceManagementPane`
  (Super User → Resource Management) shows `BusinessProfile` values (`SR_CLEANING`, `SR_BUTLER`,
  `DEMO`) - a whole-robot **operating profile**, not the per-session **cleaning-mode** selection
  (Sweep & Mop etc.) from Manual Drive. These are different concepts backed by different domain
  models (`BusinessProfile` vs `CleaningMode`), confirmed by `Grep`: no `CleaningMode`/mode-name
  string appears anywhere under `operator-ui/.../superuser/`.
- **REFERENCE MATCH?** `SuperUserResourceManagementPane`'s "Imported" list (business profiles) is
  itself CONFIRMED against the vendor Resource Management reference screenshot.
- **ACTION:** None required. Documented here explicitly so this concern is answered with
  evidence rather than left open. If a future change ever adds `CleaningMode`-shaped controls to
  any Super User screen, that WOULD be a real duplicate of item 4/5 and should be rejected.

### 7. Manual Drive Simulation

- **FEATURE:** `manual_drive_simulation_entry`
- **OWNER:** `ManualDriveScreen.kt` (same screen as item 4 - this is a navigation entry, not a
  second screen)
- **ROUTE:** `MANUAL_DRIVE` (same route as item 4)
- **ACCESS PATH:** Super User Home sidebar (extra item, below the reference-matched 5 sections)
- **DUPLICATE?** NO as an implementation (reuses the existing screen/ViewModel entirely). **YES**
  as a menu item relative to the vendor reference: none of the 5 reference screenshots show a
  "Manual Drive Simulation" entry in the Super User sidebar.
- **REFERENCE MATCH?** NOT PRESENT in the reference screenshots - an engineering/test addition.
- **ACTION:** Classify as ENGINEERING UI per the "Reference vs Engineering UI" rule. Consider
  whether it belongs in the reference-matched primary sidebar group at all, or should move to a
  clearly-separated "Engineering / Test Tools" section so it doesn't read as if the vendor's own
  Super User menu has this item. Not moved automatically - flagged for a decision.

### 8. Robot Debugging

- **FEATURE:** `robot_hardware_debug_dashboard` (current) / `robot_actuator_test_groups_legacy`
  (orphaned)
- **OWNER:** `RobotHardwareDebugScreen.kt`
- **ROUTE:** `SUPER_USER_DEBUG_GROUPS`
- **ACCESS PATH:** Super User → Robot Debugging (pane) → "Robot Debugging" row
- **DUPLICATE?** The route now has exactly one live owner. Its **predecessor**,
  `RobotDebuggingGroupsScreen` + `RobotDebuggingGroupDetailScreen` (and the `SUPER_USER_DEBUG_GROUP`
  route), is now **ORPHANED**: verified by `Grep` that no `navController.navigate(Routes.debugGroup(...))`
  call site exists anywhere in `SakarNavGraph.kt` or elsewhere - the only composable that ever
  called it (`RobotDebuggingGroupsScreen`) is no longer registered at any route.
- **REFERENCE MATCH?** CONFIRMED (8 reference screenshots) for the current `RobotHardwareDebugScreen`.
- **ACTION:** No functional action (nothing reachable is broken). For future cleanup: the orphaned
  `RobotDebuggingGroupsScreen.kt` / `RobotDebuggingGroupDetailScreen.kt` source and the
  `SUPER_USER_DEBUG_GROUP` route constant are dead code and candidates for deletion in a
  dedicated, explicitly-approved cleanup pass (not this audit).

### 9. Network

- **FEATURE:** `network_wifi_status`
- **OWNER:** shared - `NetworkScreen.kt` (operator, full screen) and `SuperUserNetworkPane` in
  `SuperUserPanes.kt` (admin, inline)
- **ROUTE:** `SETTINGS_NETWORK` (operator path only; the admin pane is inline, no separate route)
- **ACCESS PATH:** Home → Settings → Wi-Fi/Network, **and** Super User → Network
- **DUPLICATE?** NO as a data integration - both read the same `android.net.wifi.WifiManager`
  directly (there is no domain repository wrapping Wi-Fi in either case). **Presentation** is
  intentionally different (operator screen predates the reference screenshots; admin pane matches
  the vendor reference list-with-lock-icons style). This is a deliberate, accepted exception (two
  visual layers over one real data source), not a duplicate SDK integration.
- **REFERENCE MATCH?** CONFIRMED for the admin pane; the operator screen was not built against a
  reference screenshot.
- **ACTION:** None required now. Longer-term: if Wi-Fi state needs to be observed reactively
  (rather than read on screen-open) by more than these two screens, it should move behind a real
  repository so a third consumer doesn't read `WifiManager` a third time - noted for awareness,
  not an active problem today (only two consumers exist).

### 10. Resource Management

- **FEATURE:** `resource_management_business_profile`
- **OWNER:** shared - `ResourceManagementScreen` (`GeneralResourceScreens.kt`, operator) and
  `SuperUserResourceManagementPane` (admin, inline)
- **ROUTE:** `SETTINGS_RESOURCE_MGMT` (operator path; admin pane is inline)
- **ACCESS PATH:** Home → Settings → Resource Management, **and** Super User → Resource Management
- **DUPLICATE?** NO - both read/write the same `PreferencesViewModel` / `LocalPreferencesRepository`
  state (`BusinessProfile`). One shared ViewModel, two presentation layers.
- **REFERENCE MATCH?** CONFIRMED for the admin pane (Online Scenes / Imported layout).
- **ACTION:** None.

### 11. General Settings

- **FEATURE:** `general_local_preferences`
- **OWNER:** shared - `GeneralSettingsScreen` (operator) and `SuperUserGeneralSettingsPane` (admin,
  inline)
- **ROUTE:** `SETTINGS_GENERAL` (operator path; admin pane is inline)
- **ACCESS PATH:** Home → Settings → General, **and** Super User → General Settings
- **DUPLICATE?** NO - both share `PreferencesViewModel`. The admin pane additionally renders
  several reference-only rows (Standard User Password, Show Map Creation Shortcut, Rotate Screen
  180°, elevator/ROS rows) that have no backing state at all (correctly shown disabled/UNAVAILABLE,
  not backed by a second preferences store).
- **REFERENCE MATCH?** CONFIRMED for the admin pane's full row set; the operator screen only ever
  exposed a subset (language + 2 toggles), not built against this reference.
- **ACTION:** None required. Worth noting for product discussion: the operator-facing
  `GeneralSettingsScreen` could eventually be reconciled with the fuller admin row set, but that is
  a product/scope decision, not a duplication bug.

### 12. System Settings

- **FEATURE:** `super_user_system_settings` (current) / `super_user_system_settings_legacy`
  (orphaned)
- **OWNER:** `SuperUserSystemSettingsPane` in `SuperUserPanes.kt`
- **ROUTE:** inline in `SUPER_USER_HOME`
- **ACCESS PATH:** Super User → System Settings
- **DUPLICATE?** Its **predecessor**, `SuperUserSystemSettingsScreen` (`SystemSettingsScreen.kt`)
  at route `SUPER_USER_SYSTEM_SETTINGS`, is now **ORPHANED** - verified by `Grep`: nothing
  navigates to that route anymore (it used to be reachable from the old Super User flat list,
  mislabeled "General Settings" at the time, before this session's sidebar rebuild).
- **REFERENCE MATCH?** CONFIRMED for the current pane.
- **ACTION:** No functional action. `SystemSettingsScreen.kt` and the `SUPER_USER_SYSTEM_SETTINGS`
  route constant are dead code, candidates for a future explicit cleanup pass (not this audit).

### 13. Robot Installation

- **FEATURE:** `robot_installation_commissioning`
- **OWNER:** `InstallationHomeScreen.kt` + 4 sub-screens
- **ROUTE:** `INSTALLATION_HOME`
- **ACCESS PATH:** Super User → Robot Installation
- **DUPLICATE?** NO - single implementation.
- **REFERENCE MATCH?** UNCONFIRMED - not present in any of the 5 Super User sidebar reference
  screenshots obtained so far. Pre-existing in the codebase from before those references were
  available.
- **ACTION:** No change made. Flagged so a future reference screenshot of this area (if obtained)
  gets compared against what already exists here, rather than triggering a fresh rebuild from
  scratch.

### 14. Maps

- **FEATURE:** `maps_management`
- **OWNER:** `MapsListScreen.kt` / `MapDetailScreen.kt`
- **ROUTE:** `MAPS_LIST`
- **ACCESS PATH:** Super User → Maps
- **DUPLICATE?** NO - single implementation.
- **REFERENCE MATCH?** UNCONFIRMED - same status as item 13.
- **ACTION:** Same as item 13.

### 15. Logs

- **FEATURE:** `logs_viewer`
- **OWNER:** `LogsScreen.kt`
- **ROUTE:** `SUPER_USER_LOGS`
- **ACCESS PATH:** Super User → Logs
- **DUPLICATE?** NO.
- **REFERENCE MATCH?** NOT PRESENT in the reference screenshots - almost certainly an intentional
  engineering addition (an SDK-call-log viewer is a natural developer tool, not a robot-operator
  feature the vendor would expose).
- **ACTION:** Classify as ENGINEERING UI (same treatment as item 7). Consider grouping with Manual
  Drive Simulation under a clearly-labeled "Engineering / Test Tools" section if the sidebar is
  revisited, so the reference-matched 5 sections stay visually distinct from Sakar-added tooling.

### 16. Robot Information

- **FEATURE:** `robot_information`
- **OWNER:** `RobotInfoScreen.kt`
- **ROUTE:** `SETTINGS_ROBOT_INFO`
- **ACCESS PATH:** Home → Settings → Robot Information, **and** Super User → Robot Information
- **DUPLICATE?** NO - both paths reach the identical screen/route (same "shared owner, multiple
  entry points" pattern as Manual Drive/item 4).
- **REFERENCE MATCH?** CONFIRMED (vendor reference screenshot obtained earlier in the project).
- **ACTION:** None.

---

## Summary table

| # | Feature | Duplicate? | Reference match? | Action |
|---|---|---|---|---|
| 1 | Home "Super User →" button | YES (of #2) | UNCONFIRMED | **User decision required** |
| 2 | Long-press SAKAR logo | YES (of #1) | USER-STATED real | Keep |
| 3 | Super User sidebar | NO | CONFIRMED | None |
| 4 | Manual Drive | NO (shared owner) | CONFIRMED | None |
| 5 | Cleaning modes | NO | CONFIRMED | None |
| 6 | "Duplicate cleaning controls" claim | **NOT FOUND** | n/a | None - documented as a non-issue |
| 7 | Manual Drive Simulation | NO (shared owner); not in reference as a menu item | NOT PRESENT | Consider engineering-section grouping |
| 8 | Robot Debugging | predecessor is ORPHANED | CONFIRMED (current) | None; legacy files are cleanup candidates |
| 9 | Network | NO (shared data source) | CONFIRMED (admin) | None |
| 10 | Resource Management | NO (shared ViewModel) | CONFIRMED (admin) | None |
| 11 | General Settings | NO (shared ViewModel) | CONFIRMED (admin) | None |
| 12 | System Settings | predecessor is ORPHANED | CONFIRMED (current) | None; legacy file is cleanup candidate |
| 13 | Robot Installation | NO | UNCONFIRMED | None; awaiting reference |
| 14 | Maps | NO | UNCONFIRMED | None; awaiting reference |
| 15 | Logs | NO | NOT PRESENT (engineering) | Consider engineering-section grouping |
| 16 | Robot Information | NO (shared owner) | CONFIRMED | None |

**No application functionality was removed to produce this audit.**
