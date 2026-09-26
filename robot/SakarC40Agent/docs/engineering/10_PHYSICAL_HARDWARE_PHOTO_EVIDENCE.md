# Physical Hardware Photo Evidence — 2026-09-26

**Status of everything in this document: `PHYSICAL-HARDWARE CONFIRMED` for what is directly visible
in the photograph (a component exists, a label reads a specific string, a connector is present).
Nothing beyond what is directly visible or directly legible is asserted as confirmed. Any reading
of a board's *function* beyond its own silkscreen label is explicitly marked `INFERRED`, never
`CONFIRMED`, per this audit's own rule.**

Nine photographs of the physical Keenon C40 robot's internals were provided and reviewed directly
(each image was opened and read, not only described secondhand) during this session:
`IMG_0619.jpeg` through `IMG_0626.jpeg`, and `IMG_0634.jpeg`. This document records what is
actually visible in each one. [`03_HARDWARE_ARCHITECTURE.md`](03_HARDWARE_ARCHITECTURE.md) §6 turns
these into a structured inventory table and cross-references them against the rest of this
project's evidence; this document is the per-photo source record.

**A cross-cutting observation, not specific to any one photo:** three of the small interface
daughterboards (the USB hub, the USB-LoRa board, and a partially-visible board in IMG_0625) all
carry the same silkscreen brand mark, most legibly read as **"QTE"**. This suggests a common
contract manufacturer or design house produced several of these small interface boards. This is
recorded as an observation, not a confirmed vendor relationship.

---

## IMG_0619

**Visible hardware:** A PCB section dominated by a large QFP (quad flat package) IC with dense
surrounding passive components (resistors, capacitors), a crystal oscillator (silkscreen
reference `X1`), several smaller ICs (references including `U9`, `U23`, `U28`), a printed QR-code
identification sticker, and two probe leads/wires resting on the board (not part of the board
itself — appear to be test leads or hot-glue-tacked wires from the photographer's own probing
setup).

**Visible labels:**
- Silkscreen text reading **"AIR/PR GAUGE 2"**, rotated, near the upper-left of the frame.
- A partial board-revision string in the top-right corner, partially cropped, reading
  approximately **"...B_V1.14"**.
- The QFP IC's own top marking includes the legible brand text **"GigaDevice"** and **"ARM"**,
  alongside additional characters that are scratched/obscured and **not confidently legible as a
  full part number.**

**Confirmed observations:**
- A dedicated PCB hosting an ARM-class QFP microcontroller exists. `PHYSICAL-HARDWARE CONFIRMED`.
- The label "AIR/PR GAUGE 2" is directly legible on this board. `PHYSICAL-HARDWARE CONFIRMED`.
- The IC's brand marking includes legible "GigaDevice" and "ARM" text. `PHYSICAL-HARDWARE
  CONFIRMED` (as a transcription of visible text only).

**Unknowns:**
- The IC's exact part number — **`UNKNOWN`, per this audit's explicit instruction not to guess the
  chip.** The marking is partially scratched/obscured and is not read as a specific part number
  here, even though "GigaDevice" (a real MCU vendor known for ARM Cortex-M "GD32" parts) is
  legible as a brand.
- This board's exact function beyond its own "AIR/PR GAUGE 2" label — not confirmed.
- Which parent harness/connector this board attaches to — not shown in this frame.

**Relationship to existing architecture:** Not previously documented anywhere in this project. A
plausible (`INFERRED`, not confirmed) connection to the cleaning system's water/air-pressure
sensing, relevant to the "water-level sensors" line item in
[`09_KEENON_OFFICIAL_C40_DOCUMENTATION_AUDIT.md`](09_KEENON_OFFICIAL_C40_DOCUMENTATION_AUDIT.md)
§3 (previously `NOT DOCUMENTED IN OFFICIAL C40 DOCUMENTATION`) and the "water pumps"/"water-level
sensors" items in the new physical-observation list (`02_EVIDENCE_REGISTER.md` E-051).

**Follow-up required:** A clean, glare-free, angled close-up of the IC's top marking; trace this
board's harness to its parent connector; identify what "AIR PR GAUGE 1" (IMG_0620) and "AIR/PR
GAUGE 2" (this photo) actually gauge (air pressure? water pressure? both, one per tank/line?).

---

## IMG_0620

**Visible hardware:** A larger, two-board-stack custom control PCB assembly. The upper, smaller
daughterboard carries several ICs (appearance consistent with gate-driver/switching components)
and two connector groups each silkscreened **"102"**, wired via multi-colored ribbon-style leads
(pink, blue, white, green — a color pattern consistent with, but not confirmed to be, multi-phase
motor or encoder/Hall wiring) down to the main lower board. The lower board carries an
**"AiSHi"**-branded electrolytic capacitor (a capacitor manufacturer's own brand mark, not a
system-level label), a large central IC whose top marking is obscured by lens glare (not legible
in this photograph), two orange/yellow locking power connectors (XT-style, color/shape consistent
with common high-current RC/robotics connectors — exact series not confirmed), a green
"PASS"-stamped QR/QC sticker, and multiple white multi-pin connector headers.

**Visible labels:**
- **"102"** — appears twice, silkscreened at two connector/component groups on the upper
  daughterboard. Meaning not established (possibly a part or connector reference designator, not
  necessarily a protocol or model number).
- **"AiSHi"** — capacitor manufacturer's own brand, not a system label.
- **"STEP MOTOR"** — silkscreen text near a connector cluster on the right-center of the lower
  board.
- **"AIR PR GAUGE 1"** — silkscreen text near a connector on the lower-left of the lower board
  (pairs with "AIR/PR GAUGE 2" from IMG_0619 — these read as companion connectors/channels 1 and
  2 of the same gauge system).
- Heat-shrink cable labels visible in the harness leaving this board: **"MDB_J15_UP"**, **"MDB"**,
  **"FAN"**.

**Confirmed observations:**
- A dedicated, multi-section custom control PCB assembly exists with the labeled connectors above.
  `PHYSICAL-HARDWARE CONFIRMED`.

**Unknowns:**
- The board's overall function — **`REQUIRES TRACE / LABEL VERIFICATION`**, exactly as instructed;
  not inferred from appearance alone.
- The identity of the large, glare-obscured central IC on the lower board.
- What "102" denotes.
- What "MDB" stands for — a plausible reading is "Motor Driver Board" or "Main Driver Board," but
  this is **`INFERRED`, not confirmed**, and is not asserted as fact anywhere else in this project.
- Which specific mechanism the "STEP MOTOR"-labeled connector drives (candidates — squeegee lift,
  brush-lift, or another stepper-actuated mechanism — none confirmed).

**Relationship to existing architecture:** A plausible (not confirmed) physical location for part
of the "motor/motion controller board" already documented from SDK static analysis
(`03_HARDWARE_ARCHITECTURE.md` §3, "SCM IoT" protocol) and/or a point along the still-unresolved
path in E-052's topology question. Not identified as either with confidence.

**Follow-up required:** A glare-free photo of the central IC's marking; trace "MDB"/"MDB_J15_UP"/
"FAN" harnesses at both ends; determine the "STEP MOTOR" connector's actual downstream device;
determine what "102" refers to.

---

## IMG_0621

**Visible hardware:** A cylindrical gear-motor assembly in an aluminum housing, form factor
consistent with a wheel hub motor.

**Visible labels:** A green adhesive label reading, exactly:
```
YONGJIE MOTOR
BLFE15-55682
24V 350W
2025.12.16
```
plus an accompanying QR code.

**Confirmed observations:** `PHYSICAL-HARDWARE CONFIRMED` — the label text above, transcribed
exactly as printed.

**Unknowns:** Motor control protocol/interface — **not inferred from the label.** Which specific
wheel/axle position (left/right, drive/caster) this individual motor occupies — not shown in this
frame.

**Relationship to existing architecture:** The first labeled, photographic confirmation of a "hub
motor" — previously known only as the unlabeled "Hub assembly" (item 11) in the official
exploded-view diagram
([`09_KEENON_OFFICIAL_C40_DOCUMENTATION_AUDIT.md`](09_KEENON_OFFICIAL_C40_DOCUMENTATION_AUDIT.md)
§2.10) and as a generic "hub motors" entry in the new physical-observation list
(`02_EVIDENCE_REGISTER.md` E-051).

**Follow-up required:** Identify this motor's wheel position; trace its control/power wiring to
whatever board actually drives it.

---

## IMG_0622

**Visible hardware:** A small green PCB mounted on a metal bracket, with two flex/ribbon cables
(hand-labeled "LR" in blue marker on their strain-relief tape) and a red/black power lead pair.

**Visible labels:**
- Silkscreen **"USB HUB V1.1"**.
- Brand mark **"QTE"**.
- Two connector labels, **"USB1"** and **"USB2"**.
- A date code **"2021 10 22"** and a serial/QR sticker reading **"2549 0018"**.

**Confirmed observations:** `PHYSICAL-HARDWARE CONFIRMED` — a board labeled "USB HUB V1.1" (brand
"QTE") with exactly two labeled downstream USB ports ("USB1", "USB2") physically exists.

**Unknowns:**
- **Which peripherals/cameras connect to USB1 vs. USB2 — explicitly NOT assumed here.** This
  requires cable tracing or runtime USB enumeration, per this audit's own instruction.
- The hub's upstream host (i.e., which computer's USB controller feeds this hub) — not shown in
  this frame; the "ARM IPC" board (IMG_0626) is a plausible but unconfirmed candidate.
- What the "LR" hand-labeled tag denotes (possibly "Left/Right," e.g. for stereo camera left/right
  channels — **`INFERRED`, not confirmed**).

**Relationship to existing architecture:** Directly relevant to the physical observation already
recorded (`02_EVIDENCE_REGISTER.md` E-051) that "some cameras are USB-connected" — this hub is a
strong candidate for that USB fan-out point, but the connection is not traced or confirmed.

**Follow-up required:** Trace the hub's upstream cable to its host; trace USB1/USB2 to their
respective downstream devices; if accessible, read USB enumeration output (e.g. `lsusb` or
equivalent) from the host, read-only, no configuration changes.

---

## IMG_0623

**Visible hardware:** A separate green PCB, zip-tied near the chassis, with an external antenna
connected via a U.FL-to-SMA pigtail.

**Visible labels — positively legible, recorded per this audit's own exception for identifiable
markings, not treated as a guess:**
- Silkscreen **"USB LoRa V1.x"** (board name) and the same **"QTE"** brand mark seen on the USB
  hub (IMG_0622).
- A module sticker with a QR code reading **"E22-900T22S 1B"**, plus additional small print
  (manufacturer/compliance text, not fully transcribed here beyond this primary designation).

**Confirmed observations:** `PHYSICAL-HARDWARE CONFIRMED` — a board silkscreened "USB LoRa V1.x"
carrying a module marked "E22-900T22S 1B," with an external antenna, physically exists. The
designation "E22-900T22S" corresponds to a publicly-cataloged commercial LoRa (sub-GHz RF
transceiver) module family — this is a direct transcription of a legible marking, **not an
inference about how this specific robot actually uses it.**

**Unknowns — explicitly not guessed, per this audit's instruction:**
- Protocol/frequency configuration as actually used in this robot.
- Chipset-level detail beyond the module's own printed designation.
- Purpose within the overall system (candidates that are **not confirmed**: elevator-call
  signaling, workstation-to-robot auxiliary link, fleet/beacon communication, or something else).
- Network role, or whether this module is even actively used/enumerated by any host computer.

**Relationship to existing architecture: this is new.** No prior document in this project
mentions LoRa or any sub-GHz RF link. It is architecturally distinct from the already-documented
Wi-Fi/LAN phone-to-robot pairing requirement (`09_KEENON_OFFICIAL_C40_DOCUMENTATION_AUDIT.md` §3
row "Other interfaces," `02_EVIDENCE_REGISTER.md` E-045) — nothing here suggests these are the
same link, and nothing here suggests they are different systems that never interact; both remain
possible.

**Follow-up required:** Identify this module's USB (or other) host; determine whether it is
actively used, and for what, without modifying its configuration.

---

## IMG_0624

**Visible hardware:** A dense harness area near a small motor/actuator, with multiple heat-shrink
and cable-tag labels on individual wire bundles.

**Visible labels:** A white cable tag printed vertically, reading exactly **"CAN"**, directly
legible on one wire bundle. Partial date-code tags (e.g. "2025...") consistent with other
2025-dated labels seen elsewhere in this photo set (IMG_0621, IMG_0625).

**Confirmed observations:** `PHYSICAL-HARDWARE CONFIRMED` — a wire bundle explicitly labeled "CAN"
physically exists inside the robot.

**Unknowns — explicitly, per this audit's own instruction, none of the following is established
by this photo:**
- Which computer owns/drives this CAN bus.
- Where the CAN controller is physically located.
- Bus bitrate.
- CAN message IDs.
- CAN message/frame format (including whether a higher-layer protocol such as CANopen or J1939 is
  in use — **neither is assumed**).
- Whether this bus is used for motor control specifically.
- Which other devices, if any, share this bus.

**Relationship to existing architecture:** Directly relevant to E-052 (the unresolved question of
how the ROS/Robot Computer relates to the SDK's separately-documented SCM-IoT motor-controller
finding) — a CAN bus is a plausible candidate medium for that unresolved link, but this photo
alone does not establish that. See `03_HARDWARE_ARCHITECTURE.md` §6 for the topology diagram this
evidence feeds into, drawn explicitly as `UNKNOWN` pending verification.

**Follow-up required:** Trace the "CAN"-labeled harness from end to end (both terminations)
without disconnecting anything while powered; identify what sits at each end.

---

## IMG_0625

**Visible hardware:** A wider view of the same general harness area as IMG_0624 — a large white
multi-pin connector block, additional wire bundles, a second small green PCB fragment (same
general "QTE"-style silkscreen font as IMG_0622/IMG_0623 but not fully legible in this frame), a
small worm-gear actuator/motor, and a printed cable jacket.

**Visible labels:**
- Partial silkscreen near the top connector block, most consistent legible reading: **"...SH
  MOTOR HALL H3"** — read as **"BRUSH MOTOR HALL"** with connector designator **"H3"**.
- A second "CAN" tag, matching IMG_0624's label.
- A printed cable-jacket marking reading approximately **"...SPEED USB2.0 REVISION..."**,
  consistent with a standard USB 2.0-rated cable jacket print.
- Date-code tags "2025.12.16," matching the motor label in IMG_0621.

**Confirmed observations:** `PHYSICAL-HARDWARE CONFIRMED` — a connector/header labeled consistent
with "BRUSH MOTOR HALL" (designator "H3") exists, directly confirming Hall-effect sensor feedback
wiring specifically associated with a brush motor (more specific than the generic "hub motors" /
"Hall sensors" framing in the physical-observation report, E-051). A USB 2.0-rated cable is
physically present in this harness area.

**Unknowns:** Which specific brush (side brush vs. main/scrubbing brush) this Hall connector
serves; which parent board the "H3" connector belongs to (not shown in this frame).

**Relationship to existing architecture:** Refines E-051's generic "Hall sensors" entry with a
specific, legible association to a brush motor specifically.

**Follow-up required:** Trace the "H3"/"BRUSH MOTOR HALL" connector to its parent board; determine
which physical brush it serves.

---

## IMG_0626

**Visible hardware:** A small carrier PCB mounted atop a black enclosure/module, wired into the
main harness, with a label affixed to the enclosure itself.

**Visible labels:** A label reading, most legibly, **"ARM IPC"**, alongside a QR code and partial
alphanumeric codes (approximately **"X3.0L.1..."** and a serial beginning **"2603040..."**).

**Confirmed observations:** `PHYSICAL-HARDWARE CONFIRMED` — an enclosure/module labeled "ARM IPC,"
physically distinct from the RK3288 UIB board and its own separate carrier PCB, exists.

**Unknowns:** Whether this "ARM IPC" enclosure is the same entity as the previously-documented
"separate ROS/Robot Computer" (`01_MASTER_ENGINEERING_KNOWLEDGE_BASE.md` §4,
`03_HARDWARE_ARCHITECTURE.md` §2/§2.1) is **not confirmed by this photo alone.** "IPC" commonly
denotes "Industrial PC," which is *consistent with*, but not proof of, this being the ROS/Robot
Computer. Exact CPU, OS, and ROS version remain `UNKNOWN` regardless of which reading is correct.

**Relationship to existing architecture:** The **strongest physical candidate found in this photo
set** (`INFERRED`, explicitly not `CONFIRMED`) for the previously `PHYSICAL-HARDWARE CONFIRMED`-
but-otherwise-`UNKNOWN` separate ROS/Robot Computer (E-002/E-051). Recorded as a candidate match
to investigate, not a resolved identification.

**Follow-up required:** This is the single highest-priority item in the next physical test plan
(`03_HARDWARE_ARCHITECTURE.md` §6, item 1) — positively confirm or rule out whether "ARM IPC" is
the ROS/Robot Computer, e.g. via a network hostname/interface check, a serial console if one
exists, or a more complete label/marking, all read-only.

---

## IMG_0634

**Visible hardware:** A rear/underside panel assembly (plastic resin marked **"ABS ST"** — a
material/mold code, not a functional label) showing two recessed metal charging-contact plates
mounted side by side in a bracket, and a separate small sensor PCB (two small components — one
with an appearance consistent with an optical component such as a photodiode/phototransistor, the
other a small driver IC) mounted in its own black bracket nearby, wired via a cable with a strain-
relief boot leading into an armored/coiled cable run.

**Visible labels:** Only "ABS ST" (plastic material code) is legible; no electronic part marking
is legible on the sensor PCB or the charging-contact bracket in this frame.

**Confirmed observations:** `PHYSICAL-HARDWARE CONFIRMED` — two metal charging-contact plates
exist in a dedicated bracket, consistent with the robot's self-charging dock-contact interface; a
small sensor PCB with at least one optically-appearing component exists nearby.

**Unknowns:** The small sensor PCB's exact type/function (candidates — a floor/cliff sensor, a
docking-alignment sensor, or something else — **none confirmed**); the charging contacts' exact
electrical spec (voltage/current) is not independently confirmed from this photo (the
already-documented 24V motor rail, IMG_0621, is context, not a direct measurement of these
contacts).

**Relationship to existing architecture:** The first direct photographic evidence of the robot's
charging-contact hardware, complementing the already-`OFFICIAL DOCUMENTATION CONFIRMED`
charging-pile placement standard
([`09_KEENON_OFFICIAL_C40_DOCUMENTATION_AUDIT.md`](09_KEENON_OFFICIAL_C40_DOCUMENTATION_AUDIT.md)
§2.4) and the "charging system" row in `09` §3 (electrical interface was, and remains, `NOT
DOCUMENTED` in official material — this photo is physical evidence of the contacts' existence,
still without a confirmed electrical spec).

**Follow-up required:** Identify the small sensor PCB's function without further disassembly;
photograph any marking on it if one becomes visible.

---

## Cross-references

- Structured inventory table and topology diagram:
  [`03_HARDWARE_ARCHITECTURE.md`](03_HARDWARE_ARCHITECTURE.md) §6.
- Evidence IDs: [`02_EVIDENCE_REGISTER.md`](02_EVIDENCE_REGISTER.md) E-051 through E-060.
- Summary: [`01_MASTER_ENGINEERING_KNOWLEDGE_BASE.md`](01_MASTER_ENGINEERING_KNOWLEDGE_BASE.md)
  §33.
- Newly-added unknowns: [`06_UNVERIFIED_ITEMS.md`](06_UNVERIFIED_ITEMS.md).
- Prioritized next physical investigation:
  [`07_NEXT_PHYSICAL_ROBOT_AUDIT.md`](07_NEXT_PHYSICAL_ROBOT_AUDIT.md) Phase G.
