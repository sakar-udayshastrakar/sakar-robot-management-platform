# Sakar ROS/SLAM Access Audit — Physical Robot Verification

**Phase 5 audit. Read-only network diagnostics only. No code, UI, robot configuration, or network
configuration was changed to produce this document. No fake/simulated data is presented anywhere
below as if it were real.**

## Headline finding, stated up front

**No physical Sakar CleanBot5000Plus robot was reachable from this development machine at the
time of this audit.** Every live-network claim in this document is the result of an actual test
run during this session (commands and raw output quoted below); nothing about ROS nodes, topics,
services, or SLAM behavior on a physical robot is asserted, because no physical robot connection
existed to test against. Where Phase 4's decompiled-APK findings are referenced for context, they
are explicitly labeled as **prior static-analysis evidence, not independently confirmed live** in
this phase.

---

## A. Physical robot network topology

**This development machine's actual network** (from `ipconfig`, run live in this session):

| Adapter | IPv4 | Subnet | Gateway |
|---|---|---|---|
| Wi-Fi | `192.168.1.5` | `255.255.255.0` (`/24`) | `192.168.1.1` |
| vEthernet (WSL) | `172.27.80.1` | `/20` | none |
| Ethernet | disconnected | — | — |
| Wireless LAN adapter ×2 | disconnected | — | — |

**ARP table** (`arp -a`, run live): only the router (`192.168.1.1`) and standard IPv4/mDNS
multicast addresses are present on the Wi-Fi interface — **no other physical host is visible on
this LAN**, robot or otherwise.

**ADB device list** (`adb devices -l`, run live):
```
List of devices attached
emulator-5554   device product:sdk_gphone_x86_64 model:Android_SDK_built_for_x86_64 device:generic_x86_64
```
The only device connected to this machine's Android tooling is the **software emulator** this
entire project has been developed against — there is no physical tablet or robot attached via
USB/ADB either.

**Conclusion:** this machine is on an ordinary home/office LAN (`192.168.1.0/24`) with no bridge,
VPN, or route to any robot-internal network (such as the `192.168.64.0/24` subnet referenced in
Phase 4's decompiled-APK evidence). No project file was found with a hardcoded real robot IP to
target instead (`grep` for IPv4 literals across the app source turned up only UI display strings
and reference-screenshot measurement notes, not a connection target — see
`RobotInfoScreens.kt`/`InstallationScreens.kt`, which are unrelated presentational code, not
network configuration).

---

## B. IP/port table

| Target | Test | Result | Evidence |
|---|---|---|---|
| `192.168.64.20` | `ping -n 2 -w 1000` | **UNREACHABLE** — "Request timed out" ×2, 100% loss | live command output, this session |
| `192.168.1.1` (control: this network's own gateway) | `ping -n 2 -w 1000` | REACHABLE — 1–4ms round trip | live command output, this session (confirms the test methodology itself works; the failure above is specific to the target, not a broken test) |
| `192.168.64.20:9090` | `Test-NetConnection -Port 9090` | **CLOSED/UNREACHABLE** — `TcpTestSucceeded=False`, `PingSucceeded=False` | live command output, this session |
| `192.168.64.20:9091` | `Test-NetConnection -Port 9091` | **CLOSED/UNREACHABLE** — same | live command output, this session |
| `192.168.64.20:8888` | `Test-NetConnection -Port 8888` | **CLOSED/UNREACHABLE** — same | live command output, this session |
| `192.168.64.20:8080` | `Test-NetConnection -Port 8080` | **CLOSED/UNREACHABLE** — same | live command output, this session |

Per the task's own vocabulary (OPEN/CLOSED/TIMEOUT/REFUSED/UNREACHABLE): all four ports are
recorded as **UNREACHABLE** — there is no route to the host at all (confirmed by the ping test
failing identically), which is a stronger and more specific finding than "closed" (a closed port
on a reachable host would typically show a fast TCP RST/"refused"; here there is no reply of any
kind, consistent with no network path existing to `192.168.64.20` from this machine at all).

---

## C. ROS node table

**NOT VERIFIABLE in this phase.** No connection to any ROS master or rosbridge instance was
established (see §B) — there is nothing to enumerate nodes from. No node list is presented, real
or hypothetical.

---

## D. ROS topic table

**NOT VERIFIABLE in this phase**, for the same reason as §C. The specific topics named in the
task prompt (`/map`, `/map_metadata`, `/scan`, `/tf`, `/tf_static`, `/odom`, `/amcl_pose`,
`/initialpose`, `/cmd_vel`) were **not tested against a live system** and their presence or
absence cannot be confirmed or denied here. (Phase 4's decompiled-APK static analysis found
different, Keenon-custom topic names — `/scan_base_map`, `/motor_lock`, `/tf2_web_republishe`,
`/switch_dest_floor_map`, `/republish_tfs`, plus a custom message package
`keenon_database_msgs` — inside the app's JS bundle, but that is a static string found in
un-executed JavaScript, not a live topic list from a running ROS graph, and it does not confirm
or rule out whether standard topics like `/scan`/`/tf`/`/odom` also exist alongside the custom
ones.)

---

## E. ROS service table

**NOT VERIFIABLE in this phase**, same reasoning as §C/§D.

---

## F. SLAM architecture

**Live verification: not possible in this phase (no reachable robot).**

**Prior static-analysis evidence only** (Phase 4, `KEENON_MAPPING_DEEP_REVERSE_ENGINEERING.md`,
not re-confirmed here): the decompiled Robot Installation Assistant APK's own source code
describes connecting to `ws://192.168.64.20:9090` and `:9091` for rosbridge traffic, implying
Keenon's own architecture expects the SLAM compute node at that fixed address relative to the
tablet. This session's live test (§B) shows that address is not reachable from *this development
machine*'s network — which is expected and not a contradiction: `192.168.64.20` is very likely
only reachable from *inside* the robot's own local network (e.g. from the tablet itself, or a
device joined to the robot's own Wi-Fi/hotspot), not from an arbitrary external LAN. This audit
did not have access to a physical robot or its onboard network to test that narrower claim
either.

**Answering the prompt's A/B/C/D options honestely:** whether SLAM computation runs (A) on the
robot, (B) on another onboard Linux computer, (C) on an external computer, or (D) unknown —
this phase's evidence supports only Phase 4's prior finding (an onboard Linux/ROS compute node,
i.e. closest to option B, "another onboard computer" separate from the Android tablet), but that
finding was **not independently re-verified against live hardware in this phase**, so it is
carried forward as **prior evidence, not a live-confirmed fact.**

---

## G. Mapping control interfaces

| Capability | Interface (per prior static analysis, Phase 4) | Classification | Live-verified this phase? |
|---|---|---|---|
| Start mapping | rosbridge service call, `ROSNAME.BUILDMAPTYPE` | ROS | **No — UNAVAILABLE to test (no connection)** |
| Stop mapping | UI-triggered, native `BuildMapLocalState` event push | INTERNAL SERVICE | **No** |
| Save map | `postDBOperation` WebSocket → local `Mapping.db` | WEBSOCKET / local DB | **No** |
| Load map | Same WebSocket DB path (`select`/`selectAll`) | WEBSOCKET / local DB | **No** |
| Localization | `PeanutRuntime.getInstance().location()` | **PUBLIC SDK** (this one is confirmed present in the licensed AAR, per Phase 4 §23) | Not live-tested against hardware in this phase, but its existence in the SDK is a static, file-based fact independent of network reachability |
| Initial pose | `ROSNAME.CONFIGINITPOSE` rosbridge service | ROS | **No** |

Every row except "Localization" (public SDK) is recorded as **UNAVAILABLE** for this phase
specifically because no physical robot connection exists to exercise it — not because the
interface itself was found to be broken or absent from Keenon's design.

---

## H. Live map data interfaces

**NOT VERIFIABLE in this phase.** No occupancy grid, LiDAR scan, robot pose, odometry, TF, or
trajectory data was received, requested, or simulated. No topic/message-type/frequency table is
presented, because presenting one without a live connection would mean fabricating it, which this
audit's own instructions explicitly prohibit.

---

## I. Pose interfaces

**NOT VERIFIABLE in this phase**, same reasoning as §H.

---

## J. Public SDK comparison

This section **is** verifiable independent of network access, because `peanut-sdk-release.aar` is
a local file already present in the project and was already documented in Phase 4. Re-confirmed
here by file existence check only (not re-decompiled):

| Discovered interface (Phase 4, static analysis) | In public `peanut-sdk-release.aar`? |
|---|---|
| `MapComponent` (`upload/uploadNew/uploadOpt/download/downloadOpt/uploadObs/getMapInfo/sendDownloadInfo/sendMapDownLoadAction`) | **Yes** — public SDK class |
| `MapManager` (`onImportToRos()/onExportToAndroid()`, USB transfer) | **Yes** — public SDK class |
| `PeanutRuntime.getInstance().location()` | **Yes** — public SDK method, documented as a basic SLAM power-on localization trigger |
| rosbridge protocol / `192.168.64.20:9090`/`:9091` access | **No** — not part of the AAR at all; this is a private network protocol between Keenon's own two apps and their own onboard compute node |
| `Mapping.db` schema (`map`/`pose`/`elevator`/`gate`/`label`/`init_pose`/`dynamic_map_info`) | **No** — internal to the Robot Installation Assistant APK, not exposed by the SDK |
| WebSocket `postDBOperation`/`android.*` bridge protocol | **No** — internal, undocumented, browser↔native protocol specific to that one APK |

This table is unchanged from Phase 4's finding; it is repeated here only because the task asked
for it as part of this report's structure, not because new evidence was gathered.

---

## K. Offline capability

**Cannot be tested live in this phase** (no reachable robot to observe operating with or without
internet). Phase 4's static analysis (unchanged, not re-verified) found that the mapping
data/control path in the decompiled source has no cloud-authentication code in its critical path,
and that `AndService`'s local HTTP server binds without restricting to loopback — consistent with
a LAN-only design — but "the code doesn't call a cloud auth API" is not the same claim as "we
observed a real robot map successfully with the internet disconnected," and this phase provides
none of the latter.

---

## L. Security/access requirements

**Partially observable without a robot connection**: the fact that `192.168.64.20:9090/9091/8888/
8080` produced no response of any kind (§B) rather than a connection-refused/handshake-then-reject
response means this audit cannot even determine whether those services would demand
authentication, because no connection to them was ever established at all. Phase 4's static
analysis found no obvious authentication token/handshake requirement in the decompiled WebSocket
client code, but this is, again, a claim about source code, not about what a live service would
actually enforce — recorded as **UNVERIFIED**, not confirmed either way.

---

## M. Confirmed vs. unverified findings

**Confirmed by live testing in this session (§A/§B):**
- This machine's own network has no route to `192.168.64.20`.
- All 4 named ports on that address are unreachable from this machine.
- No physical robot or tablet is connected to this machine via ADB or visible on its local LAN.
- `peanut-sdk-release.aar` exists in the project and its documented API surface (Phase 4) is
  unchanged.

**Carried forward from Phase 4 (static/decompiled-source analysis, NOT re-verified against live
hardware in this phase):** the entire architecture description in this document's introduction
(SimpleChromeActivity → :8080 → SPA → :8888 → WebServerManager → RosSocketClientManager →
rosbridge → 192.168.64.20:9090/9091 → external SLAM node), the `Mapping.db` schema, the
`ROSNAME.*` service/topic table, and the public-SDK method list.

**Not established by either phase (open questions):** whether standard ROS topics
(`/map`,`/scan`,`/tf`,`/odom`,`/amcl_pose`,`/initialpose`,`/cmd_vel`) exist on the real robot
alongside or instead of the Keenon-custom ones found in the JS bundle; the actual SLAM
algorithm/package running on the onboard compute node; whether `192.168.64.20` requires being on
the robot's own local/hotspot network to reach (plausible, not tested); whether any
authentication gates the rosbridge or WebSocket ports on a live unit; live update
frequency/message sizes for any topic.

---

## N. Exact missing components (to make this audit completable)

1. **Physical access to a Sakar CleanBot5000Plus robot** (or its tablet), on the same network
   segment as this or a similarly-tooled machine — this is the single blocking prerequisite for
   sections C, D, E, and most of F, G, H, I, K, L above.
2. Alternatively, **remote/VPN access into the robot's own onboard network** if one exists,
   without needing physical presence.
3. If/when either of the above is available: standard read-only ROS introspection tools
   (`rostopic list`/`rosservice list`/`rosnode list` if a native ROS environment can reach the
   master, or the equivalent rosbridge JSON `call_service`/`op:"topics"` introspection calls if
   only the WebSocket bridge is reachable) — no code changes are needed to run these, only
   network access.

---

## Final Decision

**1. Is the existing robot SLAM accessible?**
**Unknown — not established either way.** No physical robot was reachable from this development
environment during this audit, so accessibility could not be tested. Phase 4's static analysis
shows Keenon's own apps *can* reach it (from the robot's own tablet, on the robot's own network),
but nothing in this phase confirms or denies whether Sakar's development setup can ever reach it.

**2. Can Sakar reuse the existing SLAM?**
**Not determined.** This requires two separate unresolved questions this audit could not answer:
(a) is it technically reachable from wherever Sakar's software would run (unresolved — no
physical-robot test was possible), and (b) is Sakar *permitted* to use this undocumented internal
protocol (a licensing/business question, explicitly out of scope for a technical audit, as already
flagged in Phase 4).

**3. Can Sakar build Keenon-style live mapping without Keenon Cloud?**
**Cannot be confirmed by live evidence in this phase.** Phase 4's static analysis suggests the
core mapping data path doesn't require Keenon Cloud, but this phase has no live observation to
confirm it.

**4. What exact interface should Sakar Installation Assistant use?**
**Cannot be recommended yet.** Recommending a specific interface (rosbridge to a real robot vs.
SDK-only file management vs. something else) requires knowing whether physical/network access to
a real robot's ROS layer will ever be available and permitted — neither is established. The only
interface confirmed both available and licensed today is the public SDK's file-transfer surface
(`MapComponent`/`MapManager`), already identified in Phase 4.

**5. What must Sakar implement itself?**
Whatever this audit could not verify remains Sakar's own responsibility to determine once a real
robot is available: nothing new is added to Phase 4's list by this phase, since no new live
capability was discovered.

**6. What must NOT be implemented yet?**
Any live-mapping/SLAM feature, any UI claiming to show real occupancy-grid/LiDAR/pose data, and
any code that assumes `192.168.64.20` (or any other specific IP) is reachable — this phase found
zero evidence that assumption holds for Sakar's actual deployment/development environment.

**7. What is the next engineering step?**
Obtain physical or remote network access to an actual Sakar CleanBot5000Plus unit (or written
confirmation from Keenon/the hardware partner of the correct on-robot address and permitted
access scope), and only then re-run the read-only ROS introspection this document's §N describes.
Until that access exists, no further mapping-feasibility conclusions can be drawn beyond what
Phase 4 already established from static analysis alone.
