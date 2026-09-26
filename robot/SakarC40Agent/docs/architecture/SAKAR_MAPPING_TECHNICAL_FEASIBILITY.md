# Sakar Mapping — Technical Feasibility

**Phase 4 audit. No UI implementation, no Sakar APK creation, no mock SLAM, no fake map data, no
modification to `SakarC40Agent` was performed to produce this document.** This is a feasibility
assessment built entirely on the evidence in
[`KEENON_MAPPING_DEEP_REVERSE_ENGINEERING.md`](KEENON_MAPPING_DEEP_REVERSE_ENGINEERING.md) and the
already-established Peanut SDK documentation (`PEANUT_SDK_C40_API_MATRIX.md`,
`PEANUT_SDK_C40_TECHNICAL_STUDY.md`, `SakarC40Agent/COMPATIBILITY_REPORT.md`).

## How to read "Can reproduce?"

- **Yes (SDK)** — the licensed public Peanut SDK exposes this directly.
- **Yes (own infra)** — Sakar could implement this itself using ordinary local networking/storage,
  without needing anything Keenon-private, but it would be Sakar's own code, not a wrapped Keenon
  API.
- **No (blocked)** — requires a private/internal Keenon protocol, the onboard ROS box's
  undocumented topic/service surface, or a proprietary message format not available to a
  third-party SDK licensee.
- **Unknown** — insufficient evidence either way from this audit.

---

## Feature matrix

| Feature | Keenon implementation | Public Peanut SDK? | Internal API? | Can reproduce? | Sakar requirement | Missing component | Implementation difficulty | Evidence | Confidence |
|---|---|---|---|---|---|---|---|---|---|
| Start mapping | `ROSNAME.BUILDMAPTYPE` rosbridge service call, mode via `setBuildMapType` | No | Yes — rosbridge service on the onboard ROS box | **No (blocked)** | Would need to call a documented "start SLAM" API | The onboard ROS box's actual SLAM launch service/topic contract | High | KMDRE §5, §12 | High |
| Stop mapping | "stop record" UI (`clean.stopRecord`), `BuildMapLocalState` completion event | No | Yes | **No (blocked)** | Same as above | Same as above | High | KMDRE §12, §13 | Medium (exact stop call not isolated) |
| SLAM (core algorithm) | Runs entirely off-Android, on a separate Linux/ROS box at `192.168.64.20` via rosbridge | No | Yes (external, non-Android) | **No (blocked)** | A SLAM engine of Sakar's own, or robot-vendor cooperation | The whole SLAM stack — not present in any decompiled APK at all | Very High | KMDRE §8, §26 | High |
| Live map (occupancy grid render) | Custom WebGL/THREE.js renderer consuming grid metadata from `dynamic_map_info` + a ROS topic feed | No | Yes (data source is rosbridge) | **Yes (own infra), if given a data feed** — the *rendering* is reproducible; the *data feed* is not, without SLAM data | A live occupancy-grid data source | The upstream SLAM/grid publisher | High (rendering: Medium; data: blocked) | KMDRE §10 | Medium |
| Robot pose (live) | `getRobotLocalPosition`/`getRobotPosition`, backed by rosbridge topics | No | Yes | **No (blocked)** for live pose during mapping; **partially available** via SDK for general robot state | A live pose stream during an active SLAM session | The specific rosbridge topic/message this reads | High | KMDRE §11 | Medium |
| LiDAR visualization | `/scan_base_map` ROS topic, rendered via zrender/WebGL overlays | No | Yes | **No (blocked)** | A live LiDAR scan stream | Access to `/scan_base_map` or equivalent | High | KMDRE §5, §8, §10 | Medium |
| Map save | `postDBOperation` → `map` table write (Room/SQLite, local) | Partial — `MapComponent.upload/uploadNew/uploadOpt` transfer an *already-built* map file | Yes, for the live "save what I just built" step | **Partial** — saving/transferring a finished map file is SDK-covered; capturing it *during* a live SLAM session is not | Persisting Sakar's own map representation | A live map to save in the first place (upstream of this) | Medium (once a map exists) | KMDRE §14, §15, §16, §23 | Medium-High |
| Map load | `select`/`selectAll` on the `map` table (local Room) | Partial — `MapComponent.download/downloadOpt` retrieve a map file by transfer | Yes | **Yes (SDK, for file-level load)** | Read a previously saved/transferred map file | None, if a map file already exists via the SDK's transfer path | Low-Medium | KMDRE §15, §23 | High |
| Map rename | **[INFERRED]** generic `update` action on the `map` table's `name` field | No | Yes | **Yes (own infra)**, once Sakar owns its own map storage | A mutable `name` field in Sakar's own map record | n/a — this is trivial once Sakar has any map storage at all | Low | KMDRE §15 (generic CRUD dispatch; no dedicated "rename" call site found) | Low-Medium (inferred, not directly observed) |
| Map delete | Generic `delete`/`deleteAll` action on the `map` table | No | Yes | **Yes (own infra)** | Same as above | Same as above | Low | KMDRE §15 | Medium |
| Multi-floor | `floor` column on `map`/`dynamic_map_info`/`label`; JS-side floor-switch state | No | No — purely an app-level data-modeling convention | **Yes (own infra)** — this is a data-model choice, not a protocol Sakar needs from Keenon | A `floor` field in Sakar's own map/zone records | None — this is Sakar's own schema design to make | Low | KMDRE §17 | High |
| Virtual wall | A `MAPTYPE`-typed layer, likely stored in the `map` table itself; `ROSNAME.VIRTUALWALLAUTOSAVE` service for autosave | No | Yes, for the autosave/ROS-side enforcement of the wall during navigation | **Partial** — drawing/storing wall geometry is reproducible (own infra); making the *robot* respect it during autonomous navigation requires the SLAM/nav stack to honor it, which is blocked | A way to make the robot's own navigation avoid the drawn wall | The onboard nav stack's wall-enforcement mechanism | Medium (drawing) / High (enforcement) | KMDRE §18 | Medium |
| Restricted area | **[INCOMPLETE TRACE]** likely a `map`-table layer type (`cleanZoneTypes` import found, not expanded) | No | Likely yes | **Partial**, same reasoning as virtual wall | Same as virtual wall | Same as virtual wall, plus the exact zone-type taxonomy was not fully traced | Medium/High | KMDRE §19 | Low (incomplete evidence) |
| Cleaning area | Same `cleanZoneTypes` family as restricted area | No | Likely yes | **Partial**, same reasoning | Same as above | Same as above | Medium/High | KMDRE §19 | Low (incomplete evidence) |
| Initial pose | `ROSNAME.CONFIGINITPOSE` rosbridge service; `init_pose` table (`init_method`, `angleRange`, `confidence`, `penetrate`) | No | Yes | **No (blocked)** for setting pose against a live map; storage schema is reproducible | A way to tell the robot "you are here" against its own map | The onboard localization service that accepts this | High | KMDRE §11, §20 | Medium |
| Relocalization | Same `init_pose`/`CONFIGINITPOSE` path; `confidence`/`penetrate` fields suggest AMCL-style particle-filter localization **[INFERRED]** | Partial — `PeanutRuntime.getInstance().location()` is documented as a "SLAM power-on localization trigger" | Yes, for the richer/manual relocalization flow | **Partial** — a basic localization trigger exists in the public SDK; the rich manual/assisted relocalization UI does not | A relocalization trigger | The manual/assisted relocalization protocol beyond the basic SDK trigger | Medium (basic) / High (rich UI) | KMDRE §11, §23 | Medium |
| Elevator | Local `elevator` table (`elevator_id`, `robot_id`, `available_floor`, `transit_floor`, `opendoor_time`) + `ROSNAME.ELEVATORCONFIG` service; a *separate* cloud vendor-IoT lookup exists in `peanut-clean` (`HttpElevatorApi`) | No (neither the local config nor the cloud vendor API is in the public SDK) | Yes, both | **Partial** — storing elevator association data is own-infra-reproducible; actually driving an elevator through vendor IoT integration is a completely separate, business-specific integration (contracts with building elevator vendors), not a robot-SDK question at all | Elevator metadata storage; a business decision on vendor IoT integration if physical elevator control is ever needed | The vendor-specific IoT elevator control contracts/APIs (out of scope for a robot SDK feasibility question) | Low (storage) / Very High (physical elevator control, business-gated) | KMDRE §21 | Medium-High |
| Gate | Local `gate` table (`id`, `floor`, `mac_address`) — BLE/Wi-Fi MAC-based beacon detection | No | Yes | **Yes (own infra)** — this is just "detect a known BLE/Wi-Fi MAC and associate it with a floor," ordinary Android capability | Standard Android BLE/Wi-Fi scanning + a local table | None conceptually; needs its own implementation | Medium | KMDRE §21 | High |
| Offline mapping | Confirmed: the entire mapping data/control path is local-network-only, no internet, no cloud auth required | N/A | N/A | **Yes, in principle** — IF the SLAM computation problem (blocked, see "SLAM" row) is solved by some other means, the rest of the pipeline has no offline restriction | A local SLAM engine or equivalent | The SLAM engine itself (this is the same blocker as the "SLAM" row, restated for the offline framing) | Very High (same root cause as SLAM) | KMDRE §22 | High |
| Cloud sync | Region-specific `console.peanut.keenonrobot.com`-style endpoints; `axios.put` map-file upload; genuinely optional, separate from core mapping | Partial — `MapComponent.upload/download` family is the SDK-level equivalent of this | Yes, Keenon's own richer cloud console is internal | **Yes (SDK, at file-transfer level)** | Optional; only relevant if/when Sakar wants map backup/sync | A Sakar-owned backend to sync to (Keenon's own console is not something Sakar can push to) | Medium | KMDRE §22, §23 | Medium-High |

**"KMDRE" above = `KEENON_MAPPING_DEEP_REVERSE_ENGINEERING.md`.**

---

## Final conclusion

**1. Can Sakar reproduce Keenon's Installation Assistant mapping?**
Not as a whole, not today. The UI shell, database schema, and file-management operations
(save/load/rename/delete/multi-floor/gate/elevator-metadata) are all reproducible with ordinary
engineering effort — none of that requires anything private from Keenon. The one piece that is
**not** reproducible from anything available to Sakar is the SLAM engine itself: it runs entirely
on a separate, non-Android Linux/ROS compute node, communicates over an undocumented custom
rosbridge protocol (`keenon_database_msgs`, Keenon-specific topic/service names), and is not
exposed — in any form — by the public Peanut SDK. Reproducing "mapping" in the full sense Keenon's
own reference screenshots show (live occupancy-grid streaming while driving, real-time
localization, relocalization) requires a SLAM engine Sakar does not have access to today.

**2. Can it be done offline?**
The *architecture* has no offline restriction — everything in the real mapping data/control path
(WebSocket bridge, `Mapping.db`, rosbridge to the onboard compute node) is local-network-only, no
internet or cloud account required. But this answer is conditional on having a SLAM engine at
all; "offline" describes Keenon's own working system, not a capability Sakar can claim until the
SLAM question (above) is resolved.

**3. What exact components are available today?**
The public `peanut-sdk-release.aar` gives Sakar: `MapComponent` (upload/download a
**already-built** map file as a byte blob, plus obstacle-upload and download-progress
callbacks) and `MapManager` (USB-based import/export of a ROS map file to/from the robot). Plus
`PeanutRuntime.getInstance().location()`, documented as a basic SLAM power-on localization
trigger. That is the entire mapping-adjacent surface Sakar is licensed to call today.

**4. What is missing from the Peanut SDK?**
Everything that makes mapping "live": no SLAM start/stop control, no occupancy-grid streaming
API, no laser-scan/LiDAR access, no live robot-pose-during-mapping stream, no relocalization
beyond the single basic trigger, and no equivalent of the rich `map`/`pose`/`elevator`/`gate`/
`label`/`init_pose`/`dynamic_map_info` schema Keenon's own Installation Assistant uses internally.

**5. What internal robot service is required?**
Direct rosbridge access (the `rosbridge_websocket` protocol) to the onboard Linux/ROS compute
node's SLAM stack — the same access `com.keenon.peanut.peanutservice`'s
`RosSocketClientManager` has to `ws://192.168.64.20:9090`/`:9091`. This is Keenon-internal;
nothing in the audit found evidence that this is intended to be, or currently is, exposed to
third-party SDK licensees.

**6. Does Sakar need to implement its own SLAM service?**
Only if Sakar wants live mapping without Keenon's cooperation or a different SDK tier. There are
three real options, and this audit does not recommend one over the others (that is a business/
partnership decision, not a technical one this document can resolve): (a) obtain expanded SDK/
protocol access from Keenon for the existing onboard ROS stack, (b) implement or license an
independent SLAM stack running against the robot's raw sensors (if those are separately
accessible — not established by this audit), or (c) scope Sakar's own "mapping" feature down to
what the public SDK actually supports (import/export/manage already-built maps) rather than
live map-building.

**7. Can existing Keenon robot-side mapping services be reused?**
Technically, the protocol is visible (rosbridge, standard JSON messages, documented topic/service
names extracted in this audit) and nothing found suggests the onboard `192.168.64.20` node
enforces authentication beyond being on the same LAN. Whether Sakar is **permitted** to connect to
it is a licensing/contractual question, not a technical one — this audit found no technical access
control preventing a connection, but also found no evidence Sakar is licensed or intended to use
this undocumented internal protocol. This is flagged as a decision for the business/legal side,
not something to act on from a reverse-engineering finding alone.

**8. What is the minimum architecture required (if Sakar proceeds with a scoped, SDK-only
version)?**
A `MapManagement` component using only `MapComponent`/`MapManager` from the existing licensed SDK:
list/download/rename/delete already-built maps, associate a `floor` field of Sakar's own design,
store elevator/gate *metadata* locally (own schema, own database — not Keenon's `Mapping.db`),
and present all of this honestly as **UNAVAILABLE** wherever "live mapping" (build/stream/
relocalize) would be needed, exactly the same honesty principle `SakarC40Agent` already applies to
`CleanComponent`-domain hardware that isn't in the licensed SDK either.

**9. What should be implemented first (if this direction is pursued)?**
Nothing yet — this is an audit deliverable, not an implementation plan, per this task's explicit
scope. If and when implementation is authorized, the natural first step (lowest risk, highest
confidence per the matrix above) would be the file-level operations the SDK already supports —
`MapComponent.download`/`getMapInfo` — since those are the only mapping-adjacent capabilities
confirmed both present in the public SDK **and** not dependent on the unresolved SLAM-access
question.
