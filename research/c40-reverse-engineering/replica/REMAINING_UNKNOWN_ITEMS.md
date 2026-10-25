# C40 Replica — Remaining Unknown Items

Items the available static evidence cannot resolve. Each names what evidence would resolve it. **Nothing here is fabricated to fill a gap.**

## UI / visual
- **Exact original layouts, pixel dimensions, spacing, and colors** of every HMI/mapping screen — the original apps are **minified Vue/H5** and `resources.arsc`/446 layout XMLs were not decoded. *Resolve with:* original-robot reference screenshots (per `REFERENCE_UI_MAP.md`).
- **Original dialog/loading/empty/error states** per screen — mechanism known, exact content UNKNOWN. *Resolve with:* references.
- **Full peanut-clean language-pack list** — only the SPA i18n set (en/zh/zht/ja/ko/th) is confirmed. *Resolve with:* cloud resource packs / original app.
- **Voice announcement text** behind the 79 numbered MP3 event codes — UNKNOWN (see `research/.../ui/VOICE_ASSET_INVENTORY.csv`). *Resolve with:* original app runtime or a vendor mapping.

## Map renderer
- **Exact OpenLayers projection/transform parameters** used by the original HMI — minified JS, not deobfuscated. The ROS/cartographer `map.yaml` convention (resolution/origin) is the correct, standard basis used instead (COVERAGE §4). *Resolve with:* JS deobfuscation or a synced real map to calibrate against.
- **Custom binary map format details** (if any beyond standard OccupancyGrid/pbstream) — UNKNOWN.

## ROS / robot
- **Live node/topic/service graph, pub/sub direction, frequencies** — all static; none runtime-confirmed. *Resolve with:* read-only ROS inspection (see `RUNTIME_VERIFICATION_CHECKLIST.md` §A).
- **Which launch profile runs on a physical unit** — package default is W3; C40 profile inferred. *Resolve with:* robot runtime.
- **Physical effect / timing / error behavior of every command** — UNVERIFIED_RUNTIME (no robot contacted). *Resolve with:* authorized robot testing (§D).
- **In-practice auth state** of services (rosbridge 9090/9091, CoAP, OTA, MQTT ACLs) — UNKNOWN from client artifacts. *Resolve with:* authorized runtime inspection.

## Product / policy decisions (not evidence gaps)
- Real PIN policy (replacing the any-4-digit placeholder); whether Android voice UI is in scope; elevator/gate/dispatch scope. *Resolve with:* a product decision.

## Update — 2026-10-25 (continuation iteration 2)
- **Map transform:** the standard ROS/cartographer world↔pixel convention is now implemented and tested (`MapCoordinateTransform`, `:domain`). The original app's exact **OpenLayers** projection parameters remain UNKNOWN (minified JS); the standard convention is used deliberately and only applies to a map that follows it. Still needs a **synced real map** to calibrate/verify.
- **Voice:** a `VoiceService` + labeled `MockVoiceService` now exist (`:domain`). Still UNKNOWN: the original numbered-prompt spoken text, and any real robot-side voice backend — the mock produces no audio and makes no robot-voice claim.
- Everything else in this file is unchanged (still requires original app runtime, a physical robot, or JS deobfuscation as noted).
