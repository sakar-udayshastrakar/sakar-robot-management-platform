# SLAM Investigation Status

Focused status document, answering exactly the 10 questions this consolidation was asked to
address. Cross-references [`01_MASTER_ENGINEERING_KNOWLEDGE_BASE.md`](01_MASTER_ENGINEERING_KNOWLEDGE_BASE.md),
[`02_EVIDENCE_REGISTER.md`](02_EVIDENCE_REGISTER.md), and the three Phase 4/5 deliverables
(`KEENON_MAPPING_DEEP_REVERSE_ENGINEERING.md`, `SAKAR_MAPPING_TECHNICAL_FEASIBILITY.md`,
`SAKAR_ROS_SLAM_ACCESS_AUDIT.md`).

---

## 1. What proves the robot has a separate SLAM computer?

Two independent lines of evidence, neither alone conclusive, together giving high confidence:

1. **Physical hardware observation** (user-reported, this session): a separate computer beyond
   the RK3288 UIB board exists on the physical CleanBot5000Plus and performs SLAM.
   `PHYSICAL-HARDWARE CONFIRMED`.
2. **Static analysis of Keenon's own decompiled Robot Installation Assistant APK**: its
   `RosSocketClientManager` class opens outbound WebSocket connections to `ws://192.168.64.20:9090`
   and `:9091` — i.e., the app's own source code is written as a *relay client* to an external
   rosbridge server, not as a host of the SLAM computation itself. No SLAM/ROS/LiDAR native
   library (cartographer, rtabmap, gmapping, etc.) is bundled inside any of the 5 decompiled APKs.
   `STATIC-ANALYSIS CONFIRMED`.

Neither piece of evidence was derived from the other — they were discovered independently (the
physical observation from the user, the static analysis from decompiled APK source) and happen to
agree, which is why this finding is recorded with high confidence despite neither phase having
physically inspected the SLAM computer itself.

## 2. What do we know about its role?

Per the static analysis only (`KEENON_MAPPING_DEEP_REVERSE_ENGINEERING.md` §8): it is expected to
run a `rosbridge_websocket`-compatible service (port 9090) plus a Keenon-customized second bridge
instance (port 9091) handling specific services/topics
(`/switch_dest_floor_map`, `/republish_tfs`, `/scan_base_map`, `/motor_lock`,
`/tf2_web_republishe`). This implies it hosts the actual SLAM/occupancy-grid/localization
computation and exposes it to the Android tablet only via this bridge. **None of this has been
physically observed running.**

**New physical observation (this session), `PHYSICAL-HARDWARE CONFIRMED`:** the user has directly
observed this computer (referred to as the "ROS/Robot Computer") to be connected to / responsible
for LiDAR, stereo vision, ultrasonic sensors, hub motors, actuators, water pumps, water-level
sensors, Hall sensors, depth camera, two side-brush motors, mopping-brush motors, scrubber roller
motor, vacuum motors, vSLAM, and multiple cameras (some USB-connected, some via an unspecified
internal wired interface). This is a substantially fuller picture of its role than the static-only
"expected to host SLAM/occupancy-grid/localization" statement above — it confirms this computer's
role extends to sensor and actuator I/O generally, not just SLAM computation. It does **not**
confirm any bus, protocol, ROS topic/service name, or wiring detail for any of these connections —
see [`03_HARDWARE_ARCHITECTURE.md`](03_HARDWARE_ARCHITECTURE.md) §2.1 for the full list of what
remains explicitly `UNKNOWN`, including the deliberately-unresolved question of how this finding
relates to the SDK's separately-documented "SCM IoT" motor-controller protocol (§3 there).

## 3. What do we know about its network?

Only what the static analysis of the *Android side*'s client code says about where it expects to
find the SLAM computer: a fixed IP, `192.168.64.20`, over 2 WebSocket ports (9090, 9091). Nothing
is known about the SLAM computer's own network configuration (static vs. DHCP, subnet mask,
gateway, whether it's on the same physical segment as the RK3288 or reached through some other
path) — all `UNKNOWN`.

## 4. What do we know about 192.168.64.20?

- It is referenced by name in **two independent decompiled artifacts**: Keenon's Robot
  Installation Assistant APK (`RosSocketClientManager.ADDRESS`) and the licensed Peanut SDK AAR
  itself (`PeanutConstants.REMOTE_LINK_PROXY`). `STATIC-ANALYSIS CONFIRMED` for both.
  See [`02_EVIDENCE_REGISTER.md`](02_EVIDENCE_REGISTER.md) E-011/E-012.
- It was tested live from this development machine (Phase 5) and found **completely
  unreachable** — ping timeout, and all 4 relevant ports (8080, 8888, 9090, 9091) returned no
  response of any kind. `RUNTIME VERIFIED` (the negative result itself was actually tested).
- **This is not a contradiction.** This development machine is on an unrelated home/office LAN
  (`192.168.1.0/24`) with no route to `192.168.64.0/24`. The static finding describes what
  Keenon's own software expects to find *on the robot's own internal network* — a network this
  project has never had access to.
- **Conclusion, stated precisely:** `192.168.64.20` is a real, meaningful, cross-confirmed
  address inside Keenon's own architecture. It is **not verified to be the current physical
  robot's live SLAM-computer address** — that requires testing from a machine actually on the
  robot's own network, which has not yet happened.

## 5. What has NOT been physically verified?

Everything about live behavior: whether `192.168.64.20` is reachable *from the robot's own tablet
or LAN*; whether ports 9090/9091/8888/8080 are actually open there; what ROS distribution/version
runs; whether any of the topic/service names found in decompiled JS still exist in the current
firmware; the SLAM computer's model, OS, or physical connection to the RK3288; live occupancy-grid/
LiDAR/pose data of any kind; whether mapping actually works offline on the real hardware; whether
any authentication gates any of these services on a live unit.

## 6. What can only be verified when the physical robot is accessible?

All of §5, without exception. No further progress on any of these questions is possible from
static analysis alone — this was already the conclusion of Phase 5
(`SAKAR_ROS_SLAM_ACCESS_AUDIT.md`), reaffirmed here.

## 7. What information should be collected through ADB?

Exactly the checklist in [`07_NEXT_PHYSICAL_ROBOT_AUDIT.md`](07_NEXT_PHYSICAL_ROBOT_AUDIT.md)
Phase A: `adb devices`, `getprop`, `ip addr`, `ip route`, `ip neigh`, `ps`, `dumpsys`,
`cat /proc/cpuinfo` — all read-only, no modification, no rooting.

## 8. What information should be collected from the SLAM computer?

If and when it is reachable: its own network configuration (Phase B of the checklist), which of
ports 8080/8888/9090/9091 (or others) are open (Phase C), and — strictly read-only — ROS
introspection: node list, topic list, service list, action list, ROS version/distribution
(Phase D). No launch file, configuration, or running process should be modified.

## 9. What network captures should eventually be performed?

A passive packet capture (already planned at a higher level in
`SAKAR_SECURITY_RISK_REGISTER.md` R14/R15, for a different but related purpose — OTA/unknown
Keenon traffic) covering a full charge/idle/task/mapping cycle, taken from a position that can see
traffic between the RK3288 and the SLAM computer, to observe real message rates/sizes/protocols
without altering anything. This has not been performed for mapping specifically and is recorded
here as a future step, not a completed action.

## 10. What is the minimum information needed to reproduce the mapping interface?

Per the feasibility analysis already completed (`SAKAR_MAPPING_TECHNICAL_FEASIBILITY.md`):
the UI shell, database schema, and file-management operations are reproducible today with ordinary
engineering effort. The one piece that is not reproducible without further information is the
SLAM engine itself — reproducing *that* specifically would require either (a) confirmed, permitted
access to the real rosbridge protocol on a live robot (physical audit, this document), (b) an
independently sourced/licensed SLAM stack, or (c) scoping "mapping" down to what the public SDK
already supports (manage already-built maps, not build them live). This document does not
recommend one of these three over the others — that remains a business/engineering decision
outside the scope of this audit.

## 11. What does Keenon's own official documentation say about any of this?

**Full detail:** [`09_KEENON_OFFICIAL_C40_DOCUMENTATION_AUDIT.md`](09_KEENON_OFFICIAL_C40_DOCUMENTATION_AUDIT.md)
§5.

Stated exactly as the task requesting this check required:

- ROS: **NOT DOCUMENTED IN OFFICIAL C40 DOCUMENTATION.**
- SLAM / vSLAM: **NOT DOCUMENTED IN OFFICIAL C40 DOCUMENTATION** — the word "SLAM" does not appear
  anywhere in any of the 10 official C40 documents reviewed.
- rosbridge, ROS topics, ROS services: **NOT DOCUMENTED IN OFFICIAL C40 DOCUMENTATION.**
- Mapping computer / robot computer / internal network: **NOT DOCUMENTED IN OFFICIAL C40
  DOCUMENTATION** — official material treats "the robot" as a single unit throughout, with one
  narrow exception (a LAN-pairing requirement between the operator's phone and the robot for
  carpet-area remote-control mapping specifically — not an internal robot-to-robot or
  robot-to-SLAM-computer network statement).
- LiDAR is the one exception with real official coverage: **`OFFICIAL DOCUMENTATION CONFIRMED`** to
  exist, with the stated purpose "positioning and navigation" (direct quote, official safety
  instructions). This is new corroboration for §1's "separate SLAM computer" conclusion only in the
  loose sense that *a* LiDAR sensor's existence is now confirmed from a third source — it says
  nothing about which computer processes that LiDAR's data, and does not change §1-§10's own
  evidence or confidence level.
- Localization: the only officially documented behavior is a manual recovery procedure —
  physically pushing the robot to a known landmark or to its charging station "restores
  positioning" (direct quote). No mention of AMCL, particle filters, coordinate frames, or any
  algorithm name.

**This section was answered without inferring from §1-§10's own already-established generic
Keenon architecture findings, per that task's explicit instruction** — every line above reflects
only what was or was not found in official C40 customer documentation, read independently of the
APK-based conclusions elsewhere in this document. None of §1-§10 is changed by this section.
