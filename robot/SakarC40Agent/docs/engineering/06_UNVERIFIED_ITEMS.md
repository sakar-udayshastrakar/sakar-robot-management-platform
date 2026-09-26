# Unverified Items

Every item below is explicitly `UNKNOWN` or `NOT VERIFIED` (per the status vocabulary in
[`01_MASTER_ENGINEERING_KNOWLEDGE_BASE.md`](01_MASTER_ENGINEERING_KNOWLEDGE_BASE.md)) as of this
consolidation. None of these should be treated as resolved, guessed at, or silently assumed one way
or the other anywhere else in this project's documentation or code. Each item names where it would
be resolved once physical access exists.

## Hardware

1. Physical SLAM computer model, manufacturer, and form factor — `UNKNOWN`. Resolve via
   `07_NEXT_PHYSICAL_ROBOT_AUDIT.md` Phase E.
2. SLAM computer's operating system / ROS distribution — `UNKNOWN`. Resolve via Phase D/E.
3. Exact network topology connecting the RK3288, the SLAM computer, and the motor/motion
   controller board — `UNKNOWN`. Resolve via Phase B.
4. Exact physical connection medium between the RK3288 and the SLAM computer (dedicated Ethernet?
   shared switch? USB-Ethernet?) — `UNKNOWN`. Resolve via physical inspection, not software-only
   testing.
5. Exact RK3288 UIB connector pinouts (every connector on the board) — `UNKNOWN — REQUIRES
   PINOUT / SCHEMATIC / CONTINUITY TEST`, per `03_HARDWARE_ARCHITECTURE.md` §1.8.
6. RK3288 board's exact memory part number/capacity, Ethernet PHY, Wi-Fi module, RTC battery
   presence — all `UNKNOWN`, not reported in this session.
7. Whether the RK3288's "DUAL_LVDS" naming corresponds to an actually-populated dual-LVDS display
   interface, and its exact signal/timing spec — `INFERRED` from the name only, not `CONFIRMED`.
7a. The ROS/Robot Computer's board model, manufacturer, form factor, CPU/SoC, OS, and ROS
    version/distribution — `UNKNOWN`. Its *connections* to LiDAR, stereo vision, ultrasonic
    sensors, hub motors, actuators, water pumps, water-level sensors, Hall sensors, depth camera,
    side-brush motors, mopping-brush motors, scrubber roller motor, vacuum motors, vSLAM, and
    multiple cameras are `PHYSICAL-HARDWARE CONFIRMED` (this session) — only the board's own
    identity remains unresolved. Resolve via `07_NEXT_PHYSICAL_ROBOT_AUDIT.md` Phase E.
7b. For every sensor/actuator in item 7a: its specific bus (CAN? UART? GPIO? something else?),
    connector, pinout, and — where applicable — CAN IDs, UART baud rate, or USB vendor/product ID
    — all `UNKNOWN — REQUIRES PINOUT / SCHEMATIC / CONTINUITY TEST`. The user's own report
    distinguishes only "USB-connected" vs. "internal wired interface" for the cameras; which
    specific wired interface (a second UART, a second CAN bus, a proprietary serial bus, etc.) is
    unspecified and not guessed here.
7c. Whether the SDK's SCM-IoT-addressed motor/motion-controller board (`03_HARDWARE_ARCHITECTURE.md`
    §3) is the same hardware as the ROS/Robot Computer (item 7a), a distinct board reached through
    it, or a board it does not interact with at all — `REQUIRES PHYSICAL VERIFICATION`, explicitly
    not resolved either way by either the physical observation or the static SDK analysis. See
    `02_EVIDENCE_REGISTER.md` E-052 and `09_KEENON_OFFICIAL_C40_DOCUMENTATION_AUDIT.md` §11.

## Network / SLAM live status

8. The current physical robot's actual IP address (tablet, Ethernet, and SLAM-computer addresses)
   — `NOT VERIFIED`. `192.168.64.20` is a static-analysis finding, not a live-confirmed current
   address (see `02_EVIDENCE_REGISTER.md` E-011/E-012/E-018).
9. Actual rosbridge availability on a live robot — `NOT VERIFIED`.
10. ROS version/distribution running on the live SLAM computer — `NOT VERIFIED`.
11. Live ROS topics, services, and actions on the physical robot — `NOT VERIFIED`. See
    `01_MASTER_ENGINEERING_KNOWLEDGE_BASE.md` §12 for the specific named topics checked against
    decompiled evidence and found either absent (as literals) or replaced by Keenon-custom names.
12. Whether standard ROS topics (`/map`, `/scan`, `/tf`, `/odom`, `/amcl_pose`, `/initialpose`,
    `/cmd_vel`) exist on the real robot at all, alongside or instead of the Keenon-custom names
    found in decompiled JS — `NOT VERIFIED`.
13. Whether any authentication gates the rosbridge (9090/9091), the app WebSocket (8888), or the
    local HTTP server (8080) on a live unit — `UNKNOWN`.

## Mapping control (live behavior)

14. Actual mapping **start** command as it behaves on a live robot — `NOT VERIFIED`. Static
    evidence points to a `ROSNAME.BUILDMAPTYPE` rosbridge service call, but the exact trigger was
    not isolated to a single named function even in static analysis.
15. Actual mapping **stop** command as it behaves on a live robot — `NOT VERIFIED`.
16. Actual **map save** mechanism as it behaves on a live robot — `NOT VERIFIED`. Static evidence
    describes a generic `postDBOperation` write to the `map` table; the exact "on finish, write X"
    call site was not individually traced.
17. Actual **localization/relocalization** command as it behaves on a live robot — `NOT VERIFIED`.
18. Exact `map.value` column's payload encoding (image format vs. custom binary-as-string) —
    `UNKNOWN`, not decoded in Phase 4.
19. Exact rosbridge topic backing the JS's `getRobotLocalPosition`/`getRobotPosition` calls —
    `UNKNOWN`, not isolated in Phase 4.
20. Live topic update frequency / message size for any topic — `UNKNOWN`, no live capture
    performed.

## Offline / cloud

21. Whether mapping can run fully offline on the physical robot — `NOT VERIFIED`. Static analysis
    found no cloud call in the map/pose/elevator/gate write path, but this has never been observed
    live with internet actually disconnected.
22. Whether Keenon Cloud is required for any *specific* mapping workflow (as opposed to the core
    data path) — `NOT VERIFIED`.
23. The OTA/firmware-update package's actual data source (local file, local TFTP, or a remote
    update server) — `UNKNOWN`, explicitly flagged in `PEANUT_SDK_C40_TECHNICAL_STUDY.md` §7 as
    not ruled out.

## Security (explicitly not converted into operational findings)

24. Whether the `MotorComponent.enable()` no-per-app-authorization finding (static) actually
    allows a real unauthorized unlock on a live robot — `NOT VERIFIED` (physical effect
    unconfirmed per `PEANUT_SDK_C40_API_MATRIX.md` row 24).
25. Whether real ADB access to a live robot's Android device can actually be used to bypass
    Sakar's own lock gating in practice — `NOT VERIFIED`/not attempted.
26. CORS behavior of any Keenon local HTTP server — `NOT FOUND` in existing documentation; not
    investigated in this consolidation pass.
27. Wi-Fi credential storage/exposure mechanism — `NOT FOUND` in existing documentation.
28. SSH credential derivation mechanism (Remote Assistant's `SshInfoActivity`) — `NOT FOUND` in
    existing documentation; the Activity's existence is confirmed, its credential mechanism is not.

## Hardware replacement (ROCK 4D)

29. Whether ROCK 4D supports 32-bit ARM (or a 32-bit compatibility mode) sufficient to load the
    licensed Peanut SDK's `armeabi`/`armeabi-v7a`-only native libraries — `UNKNOWN`.
30. ROCK 4D's display (LVDS or otherwise), USB, Ethernet, touch, audio, power, and any
    robot-specific connector compatibility with the current wiring harness — `UNKNOWN`, no
    investigation performed.
31. Whether ROCK 4D could replicate whatever the (currently `UNKNOWN`) physical link to the
    separate SLAM computer turns out to be — `UNKNOWN`, blocked on item 4 above.

## Official documentation gaps (new, from the Keenon official C40 documentation audit)

Full detail: [`09_KEENON_OFFICIAL_C40_DOCUMENTATION_AUDIT.md`](09_KEENON_OFFICIAL_C40_DOCUMENTATION_AUDIT.md).
These are explicitly `NOT DOCUMENTED` (per that document's own status vocabulary — see its intro
for why this is not the same statement as `UNKNOWN` above), not merged into the numbered list
above because they describe a documentation gap, not a hardware/software unknown:

32. Whether "Binocular Stereo Vision" (officially named, §2.4/§3 of `09`) is the same physical
    sensor/code path as the ARCore-based VSLAM pose assistance found in Phase 4 — `NOT DOCUMENTED`
    officially, and not established from APK analysis either. Resolve via physical sensor
    inspection or a decompiled cross-reference neither phase has attempted.
33. What the official "Multi-Robot Management" and "Map upload" mapping-workflow steps actually do
    (destination, protocol, whether cloud is involved) — `NOT DOCUMENTED`, since the images
    containing their step-by-step detail could not be downloaded in this session (see `09` §0).
    Resolve by re-attempting image retrieval when the portal's binary-asset delivery is reliable,
    or by direct product testing.
34. Roughly 180 of the ~185 total official C40 documentation page-scan images across all 10
    documents were never successfully downloaded/reviewed, due to a reproducible server-side
    reliability problem confirmed via three independent HTTP mechanisms (`09` §0, E-049) — their
    content is simply unknown, not confirmed absent. Resolve by retrying when/if the portal's
    asset delivery becomes reliable.
35. Whether the workstation's water-filling system is automatic or manual — official documentation
    never uses the word "automatic" for this system (`09` §6) — `NOT DOCUMENTED`. Resolve via the
    unreviewed workstation-deployment images (item 33) or physical inspection.
36. Exact interface/connector specification for any officially-named component (LiDAR, stereo
    vision sensor, fan, water pump, brush motors) — official documentation names these components
    but gives zero electrical/connector detail for any of them (`09` §3/§8) — `NOT DOCUMENTED`.
    This does not change any `UNKNOWN — REQUIRES PINOUT / SCHEMATIC / CONTINUITY TEST` item in
    `03_HARDWARE_ARCHITECTURE.md` §1.8, which still requires physical investigation regardless.

## Physical hardware photo evidence gaps (new, 2026-09-26)

Full detail: [`10_PHYSICAL_HARDWARE_PHOTO_EVIDENCE.md`](10_PHYSICAL_HARDWARE_PHOTO_EVIDENCE.md),
[`03_HARDWARE_ARCHITECTURE.md`](03_HARDWARE_ARCHITECTURE.md) §6.

37. Whether the enclosure labelled "ARM IPC" (photo PH-08) is the same entity as the
    previously-documented separate ROS/Robot Computer — `UNKNOWN`. This is the single
    highest-priority open item from the new photo evidence. Resolve via
    `07_NEXT_PHYSICAL_ROBOT_AUDIT.md` Phase G item 1.
38. The exact part number of the QFP MCU on the "AIR/PR GAUGE 2" board (PH-01) — `UNKNOWN`, marking
    obscured/scratched; only the brand text "GigaDevice"/"ARM" is legible. Do not guess the chip.
39. The overall function of the multi-section custom control PCB carrying "STEP MOTOR"/"AIR PR
    GAUGE 1"/"102" labels (PH-02), and the identity of its glare-obscured central IC — `REQUIRES
    TRACE / LABEL VERIFICATION`, not inferred from appearance.
40. Which peripherals/cameras connect to the "USB HUB V1.1" board's "USB1" vs. "USB2" ports
    (PH-04), and what feeds the hub upstream — `UNKNOWN`. Do not assume which cameras use this hub
    without cable tracing or runtime USB enumeration.
41. The purpose, active-use status, and host of the "USB LoRa" board's "E22-900T22S 1B" module
    (PH-05) — `UNKNOWN`. This is a genuinely new finding with no prior mention anywhere in this
    project; do not assume protocol, frequency-in-use, or network role.
42. The "CAN"-labelled harness's (PH-06/PH-07) bitrate, message IDs, message/frame format
    (including whether CANopen, J1939, or a proprietary format is used — neither assumed),
    physical endpoints, and which devices share the bus — all `UNKNOWN`. Does not resolve item 7c
    / E-052.
43. Which specific brush (side vs. main/scrubbing) the "BRUSH MOTOR HALL" connector "H3" (PH-07)
    serves, and which parent board it belongs to — `UNKNOWN`.
44. The small sensor PCB's exact function near the charging-contact bracket (PH-09) — `UNKNOWN`;
    visually consistent with an optical component, not confirmed as any specific sensor type.
45. The charging-contact plates' (PH-09) exact electrical specification (voltage/current/protocol)
    — `UNKNOWN` — a photograph of the contacts' existence is not a measurement of their spec.

---

**Rule, restated:** none of the above may be marked `CONFIRMED` anywhere in this project without a
new, explicitly cited piece of evidence added to
[`02_EVIDENCE_REGISTER.md`](02_EVIDENCE_REGISTER.md).
