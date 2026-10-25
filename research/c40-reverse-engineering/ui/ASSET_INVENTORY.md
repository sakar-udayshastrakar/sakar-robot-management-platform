# C40 Asset Inventory

Assets extracted from the C40 APKs (primarily `peanut-clean`). **Source location:** the investigation session scratchpad `…/apkx/peanut-clean/assets/` (ephemeral — these are **not** in the committed archive; they are regenerable from the APK and are vendor-proprietary).

> **Authorization rule (applies to every row):** all assets below are **Keenon vendor-proprietary** (logos, icons, fonts, images, audio, video). They are catalogued here for analysis only. **None is authorized for reuse in the Sakar production app.** Where the Sakar UI needs an equivalent, create a **Sakar-branded replacement** (brand orange `#ff6100`, Roboto, `components/ui/Icon.tsx` registry) and document the substitution in `SAKAR_UI_IMPLEMENTATION_REPORT.md`.

## peanut-clean asset categories (C-S file counts)

| Category | Dir | Count | Format(s) | Used for | Reuse |
|---|---|---|---|---|---|
| App icons / UI images | `img/` | 96 | png, gif | Screen backgrounds, buttons, clean-detail controls (play/pause/charge/model), consumables, dustBag, main-brush, filter, floor empty-states, loading/creating gifs | Replace |
| Scene images | `image/` | 13 | png | dust/filter/roll illustrations, gz backgrounds, circle/right icons | Replace |
| Backgrounds | `bg/` | 4 | **mp3** (ambient tracks: deep, pianoboy, spring, summer) | Background audio | Replace |
| Character / face | `character/` | 1 | `2_UNICODE.bin` | Emotion board face data (`/dev/ttyS2`) | N/A (hardware) |
| Expression | `expression/` | 3 | (style1 set) | Face/emotion display | Replace |
| Fonts | `font/` | 1 | `digital.TTF` (23 KB, 7-segment digital) | Numeric displays (battery %, time) | Replace (use Roboto Mono / a licensed 7-seg font) |
| Stylesheets | `css/` | 46 | css | H5 SPA styling | Reference only |
| Scripts | `js/` | 59 | js (Vue/webpack minified: app.*, chunk-*, ol.js, vendors~app) | H5 SPA logic + OpenLayers map | Reference only |
| Videos | `video/` | 4 entries | mp4 (+`maintenance/` dir) | `c40_bilge_tank`, `c40_restart`, `c40_sweep_brush_fault_141102`, maintenance clips | Replace |
| Fault pages | `fault/` | 23 | html | Fault/error detail pages (backs `res/raw/fault.json`, 144 faults) | Reference only |
| SLAM config | `slamConfig/` | 11 | json (`cleanConfigSetting.json`, mode/param defs) | Cleaning-mode/param catalog (modes 101–105) | Reference only |
| SQL | `sql/` | 19 | sql | Local DB schema (CLEAN_STRATEGY, MAP_AREA, MAP_POINTS, ELEVATOR…) | Reference only |
| Domain config | `domain/`, `json/` | 4 + 5 | json | `dev/prod/test.config.json` (server domains — **dev endpoints are a known info-leak, finding in archive**), LightConfig, sensors | Reference only (do not ship vendor endpoints) |
| Voice | `voice/` | 79 | mp3 | en(38)+zh(41) announcements — see `VOICE_ASSET_INVENTORY.csv` | Replace |
| Routing | `therouter/` | 1 | `routeMap.json` (38 routes) | Native route table | Reference only |
| JS bridge | `jsbridge/`, `WebViewJavascriptBridge.js` | 2+1 | js | H5 ↔ native bridge | Reference only |
| Top-level | — | 5 | `index.html`, `favicon.ico`, `LightConfig.json`, `sensors.json`, `WebViewJavascriptBridge.js` | SPA shell | Reference only |

## Branding assets of note
- `favicon.ico` (peanut-clean) — vendor; Sakar already has `web/public/favicon.svg`.
- `font/digital.TTF` — vendor 7-segment font for numeric readouts; **do not ship**; Sakar uses `--sakar-font-mono`.
- No Keenon logo was isolated in `assets/` (logos are likely in `res/` mipmaps/drawables, not inventoried individually; 446 layout XMLs + resources.arsc were not decoded — **UNK**).

## Sakar replacements (planned)
| C40 asset need | Sakar replacement |
|---|---|
| App logo / favicon | `web/src/assets/sakar-logo-full.png`, `web/public/favicon.svg` (existing) |
| UI icons (play/pause/charge/battery/brush/water…) | `components/ui/Icon.tsx` registry (existing ~45 inline SVGs; add any missing as Sakar SVGs) |
| 7-seg numeric font | `--sakar-font-mono` (Roboto Mono) |
| Voice announcements | Sakar-authored text via Web Speech API / Sakar clips (demo) — see voice doc |
| Background music | none (not reproduced) |

**Nothing from this inventory is copied into `web/`.** All visual needs are met with the existing Sakar design system.
