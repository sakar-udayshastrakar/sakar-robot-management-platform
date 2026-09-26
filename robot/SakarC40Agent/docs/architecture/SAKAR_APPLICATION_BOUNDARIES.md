# Sakar Application Boundaries (Proposed)

**This document proposes Sakar-side application boundaries derived ONLY after the Keenon ownership
map in [`KEENON_APPLICATION_SEPARATION_MAP.md`](KEENON_APPLICATION_SEPARATION_MAP.md) was
established from decompiled evidence.** This is an architecture/audit document. Nothing here has
been implemented: no code was written, no project was created or moved, `SakarC40Agent` has not
been touched.

## Governing rule

> Do not automatically put everything into `SakarC40Agent`. Where Keenon has a separate APK for a
> function, Sakar's equivalent should preserve that separation unless there is a specific reason
> not to (recorded explicitly below, per proposal, not assumed).

## Definitions used consistently below

- **Separate Android application** — its own package ID, its own launcher entry, built/installed/
  versioned independently. (This is what all 5 Keenon apps are.)
- **Separate module inside an application** — a Gradle module (like `SakarC40Agent`'s own
  `:robot`, `:sdk`, `:operator-ui` modules) — compiled into the *same* APK, not independently
  installable.
- **Separate screen/feature** — a Composable/Activity reached by in-app navigation within one
  application; not a packaging boundary at all.
- **Shared backend/library** — code or a service consumed by more than one of the above, whether
  linked in at compile time (a library) or called at runtime across a process boundary (a bound
  service, like `com.keenon.systemservice`'s AIDL API).

The proposals below are explicit about which of these four categories each Sakar item is, because
conflating them is exactly the mistake this audit exists to prevent.

---

## Proposed application 1: Sakar Operator (existing)

- **application_id:** `sakar-operator` (existing, no change proposed)
- **display_name:** Sakar CleanBot Operator (current `SakarC40Agent`)
- **package_name proposal:** `com.sakarrobotics.c40agent` (unchanged — this is the existing,
  already-shipped package)
- **responsibility:** operator-facing cleaning workflow, Super User admin surface, and hardware
  debug — mirrors `com.keenon.peanut.clean`'s scope exactly, which is the correct precedent: Keenon
  itself keeps the operator app, Super User, and Debug together in one APK, so Sakar keeping them
  together in `SakarC40Agent` is **architecturally correct, not a shortcut.**
- **included screens:** Home, Start Cleaning, Scheduled Cleaning (+ Edit Task), Teaching Mode (+
  Teach Route), Manual Drive, Manual Push, operator Settings (Consumables Statistics, Cleaning
  Data, Workstation, Charging Settings, Screen Lock Settings, General Settings, Screen Emoji,
  Sound Settings, DND Mode, Resource Management, About the Robot), Super User (Network, Resource
  Management, General Settings, Robot Debugging, System Settings), the Robot Debugging hardware
  dashboard.
- **excluded screens:** Robot Installation/Maps and its sub-flows (Business Settings,
  Elevator/Remote Control/Scheduling Path) — see proposed application 2; the Logs viewer — see
  proposed application 4.
- **shared libraries:** none across Sakar apps yet (there is currently only one Sakar app); the
  existing `:robot`/`:sdk`/`:navigation`/`:charging`/`:diagnostics`/`:domain`/`:data` Gradle
  modules remain internal modules of this one application, matching how `peanut-clean` bundles its
  own SDK/serial code directly rather than sharing it externally.
- **shared robot communication layer:** owns it directly — `PeanutSdkBridge` + native
  serial/SDK access, mirroring `peanut-clean`'s own direct hardware ownership.
- **access/authentication model:** local SHA-256 PIN gate for Super User (already implemented),
  matching `peanut-clean`'s own local `PassWordActivity` gate (no server-side auth call found in
  Keenon's own APK either).
- **reference sources:** `HOME-01`, `HOME-02`, `CLEAN-01`, `MANUAL-01`, `MANUAL-02`, `SCHED-01`,
  `SCHED-02`, `TEACH-01`, `TEACH-02`, `SET-01`..`SET-15`, `SU-01`..`SU-05`, `DEBUG-01`..`DEBUG-10`.

---

## Proposed application 2: Sakar Installation Assistant (NEW — not yet built)

- **application_id:** `sakar-installation-assistant`
- **display_name:** Sakar Installation Assistant
- **package_name proposal:** `com.sakarrobotics.installation` (a genuinely separate package,
  separate APK)
- **responsibility:** map creation/editing, elevator configuration, network/business
  commissioning settings, remote-control pairing — mirrors `com.keenon.peanut.peanutservice`'s
  scope. **This is the most significant proposed change from the current implementation:**
  `SakarC40Agent` today contains native Compose screens for this domain
  (`robot_installation_commissioning`, `maps_management` in `FEATURE_REGISTRY.json`, currently
  ORPHANED/unreachable from the UI per `UI_CHANGE_LEDGER.md`). Per the governing rule, this content
  should NOT be expanded inside `SakarC40Agent` — Keenon's own separation puts it in an entirely
  separate APK (and, notably, delivers it as a bundled web app rather than native views, though
  Sakar does not need to copy that specific technical choice — only the *packaging* separation is
  the architectural finding worth preserving).
- **included screens (proposed, matching Keenon's scope):** Map deployment/list, map
  route-drawing and zone editor, "Set up" (Basic Settings, Volume Setting, Network Settings,
  Business settings, Advanced setting), Elevator settings (Draw Elevator, Elevator list
  configuration, Elevator robotid configuration), Remote control robot (QR pairing), Scheduling
  path editor.
- **excluded screens:** everything in application 1; Logs; Keenon-Service-equivalent
  functionality (voice/MQTT — Sakar has none of this today, see application 3).
- **shared libraries:** would need its own thin robot-communication client — see next point.
- **shared robot communication layer:** **[OPEN QUESTION, not resolved by this audit]** — Keenon's
  own `peanutservice` app's relationship to the robot SDK was **not confirmed** (see
  `KEENON_APPLICATION_SEPARATION_MAP.md` §2, marked uncertain). Sakar's equivalent could either
  (a) talk to the robot over the same local network endpoints `SakarC40Agent` uses, independently,
  or (b) go through a to-be-designed shared layer. This audit does not have enough evidence to
  recommend one over the other and explicitly leaves it open rather than guessing.
- **access/authentication model:** not yet designed; Keenon's own equivalent showed no obvious
  in-app auth gate in the reference screenshots (unlike Super User), so a similarly ungated
  installer-only surface may be appropriate, pending a decision.
- **reference sources:** `INSTMAP-01`..`INSTMAP-11`, `BIZ-01`, `BIZ-02`, `ELEV-01`..`ELEV-06`.

---

## Proposed application 3: Sakar Service (shared backend) — status: NOT YET NEEDED

- **application_id:** `sakar-service` (reserved, not proposed for immediate creation)
- **display_name:** Sakar Service
- **package_name proposal:** `com.sakarrobotics.service` (reserved)
- **responsibility:** the Keenon precedent (`com.keenon.systemservice`) exists specifically
  because *multiple* Keenon apps needed shared MQTT/TTS/voice access without each bundling its
  own copy. **Sakar currently has exactly one application**, so there is nothing yet to share
  between. Creating this app now, before a second Sakar app exists that needs the same backend
  services, would be speculative infrastructure the project's own engineering principle (no
  premature abstraction) argues against.
- **included screens:** none proposed yet.
- **excluded screens:** all current Sakar screens remain in application 1.
- **shared libraries:** n/a yet.
- **shared robot communication layer:** n/a — this tier, per the Keenon precedent, is
  specifically *not* about robot motion control (no `com.keenon.sdk.*` evidence was found in
  `systemservice`), it is about cloud messaging/voice, which Sakar has not built at all yet.
- **access/authentication model:** notably, Keenon's own real implementation exposes this
  service with **no authentication** (`android:exported="true"`, no permission) — worth recording
  as a fact to *not* copy uncritically if/when this is ever built.
- **reference sources:** `KSVC-01` only — a single reference screen is a thin basis for designing
  a whole application; recommend revisiting this proposal only once Sakar actually needs
  cross-app MQTT/TTS/voice sharing.

---

## Proposed application 4: Sakar Log Utility (NEW — not yet built)

- **application_id:** `sakar-log-utility`
- **display_name:** Sakar Log Utility
- **package_name proposal:** `com.sakarrobotics.logutility`
- **responsibility:** device-wide log capture, retention, and export — mirrors `com.keenon.krlog`
  exactly (single Activity, a boot-started background daemon, `READ_LOGS`-class scope).
  **This is the second significant proposed change**: `SakarC40Agent` currently has an in-app
  `logs_viewer` feature (`LogsScreen`, `SUPER_USER_LOGS` route, currently ORPHANED per
  `ROUTE_REGISTRY.md`) reading from `SdkCallLogger` — i.e. Sakar's own SDK-call log, which is a
  narrower thing than krlog's device-wide log capture, but occupies the same conceptual slot
  Keenon dedicates an entire separate, boot-autostarted APK to.
- **included screens:** a log-tool main screen (file size, retention count, export-all,
  clear-and-re-record) — matching `KRLOG-01`/`KRLOG-02` exactly.
- **excluded screens:** all of application 1's screens, including its existing in-app SDK-call
  log viewer, which this proposal does not require removing — see conflicts/decision needed below.
- **shared libraries:** none required.
- **shared robot communication layer:** none — Keenon's own krlog has no robot/SDK access at all.
- **access/authentication model:** none, matching Keenon's own unauthenticated local tool.
- **reference sources:** `KRLOG-01`, `KRLOG-02`.
- **decision needed (not resolved by this audit):** should Sakar's existing in-app
  `logs_viewer` (SDK-call log, useful for engineering/support and already implemented) be (a) kept
  as-is inside `SakarC40Agent` as a narrower, different-purpose tool than krlog, (b) migrated into
  this new standalone app, or (c) both — a narrow in-app SDK-call log for support, plus a genuinely
  separate device-log utility mirroring krlog's broader scope. This audit surfaces the question;
  it does not answer it.

---

## Proposed application 5: Sakar Remote Assistant — status: NOT YET NEEDED / LICENSING-GATED

- **application_id:** `sakar-remote-assistant` (reserved, not proposed for immediate creation)
- **display_name:** Sakar Remote Assistant
- **package_name proposal:** `com.sakarrobotics.remoteassistant` (reserved)
- **responsibility:** Keenon's own equivalent is **not Keenon-original technology** — it is a
  licensed, white-labeled build of the commercial Oray/Sunlogin remote-desktop SDK. Sakar cannot
  reproduce this by writing UI code; it would require **licensing an equivalent remote-access SDK**
  (Sunlogin, TeamViewer, AnyDesk, or similar) as a prerequisite, which is a business/procurement
  decision outside the scope of a UI/architecture audit.
- **included screens:** none proposed until a remote-access SDK is licensed.
- **excluded screens:** all of application 1.
- **shared libraries:** would be entirely defined by whichever third-party SDK is licensed.
- **shared robot communication layer:** none — Keenon's own version has no robot/SDK access
  either; it is remote *screen* access to the tablet, not robot control.
- **access/authentication model:** would be defined by the licensed SDK's own account/pairing
  system.
- **reference sources:** `RA-01`, `RA-02`.

---

## Summary table

| application_id | display_name | Status | Included Keenon-equivalent scope | New Sakar package? |
|---|---|---|---|---|
| `sakar-operator` | Sakar CleanBot Operator | Existing (`SakarC40Agent`) | `com.keenon.peanut.clean` (Cleaning + Super User + Debug) | No — already exists |
| `sakar-installation-assistant` | Sakar Installation Assistant | **Proposed, not built** | `com.keenon.peanut.peanutservice` (Installation/Maps) | Yes |
| `sakar-service` | Sakar Service | **Reserved, not needed yet** | `com.keenon.systemservice` (MQTT/TTS/Voice) | Not yet |
| `sakar-log-utility` | Sakar Log Utility | **Proposed, not built** | `com.keenon.krlog` | Yes |
| `sakar-remote-assistant` | Sakar Remote Assistant | **Reserved, licensing-gated** | `com.keenon.remote_control_oray` (Sunlogin/Oray) | Not yet |

No project was created, moved, or deleted to produce this document. `SakarC40Agent` continues to
exist exactly as it did before this audit.
