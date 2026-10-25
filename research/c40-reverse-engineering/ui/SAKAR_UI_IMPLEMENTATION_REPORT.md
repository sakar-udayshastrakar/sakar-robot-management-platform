# Sakar UI Implementation Report — C40

What was built in the Sakar web app (`web/`, React 19 + TS + Vite) from the C40 reverse-engineering findings, and what was deliberately not. Scope chosen with the user: **research docs + Voice feature + C40 control view**, within the project's hard rules (web app → Sakar Cloud Backend only; data-honesty convention; nothing faked).

## Screens / features implemented

| Feature | Route | Nature | Data mode |
|---|---|---|---|
| **Voice Announcements** (settings + announcement catalog) | `/voice` | New feature | `simulated` (browser Web Speech API demo) |
| **C40 Cleaning Control** console | `/c40-control` | Composition of existing backend-backed panels | `live` |

- **Voice Announcements:** language (en/zh), volume, mute, voice/timbre select, test announcement, and a Sakar-authored announcement catalog (cleaning / charging / error / maintenance / system) with per-item browser playback. Behind a `VoiceService` abstraction so a real robot-facing implementation can replace the demo without UI changes. Settings persist per-browser (localStorage).
- **C40 Cleaning Control:** a per-robot operator console composing the existing **live** `RobotStatusPanel` (status + battery), `RobotTasksPanel` (cleaning task lifecycle with C40 modes 101–105 + synced area selection), and `RobotCleaningPanel` (real cleaning history), plus a static C40 mode reference card. Adds no new robot command path.

## Screens / features NOT implemented (and why)

- **Manual drive, actuator control (fan/brush/pump/lamp), teach-path, camera calibration, water-mode, do-not-disturb, reboot** — the original robot reaches these over CoAP/serial; the **Sakar backend exposes no endpoint** for them (`KeenonRobotAdapter` is limited to status/battery/telemetry/areas/map/start/stop/pause/dock/cleaning). Building UI for them would require faking a command path, which the project forbids. Documented in `UI_ACTION_METHOD_MAPPING.md`.
- **Multi-floor map editor / mapping-tool screens** (scan/revise/target/scene/dispatch) — these belong to the installation-assistant commissioning app over rosbridge; no Sakar backend equivalent. The app already has a live `RobotMapPanel` (map image) on Robot Detail.
- **Standalone status dashboard, PIN login, network-config wizard** — the C40 has no standalone status screen (status is composed), uses PIN-only local auth (Sakar uses its own RBAC login), and treats WiFi as an on-device component. No faithful cloud equivalent is appropriate.
- **Voice command (ASR) UI** — the evidence shows only chat/entertainment ASR and **no voice→robot-motion capability**; building a voice-command UI would imply a capability that does not exist.

## Verified (backend-backed) vs inferred

- **Verified / live (through the Sakar backend):** robot status, battery, areas, cleaning task create + start/pause/resume/stop/cancel, cleaning history, map image. The C40 mode codes 101–105 are cross-validated by both the SDK evidence and the backend `KeenonRobotAdapter`.
- **Inferred / demo:** all Voice playback (browser Web Speech API, not the robot's engine); the announcement catalog **text** (Sakar-authored — original vendor text is UNKNOWN in the evidence); screen-level widget details (H5 was minified). Clearly labeled via `SimulatedDataBanner` and `dataMode: 'simulated'`.

## Voice features implemented
Language selection (en/zh), volume, mute, voice/timbre selection, adjustable speed, test announcement, categorized announcement catalog with per-item playback, persisted settings, and a `VoiceService` abstraction (`getVoiceService()`), with a `BrowserVoiceService` demo. **No voice-command/ASR UI** (unsupported capability). **No vendor audio shipped.**

## Assets
- **Reused from the vendor: none.** All Keenon assets are proprietary (see `ASSET_INVENTORY.md`); none were copied into `web/`.
- **Sakar-branded alternatives used:** existing `components/ui/Icon.tsx` registry (`Icon.spray`, `Icon.bell`, etc.), `--sakar-*` design tokens / `sakar-*` classes, brand orange, Roboto. Voice audio is generated at runtime by the browser (no bundled clips).

## Files created
- `web/src/features/voice/VoiceService.ts`
- `web/src/features/voice/announcementCatalog.ts`
- `web/src/features/voice/VoiceSettingsPanel.tsx`
- `web/src/features/voice/VoiceAnnouncementPanel.tsx`
- `web/src/features/voice/VoicePage.tsx`
- `web/src/features/voice/VoicePage.test.tsx`
- `web/src/features/cleaning/C40ControlPage.tsx`
- `research/c40-reverse-engineering/ui/` — `UI_SCREEN_INVENTORY.md`, `UI_NAVIGATION_FLOW.md`, `UI_ACTION_METHOD_MAPPING.md`, `VOICE_MODULE_ANALYSIS.md`, `VOICE_ASSET_INVENTORY.csv`, `ASSET_INVENTORY.md`, `SAKAR_UI_IMPLEMENTATION_REPORT.md` (this file)

## Files modified
- `web/src/app/App.tsx` — added routes `/c40-control` and `/voice`.
- `web/src/components/layout/navConfig.ts` — added "C40 Cleaning Control" (live) under Robot management and a "Voice Announcements" (simulated) group.

**No files in the REA workspace (`C40 …/REVERSE_ENGINEERING_RESULTS`) were modified.**

## Build / test results
- `tsc -b` (typecheck): **0 errors**.
- `oxlint` (new + changed files): **clean**.
- `vitest run`: **256/256 tests pass across 50 files** (incl. 3 new `VoicePage` tests). No existing test broken.
- `npm run build` (`tsc -b && vite build`): **success** (223 modules; `dist/` emitted).

## Safety verification
- **No real robot commands are sent** by the new code: Voice is browser-only; C40 Control reuses existing panels that call only the Sakar backend (task lifecycle records transitions; it does not dispatch to a robot).
- **All demo data is clearly identified**: `SimulatedDataBanner` on the Voice page, `dataMode: 'simulated'` in nav, and explicit source comments.
- No secrets introduced; no ROS topic/IP assumed live; robot communication stays backend-mediated and configurable (`VITE_API_BASE_URL`).

## Known limitations
- Voice playback uses the browser's Web Speech API; availability/voices vary by browser, and it does **not** make the robot speak. zh catalog text is basic Sakar-authored wording.
- Live in-browser verification (dev server) was not run: protected routes require the Sakar backend + auth, which was not started here. Validation rests on typecheck + lint + 256 tests (which render the Voice UI headlessly) + production build.
- C40 Control is tuned for cleaning robots but does not filter the robot picker by model (no model-type field surfaced to the picker).

## Recommended next steps
1. Add a real backend voice endpoint (`/robots/{id}/voice`) + a `BackendVoiceService` behind the existing `VoiceService` interface if robot announcements are ever required.
2. Run the dev server against a backend to do a live pass (responsive/dark checks, console) and add a Voice tab to `RobotDetailPage` if desired.
3. If more C40 control is needed, extend the backend adapter first (e.g. water-mode, do-not-disturb), then surface it — never before the backend supports it.
