# Route Registry

Every navigation destination registered in `SakarNavGraph.kt`. **Check this before adding a new
route.** If an equivalent route already exists, navigate to it (optionally passing new state)
instead of creating a second one.

Status values:
- `LIVE` - reachable from at least one real navigation call site.
- `ORPHANED` - still registered in the NavHost, but no `navController.navigate(...)` call site
  reaches it anymore. Not deleted automatically (per project policy); flagged here so nobody
  assumes it's dead and nobody accidentally treats it as a template to copy.

| Route constant | Path | Screen composable | Purpose | Parent | Access level | ViewModel | Status |
|---|---|---|---|---|---|---|---|
| `HOME` | `home` | `HomeScreen` | Operator dashboard, start destination | - | OPERATOR | `HomeViewModel` | LIVE |
| `START_CLEANING` | `start_cleaning` | `StartCleaningScreen` | Start/monitor a cleaning session (SIMULATED - no `CleanComponent` in the licensed SDK) | HOME | OPERATOR | (see screen) | LIVE |
| `MANUAL_DRIVE` | `manual_drive` | `ManualDriveScreen` | Cleaning-mode/intensity selector (UI-only) + embedded Movement Controls dialog (real/simulated motor jog) | HOME | OPERATOR | `ManualDriveViewModel` (dialog only) | LIVE - the Super User "Manual Drive Simulation" access path was removed 2026-09-25 (not reference-confirmed); Home is now the only path |
| `TEACH_ROUTE_LIST` | `teach_route_list` | `TeachRouteScreen` | Route teaching entry | HOME | OPERATOR | - | LIVE |
| `TEACH_ROUTE_RECORD` | `teach_route_record` | *(constant declared, no composable registered)* | - | - | - | - | **ORPHANED (never registered in NavHost at all)** |
| `SCHEDULE_LIST` | `schedule_list` | `ScheduleListScreen` | Scheduled cleaning list | HOME | OPERATOR | - | LIVE |
| `SCHEDULE_EDIT_NEW` | `schedule_edit/new` | `ScheduleEditScreen(existing=null)` | Create a schedule | SCHEDULE_LIST | OPERATOR | - | LIVE |
| `SCHEDULE_EDIT` | `schedule_edit/{taskId}` | `ScheduleEditScreen(existing=...)` | Edit a schedule | SCHEDULE_LIST | OPERATOR | - | LIVE |
| `SETTINGS_ROOT` | `settings_root` | `SettingsRootScreen` | Operator-facing settings menu | HOME | OPERATOR | - | LIVE |
| `SETTINGS_NETWORK` | `settings_network` | `NetworkScreen` | Real Wi-Fi status/scan (Android `WifiManager`) | SETTINGS_ROOT | OPERATOR | - | LIVE - **same real Wi-Fi source as `SuperUserNetworkPane` (inline, ADMIN); restyled independently, not a second integration** |
| `SETTINGS_GENERAL` | `settings_general` | `GeneralSettingsScreen` | Language + 2 local prefs | SETTINGS_ROOT | OPERATOR | `PreferencesViewModel` | LIVE - shares `PreferencesViewModel`/`LocalPreferencesRepository` with `SuperUserGeneralSettingsPane` (inline, ADMIN) |
| `SETTINGS_RESOURCE_MGMT` | `settings_resource_mgmt` | `ResourceManagementScreen` | Business-profile radio picker | SETTINGS_ROOT | OPERATOR | `PreferencesViewModel` | LIVE - shares state with `SuperUserResourceManagementPane` (inline, ADMIN) |
| `SETTINGS_CONSUMABLES` | `settings_consumables` | `ConsumablesScreen` | - | SETTINGS_ROOT | OPERATOR | - | LIVE |
| `SETTINGS_CLEANING_DATA` | `settings_cleaning_data` | `CleaningDataScreen` | - | SETTINGS_ROOT | OPERATOR | - | LIVE |
| `SETTINGS_WORKSTATION` | `settings_workstation` | `WorkstationScreen` | - | SETTINGS_ROOT | OPERATOR | - | LIVE |
| `SETTINGS_CHARGING` | `settings_charging` | `ChargingSettingsScreen` | - | SETTINGS_ROOT | OPERATOR | - | LIVE |
| `SETTINGS_DISPLAY` | `settings_display` | `DisplaySettingsScreen` | - | SETTINGS_ROOT | OPERATOR | - | LIVE |
| `SETTINGS_SOUND` | `settings_sound` | `SoundSettingsScreen` | - | SETTINGS_ROOT | OPERATOR | - | LIVE |
| `SETTINGS_SCREEN_LOCK` | `settings_screen_lock` | `ScreenLockSettingsScreen` | - | SETTINGS_ROOT | OPERATOR | - | LIVE |
| `SETTINGS_ROBOT_INFO` | `settings_robot_info` | `RobotInfoScreen` | - | SETTINGS_ROOT | OPERATOR/ADMIN | - | LIVE - the Super User "Robot Information" access path was removed 2026-09-25 (not reference-confirmed); Settings is now the only path |
| `SETTINGS_SYSTEM_INFO` | `settings_system_info` | `SystemInfoScreen` | - | SETTINGS_ROOT | OPERATOR | - | LIVE |
| `SUPER_USER_LOGIN` | `super_user_login` | `SuperUserLoginScreen` | Real SHA-256/DataStore PIN auth (circular keypad UI) | HOME (long-press logo only - the "Super User →" text button flagged in the duplication audit was removed 2026-09-25) | gate | `SuperUserAuthViewModel` | LIVE |
| `SUPER_USER_HOME` | `super_user_home` | `SuperUserHomeScreen` | ADMIN sidebar shell - exactly the 5 vendor reference sections (Network, Resource Management, General Settings, Robot Debugging, System Settings); the non-reference extra items (Manual Drive Simulation, Logs, Robot Information, Robot Installation, Maps) were removed from the visible sidebar 2026-09-25 | SUPER_USER_LOGIN | ADMIN | - | LIVE |
| `SUPER_USER_DEBUG_GROUPS` | `super_user_debug_groups` | `RobotHardwareDebugScreen` | Full reference-matched hardware Debug dashboard | SUPER_USER_HOME (via "Robot Debugging" row inside the Robot Debugging pane) | ADMIN | `DiagnosticsViewModel`, `ManualDriveViewModel` (embedded pad) | LIVE - **composable swapped in this session; see change ledger** |
| `SUPER_USER_DEBUG_GROUP` | `super_user_debug_group/{groupId}` | `RobotDebuggingGroupDetailScreen` | Old group→test drill-down detail | *(was `SUPER_USER_DEBUG_GROUPS`, via `RobotDebuggingGroupsScreen.onOpenGroup`)* | ADMIN | `DiagnosticsViewModel` | **ORPHANED - `RobotDebuggingGroupsScreen` (the only screen that ever navigated here) is no longer registered anywhere; nothing can reach this route today** |
| `SUPER_USER_LOGS` | `super_user_logs` | `LogsScreen` | SDK call log viewer/export | *(was SUPER_USER_HOME)* | ADMIN | - | **ORPHANED - visible sidebar entry removed 2026-09-25 (not reference-confirmed); nothing navigates to this route anymore** |
| `SUPER_USER_SYSTEM_SETTINGS` | `super_user_system_settings` | `SuperUserSystemSettingsScreen` | Old App Guardian/Auto-start/Legacy Diagnostics/Exit App screen | *(was SUPER_USER_HOME's old flat list)* | ADMIN | - | **ORPHANED - its content was ported into `SuperUserSystemSettingsPane` (inline in `SuperUserHomeScreen`); nothing navigates to this route anymore** |
| `INSTALLATION_HOME` | `installation_home` | `InstallationHomeScreen` | Commissioning menu | *(was SUPER_USER_HOME, "Robot Installation")* | ADMIN | - | **ORPHANED - visible sidebar entry removed 2026-09-25 (confirmed not reference-present); nothing navigates to this route anymore** |
| `INSTALLATION_ELEVATOR` | `installation_elevator` | `InstallationElevatorScreen` | - | INSTALLATION_HOME | ADMIN | - | **ORPHANED - parent INSTALLATION_HOME is now unreachable** |
| `INSTALLATION_SCHEDULING_PATH` | `installation_scheduling_path` | `InstallationSchedulingPathScreen` | - | INSTALLATION_HOME | ADMIN | - | **ORPHANED - parent INSTALLATION_HOME is now unreachable** |
| `INSTALLATION_REMOTE_CONTROL` | `installation_remote_control` | `InstallationRemoteControlScreen` | - | INSTALLATION_HOME | ADMIN | - | **ORPHANED - parent INSTALLATION_HOME is now unreachable** |
| `INSTALLATION_ADVANCED` | `installation_advanced` | `InstallationAdvancedScreen` | - | INSTALLATION_HOME | ADMIN | - | **ORPHANED - parent INSTALLATION_HOME is now unreachable** |
| `MAPS_LIST` | `maps_list` | `MapsListScreen` | Map list | *(was SUPER_USER_HOME, "Maps")* | ADMIN | - | **ORPHANED - visible sidebar entry removed 2026-09-25 (confirmed not reference-present); nothing navigates to this route anymore** |
| `MAP_DETAIL` | `map_detail/{mapId}` | `MapDetailScreen` | Map detail/zone editor | MAPS_LIST | ADMIN | - | **ORPHANED - parent MAPS_LIST is now unreachable** |

**Not a route** but worth recording: the Legacy Diagnostics Dashboard (`com.sakarrobotics.c40agent.ui.MainActivity`, in the `:ui` module) is opened via a plain Android `Intent`, not the Compose `NavHost`, from `SuperUserSystemSettingsPane`'s "Open Legacy Diagnostics" button. It is a separate Activity, not a duplicate of any Compose route.
