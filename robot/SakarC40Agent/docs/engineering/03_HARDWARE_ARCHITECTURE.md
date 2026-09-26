# Hardware Architecture

**Status of the evidence in this document: `PHYSICAL-HARDWARE CONFIRMED` for the two board-marking
strings themselves (reported directly by the user from physical inspection of the CleanBot5000Plus
in this session). No photograph, schematic, or continuity-test data exists inside this repository
at the time of writing — everything beyond the two marking strings and their standard-part
inferences below is either `INFERRED` (from the marking strings' own naming convention) or
`UNKNOWN — REQUIRES PINOUT / SCHEMATIC / CONTINUITY TEST`. Nothing below is a guessed pinout.**

---

## 1. RK3288 UIB board (Android / application computer)

### 1.1 PCB markings observed

```
CBA_RK3288_UIB_SUPER_V2.2
RK3288_UIB_DUAL_LVDS_V1.0_20230803
```

Both strings were reported by the user from direct physical inspection during this session. Their
plain-English reading, based on standard industry naming conventions for this class of board
(**INFERRED**, not confirmed by a datasheet in this repository):

- `RK3288` — Rockchip RK3288 SoC (quad-core ARM Cortex-A17, Mali-T760 GPU) — this is a
  well-documented, publicly available SoC; nothing about its existence is in dispute.
- `UIB` — commonly "Universal Interface Board" in this class of embedded/SBC carrier-board naming
  — **INFERRED** meaning, not confirmed against this specific vendor's own documentation.
- `SUPER` / `V2.2` — a board variant/revision designator — **INFERRED**, exact difference from
  other revisions unknown.
- `DUAL_LVDS` — strongly implies **two LVDS display outputs** are present on this board —
  **INFERRED** from the naming convention; not confirmed by a pinout or schematic in this
  repository.
- `V1.0_20230803` — a second revision/date string (2023-08-03) — **INFERRED** to be a
  manufacture/design date, not confirmed.

### 1.2 RK3288 SoC

- Rockchip RK3288: quad-core ARM Cortex-A17 @ up to ~1.8 GHz, Mali-T760 GPU. This is public,
  well-known information about the SoC family, not something requiring project-specific
  verification.
- **CONFIRMED software cross-reference:** the licensed `peanut-sdk-release.aar`'s native libraries
  are 32-bit ARM only (`jni/armeabi`, `jni/armeabi-v7a` — **no `arm64-v8a`**), consistent with a
  32-bit (or 32-bit-compatibility-mode) Android build on this SoC family
  (`COMPATIBILITY_REPORT.md`, `PEANUT_SDK_C40_API_MATRIX.md` header). This is the strongest,
  most directly useful confirmed fact for any future hardware-replacement compatibility check
  (§4).

### 1.3 Samsung memory

Referenced in the task's own instruction as something to document from the photographed board.
**No specific part number, capacity, or memory type (LPDDR3/DDR3, eMMC size, etc.) was reported to
this session** — recorded here as **UNKNOWN**, not guessed. If/when a photograph or part-number
close-up becomes available, this section should be updated with the exact marking, not an assumed
capacity.

### 1.4 Ethernet section

**UNKNOWN — REQUIRES PINOUT / SCHEMATIC / CONTINUITY TEST.** No photograph exists in this
repository to identify a specific Ethernet PHY chip, connector type (RJ45 vs. header), or port
count. The existence of *some* Ethernet capability is plausible given (a) the RK3288 SoC family
commonly includes a Gigabit Ethernet MAC, and (b) the software-side evidence that the Android
application computer communicates over IP with a separate SLAM computer (Phase 4,
`KEENON_MAPPING_DEEP_REVERSE_ENGINEERING.md` §8) — but **INFERRED** plausibility is not the same as
a confirmed port. Whether that IP communication happens over a dedicated Ethernet link, a shared
Wi-Fi network, or a USB-Ethernet adapter is **UNKNOWN**.

### 1.5 Antenna / Wi-Fi section

**UNKNOWN — REQUIRES PINOUT / SCHEMATIC / CONTINUITY TEST.** No photograph exists to identify a
Wi-Fi/BT module part number or antenna connector type (u.FL, MHF, soldered chip antenna, etc.).
Wi-Fi capability's existence in general is plausible (the CleanBot's own operator UI has a Network/
Wi-Fi settings screen, per the reference-screenshot inventory), but this is a software-observable
fact, not a hardware-identification one, and does not substitute for physically identifying the
module.

### 1.6 LVDS / display-related hardware

The board's own model string (`DUAL_LVDS`) is the only evidence available: **INFERRED** to mean
two LVDS display interfaces are present. No connector pinout, signal count (single-channel vs.
dual-channel LVDS), or supported resolution/timing was reported. **UNKNOWN — REQUIRES PINOUT /
SCHEMATIC / CONTINUITY TEST** for all specifics beyond "the board's name suggests dual LVDS
exists."

### 1.7 RTC battery

**UNKNOWN.** Not reported in this session. Most SBC/UIB-class boards of this type carry a small
coin-cell RTC backup battery, but this is a general-industry inference, not a confirmed
observation of this specific board — recorded as **UNKNOWN**, not assumed present or absent.

### 1.8 Visible connectors

**UNKNOWN — REQUIRES PINOUT / SCHEMATIC / CONTINUITY TEST**, for every connector without
exception. No photograph exists in this repository to enumerate connector positions, pin counts,
keying, or silkscreen labels. This section exists as a placeholder to be filled in in
[`07_NEXT_PHYSICAL_ROBOT_AUDIT.md`](07_NEXT_PHYSICAL_ROBOT_AUDIT.md)'s Phase A/B work, with the
explicit reminder from this audit's own rules: **do not guess connector pinouts.** When
photographs/schematics do become available, each connector should get its own row here with:
physical location, silkscreen label (if any), pin count, and function — each function marked
`CONFIRMED` only once verified by pinout, schematic, or continuity test.

### 1.9 Power circuitry

**UNKNOWN — REQUIRES PINOUT / SCHEMATIC / CONTINUITY TEST.** No input voltage, connector type, or
regulation topology was reported in this session.

### 1.10 Connector labels visible in photographs

**No photographs exist in this repository.** This subsection is a placeholder for when
photographic documentation is captured during a future physical robot audit
(`07_NEXT_PHYSICAL_ROBOT_AUDIT.md`) — at that point, every visible silkscreen label should be
transcribed verbatim here, with its function marked `UNKNOWN` until independently verified, per
this audit's explicit instruction not to infer a connector's function from its label alone.

---

## 2. Separate SLAM computer / "ROS-Robot Computer" (distinct hardware entity)

**This is documented here as its own, architecturally distinct hardware entity, per this audit's
explicit instruction — it must never be conflated with, or described as a sub-component of, the
RK3288 UIB board above.** The user has since referred to this same entity as the "separate ROS/
Robot Computer" (§2.1 below) — this is treated as the same hardware entity already described here
as the "separate SLAM computer," not a second, additional computer. Nothing here is renamed or
rewritten to erase the earlier "SLAM computer" framing; §2.1 is an **extension** of this section
with newly reported physical connections, not a replacement of it.

- **Existence:** `PHYSICAL-HARDWARE CONFIRMED` (user-reported, this session) — a separate computer
  beyond the RK3288 exists and performs SLAM.
- **Model, manufacturer, form factor, OS:** **UNKNOWN.** No photograph, label, or part number was
  reported for this board in this session.
- **Network role (from independent software evidence, `STATIC-ANALYSIS CONFIRMED`, not physically
  re-verified):** expected to be reachable at `192.168.64.20` over IP, running a
  `rosbridge_websocket`-compatible service on port `9090` plus a Keenon-customized second bridge
  instance on port `9091` (`KEENON_MAPPING_DEEP_REVERSE_ENGINEERING.md` §8). This same address is
  independently referenced inside the licensed Peanut SDK's own compiled constants
  (`PeanutConstants.REMOTE_LINK_PROXY`), which is why this finding carries higher confidence than a
  single source alone would justify — see
  [`02_EVIDENCE_REGISTER.md`](02_EVIDENCE_REGISTER.md) E-011/E-012.
- **Physical connection to the RK3288 board:** **UNKNOWN.** Phase 4's evidence describes the
  *software* protocol (WebSocket relay to a rosbridge endpoint over IP) but says nothing about the
  *physical* medium — dedicated point-to-point Ethernet, a shared internal switch, USB-to-Ethernet,
  or something else entirely are all still open possibilities, none confirmed.
- **Sensors it manages (LiDAR, camera/VSLAM, etc.):** presence of *some* sensor input is
  **INFERRED** from the mapping software's own VSLAM/camera-pose code paths and bundled ARCore
  libraries (Phase 4, `KEENON_MAPPING_DEEP_REVERSE_ENGINEERING.md` §7/§8), but exactly which
  physical sensors connect to which computer (RK3288 vs. the SLAM computer) was not established
  **prior to §2.1 below.**

### 2.1 Newly reported physical connections (this session) — `PHYSICAL-HARDWARE CONFIRMED`

The user has now physically observed, and reported directly, that the separate ROS/Robot Computer
(the same entity as above, §2) is connected to / responsible for:

- LiDAR
- Stereo vision
- Ultrasonic sensors
- Hub motors
- Actuators
- Water pumps
- Water-level sensors
- Hall sensors
- Depth camera
- Two side-brush motors
- Mopping-brush motors
- Scrubber roller motor
- Vacuum motors
- vSLAM
- Multiple cameras — the user reports some are USB-connected and some are connected through
  internal wired interfaces (exact interface type per camera not specified)

**Status: `PHYSICAL-HARDWARE CONFIRMED`** for the existence of this computer and its
responsibility for/connection to each item on this list, exactly as reported. This supersedes the
prior `INFERRED`-only sensor-presence statement immediately above (§2's last bullet) with a
directly-observed, much more complete list.

**Explicitly `UNKNOWN` / `NOT VERIFIED` — none of the following is stated or implied by the above
list, and none of it is guessed here:**

- Board model, manufacturer, or form factor
- CPU/SoC identity
- Operating system
- ROS version/distribution
- CAN IDs (if CAN is even the bus in use — not established)
- UART port assignments or baud rates
- GPIO assignments
- USB device IDs (vendor/product IDs) for the USB-connected cameras
- Which specific "internal wired interface" (a second UART? a second CAN bus? a proprietary
  serial bus? something else?) the non-USB cameras use
- ROS topic or service names for any of the sensors/actuators listed above
- Network IP address(es) *of this specific set of connections* — the `192.168.64.20` value
  documented earlier in this section is a *separate*, independently-sourced (APK static analysis)
  finding about the overall rosbridge endpoint, not a claim about how these specific sensors/
  actuators are addressed
- Protocol (beyond the already-documented rosbridge/WebSocket relay at the Android-app level, §2)
- Wiring pinout for any connector

**A discrepancy with prior APK-based evidence is recorded here explicitly, not resolved:** §3
below (unchanged, from Phase 4 static analysis) describes a **third**, distinct
motor/motion-controller entity, reached from the licensed Peanut SDK via a raw "SCM IoT" protocol,
separate from both the RK3288 and the SLAM/ROS computer. The new physical observation above states
the ROS/Robot Computer is itself "responsible for" hub motors and actuators. These two findings are
not necessarily contradictory — a common real-world topology has a ROS-computer issue motion
commands that are then relayed to a lower-level motor-driver board over CAN/UART, in which case the
ROS/Robot Computer could reasonably be described as "responsible for" the motors while the SCM-IoT
protocol's target board remains a real, distinct piece of hardware in between. But this specific
relationship (does the ROS/Robot Computer talk to a separate SCM-IoT motor-controller board, or
does it drive the motors directly, with the SDK's "SCM IoT" protocol actually addressing the
ROS/Robot Computer itself under a different name?) is **not resolved by either source** and is
recorded as `REQUIRES PHYSICAL VERIFICATION`, not silently decided either way. See
[`09_KEENON_OFFICIAL_C40_DOCUMENTATION_AUDIT.md`](09_KEENON_OFFICIAL_C40_DOCUMENTATION_AUDIT.md)
§11 for the full three-way comparison entry.

Full investigation-status detail: [`04_SLAM_INVESTIGATION_STATUS.md`](04_SLAM_INVESTIGATION_STATUS.md).

---

## 3. Motor/motion controller board (a third hardware entity, noted for completeness)

Software evidence (`STATIC-ANALYSIS CONFIRMED`) shows the licensed Peanut SDK's
`MotorComponent.getState()` uses a raw "SCM IoT" binary protocol (`SCMRequest(dev=0, topic=6)`)
addressed to a motion/motor controller — a **third** distinct compute/control entity, separate
from both the RK3288 (application) and the SLAM computer (§2). Its physical identity, form factor,
and connection medium are **UNKNOWN** — this row exists only so that a future physical audit does
not conflate "the SLAM computer" with "the motor controller," which the software evidence shows are
not the same thing.

**Not resolved by the §2.1 physical observation:** the user's newly-reported finding that the
ROS/Robot Computer (§2/§2.1) is "responsible for" hub motors and actuators does not confirm or deny
whether this SCM-IoT-addressed board is (a) a distinct fourth entity in between, (b) the same board
the SCM-IoT protocol already addresses under a different description, or (c) not actually present
and the ROS/Robot Computer drives motors directly. See §2.1's own discrepancy note and
`09_KEENON_OFFICIAL_C40_DOCUMENTATION_AUDIT.md` §11 — this is recorded as `REQUIRES PHYSICAL
VERIFICATION`, not decided here.

---

## 4. Cross-reference: why this matters for hardware replacement

Any future hardware-replacement candidate (e.g. ROCK 4D — see
[`01_MASTER_ENGINEERING_KNOWLEDGE_BASE.md`](01_MASTER_ENGINEERING_KNOWLEDGE_BASE.md#26-hardware-replacement-investigation)
§26) must, at minimum, be checked against every fact and every `UNKNOWN` in this document before
any compatibility claim is made:

- 32-bit ARM native-library compatibility (§1.2) — a `CONFIRMED` requirement of the licensed SDK,
  not optional.
- Dual-LVDS display capability (§1.6) — `INFERRED` requirement, pending confirmation.
- Whatever the actual physical link to the SLAM computer turns out to be (§2) — `UNKNOWN`, and
  therefore **not yet possible to replicate on any replacement board.**
- Every connector in §1.8 — `UNKNOWN` until pinout/schematic/continuity data exists.
- **This applies with even more force to the ROS/Robot Computer itself (§2.1), not just the
  RK3288.** The newly-confirmed connection surface (LiDAR, stereo vision, ultrasonic sensors, hub
  motors, actuators, water pumps, water-level sensors, Hall sensors, depth camera, side-brush
  motors, mopping-brush motors, scrubber roller motor, vacuum motors, vSLAM, multiple cameras —
  some USB, some via an unspecified internal wired interface) is now `PHYSICAL-HARDWARE CONFIRMED`
  to exist, but every one of its connectors, buses, and protocols is `UNKNOWN`. **A replacement for
  the ROS/Robot Computer would need to replicate this entire connection surface**, not just the
  RK3288's display/network role — this is a substantially larger replacement scope than §26 of
  `01_MASTER_ENGINEERING_KNOWLEDGE_BASE.md` previously described, since that section's discussion
  of ROCK 4D was framed only as an RK3288 replacement candidate.

**No replacement claim of any kind should be made until every `UNKNOWN` in this document has been
resolved by direct physical investigation**, per this project's own explicit instruction.

---

## 5. Cross-reference: official Keenon C40 documentation

**Full detail:** [`09_KEENON_OFFICIAL_C40_DOCUMENTATION_AUDIT.md`](09_KEENON_OFFICIAL_C40_DOCUMENTATION_AUDIT.md).

Keenon's own official, customer-facing C40 documentation (`doc.keenonrobot.com`) was read directly
in a separate audit pass and cross-checked against every section above. The result:

- **Nothing above is resolved or changed.** Official documentation never names the RK3288, the
  Android/application computer, the separate SLAM computer, or any internal board, network, or
  interface — every `UNKNOWN` and `UNKNOWN — REQUIRES PINOUT / SCHEMATIC / CONTINUITY TEST` cell in
  §1-§4 above remains exactly as it was; official documentation simply has nothing to say about
  compute-hardware architecture at all.
- **One new, officially-confirmed fact belongs in §1's sensor/interface picture:** the robot "is
  equipped with LIDAR for positioning and navigation" (direct quote, official safety instructions)
  — the first non-APK, non-physical confirmation of LiDAR. This does not resolve any connector,
  pinout, or physical-mounting question in §1.8/§1.10; it confirms the sensor's existence and
  stated purpose only.
- **The document filed under Keenon's own "Hardware Connection Diagram" category is a mechanical
  parts exploded-view diagram, not an electrical/wiring/network diagram** — it labels 18 mechanical
  assemblies (fan, water tanks, pumps, brush motors, hub, chassis, etc.) with zero interface
  detail. This is recorded so that a future reader does not assume "Hardware Connection Diagram"
  already answers any of §1's open connector/interface questions — it does not.
- **§4's hardware-replacement cross-reference is unaffected:** official documentation provides no
  interface specification for the RK3288, the SLAM computer's physical link, or any connector, so
  it cannot be used to evaluate ROCK 4D (or any other candidate)'s compatibility. That evaluation
  still depends entirely on direct physical investigation, exactly as §4 already states.

---

## 6. Physical hardware inventory (photo evidence, 2026-09-26)

**Source:** nine photographs of the physical robot's internals, reviewed directly. Full per-photo
narrative: [`10_PHYSICAL_HARDWARE_PHOTO_EVIDENCE.md`](10_PHYSICAL_HARDWARE_PHOTO_EVIDENCE.md).
**"Likely function" is used only where a component is clearly labelled; every other function cell
says `REQUIRES TRACE / LABEL VERIFICATION` or `UNKNOWN` rather than guess from appearance.**

| ID | Photo | Component | Visible marking | Physical location | Observed connections | Likely function | Evidence | Status | Next verification |
|---|---|---|---|---|---|---|---|---|---|
| PH-01 | IMG_0619 | Small sensor/controller PCB | "AIR/PR GAUGE 2"; QFP IC marked "GigaDevice"/"ARM" (part number obscured); board rev "...B_V1.14" | Not established in-frame | Not shown in-frame | INFERRED: air/water pressure gauge sensor interface (from label only) | Photo, this session | PHYSICAL-HARDWARE CONFIRMED (board + label text); exact IC identity UNKNOWN | Glare-free IC close-up; trace harness to parent connector |
| PH-02 | IMG_0620 | Multi-section custom control PCB (2-board stack) | "102" (x2); "AiSHi" (capacitor brand); "STEP MOTOR"; "AIR PR GAUGE 1"; harness tags "MDB_J15_UP"/"MDB"/"FAN" | Not established in-frame | Multi-colour ribbon leads between the two boards; multiple white multi-pin headers; 2 orange/yellow power connectors | REQUIRES TRACE / LABEL VERIFICATION — do not infer from appearance | Photo, this session | PHYSICAL-HARDWARE CONFIRMED (board + labels); central IC identity and overall function UNKNOWN | Glare-free photo of central IC; trace MDB/FAN harnesses both ends; identify "STEP MOTOR" driven device |
| PH-03 | IMG_0621 | Hub/gear motor | "YONGJIE MOTOR / BLFE15-55682 / 24V 350W / 2025.12.16" | Wheel/hub position not established | Power leads (not traced) | Drive/hub motor (label-supported) | Photo, this session | PHYSICAL-HARDWARE CONFIRMED | Identify wheel position; trace control wiring to driver board |
| PH-04 | IMG_0622 | USB hub board | "USB HUB V1.1"; brand "QTE"; ports "USB1"/"USB2"; date "2021 10 22"; serial "2549 0018" | Mounted to metal bracket, near harness with "LR"-tagged ribbon cables | 2 ribbon/flex cables (hand-tagged "LR"); red/black power pair; upstream cable not traced | USB fan-out for 2 downstream devices (label-supported); which devices UNKNOWN | Photo, this session | PHYSICAL-HARDWARE CONFIRMED | Trace upstream host; trace USB1/USB2 downstream; read-only USB enumeration if accessible |
| PH-05 | IMG_0623 | USB-LoRa RF board | "USB LoRa V1.x"; brand "QTE"; module "E22-900T22S 1B" (EBYTE LoRa module family) | Zip-tied near chassis, external antenna via U.FL-to-SMA | External SMA antenna; host/upstream not shown | UNKNOWN — module family is commercially a sub-GHz LoRa transceiver; actual use in this robot not established | Photo, this session | PHYSICAL-HARDWARE CONFIRMED (board + module marking, positively legible) | Identify host; determine if/how actively used |
| PH-06 | IMG_0624 | CAN-labelled wire bundle | Cable tag "CAN" | Harness area near a small motor/actuator | Not traced to either end | UNKNOWN — do not assume CAN bitrate/IDs/protocol/endpoints | Photo, this session | PHYSICAL-HARDWARE CONFIRMED (label exists) | Trace harness end-to-end, both terminations, unpowered |
| PH-07 | IMG_0625 | "BRUSH MOTOR HALL" connector (designator "H3"); second "CAN" tag; USB2.0-rated cable jacket | Silkscreen "...SH MOTOR HALL H3"; cable print "...SPEED USB2.0 REVISION..." | Same general harness area as PH-06 | Not traced to parent board | Hall-effect feedback for a brush motor (label-supported); which brush UNKNOWN | Photo, this session | PHYSICAL-HARDWARE CONFIRMED (label exists) | Trace "H3" connector to parent board; identify which brush |
| PH-08 | IMG_0626 | "ARM IPC" enclosure + carrier PCB | Label "ARM IPC"; partial codes "X3.0L.1...", serial "2603040..." | Separate from RK3288 UIB, own enclosure | Wired into main harness; specifics not traced | INFERRED (not confirmed): candidate identity for the separate ROS/Robot Computer | Photo, this session | PHYSICAL-HARDWARE CONFIRMED (enclosure + label exists); identity as "the" ROS/Robot Computer UNKNOWN | **Highest priority** — confirm/rule out via network hostname, serial console, or fuller label, read-only |
| PH-09 | IMG_0634 | Charging-contact plates + small sensor PCB | Plastic marked "ABS ST" (material code only); no electronic part marking legible | Rear/underside panel | Cable with strain relief to armored/coiled run | Charging dock contacts (form-factor-supported); sensor PCB function UNKNOWN | Photo, this session | PHYSICAL-HARDWARE CONFIRMED (contacts + sensor PCB exist) | Identify sensor PCB function without further disassembly |

### 6.1 Topology — explicitly `UNKNOWN` until verified

Per this audit's own instruction, the following topology is recorded as a diagram of open
questions, not a confirmed circuit:

```
ROS COMPUTER  (candidate: "ARM IPC" enclosure, PH-08 — NOT CONFIRMED)
      |
      | ?  (medium/protocol UNKNOWN)
      v
CAN / Ethernet / Serial / other  (a "CAN"-labelled harness is PHYSICAL-HARDWARE CONFIRMED
      |                            to exist, PH-06/PH-07 — its bitrate, IDs, protocol, and
      |                            which devices share it are all UNKNOWN)
      v
Motor Controller / SCM-IoT Controller  (candidate location: PH-02's multi-section board —
      |                                  NOT CONFIRMED to be the SDK's SCM-IoT-addressed board)
      v
24V 350W Hub Motor  (PH-03, "YONGJIE MOTOR BLFE15-55682")
```

**This diagram exists to make the open question explicit, not to assert a confirmed data path.**
Every arrow and every box above is `UNKNOWN` except the two endpoints' own physical existence
(the "ARM IPC" enclosure and the hub motor, both `PHYSICAL-HARDWARE CONFIRMED`) and the "CAN"
label's existence. See §2.1 above and `02_EVIDENCE_REGISTER.md` E-052 (updated) for the full
statement of what this does and does not resolve.

### 6.2 Updated cross-reference for §4 (hardware replacement)

The photo evidence above makes §4's already-stated point more concrete, not different: a
replacement for the ROS/Robot Computer would need to replicate whatever "ARM IPC" (PH-08) actually
is, including its connections to the CAN-labelled bus (PH-06/PH-07), the USB hub (PH-04), and the
LoRa module (PH-05) — none of which is specified enough by photo evidence alone to design against.
