# C40 UI Navigation Flow

Navigation graph for the Keenon C40 cleaning HMI (`peanut-clean`) and the mapping tool.
Derived from `peanut-clean/assets/therouter/routeMap.json` (38 routes, **C-S**) and `../workers/peanut_clean_app.md` §3 (activity nav graph). Transitions are **STRONGLY_SUPPORTED** from route/purpose; exact triggers are **INFERRED** (H5 minified).

## peanut-clean route graph

```mermaid
flowchart TD
  SETUP["setup · SetupActivity<br/>(LAUNCHER: init, self-check, map sync)"] --> HOME["home · MainActivity<br/>(H5 dashboard + map)"]

  HOME -->|start real-time clean| RT["immediately · RealTimeTaskActivity"]
  HOME -->|scheduled clean| TIMER["timer · TimerTaskActivity"]
  HOME -->|choose zones| AREA["select_area · SelectAreaActivity"]
  HOME -->|cleaning in progress| WORK["work · WorkActivity"]
  HOME -->|charging| CHARGE["charge · ChargeActivity"]
  HOME -->|long-press / admin| PIN["password · PassWordActivity (PIN)"]
  HOME -->|alerts| PUSH["push · PushActivity (faults)"]

  RT --> AREA
  RT -->|configure| MODE["mode fragments<br/>101–105 + params"]
  AREA --> WORK
  MODE --> WORK
  TIMER --> MODE

  WORK -->|pause| PIN
  WORK -->|done / low power| CHARGE

  PIN -->|user ok| SET["setting · SettingActivity"]
  PIN -->|admin ok| ADMIN["admin · AdminSettingActivity"]

  SET --> CONS["ConsumptiveStatistic"]
  SET --> AUDIO["Audio (volume/mute)"]
  SET --> DND["DoNotDisturb"]
  SET --> CHSET["ChargeSet"]
  SET --> EXPR["Expression (face)"]
  SET --> WATER["water_collect_settings"]
  SET --> CRUISE["cruise_config"]
  CRUISE --> CVL["cruise_voice_list"]

  ADMIN --> RINFO["RobotInfo / hw_version"]
  ADMIN --> REGION["region_domain"]
  ADMIN --> FAULT["FaultEvent"]
  ADMIN --> MAINT["maintenance · MaintenanceActivity"]
  ADMIN --> DEBUG["debug / new/debug / recharge_debug /<br/>navi_debug / washing_test_c40 / ros_net_connection_test"]

  HOME --> LOC["location / relocation / relocation_choose /<br/>retrieve_location / vslam_location"]
  HOME --> TEACH["teach_path → teach_path_creation → teach_path_preview"]
  HOME --> HAND["hand_c40 / hand (manual drive)"]

  OVL["WindowStatusBar overlay<br/>(battery / clean state)"] -.persistent.- HOME
```

## Key flows (S-S)

- **Clean now:** home → (select_area ↔ mode) → work → [pause→PIN] → charge-on-complete.
- **Scheduled clean:** home → timer → mode → saved to `CLEAN_STRATEGY`; fires later → work.
- **Auth gate:** any privileged action → password (PIN) → setting (user PIN) or admin (admin PIN). PIN-only; no account login.
- **Recovery:** home → location/relocation to re-establish pose before cleaning.
- **Maintenance:** PIN(admin) → admin → maintenance/debug (service-only screens).

## Mapping tool (installation-assistant) flow

```mermaid
flowchart LR
  LAUNCH["SimpleChromeActivity (Chromium)"] --> SPA["Vue SPA #/install"]
  SPA --> SCAN["scan (build map)"]
  SCAN --> REVISE["revise / mapEdit"]
  REVISE --> TARGET["target (points)"]
  TARGET --> SCENE["scene config"]
  SCENE --> DANGER["danger (forbidden zones)"]
  DANGER --> DISPATCH["dispatch / gate (elevator)"]
  DISPATCH --> MAPSYNC["mapSync"]
  SPA -.remote QR.-> PHONE["phone (remote=true via :8888 relay)"]
```
Transport: SPA → WS `:8888` `{requestType}` → RobotManager → PeanutSDK; or rosbridge `ws://192.168.64.20:9090/9091`.

## Implications for the Sakar web UI

The Sakar app is **not** a tablet-on-robot HMI; it is a cloud console talking to the Sakar backend. The useful transferable structure is the **task/flow hierarchy** (home → configure (mode+area) → run → monitor → charge) and the **auth/role split** (operator vs admin), reproduced with Sakar's permission system — not the PIN gate. See `SAKAR_UI_IMPLEMENTATION_REPORT.md`.
