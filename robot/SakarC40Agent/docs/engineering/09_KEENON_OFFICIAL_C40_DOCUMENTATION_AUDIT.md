# Keenon Official C40 Documentation Audit

**This is a documentation-only research pass.** No `SakarC40Agent` code, UI, or APK was touched to
produce this file. Everything below comes from Keenon's own official, publicly reachable
documentation portal at `https://doc.keenonrobot.com/dashboard/en/cleans/c40`, read directly during
this session (2026-09-25). It is a **new, third evidence source**, alongside (1) decompiled-APK
static analysis (Phases 3-4) and (2) physical hardware observation (user-reported). It does **not**
silently reconcile with either — every point of contact with prior findings is recorded explicitly
in §7 as either a genuine conflict, a refinement, or "no overlap."

## Status vocabulary for this document

| Status | Meaning |
|---|---|
| `OFFICIAL DOCUMENTATION CONFIRMED` | Stated explicitly, in these words or clearly equivalent words, in an official Keenon C40 document read during this pass. |
| `STATIC APK ANALYSIS CONFIRMED` | Carried over from Phase 3/4's decompiled-APK work (see `02_EVIDENCE_REGISTER.md`). |
| `PHYSICAL HARDWARE CONFIRMED` | Carried over from the user's own physical inspection (see `03_HARDWARE_ARCHITECTURE.md`). |
| `RUNTIME VERIFIED` | Carried over from Phase 5's live network test. |
| `INFERRED` | A reasonable reading of official material that does not use the exact term. |
| `UNKNOWN` | No evidence from any source. |
| `NOT DOCUMENTED` | This official documentation pass looked for this specific thing and did not find it. This is **not** the same as `UNKNOWN` — see the rule below. |

**Rule, restated from the task's own instruction:** `NOT DOCUMENTED` describes what the *official
documentation* does or doesn't say. It is never converted into a claim about what the hardware
does or doesn't do. A `NOT DOCUMENTED` item may still be `STATIC APK ANALYSIS CONFIRMED` elsewhere
in this project — the two statuses answer different questions and both are recorded side by side
where that happens.

---

## 0. Access method and a documented, reproducible portal reliability problem

**This section itself is a required part of the audit** — accessing the portal was not
straightforward, and being honest about how the data below was actually obtained matters for
anyone re-verifying it later.

- The portal (`doc.keenonrobot.com`) is a VitePress-style single-page app. On this attempt it
  **did** render (title progressed from generic "Document | Docs Center" to "C40 | Docs Center",
  and `get_page_text` returned real content) — earlier attempts within this same project's history
  had failed outright (page never rendered, `document.body` stayed `null`; see the session record
  for the exhaustive troubleshooting that preceded this success). No explanation for the
  intermittent success/failure was found; it is recorded as observed, not diagnosed.
- The site's real content source is a **public Strapi CMS JSON API** at
  `https://doc.keenonrobot.com/strapi/api/cleans`, which the SPA itself calls under the hood
  (discovered via the browser's own network log). This API was used directly, in preference to
  clicking through the flaky UI, to reliably enumerate and read every C40 document's text content.
  This is still the *same* official portal and the *same* official content the UI displays — the
  API is simply a more reliable way to read it than the rendering layer. No authentication was
  required or used; no non-public endpoint was accessed.
- **A separate, severe, and reproducible reliability problem affects only large binary assets**
  (the scanned page-image PNGs/JPGs that make up most of these documents' actual content — text
  content came back reliably every time via the JSON API; only the accompanying page-scan *images*
  were affected). Confirmed independently via three different mechanisms in this project's session
  history: in-browser `fetch()` (`net::ERR_CONTENT_LENGTH_MISMATCH`, indefinite hangs), the
  separate `WebFetch` tool (60-second timeouts, `ECONNRESET`), and direct `curl` downloads from
  this session (`curl` exit code 28 "operation timed out", files truncated mid-download with no
  error surfaced by the HTTP status). Retried downloads sometimes succeeded (roughly 1 in 3-4
  attempts for a given image), but there was no reliable way to guarantee success. **This is
  recorded as a genuine, current server/CDN-side problem with `doc.keenonrobot.com`, not a
  client-side or automation-detection issue** — the same failure mode occurred via three
  independently-implemented HTTP clients.
- **Practical consequence:** out of roughly 185 total page-scan images across the 10 C40 documents
  found, **5 were successfully and fully downloaded and visually reviewed** in this pass (see §2 for
  which ones and what they showed). The remaining images were **not reviewed** — their existence and
  filenames are known (from the reliably-fetched HTML/JSON), but their visual content is not. Every
  finding below that depends on an image says explicitly whether that image was actually viewed or
  only known to exist by filename/heading.

---

## 1. Document inventory — what actually exists on the official portal for C40

The task named 11 document categories to study. The official portal's own category tree (read
directly, `OFFICIAL DOCUMENTATION CONFIRMED`) does list all 11 as named categories under Clean
Series → C40. However, **only 8 of the 11 categories have any actual document published under
them**; the other 3 are empty categories (verified by querying the API with no document-count
limit and finding zero rows, not by a failed navigation attempt):

| # | Requested category | Documents found | Status |
|---|---|---|---|
| 1 | Product Introduction | 2 (`C40 Product Manual`, `C40 Station Product Manual`) | Reviewed (partial — see §2) |
| 2 | Clean Map Deployment Manual | 2 (`cleaning Mapping Manual`, `Deployment Of Workstation`) | Reviewed (headings only — see §2) |
| 3 | Clean Map Precautions | 1 (`Mapping Specifications`) | Reviewed (full text) |
| 4 | Machine Usage Environment Standards | 1 (`> Environment Standards`) | Reviewed (full text) |
| 5 | Clean App Operation Manual | 1 (`C40-APP`) | Reviewed (headings + partial text) |
| 6 | Machine Maintenance Methods | 2 (`C40 operation`, `C40-Maintenance Manual`) | Reviewed (full text — these two are text/video-only, no images) |
| 7 | Merchant Quick Action Guide | **0** | `NOT DOCUMENTED` — category exists, no document published |
| 8 | Cloud Platform User Manual | **0** | `NOT DOCUMENTED` — category exists, no document published |
| 9 | Mobile app user manual | **0** | `NOT DOCUMENTED` — category exists, no document published |
| 10 | Product Disassembly and Assembly Guide | 0 as a separate category — see note | See note below |
| 11 | Hardware Connection Diagram | 1 (`C40 Exploded view`) | Reviewed (full image) |

**Note on #10:** the task named "Product Disassembly and Assembly Guide" as its own category. The
live portal's actual category tree groups "Hardware Connection Diagram" under a top-level group
called "Maintenance of Equipment" and does **not** have a separately-named "Product Disassembly and
Assembly Guide" category for C40 — only "Hardware Connection Diagram" exists under that group,
and it is the one document (`C40 Exploded view`) counted above. This is recorded as an observed
difference between the task's assumed category list and the portal's actual current category
list, not resolved one way or the other.

**10 total published documents exist for C40** as of this session. This entire audit covers those
10 documents and nothing else — no other Keenon-hosted document, forum post, or file was consulted.

---

## 2. Per-document findings

### 2.1 "C40 Product Manual" (Product Introduction) — id 37, 19 page-images (English section)

Multi-language scanned manual (English, 日本語, 한국어, Français, Deutsch, 繁體 — English is pages
3-18). **4 of 19 English pages were successfully downloaded and visually reviewed**: the cover
page, the safety-instructions page, and the transport/lifting-instructions page (the fourth,
page 2, is the language index).

- **Official product name: "KLEENBOT C40" / "KEENON Smart Clean Robot User Manual"**
  (`OFFICIAL DOCUMENTATION CONFIRMED`, directly quoted from the cover page). This is the first
  time this project's documentation records the product's actual marketed name — prior
  documentation used "C40" throughout, which remains correct as the model/chassis designation, but
  "KLEENBOT C40" is the name Keenon itself prints on the customer-facing manual cover.
- **"This product is equipped with LIDAR for positioning and navigation, do not look directly at
  the laser with your eyes."** (`OFFICIAL DOCUMENTATION CONFIRMED`, direct quote, Safety
  Instructions §2.3). This is the single most direct piece of official confirmation obtained in
  this pass for the hardware-extraction checklist: **LiDAR's existence and its stated purpose
  (positioning and navigation) is now `OFFICIAL DOCUMENTATION CONFIRMED`**, not merely inferred
  from decompiled sensor-check API names (`LIDAR_CHECK_RANGING`, `LIDAR_CHECK_MATCHING`,
  `LIDAR_VERIFY`, per `01_MASTER_ENGINEERING_KNOWLEDGE_BASE.md` §19).
- Operating temperature range: **0°C to 40°C**; **indoor use only**; machine is "only to be stored
  indoors" (`OFFICIAL DOCUMENTATION CONFIRMED`, direct quote).
- Do not disassemble/modify the battery; do not charge with wet hands; do not short-circuit
  charging-station contacts; battery must be removed before disposal (professional recycling)
  (`OFFICIAL DOCUMENTATION CONFIRMED`).
- Warning against inserting fingers into "the conveyor belt or other rotating parts" during use,
  and against opening the enclosure during normal operation (electric-shock risk)
  (`OFFICIAL DOCUMENTATION CONFIRMED`).
- Manual lifting/transport procedure: **two people, lift from the "laser layer gap"**, keep upright
  (`OFFICIAL DOCUMENTATION CONFIRMED`, direct quote) — this independently corroborates the LiDAR's
  approximate physical mounting height (at a distinct horizontal "layer" in the chassis, consistent
  with a rotating 2D LiDAR puck) without providing an exact measurement.
- Manual push-transport: "the machine switches to manual push mode" via pulling up armrests
  (`OFFICIAL DOCUMENTATION CONFIRMED`).
- **Not reviewed** (download failed, content unknown): pages 4-5, 7-13, 15-18 of the English
  section — these likely contain feature overview, spec table, part names, and operation basics
  given typical manual structure, but this is **not confirmed**; nothing about their content is
  claimed here.

### 2.2 "C40 Station Product Manual" (Product Introduction) — id 108, 8 page-images

**Not reviewed** — all 8 images failed to download in this session (`ERR_CONTENT_LENGTH_MISMATCH`-
class failures, per §0). Title and category confirm this is a workstation-specific product manual,
distinct from the robot's own manual (§2.1). Its content is entirely `UNKNOWN` to this audit.

### 2.3 "Machine Usage Environment Standards" — id 169, full text extracted

This document's actual body **text** (not just images) was fully retrieved via the API and is the
richest new source of operational-limit information in this pass. All of the following are
`OFFICIAL DOCUMENTATION CONFIRMED`:

- **Maximum area per single map: ≤ 20,000 m².** This is a new, previously undocumented operational
  limit — not derivable from any prior APK/SDK finding.
- **Passability specs:** braking distance 35-55cm; minimum passable width 650mm; minimum U-turn
  width 800mm; maximum climbing angle (full load, non-operating mode) ≤8° for slopes ≤5m long;
  maximum climbing angle (full load, operating mode, non-sweeping/non-mopping) ≤2° for slopes ≤5m
  long; obstacle-climbing height (non-cleaning mode) ≤15mm on fully hard floors, ≤10mm on
  mixed/soft floors; passage in cleaning mode over thresholds is "not recommended."
- **Applicable scenarios:** office buildings, hotels, small-scale shopping malls, schools, public
  places.
- **Explicitly inapplicable scenarios:** outdoor and semi-outdoor open scenarios; saunas and
  bathhouses, pools/swimming pools/landscape ponds, dust-free workshops, tire workshops, garages,
  explosion-proof workshops; railway/subway/station platforms without unilateral (edge) protection;
  scenarios with no obvious building features within a 25-meter radius and frequently-changing map
  boundaries (the document's own example: warehouses); scenarios with high electrostatic-protection
  or cleanliness-class requirements.
- 14 accompanying images (titled `Robot_Risk_Area_Deployment_Manual_01` through `_14`) exist but
  were **not reviewed** — their content (presumably diagrams of the risk-area/passability rules
  above) is `UNKNOWN`.

### 2.4 "Mapping Specifications" (Clean Map Precautions) — id 136, full text extracted

The single richest document for the mapping-extraction checklist. All of the following are
`OFFICIAL DOCUMENTATION CONFIRMED`, taken directly from the document's own body text (only 2
accompanying images exist for this document; neither was reviewed):

- **Two-layer map model, explicitly named:**
  - **Base layer:** the raw laser-scan data. "Should not be modified in principle." Functions:
    displays scanned features, supplements fixed scene features, removes noise. The robot
    localizes itself using base-layer features.
  - **Application layer:** built on top of the base layer to achieve operational behavior —
    restriction lines, speed-limit zones, charging points, virtual walls, origin point, target
    points, scheduling paths, area division.
- **Restriction lines:** system-generated restriction lines must not be erased during editing; if
  not closed, navigation fails; an in-app "restore" function exists for accidental edits (which
  clears *all* non-system-generated restriction lines when used).
- **Pre-mapping preparation:** the scene must be restored to its normal daily-use layout (e.g.
  restaurant tables/chairs in guest position) and all *temporary* obstacles removed before mapping,
  while *permanent* obstacles (walls, pillars, fixed cabinets) are intentionally left in place and
  must have restriction lines drawn around them.
- **Starting-point requirement for mapping:** must have distinct solid-wall features (concave/
  convex shapes, cylinders, cubic columns, edges, corners); open spaces and long, featureless
  corridors must be avoided as a mapping starting point.
- **Pushing-speed limits during mapping:** an unspecified but enforced linear speed limit (the APP
  gives an automatic voice alarm if exceeded) and an explicit **rotation-speed limit of 60°/s**
  (also alarmed by the app if exceeded).
- **Carpet-area mapping:** remote-control push mode is recommended instead of manual pushing, to
  avoid wheel-slip errors; this requires **"the robot and phone [to be] on the same LAN"**
  (`OFFICIAL DOCUMENTATION CONFIRMED` — direct evidence that at least this one operator workflow
  uses a local-network connection between the operator's phone and the robot, consistent with, and
  not contradicting, the APK-analysis finding that the mapping/DB write path is local-only; see
  §7).
- **Non-LiDAR-visible obstacle handling, all via restriction lines drawn manually during mapping:**
  height-difference obstacles below the LiDAR's scanning plane; staircase entrances/drop-risk
  areas; glass walls ("unrecognizable by the Lidar"); overhanging obstacles in the LiDAR's blind
  spot.
- **Elevator-area mapping procedure, in detail:**
  - Rotate the robot a full circle inside the elevator car for more than 5 seconds during scanning.
  - If the elevator's interior walls can't be scanned (light-transmitting, light-absorbing, or
    reflective surfaces), apply frosted film first.
  - **Physical paper labels must be attached** at defined positions because the elevator interior
    is "unrecognizable by Binocular Stereo Vision": first label pair at 0.2m from each wall,
    second pair at 0.8m, additional labels every 1m of horizontal distance thereafter, with 0.3-1m
    spacing between adjacent labels. A worked example is given for a 2m-wide elevator entrance
    (labels A1/A2, B1/B2, C).
  - **Three named elevator reference points:** in-elevator point (60-100cm from the door, inside),
    waiting point (60-100cm from the door, outside), queuing point (60-100cm behind the waiting
    point).
  - **"Binocular Stereo Vision" is named explicitly and separately from LiDAR** — see §7 for how
    this refines a prior `INFERRED`-only finding.
- **Charging-pile placement standard:** must be against a flat wall section with no concave/convex
  structure; not at corners; not against a transparent (glass) background; no obstacles within
  150cm in front of the pile and 50cm to each side.
- **Cleaning-area/route generation:** system prioritizes generating cleaning areas globally first
  (worked example: separating the area right at a hotel room door specifically), then lets the
  operator subdivide; if a generated cleaning route looks distorted, the guidance is to re-divide
  the cleaning areas rather than hand-edit the route.

### 2.5 "cleaning Mapping Manual" (Clean Map Deployment Manual) — id 141, headings only

This document's body content is **entirely images** (38 of them) — no descriptive body text exists
in the HTML beyond 9 section headings, and none of the 38 images were successfully downloaded in
this session. The 9 official section names themselves are still `OFFICIAL DOCUMENTATION CONFIRMED`
as the documented top-level mapping workflow, even though their contents were not reviewed:

1. Map preparation
2. **Multi-Robot Management** — an officially-named workflow step not previously known to this
   project; its actual content (what "multi-robot management" means operationally — shared maps
   across robots? a fleet console? something else?) is `NOT DOCUMENTED` beyond this heading, since
   the images were not reviewed.
3. Scan
4. Map editing
5. Point setting
6. Area Settings (heading itself reads "Ares Settings" — likely a typo in the source document,
   transcribed verbatim rather than silently corrected)
7. Mode Settings (heading itself reads "Mors settings" — same note)
8. Settings
9. **Map upload** — confirms a map-upload capability is officially documented as a named step, but
   its actual mechanism (local file export? cloud upload? to which destination?) is `NOT
   DOCUMENTED` beyond this heading.

### 2.6 "Deployment Of Workstation" (Clean Map Deployment Manual) — id 118, headings only

Also entirely images (5 of them, none downloaded). 5 official section headings
(`OFFICIAL DOCUMENTATION CONFIRMED` as section names only):

1. Workstation Environment Standards
2. Basic Knowledge Of Charging Stations & Workstation
3. Deployment Of Workstation
4. Deployment Standard for Workstation Water Filling Points
5. Installation Precautions For The Clean Water Filter Of The Workstation

Confirms, at the heading level only, that the workstation has: its own environment/placement
standards (distinct from the robot's own, §2.3), a defined deployment procedure, water-filling
point placement standards, and a clean-water filter with its own installation precautions. No
electrical, network, or docking-interface detail was found — this document, like §2.5, does not
describe *how* the robot and workstation physically or electrically connect, only how to place/
install the workstation itself.

### 2.7 "C40-APP" (Clean App Operation Manual) — id 186, headings + partial text

Mostly images (34), but one heading carried real body text. Section headings
(`OFFICIAL DOCUMENTATION CONFIRMED`):

1. APP Introduction
2. Immediate Cleaning
3. Scheduled Cleaning
4. Teaching Mode
5. Manual Push Cleaning
6. Function Introduction — **body text present:** *"Push to the landmark to restore positioning.
   Push it to the charging station to restore [positioning]."* (`OFFICIAL DOCUMENTATION CONFIRMED`,
   direct quote, lightly reconstructed for the truncated second sentence which the source itself
   cuts off mid-thought). This confirms a manual, physical relocalization-recovery workflow exists
   and is operator-facing: if the robot loses localization, physically pushing it to a known
   landmark or to its charging station is the documented recovery method.

### 2.8 "C40 operation" (Machine Maintenance Methods) — id 144, full text (video-title list)

This document has **no prose body text at all** — every section is a heading followed by an
embedded instructional video, with no surrounding description. The video titles themselves are
still real, official content (`OFFICIAL DOCUMENTATION CONFIRMED` as a list of documented
maintenance/operation procedures), and none of the videos themselves were watched (out of scope —
video content extraction was not attempted in this pass):

Power On; Power Off; Lift and Lower the Handle; Open the Top Cover; Close the Top Cover; Side
Brush Replacement; Main Brush Replacement; Replace Main Brush with Dust Mop Brush; Floor Scrub
Brush Replacement; Replace Floor Scrub Brush with Mopping Brush; Squeegee Rubber Strip Replacement;
Dust Tank and Sewage Tank Replacement; Dust Bag Replacement; HEPA Filter Replacement; Battery
Replacement.

### 2.9 "C40-Maintenance Manual" (Machine Maintenance Methods) — id 147, full text (video-title list)

Same structure as §2.8 — headings + embedded video, no prose:

Sweeping brush cleaning; Scrubbing brush cleaning; Water squeegee cleaning; Dirty water suction
pipe cleaning; Dust suction hose cleaning; HEPA filter cleaning.

### 2.10 "C40 Exploded view" (Hardware Connection Diagram) — id 191, full image reviewed

**This is the one document filed under the "Hardware Connection Diagram" category, and its title
image was successfully downloaded and visually reviewed in full.** Its actual content is
important to state precisely: **it is a mechanical parts exploded-view diagram, not an electrical,
wiring, or network interface diagram.** See §7 for why this matters as a documented mismatch.

The reviewed image ("C40 Complete Unit Exploded View") labels 18 numbered mechanical assemblies
(`OFFICIAL DOCUMENTATION CONFIRMED`, transcribed directly from the diagram):

1. Screen handle assembly
2. Top shell assembly
3. Outer Housing Assembly
4. Battery Compartment Assembly
5. Fan Assembly
6. Dirty Water Tank Assembly
7. Clean Water Tank Assembly
8. Water Suction Assembly
9. Scrubbing Assembly
10. Scrubbing Motor Assembly
11. Hub assembly
12. Water Pump Box Assembly
13. Sweeping Assembly
14. Sweeping Motor Assembly
15. Side Brush Assembly
16. Chassis Assembly
17. Squeegee (+ Connecting Rod Assembly)
18. HEPA Assembly

15 further sub-assembly detail images exist (one per numbered item, each presumably an exploded
view of that specific assembly) but **were not reviewed** — 2 were attempted and failed to fully
download in this session (Screen handle assembly, Chassis Assembly), so any finer detail they
might contain (e.g. whether the Chassis Assembly image shows an internal electronics bay) is
`UNKNOWN`, not `NOT DOCUMENTED` — the document may document this, but this pass could not read it.

---

## 3. Hardware extraction (against the task's own 25-item checklist)

Per the task's explicit instruction: every row gets Hardware / Official documentation evidence /
Connection-interface / Function / Source document / Status. **No interface is guessed** — every
"Connection/interface" cell is either a direct quote/reading or `NOT DOCUMENTED`.

| Hardware | Official documentation evidence | Connection/interface (if documented) | Function (per official docs) | Source document | Status |
|---|---|---|---|---|---|
| ROS computer / separate SLAM computer | None found anywhere in any of the 10 documents — no mention of "ROS," "SLAM," a second computer, or an internal network | `NOT DOCUMENTED` | `NOT DOCUMENTED` | — | `NOT DOCUMENTED IN OFFICIAL C40 DOCUMENTATION` (separately: `STATIC APK ANALYSIS CONFIRMED` + `PHYSICAL HARDWARE CONFIRMED` exist elsewhere — see §7) |
| Android/UI computer (RK3288) | None found — no board name, model, or "Android" is mentioned anywhere in customer-facing docs | `NOT DOCUMENTED` | `NOT DOCUMENTED` | — | `NOT DOCUMENTED IN OFFICIAL C40 DOCUMENTATION` |
| RK3288 (specifically) | None found | `NOT DOCUMENTED` | `NOT DOCUMENTED` | — | `NOT DOCUMENTED IN OFFICIAL C40 DOCUMENTATION` |
| LiDAR | **"This product is equipped with LIDAR for positioning and navigation, do not look directly at the laser with your eyes."** | `NOT DOCUMENTED` (no interface/wiring given, only a safety statement) | Positioning and navigation | C40 Product Manual §2.3 (§2.1) | `OFFICIAL DOCUMENTATION CONFIRMED` (existence + stated function only) |
| Stereo vision | **"Binocular Stereo Vision"** named explicitly, described as unable to recognize reflective/transparent/light-absorbing elevator interiors and requiring physical paper labels as a workaround | `NOT DOCUMENTED` | Implied: visual landmark/label recognition (elevator entry, by exclusion also implied for staircase-entrance labels) | Mapping Specifications §"Elevator Area Mapping Operation" (§2.4) | `OFFICIAL DOCUMENTATION CONFIRMED` (existence + one specific limitation/use case) |
| Depth camera | Not named as a distinct component anywhere; "Binocular Stereo Vision" may or may not be the same physical sensor as a "depth camera" — the documentation never uses the term "depth camera" | `NOT DOCUMENTED` | `NOT DOCUMENTED` | — | `NOT DOCUMENTED IN OFFICIAL C40 DOCUMENTATION` (do not assume it is or isn't the same as the stereo-vision sensor above) |
| Ultrasonic sensors | None found | `NOT DOCUMENTED` | `NOT DOCUMENTED` | — | `NOT DOCUMENTED IN OFFICIAL C40 DOCUMENTATION` |
| vSLAM | None found — "SLAM" as a term never appears | `NOT DOCUMENTED` | `NOT DOCUMENTED` | — | `NOT DOCUMENTED IN OFFICIAL C40 DOCUMENTATION` |
| Cameras (general) | Only the stereo-vision sensor above is named; no other camera is mentioned | `NOT DOCUMENTED` | See stereo vision row | — | `NOT DOCUMENTED IN OFFICIAL C40 DOCUMENTATION` beyond the stereo-vision row |
| Hub motors | "Hub assembly" (item 11) labeled in the exploded-view diagram | `NOT DOCUMENTED` (mechanical assembly only, no electrical spec) | Drive wheel hub (by position in diagram, adjacent to Scrubbing Motor Assembly and Sweeping Motor Assembly) | C40 Exploded view (§2.10) | `OFFICIAL DOCUMENTATION CONFIRMED` (existence + assembly name only) |
| Hall sensors | None found | `NOT DOCUMENTED` | `NOT DOCUMENTED` | — | `NOT DOCUMENTED IN OFFICIAL C40 DOCUMENTATION` |
| Actuators (general) | Covered by the specific assemblies below; no separate generic "actuator" documentation | — | — | — | See specific rows |
| Water pumps | "Water Pump Box Assembly" (item 12) | `NOT DOCUMENTED` | Water delivery for cleaning (by position/name only) | C40 Exploded view (§2.10) | `OFFICIAL DOCUMENTATION CONFIRMED` (existence + assembly name only) |
| Water-level sensors | None found by name; workstation's "Deployment Standard for Workstation Water Filling Points" heading exists but its body (images) was not reviewed | `NOT DOCUMENTED` | `NOT DOCUMENTED` | Deployment Of Workstation heading list (§2.6) | `NOT DOCUMENTED IN OFFICIAL C40 DOCUMENTATION` (heading exists; content not reviewed, so this is a gap in this pass, not a confirmed absence — recorded honestly as `NOT DOCUMENTED` per this pass's own coverage, not as `UNKNOWN` hardware behavior) |
| Side brush motors | "Side Brush Assembly" (item 15); maintenance doc separately confirms "Side Brush Replacement" as a documented procedure | `NOT DOCUMENTED` (mechanical only) | Side sweeping | C40 Exploded view (§2.10); C40 operation video list (§2.8) | `OFFICIAL DOCUMENTATION CONFIRMED` (existence + assembly name + maintenance procedure name) |
| Mopping brush motors | "Replace Main Brush with Dust Mop Brush" and "Replace Floor Scrub Brush with Mopping Brush" confirm a mopping-brush accessory exists and is swappable with other brush types | `NOT DOCUMENTED` | Mopping (as an alternate brush configuration, not a separate dedicated motor per the diagram — the diagram does not label a distinct "mop motor") | C40 operation video list (§2.8) | `OFFICIAL DOCUMENTATION CONFIRMED` (existence as a swappable brush type); whether it has its own dedicated motor distinct from the Scrubbing/Sweeping motors is `NOT DOCUMENTED` |
| Scrubber roller motor | "Scrubbing Assembly" (item 9) + "Scrubbing Motor Assembly" (item 10) | `NOT DOCUMENTED` | Floor scrubbing | C40 Exploded view (§2.10) | `OFFICIAL DOCUMENTATION CONFIRMED` (existence + assembly name only) |
| Vacuum motors | "Fan Assembly" (item 5); maintenance docs separately reference "Dust suction hose cleaning" and "Dirty water suction pipe cleaning" | `NOT DOCUMENTED` | Vacuum/suction (dust and dirty-water suction) | C40 Exploded view (§2.10); C40-Maintenance Manual video list (§2.9) | `OFFICIAL DOCUMENTATION CONFIRMED` (existence + assembly name only) |
| Charging system | Charging-pile placement standard fully documented (§2.4); "Basic Knowledge Of Charging Stations & Workstation" heading exists (§2.6, content not reviewed) | `NOT DOCUMENTED` (electrical/contact-interface spec not given — only placement geometry) | Robot charging, self-docking (implied by "push it to the charging station to restore [positioning]," §2.7) | Mapping Specifications (§2.4); Deployment Of Workstation heading list (§2.6) | `OFFICIAL DOCUMENTATION CONFIRMED` (placement standard + one behavioral fact); electrical interface `NOT DOCUMENTED IN OFFICIAL C40 DOCUMENTATION` |
| Workstation | Environment standards, basic knowledge, deployment procedure, water-filling-point standard, and clean-water-filter installation precautions all confirmed as named, documented topics (headings only — body images not reviewed) | `NOT DOCUMENTED` | Water filling/refill, filtration (by heading name only) | Deployment Of Workstation heading list (§2.6) | `OFFICIAL DOCUMENTATION CONFIRMED` (existence + 5 named topics); operational/interface detail `NOT DOCUMENTED IN OFFICIAL C40 DOCUMENTATION` (headings only, images unreviewed) |
| Internal communication buses | None found | `NOT DOCUMENTED` | `NOT DOCUMENTED` | — | `NOT DOCUMENTED IN OFFICIAL C40 DOCUMENTATION` |
| Ethernet | None found | `NOT DOCUMENTED` | `NOT DOCUMENTED` | — | `NOT DOCUMENTED IN OFFICIAL C40 DOCUMENTATION` |
| USB | None found | `NOT DOCUMENTED` | `NOT DOCUMENTED` | — | `NOT DOCUMENTED IN OFFICIAL C40 DOCUMENTATION` |
| Serial/UART | None found | `NOT DOCUMENTED` | `NOT DOCUMENTED` | — | `NOT DOCUMENTED IN OFFICIAL C40 DOCUMENTATION` |
| CAN | None found | `NOT DOCUMENTED` | `NOT DOCUMENTED` | — | `NOT DOCUMENTED IN OFFICIAL C40 DOCUMENTATION` |
| Other interfaces | The only network-adjacent statement found anywhere in official docs is the mapping precaution that "the robot and phone [must be] on the same LAN" for remote-control-assisted carpet mapping (§2.4) — this confirms Wi-Fi/LAN connectivity between the operator's phone (running the app) and the robot exists and is used operationally, but says nothing about Ethernet/USB/serial/CAN | Wi-Fi/LAN (phone-to-robot only; nothing about robot-to-SLAM-computer or robot-to-cloud) | Remote-control mapping assistance on carpet | Mapping Specifications (§2.4) | `OFFICIAL DOCUMENTATION CONFIRMED` (this one specific LAN requirement only) |

**Summary count for this table: 8 rows carry at least one `OFFICIAL DOCUMENTATION CONFIRMED` cell
(LiDAR, stereo vision, hub motors, water pumps, side brush motors, mopping brush existence,
scrubber/vacuum motors, charging placement standard, workstation topics, LAN requirement — note
several rows share evidence); 17 rows are entirely `NOT DOCUMENTED IN OFFICIAL C40 DOCUMENTATION`.**

---

## 4. Mapping extraction

| Topic | Official documentation finding | Status |
|---|---|---|
| Map creation / procedure | Two-layer model (base + application layer); 9-step named workflow (Map preparation → Multi-Robot Management → Scan → Map editing → Point setting → Area Settings → Mode Settings → Settings → Map upload) — step *names* confirmed, step *content* mostly not reviewed (images) | `OFFICIAL DOCUMENTATION CONFIRMED` (structure/names); step detail mostly `NOT DOCUMENTED` in this pass (images unreviewed) |
| Mapping prerequisites | Scene must be restored to normal daily layout; temporary obstacles removed; starting point must have distinct wall features; pushing speed/rotation-speed limits enforced with an audible alarm | `OFFICIAL DOCUMENTATION CONFIRMED` |
| Internet requirement | Not addressed anywhere — the only network statement found is phone-robot LAN pairing for carpet-area remote-control mapping specifically, not a general internet/cloud requirement statement | `NOT DOCUMENTED IN OFFICIAL C40 DOCUMENTATION` (neither confirms nor denies an internet requirement for mapping in general) |
| Cloud requirement | Not addressed | `NOT DOCUMENTED IN OFFICIAL C40 DOCUMENTATION` |
| Map storage | Not addressed (no mention of where/how a map is stored — locally, in the app, on a server) | `NOT DOCUMENTED IN OFFICIAL C40 DOCUMENTATION` |
| Map upload | Named as step 9 of the mapping workflow ("Map upload") | `OFFICIAL DOCUMENTATION CONFIRMED` (exists as a named step); mechanism `NOT DOCUMENTED` |
| Map download | Not named anywhere as a distinct step | `NOT DOCUMENTED IN OFFICIAL C40 DOCUMENTATION` |
| Localization | "Push to the landmark to restore positioning. Push it to the charging station to restore [positioning]" — a manual physical relocalization-recovery procedure | `OFFICIAL DOCUMENTATION CONFIRMED` (this one mechanism only) |
| Initial pose | Not addressed by name ("initial pose"/"init pose" never appears in official text) | `NOT DOCUMENTED IN OFFICIAL C40 DOCUMENTATION` |
| Multi-floor mapping | Not addressed directly, but the elevator-area mapping procedure (full-circle scan, physical labels, 3 named reference points) strongly implies multi-floor operation is a supported, documented workflow, just not under that exact name | `INFERRED` from the elevator-mapping procedure; not literally named "multi-floor mapping" |
| Map editing | Named as step 4 of the mapping workflow; restriction-line editing rules documented in detail (§2.4) | `OFFICIAL DOCUMENTATION CONFIRMED` |
| Restricted areas / virtual walls | Explicitly named as application-layer elements ("virtual walls," "restriction lines," "speed limit zone"); detailed editing rules given (system-generated lines must not be erased; must be closed or navigation fails) | `OFFICIAL DOCUMENTATION CONFIRMED` |
| Cleaning areas | "Cleaning Area and Cleaning Route Handling Methods" section: global-first area generation, worked example of separating a hotel room-door area, guidance to re-divide areas if routes look distorted | `OFFICIAL DOCUMENTATION CONFIRMED` |
| Map deployment | "Clean Map Deployment Manual" category exists with 2 documents (mapping manual + workstation deployment); workstation deployment headings confirmed (§2.6) | `OFFICIAL DOCUMENTATION CONFIRMED` (as a named, documented process); step-by-step detail mostly `NOT DOCUMENTED` (images unreviewed) |
| Map backup | Not addressed anywhere in official text | `NOT DOCUMENTED IN OFFICIAL C40 DOCUMENTATION` |
| Multi-Robot Management | Named as step 2 of the mapping workflow; no further detail available (heading only, images unreviewed) | `OFFICIAL DOCUMENTATION CONFIRMED` (exists as a named step); content `NOT DOCUMENTED` |

---

## 5. ROS / SLAM extraction

Per the task's explicit instruction, stated exactly as required:

- ROS: **NOT DOCUMENTED IN OFFICIAL C40 DOCUMENTATION.**
- SLAM: **NOT DOCUMENTED IN OFFICIAL C40 DOCUMENTATION** (the word "SLAM" does not appear anywhere
  in any of the 10 documents' extracted text or reviewed images).
- vSLAM: **NOT DOCUMENTED IN OFFICIAL C40 DOCUMENTATION.**
- LiDAR: `OFFICIAL DOCUMENTATION CONFIRMED` to exist, stated function "positioning and navigation"
  (§2.1/§3) — this is the one item in this list with any official coverage at all.
- Localization / pose: only the manual push-to-landmark/push-to-charging-station recovery
  procedure is documented (§2.7/§4); no mention of a "pose," coordinate frame, or localization
  algorithm.
- Navigation: never described technically; only behavioral/physical specs (passability, climbing
  angles, etc., §2.3) are documented — **NOT DOCUMENTED IN OFFICIAL C40 DOCUMENTATION** for any
  navigation-stack detail.
- Mapping computer / robot computer: **NOT DOCUMENTED IN OFFICIAL C40 DOCUMENTATION** — no mention
  of a second computer, an "onboard computer," or any compute-hardware distinction at all. The
  documentation's entire framing treats "the robot" as a single unit throughout.
- Internal network: **NOT DOCUMENTED IN OFFICIAL C40 DOCUMENTATION**, except for the one
  phone-to-robot LAN pairing statement for carpet mapping (§2.4/§3), which is about the
  operator's phone, not an internal robot-to-robot or robot-to-SLAM-computer network.
- ROS topics / ROS services: **NOT DOCUMENTED IN OFFICIAL C40 DOCUMENTATION.**
- rosbridge: **NOT DOCUMENTED IN OFFICIAL C40 DOCUMENTATION.**

**This section was not inferred from Phase 4's already-established generic Keenon architecture
findings, per the task's explicit instruction** — every line above reflects only what was or was
not found in the 10 official C40 documents read in this pass.

---

## 6. Cleaning system extraction

| Topic | Official documentation finding | Status |
|---|---|---|
| Sweep | "Sweeping Assembly" + "Sweeping Motor Assembly" (items 13-14, exploded view); "Sweeping brush cleaning" maintenance procedure | `OFFICIAL DOCUMENTATION CONFIRMED` |
| Mop | "Replace Main Brush with Dust Mop Brush" / "Replace Floor Scrub Brush with Mopping Brush" — mopping exists as a swappable brush configuration | `OFFICIAL DOCUMENTATION CONFIRMED` (as an accessory/configuration); no dedicated "mop motor" named separately |
| Vacuum | "Fan Assembly" (item 5); "Dust suction hose cleaning," "Dust Bag Replacement," "HEPA Filter Replacement" maintenance procedures | `OFFICIAL DOCUMENTATION CONFIRMED` |
| Scrub | "Scrubbing Assembly" + "Scrubbing Motor Assembly" (items 9-10); "Scrubbing brush cleaning," "Floor Scrub Brush Replacement" maintenance procedures | `OFFICIAL DOCUMENTATION CONFIRMED` |
| Side brushes | "Side Brush Assembly" (item 15); "Side Brush Replacement" maintenance procedure (remove by hand, reinstall until a click is heard) | `OFFICIAL DOCUMENTATION CONFIRMED` |
| Roller | Not named by this exact term — "Scrubbing Assembly"/"Sweeping Assembly" are the closest documented equivalents | `NOT DOCUMENTED IN OFFICIAL C40 DOCUMENTATION` under the term "roller" specifically |
| Water | "Dirty Water Tank Assembly" + "Clean Water Tank Assembly" (items 6-7); "Water Suction Assembly" (item 8); "Dirty water suction pipe cleaning," "Water squeegee cleaning" procedures; "Sewage tank: check for sludge/debris" | `OFFICIAL DOCUMENTATION CONFIRMED` |
| Detergent | Not mentioned anywhere in any reviewed document | `NOT DOCUMENTED IN OFFICIAL C40 DOCUMENTATION` |
| Pumps | "Water Pump Box Assembly" (item 12) | `OFFICIAL DOCUMENTATION CONFIRMED` (existence + name only) |
| Water levels | "Deployment Standard for Workstation Water Filling Points" heading exists; content (images) not reviewed | `NOT DOCUMENTED IN OFFICIAL C40 DOCUMENTATION` beyond the heading itself |
| Cleaning modes | Not named/enumerated in any reviewed official text (the "Immediate Cleaning" / "Scheduled Cleaning" / "Teaching Mode" / "Manual Push Cleaning" app-section headings describe *trigger methods*, not brush/water cleaning-mode combinations like Sakar's own UI's "Sweep & Mop / Water Suction / Sweep & Vacuum / Sweep & Push / Sweep") | `NOT DOCUMENTED IN OFFICIAL C40 DOCUMENTATION` for a mode taxonomy matching Sakar's own UI; the app's own *trigger-method* sections are `OFFICIAL DOCUMENTATION CONFIRMED` (§2.7) |
| Cleaning schedules | "Scheduled Cleaning" confirmed as a named app section/feature; no scheduling parameters (frequency, recurrence rules) documented in reviewed text | `OFFICIAL DOCUMENTATION CONFIRMED` (feature exists); parameters `NOT DOCUMENTED` |
| Cleaning areas | Fully documented — see §4 | `OFFICIAL DOCUMENTATION CONFIRMED` |
| Workstation | 5 named topics confirmed (§2.6); interface/mechanism detail not reviewed (images) | `OFFICIAL DOCUMENTATION CONFIRMED` (existence + topic names); mechanism `NOT DOCUMENTED` |
| Automatic water fill | Implied by "Workstation Water Filling Points" and "Clean Water Filter" headings, but never described as "automatic" in any reviewed text — could be manual or automatic, not stated | `NOT DOCUMENTED IN OFFICIAL C40 DOCUMENTATION` (the word "automatic" is not used; do not assume automation) |
| Drainage | Not mentioned anywhere in reviewed text — "Dirty Water Tank" and "sewage tank" checks are maintenance/emptying instructions, not a described drainage mechanism | `NOT DOCUMENTED IN OFFICIAL C40 DOCUMENTATION` |
| Charging | Covered in §3's "Charging system" row | `OFFICIAL DOCUMENTATION CONFIRMED` (placement standard + push-to-charge relocalization behavior) |

---

## 7. Comparison against APK reverse-engineering findings — conflicts and refinements recorded explicitly

Per the task's explicit instruction, **nothing below reconciles a disagreement silently.** Each row
states both sides and names what kind of relationship it is.

| # | APK reverse-engineering finding (Phase 3/4, `STATIC APK ANALYSIS CONFIRMED`) | Official documentation finding (this pass) | Relationship |
|---|---|---|---|
| 1 | The task's own framing, and this project's prior documentation, treated "Hardware Connection Diagram" as the document expected to reveal RK3288/SLAM-computer/network interface information (see the original task instruction's own hardware-extraction framing) | The one document actually filed under "Hardware Connection Diagram" (`C40 Exploded view`, §2.10) is a **mechanical parts exploded-view diagram** — it shows physical assemblies (fan, water tanks, motors, brushes) with no electrical, wiring, or network content whatsoever | **Scope mismatch, not a factual conflict.** The category name promises more than its one published document delivers. Recorded so nobody assumes "Hardware Connection Diagram" already covers electrical/network interfaces just because a document exists under that name. |
| 2 | A separate, non-Android SLAM computer exists, reachable (per static analysis) at `192.168.64.20:9090`/`:9091` (`01_MASTER_ENGINEERING_KNOWLEDGE_BASE.md` §4, E-011/E-012) | No official C40 document mentions a second computer, an internal network, an IP address, or ROS/SLAM by name at all | **No conflict — a gap, not a contradiction.** Customer-facing documentation of this kind commonly omits internal system architecture entirely; the official documentation's silence on this point neither confirms nor denies the APK-based finding. The APK-based finding's status is unchanged by this pass. |
| 3 | Phase 4 inferred "camera/VSLAM... ARCore libraries bundled" for pose assistance, without distinguishing sensor types (`01_MASTER_ENGINEERING_KNOWLEDGE_BASE.md` §10) | Official docs name **two distinct sensing modalities**: "LIDAR" (positioning and navigation) and "Binocular Stereo Vision" (elevator/label recognition, explicitly described as unable to see through/reflect off certain surfaces) | **Refinement, not a conflict.** The official documentation gives named, distinct identities to what Phase 4 had only bundled together as an `INFERRED` "camera/VSLAM" capability. This does not prove the ARCore-based code path *is* the same "Binocular Stereo Vision" component named officially — that mapping is itself `UNKNOWN`, not asserted here. |
| 4 | The public Peanut SDK has no `CleanComponent` and no cleaning-hardware control API at all (`NOT FOUND`, `01_MASTER_ENGINEERING_KNOWLEDGE_BASE.md` §14, E-015) | The official exploded-view diagram and maintenance manuals confirm the *physical* cleaning hardware (fan, pumps, brush motors, water tanks) genuinely exists on the robot | **No conflict.** These are answers to two different questions: the SDK finding is about what the *licensed public API* exposes to third-party developers (nothing), while the official documentation is about what physically exists on the customer's own machine (a lot). A robot can have real cleaning hardware that a specific SDK tier simply doesn't expose — both findings stand unchanged. |
| 5 | Phase 4 found the entire `Mapping.db`/rosbridge data path to be local-only, with no cloud call observed in that specific path (`01_MASTER_ENGINEERING_KNOWLEDGE_BASE.md` §13) | Official documentation states the operator's phone and the robot must be "on the same LAN" for carpet-area remote-control mapping (§2.4), and separately names an undetailed "Map upload" step | **Consistent, with one open thread.** The LAN-pairing requirement is consistent with (does not contradict) the static finding of a local-only mapping data path. The "Map upload" step's actual destination (local file vs. some cloud/console target) is `NOT DOCUMENTED` here and was not resolved by Phase 4 either — this remains an open question on **both** sides, not a conflict between them. |
| 6 | No prior finding addressed a maximum map area or physical passability specs numerically | Official docs give exact numbers (≤20,000 m² per map; 650mm min width; 800mm min U-turn; etc., §2.3) | **New information, no prior finding to compare against.** Recorded as new rather than as a conflict or refinement. |

**No item in this audit was found to directly contradict a specific, previously-recorded APK
reverse-engineering fact.** Every point of contact above is either a scope mismatch, a refinement
of a previously-bundled inference into named specifics, a "different question, both answers
stand" situation, or genuinely new information with nothing prior to compare against.

---

## 8. Hardware-replacement-relevant findings (interfaces of the *original* hardware only)

Per the task's explicit instruction, this section identifies only what the official documentation
reveals about interfaces the *original* Keenon hardware requires — it draws **no conclusion** about
ROCK 4D or any other replacement candidate's compatibility.

- The official documentation never describes the RK3288, the Android/application computer, or any
  internal compute hardware by name, model, or interface (§3). **There is nothing in the official
  documentation that can be used to evaluate a compute-board replacement's interface
  compatibility** — that evaluation, if it happens, must rely entirely on the physical/APK-based
  evidence already in `03_HARDWARE_ARCHITECTURE.md`, not on anything found in this pass.
  `06_UNVERIFIED_ITEMS.md` items 3-7 and 29-31 (RK3288 pinouts, the SLAM-computer's physical link,
  ROCK 4D compatibility) are **unchanged** by this audit — nothing here resolves any of them.
- The one concrete interface fact this pass *does* newly confirm — a LAN connection is used
  between the operator's phone and the robot during at least one workflow (§2.4/§3) — describes an
  **external, operator-facing Wi-Fi connection**, not an internal board-to-board interface. It has
  no direct bearing on RK3288-to-SLAM-computer replacement questions.
- No electrical, power, or connector specification of any kind was found for any component named
  in this document (LiDAR, stereo-vision sensor, fan, pumps, or motors) — replacement/compatibility
  evaluation for any of these individual components is equally unsupported by this pass.

---

## 9. What this pass explicitly did NOT do

- Did not review roughly 180 of the ~185 total page-scan images across the 10 documents — see §0
  for why, and §2 for exactly which few were reviewed.
- Did not watch any of the ~21 embedded instructional videos referenced in §2.8/§2.9 (or the
  several videos referenced within the mostly-image documents) — only their titles were read.
- Did not attempt to access any Keenon documentation for any product other than C40 (T3/T8/T9/
    T9Pro/T10/T11/W3-family robots, also visible in the portal's own navigation, were not opened).
- Did not attempt to log in, register, or access any authenticated/non-public area of the portal —
  everything read was reachable without credentials.
- Did not modify `SakarC40Agent` code, UI, or configuration, and did not create or modify any APK,
  per the task's own explicit scope.

---

## 11. Three-way comparison — official documentation vs. APK reverse engineering vs. physical observation

**This section responds to a later continuation of this same audit task, which reported a new,
detailed physical observation of the separate ROS/Robot Computer's sensor/actuator connections and
asked for every finding to be compared three ways, with an explicit status from this exact set:
`OFFICIAL DOCUMENTATION CONFIRMED`, `NOT DOCUMENTED`, `CONFLICT WITH APK ANALYSIS`, `REQUIRES
PHYSICAL VERIFICATION`.** These four labels are used only in this section, as a compact
cross-reference; the fuller vocabulary in this document's own intro (with `INFERRED`/`UNKNOWN`
distinctions) remains authoritative for §1-§9 above and is not replaced by it.

### 11.1 Separate ROS/Robot Computer and its sensor/actuator connections

- **OFFICIAL DOCUMENTATION:** `NOT DOCUMENTED`. No official C40 document names a second computer,
  states where LiDAR data is processed, or describes any sensor/actuator wiring (§5 above).
- **APK REVERSE ENGINEERING:** `STATIC APK ANALYSIS CONFIRMED` — a separate SLAM computer,
  expected reachable at `192.168.64.20:9090`/`:9091` running rosbridge
  (`01_MASTER_ENGINEERING_KNOWLEDGE_BASE.md` §4); **and, separately**, a **third**, distinct
  motor/motion-controller entity reached from the licensed SDK via a raw "SCM IoT" protocol
  (`03_HARDWARE_ARCHITECTURE.md` §3, `02_EVIDENCE_REGISTER.md` E-016).
- **PHYSICAL OBSERVATION:** `PHYSICAL-HARDWARE CONFIRMED` (this session) — the separate ROS/Robot
  Computer is directly connected to / responsible for LiDAR, stereo vision, ultrasonic sensors, hub
  motors, actuators, water pumps, water-level sensors, Hall sensors, depth camera, two side-brush
  motors, mopping-brush motors, scrubber roller motor, vacuum motors, vSLAM, and multiple cameras
  (some USB, some via an unspecified internal wired interface). Board model, CPU, OS, ROS version,
  buses, protocols, and pinouts are explicitly **not** part of this confirmation (`02_EVIDENCE_
  REGISTER.md` E-051).
- **STATUS:** `CONFLICT WITH APK ANALYSIS` — **not resolved, recorded as open.** All three sources
  agree a non-Android compute node exists and handles SLAM/sensor processing. They do **not**
  agree on how motor/actuator control fits in: the APK evidence names a *separate, third* SCM-IoT
  board for motors, while the new physical observation describes the ROS/Robot Computer itself as
  "responsible for" hub motors and actuators. Three readings remain equally possible and **none is
  chosen here**: (a) the ROS/Robot Computer relays motion commands to the SCM-IoT board, which is a
  real, distinct fourth piece of hardware; (b) the SCM-IoT protocol actually addresses the ROS/
  Robot Computer itself under a different description; (c) the physical "responsible for" framing
  is at the system level (this computer's software orchestrates motor behavior) while the
  SCM-IoT-addressed board remains the actual electrical driver, consistent with (a). See
  `02_EVIDENCE_REGISTER.md` E-052 and `03_HARDWARE_ARCHITECTURE.md` §2.1/§3 for the full statement
  of what remains unresolved. `REQUIRES PHYSICAL VERIFICATION` to resolve.

  **Update, 2026-09-26 — photographic evidence added, still not resolved:** a set of physical
  photographs was reviewed and adds two concrete data points, neither of which decides this
  question: a wire bundle explicitly labelled **"CAN"** exists
  (`03_HARDWARE_ARCHITECTURE.md` §6, PH-06/PH-07), and a multi-section custom control PCB carrying
  "STEP MOTOR"/"AIR PR GAUGE 1" labels is a plausible, unconfirmed candidate for the SCM-IoT
  motor-controller board (PH-02). A separate enclosure labelled **"ARM IPC"** (PH-08) is the
  strongest physical candidate yet for the ROS/Robot Computer itself. Full per-photo detail:
  [`10_PHYSICAL_HARDWARE_PHOTO_EVIDENCE.md`](10_PHYSICAL_HARDWARE_PHOTO_EVIDENCE.md); the topology
  this evidence maps onto (drawn explicitly as `UNKNOWN` pending verification):
  `03_HARDWARE_ARCHITECTURE.md` §6.1.

### 11.2 LiDAR

- **OFFICIAL DOCUMENTATION:** `OFFICIAL DOCUMENTATION CONFIRMED` — "This product is equipped with
  LIDAR for positioning and navigation" (§2.1).
- **APK REVERSE ENGINEERING:** `STATIC APK ANALYSIS CONFIRMED` — `LIDAR_CHECK_RANGING`,
  `LIDAR_CHECK_MATCHING`, `LIDAR_VERIFY` diagnostic API names exist in the licensed SDK
  (`01_MASTER_ENGINEERING_KNOWLEDGE_BASE.md` §19); the Keenon-custom `/scan_base_map` topic implies
  LiDAR-sourced scan data (§12 there).
- **PHYSICAL OBSERVATION:** `PHYSICAL-HARDWARE CONFIRMED` — LiDAR is connected to the ROS/Robot
  Computer (§11.1 above).
- **STATUS:** No conflict. All three sources independently agree LiDAR exists; none contradicts
  another.

### 11.3 Stereo vision / depth camera

- **OFFICIAL DOCUMENTATION:** `OFFICIAL DOCUMENTATION CONFIRMED` for "Binocular Stereo Vision"
  specifically (named, used for elevator/label recognition, §2.4). "Depth camera" as a separate
  term: `NOT DOCUMENTED`.
- **APK REVERSE ENGINEERING:** Only an `INFERRED`, bundled "camera/VSLAM... ARCore libraries"
  finding (`01_MASTER_ENGINEERING_KNOWLEDGE_BASE.md` §10) — never named as "stereo vision" or
  "depth camera" specifically in decompiled evidence.
- **PHYSICAL OBSERVATION:** `PHYSICAL-HARDWARE CONFIRMED` — the user names **both** "stereo vision"
  and "depth camera" as separate connected items.
- **STATUS:** No direct conflict, but an open question recorded rather than resolved: whether
  official "Binocular Stereo Vision," the physically-observed "stereo vision," and the
  physically-observed "depth camera" are one sensor, two sensors, or three is `UNKNOWN` — none of
  the three sources states this, and none is guessed here. `REQUIRES PHYSICAL VERIFICATION`.

### 11.4 "Hardware Connection Diagram" category content

- **OFFICIAL DOCUMENTATION:** `OFFICIAL DOCUMENTATION CONFIRMED` (as to what it actually contains)
  — the one document under this category ("C40 Exploded view") is a mechanical parts exploded-view
  diagram (18 labeled assemblies), with zero electrical/network/interface content (§2.10/§7 row 1).
- **APK REVERSE ENGINEERING:** Not applicable — no APK claims to be a hardware connection diagram.
- **PHYSICAL OBSERVATION:** The new physical observation (§11.1) is, in this project, the closest
  thing to an actual hardware-connection description that exists anywhere — and it did not come
  from this official category at all.
- **STATUS:** `NOT DOCUMENTED` (official docs do not deliver what their own category name
  promises for electrical/network interfaces) — not a `CONFLICT WITH APK ANALYSIS`, simply a gap
  between a category's name and its one published document's actual content.

### 11.5 Multiple cameras (USB vs. internal-wired)

- **OFFICIAL DOCUMENTATION:** `NOT DOCUMENTED`.
- **APK REVERSE ENGINEERING:** `STATIC APK ANALYSIS CONFIRMED` only for bundled ARCore/camera
  library presence; no USB/interface-level detail found in decompiled evidence.
- **PHYSICAL OBSERVATION:** `PHYSICAL-HARDWARE CONFIRMED` — multiple cameras exist; some are
  USB-connected, some via an unspecified internal wired interface.
- **STATUS:** No conflict between sources — each contributes a different level of detail, and none
  contradicts another. Exact interface/device IDs: `REQUIRES PHYSICAL VERIFICATION`.

---

## 12. Cross-references

- [`01_MASTER_ENGINEERING_KNOWLEDGE_BASE.md`](01_MASTER_ENGINEERING_KNOWLEDGE_BASE.md) §32 (new) —
  summary pointer into this document.
- [`02_EVIDENCE_REGISTER.md`](02_EVIDENCE_REGISTER.md) E-037 through E-052 (new rows) — itemized
  IDs for every finding above, including E-051 (the new ROS/Robot Computer connection list) and
  E-052 (the unresolved SCM-IoT relationship, §11.1).
- [`03_HARDWARE_ARCHITECTURE.md`](03_HARDWARE_ARCHITECTURE.md) §5 (new) — official-documentation
  cross-reference section.
- [`04_SLAM_INVESTIGATION_STATUS.md`](04_SLAM_INVESTIGATION_STATUS.md) §11 (new) — official-
  documentation answer to the "what does official documentation say" question this pass was asked
  to add.
- [`05_KEENON_TO_SAKAR_ARCHITECTURE.md`](05_KEENON_TO_SAKAR_ARCHITECTURE.md) — no changes required;
  this pass found nothing that changes the Keenon-vs-Sakar comparison table's content, only
  additional detail on the Keenon side already reflected here.
- [`06_UNVERIFIED_ITEMS.md`](06_UNVERIFIED_ITEMS.md) §"Official documentation" (new) — items this
  pass could not resolve, added without disturbing the existing numbered list; items 7a-7c (new) —
  the ROS/Robot Computer's own unresolved specifics.

---

## Final Report

**OFFICIAL DOCUMENTS REVIEWED:**
10 of 10 published C40 documents (100% of what exists on the portal), spanning 8 of the 11
requested categories — 3 categories (Merchant Quick Action Guide, Cloud Platform User Manual,
Mobile App User Manual) have zero published documents. Full text/headings retrieved for all 10;
only 5 of ~185 total page-scan images were successfully downloaded and visually reviewed, due to a
reproducible server-side reliability problem affecting large binary assets (§0).

**HARDWARE FINDINGS:**
LiDAR (existence + stated purpose, `OFFICIAL DOCUMENTATION CONFIRMED`); Binocular Stereo Vision
(existence + one use case, `OFFICIAL DOCUMENTATION CONFIRMED`); 18 labeled mechanical assemblies
from the exploded-view diagram (`OFFICIAL DOCUMENTATION CONFIRMED`); a charging-pile placement
standard; a phone-to-robot LAN requirement for one mapping workflow. Separately, from the new
physical observation (not from official documentation): the ROS/Robot Computer's connection to
LiDAR, stereo vision, ultrasonic sensors, hub motors, actuators, water pumps, water-level sensors,
Hall sensors, depth camera, side-brush motors, mopping-brush motors, scrubber roller motor, vacuum
motors, vSLAM, and multiple cameras — all `PHYSICAL-HARDWARE CONFIRMED`, none of it from official
documentation (§11.1).

**MAPPING FINDINGS:**
A two-layer map model (base/application layer); a 9-step named mapping workflow; a detailed
elevator-labeling procedure with exact distances; 3 named elevator reference points;
cleaning-area/route generation rules; a manual push-to-recover relocalization procedure. All
`OFFICIAL DOCUMENTATION CONFIRMED` (§4).

**ROS/SLAM FINDINGS:**
Essentially none from official documentation — ROS, SLAM, vSLAM (as a term), rosbridge, ROS
topics/services, and any internal-computer/network detail are all `NOT DOCUMENTED` (§5). LiDAR is
the one exception with real official coverage. From the physical-observation side (not official
documentation): vSLAM is now `PHYSICAL-HARDWARE CONFIRMED` to run in connection with the ROS/Robot
Computer, alongside the sensor list above (§11.1).

**CLEANING FINDINGS:**
Sweep, mop (as a swappable configuration), vacuum, scrub, side brushes, water tanks/pump, and
maintenance procedures for each — all `OFFICIAL DOCUMENTATION CONFIRMED` by name (§6). Cleaning
modes taxonomy, detergent, drainage, and "automatic" water-fill: `NOT DOCUMENTED`.

**CONFLICTS:**
One recorded, unresolved `CONFLICT WITH APK ANALYSIS`: whether the SDK's SCM-IoT-addressed
motor/motion-controller board is the same hardware as the ROS/Robot Computer, a distinct board
reached through it, or something else — the new physical observation and the pre-existing APK
finding are not decided against each other (§11.1). All other points of contact between official
documentation and APK analysis (§7) were scope mismatches, refinements, or "no overlap," not direct
contradictions.

**NOT DOCUMENTED:**
ROS, SLAM, rosbridge, RK3288, the Android/UI computer, any internal network/IP/interface detail,
Ethernet/USB/serial/CAN specifics for any component, cleaning-mode taxonomy, detergent, drainage,
map storage/backup mechanism, and the 3 empty document categories (§3-§6, §1).

**REQUIRES PHYSICAL VERIFICATION:**
The SCM-IoT/ROS-Robot-Computer relationship (§11.1); whether "Binocular Stereo Vision," physically-
observed "stereo vision," and physically-observed "depth camera" are one, two, or three sensors
(§11.3); every bus/protocol/pinout/CAN-ID/UART-baud-rate/USB-device-ID/ROS-topic-name for the newly
confirmed ROS/Robot Computer connections (§8, `06_UNVERIFIED_ITEMS.md` items 7a-7c); the exact
mechanism behind "Map upload"/"Multi-Robot Management" (heading-only, images unreviewed).

**MOST IMPORTANT NEW FINDINGS:**
(1) Official confirmation that the robot "is equipped with LIDAR for positioning and navigation" —
the first non-APK, non-physical source to confirm this. (2) The new physical observation that a
single separate ROS/Robot Computer is connected to a much larger set of sensors and actuators than
previously documented (LiDAR, stereo vision, ultrasonic, hub motors, actuators, pumps, water-level
sensors, Hall sensors, depth camera, multiple cameras, side-brush/mopping-brush/scrubber/vacuum
motors, vSLAM) — which substantially widens the scope of any future hardware-replacement
investigation beyond just the RK3288 (§8, `03_HARDWARE_ARCHITECTURE.md` §2.1/§4). (3) The
unresolved tension this creates with the SDK's separately-documented SCM-IoT motor-controller
finding, recorded as an open conflict rather than silently decided.

**STOP AFTER DOCUMENTATION. NO IMPLEMENTATION.** No `SakarC40Agent` code, UI, or APK was touched to
produce this report or any of the file updates it describes.
