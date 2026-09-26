# Keenon Application Separation Map

**Phase 3 audit. Read-only evidence gathering — no application code, registries, or reference
screenshots were modified to produce this document.**

## Evidence base and its location

Real, decompiled Keenon APK evidence for this audit exists **outside this project's own directory
tree**, at:

```
C:\Users\sakar\Downloads\APK-Reverse-Engineering-Suite-main 2\APK-Reverse-Engineering-Suite-main\
  APK-Reverse-Engineering-Suite-main\output\C40_S_LS_M014C00_RW_F00_V246\
    peanut-clean-v3.7.8\
    robot-installation-assistant-v4.11.0\
    ServiceSystem-v2.0.11\
    krlog-v1.2.6\
    RemoteControl-v3.3.1\
```

Each app directory contains `apktool/` (raw `AndroidManifest.xml` + smali), `jadx/` (decompiled
Java source), and generated `reports/*.md` (Manifest, APIs, Libraries, Security, etc.). Every
factual claim below is sourced from these files — the raw manifest, decompiled source, or a
generated report — not from the reference-screenshot folder names or filenames. Nothing under
`D:\Sakar Robotics Projects\` contains decompiled Keenon *application* source; the only
Keenon-derived material inside the project is the licensed `peanut-sdk-release.aar` (a compiled
SDK library, inspected via `javap`, not a decompiled app) already in use by `SakarC40Agent`.

Where a claim is inferred rather than directly stated in the manifest/source (e.g. matching a
screenshot to a specific Activity), that is marked **[INFERRED]** with the reasoning given. Facts
taken directly from a manifest, decompiled class, or generated report are unmarked.

---

## 1. Cleaning / Operator application — `com.keenon.peanut.clean`

- **APK / package name:** `com.keenon.peanut.clean`
- **Version:** `v3.7.8-0-g153214a` (versionCode 24); other captured builds exist at v3.7.4/v3.7.5/v3.7.6
- **App display name (manifest metadata):** 擎朗机器人 ("Keenon Robot")
- **Launcher entry point:** `com.keenon.module.main.ui.setup.SetupActivity` (`singleTask`,
  landscape-locked) — confirmed via the manifest's `MAIN`/`LAUNCHER` intent-filter
- **Purpose:** the primary operator-facing cleaning application — home dashboard, cleaning
  execution, scheduling, teaching, manual drive/push, operator settings, **and** the
  privileged Super User / hardware-debug surface. All of these live in **one APK**, not
  separate ones.
- **Reference screenshots belonging to it (all groups from `REFERENCE_SCREEN_INVENTORY.md`
  except Robot Installation/Maps, Business Settings, Elevator/Remote Control/Scheduling Path,
  Keenon Service, krlog, and Remote Assistant):** Home (`HOME-01`, `HOME-02`), Cleaning
  (`CLEAN-01`), Manual Drive/Manual Push (`MANUAL-01`, `MANUAL-02`), Scheduled Cleaning
  (`SCHED-01`, `SCHED-02`), Teaching Mode (`TEACH-01`, `TEACH-02`), Settings (`SET-01`..`SET-15`),
  Super User (`SU-01`..`SU-05`), Robot Debugging (`DEBUG-01`..`DEBUG-10`).
- **Major screens (Activities confirmed present in the manifest, 48 total; selected ones matched
  to reference screenshots):**
  | Activity (real class name) | Reference screenshot | Match basis |
  |---|---|---|
  | `ui.activity.main.MainActivity` | `HOME-01` | **[INFERRED]** only plausible Home/dashboard-named activity |
  | `ui.location.LocationActivity` / `ReLocationActivity` / `RetrieveLocationActivity` / `PositionChooseActivity` | `HOME-02` (Recover Positioning) | **[INFERRED]** name family matches "recover positioning" domain exactly |
  | `ui.activity.push.PushActivity` | `MANUAL-02` (Manual Push) | **[INFERRED]** direct name match to "Push" |
  | `ui.activity.hand.HandActivity` | `MANUAL-01` (Manual Drive mode/intensity picker) | **[INFERRED]** "Hand"-control naming; no more specific "manual drive" activity exists |
  | `ui.activity.area.SelectAreaActivity` | `CLEAN-01` (Start Cleaning area picker) | **[INFERRED]** name matches area-selection map screen |
  | `ui.activity.timer.TimerTaskActivity` | `SCHED-01` | **[INFERRED]** "Timer Task" = scheduled task list |
  | `ui.activity.config.TaskConfigModifyActivity` / `ui.activity.setting.TaskDetailActivity` / `TaskNoteActivity` | `SCHED-02` (Edit Task) | **[INFERRED]** task-edit naming family |
  | `ui.activity.teach.TeachPathActivity` | `TEACH-01` | direct name match ("Teach Path" = "Teach Route") |
  | `ui.activity.teach.TeachPathCreationActivity` / `TeachPathPreviewActivity` | `TEACH-02` (operation-steps dialog) | **[INFERRED]** creation-flow naming |
  | `ui.activity.setting.SettingActivity` | `SET-01`..`SET-15` | **[INFERRED]** single settings Activity; the many sub-screens observed (Consumables Statistics, Cleaning Data, Workstation, Charging, Screen Lock, General, Screen Emoji, Sound, DND, Resource Management, About) are almost certainly internal Fragments of this one Activity, not separate Activities — no per-sub-screen Activity names exist in the manifest |
  | `ui.activity.setting.WaterCollectSettingsActivity` | part of `SET-06` (Workstation) | direct name match to workstation/water-related settings |
  | `ui.admin.PassWordActivity` (in `ui.password` package) | Super User PIN gate (not itself a captured reference image, but the gate in front of `SU-01`..`SU-05`) | direct name match |
  | `ui.admin.AdminSettingActivity` | `SU-01`..`SU-05` (Super User sidebar) | direct name match ("Admin Setting" = Super User) |
  | `ui.admin.FaultEventActivity`, `HwVersionActivity`, `RegionDomainActivity` | sub-screens reachable from Super User, not individually captured | direct name match to admin-domain features |
  | `ui.activity.debug.DebugActivity` / `NewDebugActivity` (C40 hardware) / `NewDebugC55Activity` (a **different robot model**, C55 — not applicable to this C40 audit) | `DEBUG-04`..`DEBUG-10` (main Debug dashboard) | **[INFERRED]** DebugActivity/NewDebugActivity family; exact which-one-is-current not determinable from the manifest alone |
  | `ui.activity.debug.RosNetConnectionTestActivity` | `DEBUG-01` (Industrial computer/ROS connection test) | direct name match |
  | `ui.activity.recharge.RechargeDebugActivity` | `DEBUG-02` (Return to charge stress test) | **[INFERRED]** "Recharge Debug" matches the recharge stress test |
  | `ui.activity.debug.C40WashingTestActivity` | `DEBUG-03` (Wash Pressure Test) | direct name match, and explicitly C40-specific (confirms this is the right model variant for our C40 audit) |
  | `ui.activity.cruise.CruiseConfigActivity` / `RouteConfigActivity` / `CruiseVoiceListActivity` | not individually captured in our reference set | **[INFERRED]** cruise/route configuration, adjacent to Teaching Mode |
  | `ui.activity.recharge.ChargeActivity` | not individually captured | direct name match, real (non-debug) recharge flow |
  | `ui.activity.work.WorkActivity` / `real_time.RealTimeTaskActivity` | not individually captured (likely the active-cleaning-in-progress screen) | **[INFERRED]** |
  | `ui.activity.maintenance.MaintenanceActivity`, `component.upgradeui.activity.*` | not captured | app/firmware update UI, out of scope for this audit |
- **Navigation entry points:** device launcher icon only (`SetupActivity`, `MAIN`/`LAUNCHER`); a
  second `mapkeenon://mapandroid.com` deep-link intent-filter exists but is registered on
  `robot-installation-assistant`'s `SimpleChromeActivity`, **not** on this app.
- **Services/components:** `HeartService` and `NetService` (own package,
  `module.android.service`) — heartbeat/network monitoring; `UpdateService`
  (`module.common.service`) — OTA; `RecordUploadService` / `AutoCheckUpgradeService`
  (`component.upgrade.service`); `com.amap.api.location.APSService` (bundled Amap/AutoNavi
  location SDK); `androidx.room.MultiInstanceInvalidationService` (Room DB, standard). Broadcast
  receivers: `AlarmReceiver`, `BootReceiver`, `InstallResultReceiver`, `ApkInstallReceiver` — the
  last two indicate this app can install/react to installation of other APKs on the device
  (consistent with the sibling-app list found in source, below).
- **SDK APIs used:** the robot SDK is bundled directly inside this APK under `com.keenon.sdk.*`
  (e.g. `com.keenon.sdk.coapapi.api.clean.CleanCurrentMotorSpeed`,
  `com.keenon.sdk.embedded.lightall.PeanutLightHalManage`) — the same SDK namespace as the
  `peanut-sdk-release.aar` already bundled in `SakarC40Agent`. Native libraries
  `libandroid_serial_port.so` and `libkeenon_serial.so` are present, confirming direct serial
  communication with the robot's onboard hardware from this process.
- **Internal (cross-app) APIs used:** bundles the AIDL stub
  `com.keenon.aidl.mqtt.IMqttAidlApi.aidl` and calls it — this is the interface exposed by
  `com.keenon.systemservice`'s exported `MqttApiService` (see application 3). Source file
  `com/keenon/component/core/utils/CoreUtil.java` maintains explicit lists of sibling package
  names it is aware of: `com.keenon.remote_control_oray`, `com.keenon.peanut.peanutservice`
  (both directly referenced by package-name string literal), plus two unrelated sibling
  product-line packages `com.keenon.peanut.solutionfooddelivery` and
  `com.keenon.peanut.solutionsaler` (other Keenon robot types — not part of this C40 cleaning
  audit, listed here only because their presence in the same source file confirms peanut-clean
  is architected as one member of a **family** of separate sibling apps, not a monolith).
- **Cloud APIs used:** REST base path `/api/cleanapp/iot/...` (`PeanutApi.java`) — e.g.
  `/api/cleanapp/iot/v3/clean/new/batchSaveForAndroidPad`, `/api/cleanapp/iot/v2/robot/change/map`,
  `/api/cleanapp/iot/v1/email`, `/api/app/marketing/material`; a separate elevator-integration API
  family `/api/elevator/v2/elevator/search/lift/configInfo` (`HttpElevatorApi.java`) — this is the
  real backing API for the "Elevator Communication Method" setting found in `SET-09`. A local
  (LAN, not internet) WebSocket to `ws://192.168.64.20:9090` and a local HTTP base
  `http://192.168.64.20:5555` are also present — almost certainly the on-robot embedded
  computer/ROS bridge, not a cloud endpoint. Crash/telemetry reporting via Sentry
  (`io.sentry.*`) and analytics via Umeng (`libumeng-spy.so`).
- **Shared dependencies:** Jetpack, RxJava/RxJava3, Kotlin, Room (with SQLCipher), Retrofit +
  OkHttp, Gson, Dagger, Glide/Picasso, EventBus, ButterKnife, Navigation, DataBinding/ViewBinding —
  a large, conventional native-Android stack. Nothing here is shared as a *library* with the other
  4 apps (each app bundles its own copies); the only real sharing is the **AIDL-mediated service
  call** to `com.keenon.systemservice`, which is process-level sharing, not library sharing.
- **Authentication/access model:** the app itself has no login; internally, Super User (`SU-*`,
  `DEBUG-*`) is gated behind `PassWordActivity`, a local PIN check with no evidence of a
  server-side authentication call in the APIs report (consistent with what `SakarC40Agent`
  already implements as a local SHA-256 check).
- **Communicates directly with the robot:** **Yes** — native serial libraries
  (`libandroid_serial_port.so`, `libkeenon_serial.so`) plus the embedded `com.keenon.sdk.*`
  classes confirm direct, in-process hardware communication.
- **Standalone Android package:** **Yes** — its own package ID, its own launcher entry, installed
  and versioned independently of the other 4 apps.

---

## 2. Robot Installation / Commissioning application — `com.keenon.peanut.peanutservice`

- **APK / package name:** `com.keenon.peanut.peanutservice`
- **Version:** `v4.11.0-0-gb7c7c7b` (versionCode 350)
- **App display name (manifest metadata):** "Robot Installation Assistant"
- **Launcher entry point:** `org.chromium.chrome.browser.keenon.SimpleChromeActivity` — a
  **customized Chromium browser shell**, confirmed by the manifest's `MAIN`/`LAUNCHER`
  intent-filter on that exact class, plus a second intent-filter handling
  `VIEW` on scheme `mapkeenon://mapandroid.com` (`BROWSABLE`+`DEFAULT` categories).
- **Purpose:** hosts a **bundled local web application** (found at
  `assets/ros/dist/index.html` inside the APK — a built single-page app, "ros" almost certainly
  short for the ROS/mapping tooling this Activity presents) inside a full Chromium engine, for
  robot installation/commissioning: map creation, elevator configuration, network/business setup,
  remote-control pairing.
  **This is the single most consequential finding of this audit: the "Robot Installation" and
  "Maps" reference screens are not native Android views. They are a web app rendered in an
  embedded browser**, delivered by a different, separate APK from the cleaning app.
- **Reference screenshots belonging to it:** Robot Installation/Maps
  (`INSTMAP-01`..`INSTMAP-11`), Business Settings (`BIZ-01`, `BIZ-02`), Elevator/Remote
  Control/Scheduling Path (`ELEV-01`..`ELEV-06`). **[INFERRED — see reasoning below, this is not
  taken from the screenshot folder name.]**
- **Reasoning for the above match (not filename-based):** `com.keenon.peanut.clean`'s full,
  manifest-confirmed 48-activity list (audited above) contains **no** activity resembling
  "Installation", "Elevator", "Business Settings", "Network Settings", or "Map deployment" by
  name or by any plausible synonym. `com.keenon.peanut.peanutservice`, by contrast, has its own
  real components purpose-built for exactly this domain: `com.keenon.peanut.peanutservice.app.SlamApplication`
  (SLAM = mapping), `service.WifiHotpotService` (matches a Wi-Fi/hotspot step in a commissioning
  flow), and the bundled `assets/ros/dist/` web bundle. The absence of a match in one app plus a
  strong component-level match in the other — not the screenshot folder's name — is the basis for
  this assignment. This is recorded as inferred, not confirmed, because the APK's web bundle was
  not executed/rendered to directly verify its screen contents against the reference images.
- **Major screens:** not separately confirmable as native Activities (they are routes within the
  bundled web SPA); the reference screenshots themselves stand in as the only evidence of screen
  content (Map deployment list, Set up sidebar with Basic Settings/Volume Setting/Network
  Settings/Business settings/Advanced setting, Elevator settings sub-flows, Remote control robot
  pairing, Scheduling path editor).
- **Navigation entry points:** device launcher icon (`SimpleChromeActivity`); external deep link
  `mapkeenon://mapandroid.com`, meaning another app (plausibly `peanut-clean`, given its
  `ApkInstallReceiver`/sibling-package awareness) could launch directly into this app's web UI.
- **Services/components:** `com.keenon.peanut.peanutservice.server.http.Service.AndService` — an
  in-process local HTTP server (near-certainly what serves the bundled web assets to the Chromium
  view); `service.UploadService`; `service.WifiHotpotService`; `receiver.APPReceiver`. The
  remaining 80+ services/activities in its manifest are Chromium/Play-Services/Cast-framework
  infrastructure (`org.chromium.chrome.*`, `com.google.android.gms.*`) that ship with the
  browser engine itself, not app-specific functionality.
- **SDK APIs used:** no direct evidence of `com.keenon.sdk.*` classes was found in this app's own
  package namespace in the time available for this audit (unlike `peanut-clean`, which embeds the
  SDK directly) — **[UNCERTAIN]**, flagged for a deeper pass rather than asserted either way.
- **Internal (cross-app) APIs used:** none confirmed pointing *out* of this app in the time
  available; it is plausible callers (like `peanut-clean`) reach it via the `mapkeenon://` deep
  link rather than AIDL.
- **Cloud APIs used:** not analyzed in this pass — flagged as an open item.
- **Shared dependencies:** the full Chromium/WebView engine (a very large dependency, unique to
  this app among the 5), Google Play Services (Cast framework, Sign-In), ARCore installer,
  `com.blankj.utilcode` (also seen in `robot-installation-assistant`'s manifest, likely a common
  small utility library rather than a sign of deep integration).
- **Authentication/access model:** not analyzed in this pass — open item.
- **Communicates directly with the robot:** **Uncertain** — no native serial libraries or
  `com.keenon.sdk.*` classes were confirmed in the time available; it may reach the robot only via
  its own local HTTP server (`AndService`) talking to the same on-robot network endpoints
  (`192.168.64.20:*`) that `peanut-clean` uses, but this was not directly verified.
- **Standalone Android package:** **Yes** — separate package ID, separate launcher, separate
  version line.

---

## 3. Keenon Service (shared backend) — `com.keenon.systemservice`

- **APK / package name:** `com.keenon.systemservice`
- **Version:** `v2.0.11-0-g94566e5` (versionCode 200100)
- **App display name (manifest metadata):** "Keenon Service"
- **Launcher entry point:** **none of its 9 activities carry a `MAIN`/`LAUNCHER` intent-filter**
  in the excerpts reviewed — this app is architected primarily as a background service provider,
  not something a user opens from the app drawer in normal operation. (The reference screenshot
  `KSVC-01`, titled "Keenon Service" with an SN/Version display, must therefore be reached via an
  explicit Intent from another app or a system/debug path, not the launcher — **[UNCERTAIN]**
  exactly which Activity renders it; `SystemStubActivity` is the best name-based candidate but was
  not confirmed against the screenshot's content.)
- **Purpose:** a shared background-services process other Keenon apps bind into for MQTT
  messaging, text-to-speech, voice recognition/wake-word, and BI/analytics — confirmed as
  genuinely shared because its `MqttApiService`, `TtsApiService`, and `VoiceApiService` are all
  declared `android:exported="true"` with **no custom permission requirement**, meaning any app
  on the device (including `peanut-clean`, which bundles the matching
  `IMqttAidlApi.aidl` client stub) can bind to them freely.
- **Reference screenshots belonging to it:** Keenon Service (`KSVC-01`) only.
- **Major screens:** `VoiceConfigActivity`, `VoiceCheckActivity`, `VoiceFuncTestActivity`,
  `VoiceConfigItemSelectActivity`, `VoiceActivationActivity` (voice/wake-word setup and testing),
  `DeveloperOptionsActivity`, `SystemStubActivity` (**[INFERRED]** likely candidate for
  `KSVC-01`'s SN/Version display), plus 2 activities from the bundled iFlytek speech SDK
  (`OpenWebActivity`, `VideoPlayActivity`).
- **Navigation entry points:** no confirmed launcher icon; reached via explicit Intent from
  another app, or via its exported services being bound to (not navigated to visually).
- **Services/components:** `MqttApiService`, `TtsApiService`, `VoiceApiService` (all exported, no
  permission gate), `BiService` (analytics), `org.eclipse.paho.android.service.MqttService`
  (the underlying MQTT client library service).
- **SDK APIs used:** none of the robot-control `com.keenon.sdk.*` classes were found here — this
  app is about cloud messaging/voice, not robot motion/hardware.
- **Internal (cross-app) APIs used:** exposes (does not consume) `IMqttAidlApi`, `TtsApiService`,
  `VoiceApiService` for other apps to bind to.
- **Cloud APIs used:** Microsoft Cognitive Services Speech SDK
  (`com.microsoft.cognitiveservices.speech`) and an iFlytek ("南方" / South region) speech SDK
  (`com.iflytek.south.robot.sdk`) — two distinct, competing TTS/voice cloud providers bundled in
  the same app; MQTT broker connection (host not confirmed in this pass); Sentry crash reporting
  to `sentry.keenonrobot.com` (a real, confirmed Keenon-owned domain, found verbatim in the
  manifest's `io.sentry.dsn` meta-data).
- **Shared dependencies:** `androidx.work` (WorkManager) — the same background-scheduling stack
  seen in `RemoteControl`, suggesting a shared internal Keenon Android starter/base template
  across their apps, not a runtime-shared library.
- **Authentication/access model:** none observed for its exported services (open binding, as
  noted above) — this is a real, notable security-relevant fact for the "authentication/access
  requirements" field, not a judgment about whether it's a problem.
- **Communicates directly with the robot:** **No evidence found** — its role is cloud/voice
  services, not robot motion/hardware.
- **Standalone Android package:** **Yes.**

---

## 4. krlog — `com.keenon.krlog`

- **APK / package name:** `com.keenon.krlog`
- **Version:** `v1.2.6-0-g18a40d0` (versionCode 38)
- **App display name (manifest metadata):** "krlog"
- **Launcher entry point:** `com.keenon.krlog.view.MainActivity` (the only real, non-library
  activity in this APK's 3-activity manifest — the other 2 are a generic utility library's
  transparent trampoline activities).
- **Purpose:** a small, standalone device-wide log capture/export/retention utility — matches the
  reference screenshots exactly (log file size, retention count, export-all, clear-and-re-record).
  `android.permission.READ_LOGS` confirms it reads system-wide logs, not just its own.
- **Reference screenshots belonging to it:** krlog (`KRLOG-01`, `KRLOG-02`).
- **Major screens:** a single `MainActivity` (the log tool UI seen in both reference captures is
  almost certainly one Activity with dialog/sheet states, given only one real Activity exists).
- **Navigation entry points:** device launcher icon; `AutoStartBroadcastReceiver` +
  `RECEIVE_BOOT_COMPLETED` mean its `DaemonService` starts automatically at boot regardless of
  whether the user ever opens the app.
- **Services/components:** `LogService` (log capture), `DaemonService` (persistent background
  process, boot-started).
- **SDK APIs used:** none — no `com.keenon.sdk.*` references found; this app has no robot-control
  role.
- **Internal (cross-app) APIs used:** none confirmed; `peanut-clean`'s source references the
  string `com.keenon.krlog` only inside `UpgradeUiUtils.java`, consistent with a generic
  "is this package installed / what version" check (e.g. to decide whether to prompt installing
  or updating krlog), not a live IPC binding.
- **Cloud APIs used:** none found.
- **Shared dependencies:** `com.blankj.utilcode` (small Android utility library, also seen in
  `robot-installation-assistant`) — a common convenience library, not evidence of deep sharing.
- **Authentication/access model:** none — a local, unauthenticated system tool.
- **Communicates directly with the robot:** **No.**
- **Standalone Android package:** **Yes** — smallest, simplest, most clearly standalone of the 5.

---

## 5. Remote Assistant — `com.keenon.remote_control_oray`

- **APK / package name:** `com.keenon.remote_control_oray`
- **Version:** `v3.3.1-0-gfe68b6d` (versionCode 310)
- **App display name (manifest metadata):** "Remote Assistant" — matches the reference
  screenshots' own on-screen title exactly.
- **Launcher entry point:** `com.keenon.remote_control.activity.RemoteHomeActivity`
  **[INFERRED — not directly confirmed to carry the `LAUNCHER` category in the excerpt reviewed,
  but it is the only non-library, non-`daemon`-package activity with a plausible "home" role]**.
- **Purpose:** remote-desktop/remote-support access to the robot's tablet, built on a
  **licensed, white-labeled third-party SDK — Oray/Sunlogin** (`com.oray.sunlogin.*` classes,
  package suffix `_oray`) — this is commercial remote-access software (a Chinese TeamViewer-class
  product), not a Keenon-original remote-control implementation.
- **Reference screenshots belonging to it:** Remote Assistant (`RA-01`, `RA-02`).
- **Major screens:** `RemoteHomeActivity` (**[INFERRED]** → `RA-01`, the "About" landing/info-icon
  screen), `com.keenon.remote_control.activity.AboutActivity` (direct name match → `RA-02`, the
  detailed SN/WLAN-IP/Build/Version screen), `com.keenon.ssh.SshInfoActivity` (SSH connection info,
  not captured in our reference set), `com.keenon.remotectrl.PermissionActivity` (permission
  grant flow, not captured).
- **Navigation entry points:** device launcher icon.
- **Services/components:** `com.keenon.remote_control.daemon.HeadService`, `Service1`, `Service2`
  (background daemons), `RobotBootReceiver` + boot-started daemons (`Receiver1`, `Receiver2`),
  `com.oray.sunlogin.service.AdbSimpleIME` (the Sunlogin SDK's own input-method-based remote
  input-injection mechanism), Samsung Knox permission requests (`KNOX_HW_CONTROL`,
  `KNOX_REMOTE_CONTROL`, etc.) — suggesting this same APK (or SDK) targets Samsung Knox-enabled
  devices as one of its supported hardware targets, not just the robot's own tablet vendor.
- **SDK APIs used:** none of the robot-control `com.keenon.sdk.*` — this app's domain is
  remote screen access, not robot motion.
- **Internal (cross-app) APIs used:** referenced by `peanut-clean`'s `CoreUtil.java` sibling-app
  list (by package name `com.keenon.remote_control_oray`) but no evidence in the time available of
  a live AIDL/IPC call between the two.
- **Cloud APIs used:** the Sunlogin/Oray remote-relay backend (a third-party commercial service;
  exact endpoint not extracted in this pass).
- **Shared dependencies:** `androidx.work` (WorkManager), same as `ServiceSystem`.
- **Authentication/access model:** governed by the Sunlogin/Oray SDK's own account/pairing
  mechanism (not analyzed further in this pass) plus an in-app `PermissionActivity` grant flow.
- **Communicates directly with the robot:** **No evidence found** of direct hardware/serial
  access — its function is remote *screen* access to the tablet, not robot motion control.
- **Standalone Android package:** **Yes.**

---

## Additional APKs discovered during the audit

No 6th Keenon application/APK was found among the decompiled set. Two **sibling package names**
were found referenced (by string literal, not as installed APKs in this decompiled set) inside
`peanut-clean`'s own source: `com.keenon.peanut.solutionfooddelivery` and
`com.keenon.peanut.solutionsaler`. These are other Keenon robot product lines (food-delivery and
retail/sales robots) built on the same "peanut" platform family — **not applicable to the C40
cleaning robot** and out of scope for Sakar's equivalent, but recorded here because their
existence confirms peanut-clean is one of several sibling "solution" apps sharing a platform
convention, not a one-off.

---

## Summary table

| # | Application | Package | Standalone APK? | Talks to robot directly? | Reference screens |
|---|---|---|---|---|---|
| 1 | Cleaning/Operator (incl. Super User + Debug) | `com.keenon.peanut.clean` | Yes | Yes (serial + embedded SDK) | Home, Cleaning, Manual Drive/Push, Scheduled Cleaning, Teaching Mode, Settings, Super User, Robot Debugging |
| 2 | Robot Installation Assistant | `com.keenon.peanut.peanutservice` | Yes | Uncertain | Robot Installation/Maps, Business Settings, Elevator/Remote Control/Scheduling Path |
| 3 | Keenon Service | `com.keenon.systemservice` | Yes | No | Keenon Service |
| 4 | krlog | `com.keenon.krlog` | Yes | No | krlog |
| 5 | Remote Assistant | `com.keenon.remote_control_oray` | Yes | No | Remote Assistant |

All 5 are genuinely separate, independently-versioned Android applications (separate package IDs,
separate launcher/entry points, separate APK files) — none of them is a module inside another.
The only confirmed cross-app coupling is process-level: `peanut-clean` binds to
`com.keenon.systemservice`'s exported, unauthenticated `MqttApiService` via a bundled AIDL stub.
