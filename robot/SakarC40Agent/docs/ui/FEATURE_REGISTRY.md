# Feature Registry

**Before creating or modifying any UI feature, check here first.** If the feature already
exists, reuse its route/ViewModel/repository/component or navigate to it - do not build a second
implementation. See `ui_preflight` and `ui_duplicate_check` (sakar-robot-dev-mcp) for a
tool-assisted version of this check.

Machine-readable mirror: [`FEATURE_REGISTRY.json`](FEATURE_REGISTRY.json) - the JSON is the
source of truth `ui_preflight`/`ui_duplicate_check` actually search; this file is the
human-readable index of the same data plus the rules for maintaining it.

## Fields

| Field | Meaning |
|---|---|
| `feature_id` | Stable snake_case identifier. Never reused for a different feature, even after removal. |
| `display_name` | What a person calls this feature. |
| `category` | `OPERATIONS` (Home/Manual Drive/Cleaning/Scheduling/Navigation), `ADMINISTRATION` (Super User), or `ENGINEERING` (simulation, logs, SDK diagnostics, dev tools). |
| `owner_screen` | The ONE composable that owns this feature's implementation. |
| `owner_route` | The route constant from `Routes.kt` that reaches the owner screen (or "inline" if it's a pane with no dedicated route, e.g. inside `SuperUserHomeScreen`). |
| `access_level` | `OPERATOR`, `ADMIN`, or both (when one owner screen is reachable from both, e.g. Robot Information). |
| `entry_points` | Every real navigation path that reaches this feature. More than one entry point is fine **as long as they all reach the same owner** - that is not duplication. |
| `viewmodel` | The ViewModel class, or "none" for local-state-only screens. |
| `repository_or_usecase` | The domain repository/use case backing it, or "NONE" if it's UI-only. |
| `hardware_backend` | What actually executes: a named SDK component, `SIMULATED`, `LOCAL`, or `UNAVAILABLE` with the reason. |
| `capability_state` | One of the app's own `Capability` enum values (`REAL`/`SIMULATED`/`LOCAL`/`GATED`/`UNAVAILABLE`), or a mix when a screen has per-row capability. |
| `reference_source` | Which vendor reference screenshot (if any) this was built against. "NOT present in vendor reference" is itself meaningful - it flags an engineering-only addition. |
| `duplicate_of` | `NONE`, or the `feature_id` this duplicates/supersedes/is superseded by. |
| `status` | `LIVE`, `ORPHANED` (still compiles, unreachable - not auto-deleted), or a note. |

## Summary (see FEATURE_REGISTRY.json for full detail)

| feature_id | category | owner_screen | owner_route | status |
|---|---|---|---|---|
| `home_dashboard` | OPERATIONS | HomeScreen | HOME | LIVE |
| `manual_cleaning_mode_selector` | OPERATIONS | ManualDriveScreen | MANUAL_DRIVE | LIVE |
| `manual_drive_motion_control` | OPERATIONS | ManualDriveScreen (dialog) | MANUAL_DRIVE | LIVE |
| `manual_drive_simulation_entry` | ENGINEERING | ManualDriveScreen | MANUAL_DRIVE | **REMOVED** (visible Super User sidebar entry removed 2026-09-25; Home -> Manual Drive is the only path now) |
| `start_cleaning_session` | OPERATIONS | StartCleaningScreen | START_CLEANING | LIVE |
| `scheduled_cleaning` | OPERATIONS | ScheduleListScreen | SCHEDULE_LIST | LIVE |
| `teach_route` | OPERATIONS | TeachRouteScreen | TEACH_ROUTE_LIST | LIVE |
| `operator_settings_root` | OPERATIONS | SettingsRootScreen | SETTINGS_ROOT | LIVE |
| `network_wifi_status` | SHARED | NetworkScreen / SuperUserNetworkPane | SETTINGS_NETWORK | LIVE |
| `general_local_preferences` | SHARED | GeneralSettingsScreen / SuperUserGeneralSettingsPane | SETTINGS_GENERAL | LIVE |
| `resource_management_business_profile` | SHARED | ResourceManagementScreen / SuperUserResourceManagementPane | SETTINGS_RESOURCE_MGMT | LIVE |
| `super_user_authentication` | ADMINISTRATION | SuperUserLoginScreen | SUPER_USER_LOGIN | LIVE (single entry point: long-press logo; visible "Super User →" button removed 2026-09-25) |
| `super_user_home_shell` | ADMINISTRATION | SuperUserHomeScreen | SUPER_USER_HOME | LIVE |
| `robot_hardware_debug_dashboard` | ADMINISTRATION | RobotHardwareDebugScreen | SUPER_USER_DEBUG_GROUPS | LIVE |
| `robot_actuator_test_groups_legacy` | ADMINISTRATION | RobotDebuggingGroupsScreen | (was SUPER_USER_DEBUG_GROUPS) | **ORPHANED** |
| `super_user_system_settings` | ADMINISTRATION | SuperUserSystemSettingsPane | SUPER_USER_HOME (inline) | LIVE |
| `super_user_system_settings_legacy` | ADMINISTRATION | SuperUserSystemSettingsScreen | SUPER_USER_SYSTEM_SETTINGS | **ORPHANED** |
| `logs_viewer` | ADMINISTRATION | LogsScreen | SUPER_USER_LOGS | **ORPHANED** (visible Super User sidebar entry removed 2026-09-25) |
| `robot_information` | SHARED | RobotInfoScreen | SETTINGS_ROBOT_INFO | LIVE (Super User entry point removed 2026-09-25; reachable only via Home -> Settings) |
| `robot_installation_commissioning` | ADMINISTRATION | InstallationHomeScreen | INSTALLATION_HOME | **ORPHANED** (visible Super User sidebar entry removed 2026-09-25; confirmed not reference-present) |
| `maps_management` | ADMINISTRATION | MapsListScreen | MAPS_LIST | **ORPHANED** (visible Super User sidebar entry removed 2026-09-25; confirmed not reference-present) |
| `legacy_diagnostics_dashboard` | ENGINEERING | ui.MainActivity | n/a (Intent) | LIVE |

## Maintenance rule

Whenever a screen, route, ViewModel, or repository is added or removed, update
`FEATURE_REGISTRY.json` (and this summary table) **in the same change**, and add a row to
[`UI_CHANGE_LEDGER.md`](UI_CHANGE_LEDGER.md). A feature registry that lags the code is worse than
no registry - it gives false confidence.
