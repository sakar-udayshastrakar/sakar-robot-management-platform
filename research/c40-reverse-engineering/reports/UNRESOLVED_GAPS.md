# UNRESOLVED GAPS

Investigation: C40 (S-LS-M014C00-RW-F00-V246) static reverse-engineering
Last update: 2026-10-24 (finalization iteration 2)

This file enumerates everything the analysis could **not** establish, and why. It is the authoritative companion to `SECURITY_AUDIT.md`, `ROS_COMMUNICATION_ARCHITECTURE.md`, and the master report. Every item below is a gap, not a finding; findings live in `FINDINGS_REGISTER.csv`.

## 1. Method boundary (by constraint, not by omission)

The engagement was **static-only**. The following were deliberately **not performed**, per the task constraints:

- No live/dynamic testing, no code execution of the target artifacts.
- No network scanning, probing, connection attempts, or packet capture.
- No robot control, actuation, or firmware flashing.
- No credential use and no secret extraction (hard-coded secrets are located and named, never read out or validated).
- No exploitation or proof-of-concept against any finding.

Consequence: **no finding is `CONFIRMED_LIVE`.** Every classification is `CONFIRMED_STATIC`, `STRONGLY_SUPPORTED`, `INFERRED`, or `UNVERIFIED_RUNTIME`.

## 2. Tooling blockers (capability gaps)

| Area not fully analyzed | Blocker | Impact on findings |
|---|---|---|
| Native `.so` / ELF internal logic | No Ghidra / IDA / Hopper / objdump / rizin installed (REA policy: not auto-installed) | Native libs inspected by **symbols/strings/imports only**, not disassembled. Call-chain proof for native paths is partial. |
| Firmware deep structure (`.bin` GD32/STM32) | No binwalk / unblob | Framing (`AA AA..55 55`, CRC16 0xA001, 128-byte IAP) confirmed from strings/host-side code; on-chip logic not decompiled. |
| BKS keystore contents | No keystore parser available | Keystore presence confirmed; contents UNKNOWN. |
| `resources.arsc` | Not parsed (no tool) | Some resource-resolved strings/configs not expanded. |
| Packet/protocol semantics | No tshark / live capture | Wire-level framing of CoAP/WS/HTTP transports INFERRED from client code. |
| REA source build | `npm run build` BLOCKED (missing prebuilt native `rea-windows-x64.node`); used published `rea-agents@6.7.0` tarball instead | REA CLI available; REA MCP server not registered in-session (used CLI directly). No functional loss for static analysis. |

## 3. Runtime-unverified (UNVERIFIED_RUNTIME)

These are statically determined but cannot be confirmed without read-only live inspection:

- **Which launch profile actually runs on a physical C40 unit.** Package default is W3; C40 profile derived from `keenon_robot_C40.launch` args. (RIP-008, ROS-002)
- **Live ROS node/topic/service graph.** No `rostopic/rosnode/rosservice list` was run. Topic *names* harvested from ELF strings; **advertisement at runtime, pub/sub direction, and topic↔type pairing are not proven** (mostly INFERRED by convention). (RIP-010, ROS-011)
- **Effective reachability and in-practice authentication** of every network service (rosbridge 9090/9091, CoAP 5683, HTTP 54322/34569, WS 22345/12386, TFTP 9527, OTA `ros_update`, WebSocket 8888, REST 8089, MQTT). Existence CONFIRMED_STATIC; exposure/auth-in-practice UNVERIFIED_RUNTIME. (RIP-004, RIP-005, RIP-007, ROS-030/031, PCL-048, RIA-001)
- **Actual on-device state** of adb-over-TCP, Wi-Fi AP credentials (may be changed from shipped defaults), and config toggles (`ROS-034`, `ROS-042`, `SYS-006`).
- **Actual SoC models** and whether the aarch64 kernel runs a 32-bit userland on the Rockchip SoC (STRONGLY_SUPPORTED, not confirmed).

## 4. Finding-level open items (NOT_ANALYZED / UNKNOWN)

Carried from `FINDINGS_REGISTER.csv` (16 items):

- **ROS-006** chassis serial behaviour NOT_ANALYZED (strings/config only).
- **ROS-007** STM32 IAP-over-serial framing details NOT_ANALYZED.
- **ROS-017** upload path UNKNOWN.
- **ROS-020** DB column-level schema NOT_ANALYZED (table names only).
- **ROS-031** `ros_update` exact port (9527 INFERRED from adjacent literal; 9013 also seen) and authentication behaviour UNKNOWN/NOT_ANALYZED.
- **ROS-032** remote-tools WsServer listening port and input sanitization UNKNOWN.
- **ROS-033 / RIP-006** CoAP bridge exact port (5683 default INFERRED).
- **ROS-034** effective on-device network/config state UNKNOWN.
- **ROS-045** whether the located constant is the actual default credential NOT_ANALYZED (not read out).
- **ROS-046** CoAP-bridge `sudo` command-composition injection exploitability NOT_ANALYZED.
- **ROS-047** Samba/`smb` copy execution INFERRED; presence/users UNKNOWN.
- **ROS-053** whether a given script is installed on shipped units UNKNOWN.
- **ROS-072 / ROS-074** selected native headers/protocols behaviour NOT_ANALYZED.
- **Server-side controls** (MQTT broker ACLs, cloud auth, actual adb security posture) are server-side and UNKNOWN from client artifacts alone.

## 5. Highest-value next steps (read-only, if authorized access is later granted)

1. `ROS_MASTER_URI=http://192.168.64.20:11311 rostopic list -v && rosnode list && rosservice list` — upgrades the node/topic/service set from `CONFIRMED_STATIC` to `CONFIRMED_LIVE` and resolves pub/sub direction via `rostopic info`.
2. Confirm whether rosbridge **:9091 (`control` ns)** and REST **:8089** truly accept unauthenticated control in practice.
3. Verify adb-over-TCP state and whether shipped Wi-Fi AP / SMB / sudo defaults remain unchanged on a real unit.
4. With a disassembler: decompile `libcoap_bridge.so`, `send_depth_node`, and the GD32/STM32 `.bin` images to confirm the injection and IAP paths.

All of step 1–4 require authorization and physical/network access that did not exist in this environment; none was attempted.
