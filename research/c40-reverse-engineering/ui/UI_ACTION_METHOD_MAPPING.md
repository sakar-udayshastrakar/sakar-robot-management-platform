# C40 UI Action → Method → Transport Mapping

How a user action in the C40 apps reaches the robot. **Source:** `../workers/peanut_clean_app.md` §7–8, `../workers/installation_assistant_app.md` §9, `../reports/SDK_API_CATALOG.md`, `../workers/peanut_clean_app_ros_refs.json` (kind `sdk_method_to_coap`).

**Chain model (peanut-clean):**
`UI (H5 click) / MQTT / timer → TaskFlowManager → C40CleanTaskController → IRosTask → ConsoleCleanApiRemote/ConsoleRobotRemote → PeanutSDK/RobotApiCompat → coapapi.impl.*ApiImpl (@CoapCommond) → CoAP coap://192.168.64.20:5683`

- **SDK-method → CoAP-path hop: CONFIRMED_STATIC** (from `@CoapCommond` annotations).
- **UI-click → task-start hop: INFERRED** (H5 minified; only 57 of ~16,081 Java methods decompiled).
- **Physical effect of any command: UNVERIFIED_RUNTIME** (no robot was ever contacted).
- **All transports have no auth and no TLS** (CONFIRMED_STATIC).

> **Browser reachability:** the robot command bus is **CoAP/UDP + serial** — a browser cannot speak it. A faithful on-robot web UI would use peanut-clean's local REST `:8089` `/api/v1/*` (the H5 app's real backend, HTTP+JSON, no auth) or the mapping-tool WS `:8888` / rosbridge `9090/9091`. The **Sakar cloud app uses none of these** — it calls the Sakar backend, which bridges to the vendor. See the capability note at the end.

## peanut-clean action table

| Action | UI origin | SDK method (exact) | CoAP path (C-S) | Confidence |
|---|---|---|---|---|
| Start clean | RealTime/Timer/Work; MQTT `CLEAN_BEGIN*_WEB` | `ConsoleCleanApiRemote.startClean` → `CleanComponent.cleanStartV2(id[,bool],cb)` | `/clean/dstV2` (legacy `/clean/dst`) | SDK→CoAP C-S; UI hop INF |
| Pause / resume / stop / skip-area | Work controls; MQTT `CLEAN_PAUSE/CONTINUE/FINISH/SKIP_TASK_AREA_WEB` | `clean().cleanAction(int action, cb)` | `/clean/action` | S-S |
| Apply mode / params (speed, suction, fan, water) | mode fragments | `clean().cleanConfig(CleanConfigParam,cb)`; `postCleanParamsConfig(CleanParamConfig,cb)` | `/clean/config`; `/clean/params_config` | path C-S; mode map S-S |
| Set water mode (wet/dry/suction) | mode select | `configWaterClean(0\|1\|3)` (in `CleanTask.sendRosWaterModeParam`) | folded into `/clean/config` | S-S (101/201/301→1, 103/303→0, 102/202/302→3) |
| Do-not-disturb | DoNotDisturb fragment | `clean().postNoDisturbMode(bool,cb)` | `/clean/noDisturbMode` | C-S path |
| Auto recharge / return to dock | low-power / task-end / Charge / MQTT `ROBOT_RECHARGE_WEB` | `ConsoleCleanApiRemote.autoCharge` → `battery().autoCharge(int pile,cb)` | `/charge/auto` | C-S / HIGH |
| Stop charge | Charge screen | `battery().stopCharge(cb)` | `/charge/stop` | C-S |
| Manual / default charge | ChargeSet | `battery().manualCharge` / `chargeDefault(int)` | `/charge/manu` / `/charge/default` | C-S path |
| Charge/battery state (observe) | status | `battery().getStatus`; ROS `/charge_state_fromSTM32` | `/charge/status` (observe) | C-S |
| Self-clean (station) | SelfCleanTask / cloud | `clean().postTaskSelfClean(cb,int)` | `/clean/taskSelfClean` (status `/clean/taskSelfCleanStatus`) | C-S / HIGH |
| Teach / personal path | TeachPath* | `cleanPersonPathAction(PersonalPathParam,cb)`, `onSavePersonalPath`, `cleanMotionRecordAction` | `/clean/personalPath`, `/map/savePersonalPath`, `/clean/motionRecord` | S-S; actuation INF |
| Re-localize / set pose | Location screens | `cleanReLocationPosition(cb,int,int)`; SDK `/localization/set_pose` | `/clean/relocationPosition`; ROS `initialpose` | S-S |
| Navigation target / motion | shared w/ nav | `navigation().setTarget/stop/pause/resume/reset/setSpeed` | `/navigation/{dst,action,speed,…}` | S-S |
| Reboot / restart ROS | Admin/Debug; MQTT `ROBOT_RESTART_WEB` | `device().reboot(+algBoard/service)` | `/devices/reboot` | C-S path |
| Camera calibration | Debug/maintenance | `clean().cameraCalibration(int,cb)` / `…Status` | `/clean/cameraCalibration` / `…Status` | C-S path |
| Actuator serial (fan/brush/pump/lamp) | Debug/hand screens | KC01 serial (`@SerialCommand`) | serial `/dev/ttyS1` hex: fan `A6`, roller brush `A5`, side brush `A8`, dust push `AB`, lamp `AA` | S-S |

**Status/telemetry the UI observes (CoAP observe, C-S):** `/clean/status`, `/clean/deviceStatus`, `/clean/positionStatus`, `/clean/motionReport`, `/charge/status`, `/navigation/status`, `/position/realtimePositionStatus`, `/schedule/*`, `/runtime/rostime`, `/rk/ip` → delivered to UI via EventBus (`CleanInfoEvent`, `NavigationInfoEvent`, `WaterTankStateEvent`, `LowPowerProtectEvent`).

**On-tablet local REST (`:8089`, no auth, CORS `*` — finding PCL-048):** 115 `@Request` handlers under `/api/v1/{clean,cleanConfig,map,robot,robotStatistic,usb,sysDict,soft}`; the H5 SPA calls these via XHR. Confirmed: `/api/v1/robotStatistic/{getConsumptiveStatistic,resetConsumptive}`, `/api/v1/usb/{getAntiDrop,getEmotion,getEmotionC40,getLightStrip,getLockTime}`, `/api/v1/soft/getByVersion`.

## installation-assistant action table (S-S)

SPA → WS `:8888` `{requestType}` → `WebSocketHelp.onHandleMessage` → managers → PeanutSDK → CoAP/serial; or SPA → rosbridge relay → ROS topics.

| Action | requestType | → SDK | CoAP / ROS |
|---|---|---|---|
| Auto charge | `operationAutoCharger` | `battery().autoCharge` | `/charge/auto`; ROS `/app_cmd_charge` |
| Stop charge | `operationStopCharger` | `BatteryComponent.stopCharge` | `/charge/stop` |
| Go to target | `operationNavigationDst` | `navigation().setTarget` | `/navigation/dst`; ROS `/app_cmd_clean_dst` |
| Motion control | `operationNavigationAction` | `NavigationComponent.stop/reset/pause/resume` | `/navigation/action` |
| State push | `subscribe NavigationState\|ChargerState\|RuntimeCode` | `PeanutSDK.subscribe(...)` | observers |
| Manual drive / motors | SPA rosbridge | — | ROS `/wheel_vel`, `/cmd_vel`, `/motor_*` |
| Volume get/set | `getAudioVolume`/`setAudioVolume` | `AudioVolumeManager` | — |
| Env / language switch | `operationEnvironmentConfiguration` | `AppHandleHelp.onChangeEnvironment` | — |

## Cloud / MQTT command verbs (C-S)
Handled by `CleanServerServiceImpl.cleanRobotCommandHandle` (`RobotCmdEnum`): `CLEAN_BEGIN/PAUSE/CONTINUE/FINISH/SKIP_TASK_AREA_WEB`, `ROBOT_RECHARGE_WEB`, `CLEAN_MODEL_WEB`, `CLEAN_STRATEGY_*_WEB`, `MAP_*`, `ROBOT_RESTART_WEB`, `SCREEN_UNLOCK`. These are the exact remote verbs the vendor cloud already uses.

## What this means for the Sakar build (capability-bounded)
The Sakar web app calls the **Sakar Cloud Backend only**. The backend's `KeenonRobotAdapter` supports exactly: `GET_STATUS, GET_BATTERY, GET_TELEMETRY, GET_AREAS, GET_MAP, START_TASK, STOP_TASK, PAUSE_TASK, RETURN_TO_DOCK, CLEANING` with cleaning modes **SWEEP_MOP=101, WATER_SUCTION=102, SWEEP_VACUUM=103, SWEEP_PUSH=104, SWEEP=105** — which exactly match the SDK's C40 mode codes above. **Actions outside that set (manual drive, actuator serial, teach-path, calibration, water-mode, do-not-disturb) have no Sakar backend path and must be shown as `unavailable`, not faked.**
