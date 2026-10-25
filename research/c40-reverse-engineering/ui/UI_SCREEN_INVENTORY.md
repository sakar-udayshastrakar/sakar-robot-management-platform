# C40 UI Screen Inventory

Static reverse-engineering of the Keenon C40 (`S-LS-M014C00-RW-F00-V246`) tablet apps.
**Source of truth:** `../workers/peanut_clean_app.md`, `../workers/installation_assistant_app.md`, `../workers/system_apps.md`, the extracted `peanut-clean/assets/therouter/routeMap.json`, and app manifests.

> **Fidelity caveat (read first).** The cleaning HMI (`peanut-clean`, `com.keenon.peanut.clean` v3.7.8) renders its screens as a **Vue + OpenLayers H5 single-page app** served locally (`http://localhost:8090`) inside `MainActivity`'s `BridgeWebView`. The mapping tool (`installation-assistant`, `com.keenon.peanut.peanutservice` v4.11.0) is a **Vue 3 + Element-Plus SPA** in an embedded Chromium. **Activity/route names and purposes are CONFIRMED_STATIC** (routeMap.json, manifest, DEX supertypes). **Exact widgets, labels, and layout are UNKNOWN** — the H5/SPA JS is minified and 446 layout XMLs were not decoded. Every "UI components / buttons" entry below is **INFERRED from screen purpose + available SDK actions** unless marked otherwise. Nothing is runtime-verified.

Confidence legend: **C-S** = CONFIRMED_STATIC · **S-S** = STRONGLY_SUPPORTED · **INF** = INFERRED · **UNK** = UNKNOWN.

## 1. peanut-clean — cleaning HMI (primary UI, 38 TheRouter routes)

Route prefix `/solution_clean/`. Chain for all robot actions: H5/native → `TaskFlowManager` → `C40CleanTaskController` → `PeanutSDK` → CoAP `192.168.64.20:5683` (see `UI_ACTION_METHOD_MAPPING.md`).

| Functional screen | Route · Activity | Purpose | Components / buttons (INF) | Navigates to | SDK / API (C-S paths) | Confidence |
|---|---|---|---|---|---|---|
| Boot / launcher | `setup` · SetupActivity (LAUNCHER) | Init, permission self-check, map sync, starts DaemonService | splash, self-check dialogs | → home | — | name C-S; flow S-S |
| Home / dashboard | `home` · MainActivity (hosts BridgeWebView) | Home + embedded H5 map; entry to all tasks | map canvas, task buttons, status bar overlay; long-press → PIN | → work, immediately, timer, select_area, setting, charge | status observes `/clean/status`, `/rk/ip` | C-S |
| Cleaning operation | `work` · WorkActivity | Live cleaning progress | progress %, pause (→PIN), stop | → password, home | observe `/clean/status`,`/clean/motionReport`; `/clean/action` | name C-S; widgets INF |
| Real-time clean | `immediately` · RealTimeTaskActivity | On-the-spot clean setup | zone pick, mode pick, start | → work | `/clean/dstV2`, `/clean/config` | S-S |
| Area selection | `select_area` · SelectAreaActivity | Choose zones/areas to clean | map zones, multi-select, confirm | → immediately/work | `MAP_AREA` zones; areas via local REST | C-S (name) |
| Task config | `task_config_modify` · TaskConfigModifyActivity | Edit a task's mode/params | mode, params, areas | back | `/clean/config`,`/clean/params_config` | C-S (name) |
| Scheduling | `timer` · TimerTaskActivity (+11 fragments) | Scheduled/timer cleaning | schedule list, add/edit, repeat | back | `CLEAN_STRATEGY` table; `/schedule/*` | C-S |
| Cleaning mode selection | mode fragments (driven by `assets/slamConfig/cleanConfigSetting.json`) | Pick mode/params | mode cards 101–105, sliders (speed/suction/fan/water) | — | modes 101–105 (C40) → `/clean/config` | S-S |
| Charging | `charge` · ChargeActivity (`charge/c55` · ChargeActivityC55) | Recharge control | charge/stop buttons, battery % | back | `/charge/auto`, `/charge/stop`, `/charge/status` | C-S paths |
| Water settings | `water_collect_settings` · WaterCollectSettingsActivity | Water collection / self-clean water | config inputs | back | transport NOT_ANALYZED | C-S name; UNK transport |
| Consumables status | ConsumptiveStatistic fragment | Brush/HEPA/wash-brush remaining-time | consumable rows, reset | — | local REST `/api/v1/robotStatistic/{getConsumptiveStatistic,resetConsumptive}` | C-S |
| Maintenance | `maintenance` · MaintenanceActivity | Maintenance menu | menu list | → debug screens | — | C-S name |
| Service / debug | `debug`,`new/debug`,`recharge_debug`,`navi_debug`,`washing_test_c40`,`ros_net_connection_test` | Calibration, actuator/wash tests, ROS net test | test controls | — | actuator serial, `/clean/cameraCalibration` | C-S names (dev-only) |
| Teach path | `teach_path` → `teach_path_creation` → `teach_path_preview` · TeachPath*Activity | Record personal/teach path | record/save/preview | chained | `/clean/personalPath`,`/map/savePersonalPath` | S-S; actuation INF |
| Manual drive | `hand_c40` · HandActivityC40 (`hand` · HandActivity) | Hand-push cleaning (C40) | brush/light toggles, drive | back | PeanutManager/serial (chain INF) | C-S name; chain INF |
| Localization | `location`,`relocation`,`relocation_choose`,`retrieve_location`,`vslam_location` · Location/ReLocation/PositionChoose Activities | Set / recover robot pose | pose pick on map, confirm | back | `/clean/relocationPosition`; ROS `initialpose` | S-S |
| Cruise config | `cruise_config` · CruiseConfigActivity | Configure cruise/patrol | cruise params | → cruise_voice_list | `/navigation/setCruise` | C-S name |
| Cruise voice list | `cruise_voice_list` · CruiseVoiceListActivity | Select cruise announcement clip | voice clip list (from `assets/voice/`) | back | SoundPlayer (bundled mp3) | C-S |
| Settings hub (user) | `setting` · SettingActivity | User settings; fragments: ConsumptiveStatistic, DataNote, WorkStation, ChargeSet, PasswordSetting, General, Expression, **Audio**, DoNotDisturb, SceneSetting, AboutMe, MachineAdjust | tabbed fragments | sub-fragments | per-fragment | fragment names C-S |
| Admin settings | `admin` · AdminSettingActivity | Admin hub: RobotInfo, System, CommonSet, ElevatorApproach, feature switches, BarCode, Region/Domain, HwVersion, FaultEvent | tabbed | sub-screens | requires admin PIN | names C-S |
| Region / domain | `region_domain` · RegionDomainActivity | Server region / domain switch | region picker | back | `DomainManager` | C-S name |
| HW version / robot info | `hw_version` · HwVersionActivity; RobotInfo/AboutMe fragments | Hardware & software info | read-only fields | — | read-only | C-S name |
| PIN auth | `password` · PassWordActivity | PIN gate (user + admin) | numeric keypad | gated screen | `isPasswordCorrect` vs MMKV `app_in_code`/`system_in_code` | C-S |
| Push / alerts | `push` · PushActivity | Alerts / warnings / fault messages | message list | → fault detail | shows faults (`res/raw/fault.json`, 144 faults) | C-S name |
| Do-not-disturb | DoNotDisturb fragment | Quiet mode | toggle, schedule | — | `/clean/noDisturbMode` | C-S path |
| Expression / face | Expression fragment | Emotion/face display | emotion select | — | emotion board `/dev/ttyS2`; `character/2_UNICODE.bin` | C-S |
| Audio settings | **Audio fragment** (Setting) | Volume / audio | volume slider, mute | — | `MODIFY_AUDIO_SETTINGS`; AudioVolume (see voice doc) | C-S name; controls INF |
| Status bar overlay | WindowStatusBar (SYSTEM_ALERT_WINDOW) | Persistent status bar window | battery/clean state | n/a | observes status | C-S (pref `IF_SHOW_STATUS_BAR`) |
| Dialogs / loading | ~106 `ui.dialog` classes (OfflineModePinDialog, self-check, CrashReportDialog/ACRA) | Modals, loading, self-check | — | — | — | C-S (count) |

**Functional-coverage notes (C-S):** there is **no standalone "robot status dashboard"** (status = WorkActivity + overlay bar); **no network-config wizard** (WiFi is a component surfaced via settings); **no multi-user login** — auth is **local PIN only** (user vs admin) + offline work code; cloud identity is robot SN + RSA keypair + token (headless).

## 2. installation-assistant — "Keenon Mapping Tool" (commissioning UI)

One native screen `SimpleChromeActivity` (LAUNCHER, full-screen Chromium, landscape; deep link `mapkeenon://mapandroid.com`) hosting a Vue 3 + Element-Plus SPA. Entry `http://<ip|192.168.64.10|localhost>:8080/#/install`. **SPA sections = i18n namespaces (C-S, 33 in en):** scan, revise (map edit), scene, target (points), setting, map, mapEdit, danger (forbidden zones), dispatch, gate (elevator), busyArea, hotel, labelAndVSlam, closePoint, vslam, clean, rosDispatch, pointsModify, phone, mapSync, largeMap, robotList, welcomeMapping, bindCharge, offlinePage, lockPage. Button→action is INFERRED; action transport is WS `:8888` `{requestType}` or rosbridge `9090/9091` (see mapping doc).

## 3. Mapping to the requested Sakar screen set

| Requested screen | C40 source | Recoverable? |
|---|---|---|
| Main/home | MainActivity + H5 | structure yes, pixels no |
| Robot status | (no standalone; WorkActivity + overlay) | inferred |
| Cleaning operation | WorkActivity | structure yes |
| Map / multi-floor | MainActivity H5 + mapping-tool SPA | structure yes |
| Cleaning mode / area | mode fragments / SelectAreaActivity | modes C-S (101–105) |
| Scheduling | TimerTaskActivity | structure yes |
| Battery / charging | ChargeActivity | C-S paths |
| Water / consumables | WaterCollectSettings / ConsumptiveStatistic | C-S (consumables via local REST) |
| Error / warning | PushActivity + fault.json | C-S |
| Maintenance / service | MaintenanceActivity + debug | names C-S |
| Settings / network / robot info | SettingActivity / AdminSettingActivity | names C-S |
| Login/auth | PassWordActivity (PIN only) | C-S |
| Dialogs / loading | ui.dialog (~106) | C-S count |
| Voice | Audio fragment + cruise_voice_list + ServiceSystem TTS | see VOICE_MODULE_ANALYSIS.md |
