# Next Physical Robot Audit — Exact Checklist

**This checklist is read-only discovery only. It does not authorize, and must never be used to
justify, any modification to the robot.**

## Absolute rules (apply to every phase below, without exception)

- **NO MODIFICATION.**
- **NO FLASHING.**
- **NO ROOTING.**
- **NO CONFIGURATION CHANGES.**
- **NO PRODUCTION DISCONNECTION.**
- Every command below is a read/observe operation. If a step would require write access, root, or
  a reboot/flash, it is out of scope for this checklist and must be proposed and approved
  separately, not folded into a "discovery" pass.
- Record every result verbatim (paste actual command output into the evidence register, don't
  paraphrase) and tag it `PHYSICAL-HARDWARE CONFIRMED` or `RUNTIME VERIFIED` in
  [`02_EVIDENCE_REGISTER.md`](02_EVIDENCE_REGISTER.md) as appropriate, with a fresh evidence ID.

---

## Phase A — RK3288 / Android (read-only ADB)

```
adb devices
adb shell getprop
adb shell ip addr
adb shell ip route
adb shell ip neigh
adb shell ps
adb shell dumpsys
adb shell cat /proc/cpuinfo
```

Purpose: confirm the tablet's own Android build/properties, its network interfaces and routes, and
whether any process names hint at the SLAM bridge / rosbridge client already discussed in
`04_SLAM_INVESTIGATION_STATUS.md` (e.g. anything resembling `WebServerSocket`,
`RosSocketClientManager`, or the `com.keenon.peanut.peanutservice` package actually running).

## Phase B — Network

Identify, from Phase A's output plus direct observation:

- Tablet's own IP address
- Ethernet IP (if a wired interface exists and is up)
- Expected SLAM computer IP — test whether `192.168.64.20` (the static-analysis value) is actually
  reachable **from this network**, and if not, discover what address space the robot's internal
  network actually uses
- Gateway
- Subnet
- Which interfaces are actually active (do not assume Wi-Fi vs. Ethernet from Phase A output alone
  — cross-check against `ip addr`'s `UP`/`DOWN` state)

## Phase C — Services

Check reachability (not behavior) of the 4 ports already identified from static analysis:

```
8080   (AndService — Robot Installation Assistant's local HTTP server, per Phase 4 static analysis)
8888   (WebServerSocket — the JS↔native bridge/relay, per Phase 4 static analysis)
9090   (expected rosbridge_websocket primary port, per Phase 4 static analysis)
9091   (expected Keenon-customized second bridge instance, per Phase 4 static analysis)
```

Use simple, read-only connectivity tests only (e.g. `nc -zv <ip> <port>` from a shell with network
access to the robot's internal network, or the Android device's own `adb shell` if it has
connectivity the external test machine lacks). Do not send any payload beyond what a bare TCP
connect implies.

## Phase D — ROS (read-only discovery only)

If, and only if, Phase C confirms 9090/9091 (or wherever rosbridge actually lives) is reachable:

- Node list
- Topic list
- Service list
- Action list
- ROS version/distribution

Use standard read-only introspection (`rostopic list`/`rosservice list`/`rosnode list` if a native
ROS environment can reach the master directly, or the equivalent rosbridge JSON introspection
calls — e.g. an `op:"call_service"` to a topics/services listing service — if only the WebSocket
bridge is reachable). **Do not call `BUILD_MAP`, `CONFIGINITPOSE`, or any other action-triggering
service identified in Phase 4's static analysis during this discovery pass** — listing what exists
is in scope; triggering it is not, until a separate, explicitly-approved test plan exists for that.

## Phase E — SLAM

Identify (read-only, from whatever Phase D's introspection reveals, plus physical inspection of
the separate SLAM computer if accessible):

- SLAM executable/process name
- ROS package name
- Namespace
- Launch configuration, if discoverable without modifying anything (e.g. reading an existing
  launch file on disk, not re-launching anything)
- Map server node
- Localization node
- Sensor input topics (LiDAR, camera/VSLAM)

Also, for the SLAM computer's own hardware (if physically accessible): board markings, model,
manufacturer — to fill in the `UNKNOWN` fields in
[`03_HARDWARE_ARCHITECTURE.md`](03_HARDWARE_ARCHITECTURE.md) §2.

## Phase F — Mapping (observation only)

Using the robot's own existing, already-installed Keenon Installation Assistant app (do not
install anything new, do not use `SakarC40Agent` to attempt any of this):

- **Observe** a start-mapping action being performed through Keenon's own UI (do not trigger it via
  a raw rosbridge call unless a separate, approved test plan exists for that).
- **Observe** stop, save, load, localize, and initial-pose actions the same way.
- Record what WebSocket/HTTP traffic is visible during each (read-only capture only, matching the
  passive packet-capture approach already planned at a higher level in
  `SAKAR_SECURITY_RISK_REGISTER.md` R14/R15).

**This phase is observational only.** It exists to confirm or correct Phase 4's static-analysis
predictions against real behavior — it is not a green light to build a competing implementation
during the same session.

---

## Phase G — Photo-informed follow-up (read-only, added 2026-09-26)

Built directly from the new physical photo evidence in
[`10_PHYSICAL_HARDWARE_PHOTO_EVIDENCE.md`](10_PHYSICAL_HARDWARE_PHOTO_EVIDENCE.md) and
[`03_HARDWARE_ARCHITECTURE.md`](03_HARDWARE_ARCHITECTURE.md) §6. **Same absolute rules as above
apply without exception: no powered probing, no wire cutting, no connector unplugging while
powered, no firmware changes, no configuration changes.** Ordered by priority:

1. **Identify the ROS computer.** Specifically: determine whether the enclosure labelled "ARM IPC"
   (PH-08) is the separate ROS/Robot Computer already documented from static analysis and physical
   observation (§4 of `01_MASTER_ENGINEERING_KNOWLEDGE_BASE.md`). Read-only means: check for a
   network hostname/interface if reachable, a serial console if one exists and is already exposed,
   or a fuller label — do not open, modify, or reflash anything to find out.
2. **Identify every controller board between the ROS computer and the motors.** Specifically:
   determine whether the multi-section board carrying "STEP MOTOR"/"AIR PR GAUGE 1" labels (PH-02)
   is the SDK's SCM-IoT-addressed motor controller (§3), a different board, or unrelated.
3. **Trace the CAN-labelled harness** (PH-06/PH-07) along its physical run without disconnecting
   anything powered.
4. **Identify where the CAN bus originates** (which board's connector).
5. **Identify where the CAN bus terminates** (which board's connector).
6. **Identify the motor controller board** driving the hub motor(s) (PH-03) — confirm or rule out
   the PH-02 board as this controller.
7. **Identify whether hub-motor Hall feedback returns to a controller**, and if so which one — the
   "BRUSH MOTOR HALL" connector "H3" (PH-07) is confirmed to exist for a brush motor specifically;
   determine whether an equivalent exists for the hub motor(s) too.
8. **Identify the USB hub's (PH-04, "USB HUB V1.1") upstream connection** — which computer's USB
   host feeds it.
9. **Identify which cameras use USB** — specifically, which camera(s) connect to "USB1" vs. "USB2"
   on the PH-04 hub.
10. **Identify which cameras use internal wired interfaces** (as opposed to USB) — per the
    physical observation already recorded (E-051), some cameras use an interface other than USB;
    identify which and what that interface actually is.
11. **Identify the communication/wireless module's actual use** — the "USB LoRa" board (PH-05,
    module "E22-900T22S 1B") is confirmed to exist; determine whether it is actively used, by
    which host, and for what purpose, without changing its configuration.
12. **Identify all visible board labels and part numbers** not yet captured — including a
    glare-free reading of the PH-01 QFP IC's marking and the PH-02 board's central IC marking.

Every finding from this phase gets a new evidence row in
[`02_EVIDENCE_REGISTER.md`](02_EVIDENCE_REGISTER.md) (continuing from E-061) and moves the
corresponding item out of [`06_UNVERIFIED_ITEMS.md`](06_UNVERIFIED_ITEMS.md)'s "Physical hardware
photo evidence gaps" section (items 37-45), per this document's own "After completing any phase"
rule below.

---

## After completing any phase

1. Update [`02_EVIDENCE_REGISTER.md`](02_EVIDENCE_REGISTER.md) with a new evidence row per finding,
   tagged with the correct status (`PHYSICAL-HARDWARE CONFIRMED` for hardware observations,
   `RUNTIME VERIFIED` for live command output).
2. Update [`06_UNVERIFIED_ITEMS.md`](06_UNVERIFIED_ITEMS.md) — move resolved items out of the
   unverified list (do not delete the historical record of what was previously unknown; note the
   resolution and the evidence ID that resolved it).
3. Update [`04_SLAM_INVESTIGATION_STATUS.md`](04_SLAM_INVESTIGATION_STATUS.md)'s answers to
   questions 1–10 with whatever was actually observed.
4. Do **not** begin implementation immediately after this audit. Report findings first; a separate
   decision (business and engineering) is required before any Installation Assistant, mapping, or
   SLAM code is written.
