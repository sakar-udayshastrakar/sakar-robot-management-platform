# C40 Reverse-Engineering — Master Report

**Target:** C40 `S-LS-M014C00-RW-F00-V246` (Keenon cleaning-robot platform)
**Scope:** Static reverse-engineering of shipped artifacts (5 APKs, ROS `.deb` packages, GD32/STM32 firmware, base-station/schedule/SLAM update packages).
**Method:** Static-only. No live testing, probing, robot control, firmware flashing, credential use, or secret extraction.
**Date:** 2026-10-24
**Status:** COMPLETE (Phases 1–4). This report is the consolidated index over all deliverables below.

---

## 1. Executive summary

A five-domain static analysis covered **127 inventoried artifacts** and produced **226 findings** (0 duplicate IDs, every finding carries evidence). The system is an Android tablet (`192.168.64.10`) driving a ROS computer (`192.168.64.20`) over a **flat, largely unauthenticated `192.168.64.0/24` LAN**; the ROS computer reaches the Internet *through* the tablet (`net_gw.conf` default route via `.10`).

The dominant risk theme is **absent authentication/authorization and trust boundaries that assume an isolated LAN**: unauthenticated rosbridge control (9090/9091), an unauthenticated REST API with reboot/ROS control (8089), an unauthenticated WebSocket control server (8888), an OTA update service running as root without integrity controls, an exported Android receiver that runs arbitrary shell commands from an unauthenticated broadcast, MQTT-delivered remote shell, adb-over-TCP reachable through an FRP reverse tunnel, and multiple hard-coded credentials/keys and trust-all TLS paths.

**Severity:** 1 Critical, 24 High, 52 Medium, 27 Low, 122 Info.
**No finding is `CONFIRMED_LIVE`** — all are static/inferred; see `UNRESOLVED_GAPS.md`.

## 2. Deliverables index (actual paths)

All paths are under `D:\Sakar Robotics Projects\C40 S-LS-M014C00-RW-F00-V246\REVERSE_ENGINEERING_RESULTS\`.

| Deliverable | File | Content |
|---|---|---|
| This master report | `REVERSE_ENGINEERING_MASTER_REPORT.md` | Consolidated index, exec summary, top findings |
| Findings register | `FINDINGS_REGISTER.csv` | 226 findings, stable IDs, evidence, severity, status |
| Security audit | `SECURITY_AUDIT.md` | Narrative security analysis |
| Security table | `_sec_table.md` | Sorted security findings table |
| All-findings table | `_all_findings_table.md` | All 226 findings, per-domain |
| Network endpoints | `NETWORK_ENDPOINTS.csv` | 394 endpoints (host:port/scheme) |
| Network protocols | `NETWORK_PROTOCOL_ANALYSIS.md` | Protocol/port analysis |
| ROS references | `ROS_STATIC_REFERENCES.csv` | 1,115 static ROS references |
| ROS architecture | `ROS_COMMUNICATION_ARCHITECTURE.md` | ROS graph, launch profile, transports |
| ROS IP identification | `ROS_IP_IDENTIFICATION.md` | IP derivation with evidence |
| ROS IP/topic findings | `ROS_IP_TOPICS_FINDINGS.json` | RIP-001..015 structured evidence |
| ROS topics inventory | `ROS_TOPICS_INVENTORY.md` | Per-node topic names (static) |
| System architecture | `ROBOTICS_SYSTEM_ARCHITECTURE.md` | HW/SW/board/firmware layout |
| Artifact inventory | `ARTIFACT_INVENTORY.csv` | 127 artifacts, hashes, coverage |
| Embedded inventory | `ARTIFACT_INVENTORY_EMBEDDED.csv` | 18,047 embedded entries |
| Unresolved gaps | `UNRESOLVED_GAPS.md` | Everything not established + why |
| Investigation state | `INVESTIGATION_STATE.md` | Phase tracking |
| Worker partials | `partials/*.{md,json}` | Raw per-domain outputs (5 workers) |
| Supporting evidence | `work/*.json` | REA/validation evidence artifacts |

## 3. Coverage & consistency (verified 2026-10-24, finalization iteration 2)

| Check | Result |
|---|---|
| Artifacts inventoried / covered | 127 / 127 (0 uncovered) |
| Findings | 226 — **0 duplicate IDs, 0 missing IDs, 0 missing evidence** |
| Finding ID prefixes | FW 31, RIA 50, PCL 68, ROS 53, SYS 24 |
| Endpoints (partials vs `NETWORK_ENDPOINTS.csv`) | 394 / 394 ✓ |
| ROS references (partials vs `ROS_STATIC_REFERENCES.csv`) | 1,115 / 1,115 ✓ |
| Findings (partials vs `FINDINGS_REGISTER.csv`) | 226 / 226 ✓ |
| JSON parse integrity (all partials) | 0 failures |
| Broken file references in docs | 0 (was 1 — `UNRESOLVED_GAPS.md`, now created) |

## 4. ROS IP & topic evidence (exact)

| ID | Item | Value | Class | Conf |
|---|---|---|---|---|
| RIP-001 | ROS computer IP | `192.168.64.20` | CONFIRMED_STATIC | HIGH |
| RIP-002 | ROS Master URI | `http://192.168.64.20:11311` | CONFIRMED_STATIC | HIGH |
| RIP-003 | Android tablet IP / gateway for ROS PC | `192.168.64.10` | CONFIRMED_STATIC | HIGH |
| RIP-004 | rosbridge WS data / control | `ws://…:9090` / `ws://…:9091` (no auth) | CONFIRMED_STATIC | HIGH |
| RIP-005 | SDK transports to ROS PC | CoAP 5683, HTTP 54322/34569, WS 22345/12386, TFTP 9527 | CONFIRMED_STATIC | HIGH |
| RIP-007 | OTA `ros_update` (root) | HTTP upgrade/action (port 9527 inferred; 9013 seen) | CONFIRMED_STATIC | MEDIUM |
| RIP-008 | C40 ROS node set | ~35 always-on + conditional nodes | CONFIRMED_STATIC | HIGH |
| RIP-011 | STM32/GD32 serial bridge topics | `/stm32_up`,`/stm32_down`,`/cmd_vel`,`/odom`,… | CONFIRMED_STATIC | HIGH |
| RIP-012 | Network LiDAR endpoints | `.103:2111` (C40 main), `.100:2112`, `.107`, … | CONFIRMED_STATIC | HIGH |
| RIP-015 | Live ROS verification | **not performed** | UNVERIFIED_RUNTIME | HIGH |

Evidence basis for `.20`/`.10`/Master URI: `slam-ros robot-settings` `etc/network/interfaces` (`br0` = `.20`), `etc/init/keenonrobot.conf` (`ROS_IP=192.168.64.20`), `usr/bin/keenonrobot_setup.sh` (`export ROS_MASTER_URI=http://192.168.64.20:11311`), `rosenv --remote`, `net_gw.conf` (default route via `.10`), corroborated by SDK constants `REMOTE_LINK_PROXY=192.168.64.20` / `LOCAL_ETHER_IP=192.168.64.10` in two independently-built APKs. Topic names are harvested from ELF strings — **present ≠ advertised at runtime**; pub/sub direction and topic↔type pairing are mostly INFERRED. Full detail: `ROS_IP_TOPICS_FINDINGS.json`, `ROS_IP_IDENTIFICATION.md`, `ROS_TOPICS_INVENTORY.md`.

## 5. Top findings (1 Critical + 24 High)

| Sev | ID | Dom | Title |
|---|---|---|---|
| Crit | SYS-001 | SEC | Exported receiver executes arbitrary shell commands from an unauthenticated broadcast |
| High | PCL-048 | SEC | Unauthenticated wildcard-CORS REST API on all interfaces (8089) with reboot/ROS control |
| High | PCL-049 | SEC | PIN gate: compiled-in backup admin PIN, native default PINs, PIN logged in clear |
| High | PCL-050 | SEC | MQTT TLS trust manager only checks cert subject contains "keenon"/"keenoon" |
| High | RIA-001 | SEC | Unauthenticated WebSocket control server bound to all interfaces (TCP 8888) |
| High | RIA-002 | SEC | Arbitrary file write with path traversal via "postWriteInFile" request |
| High | RIA-003 | SEC | rosbridge relay: unauthenticated LAN clients reach ROS (.20:9090/9091) |
| High | RIA-012 | SEC | Outdated embedded Chromium 73.0.3683.93 with default-exported components |
| High | ROS-030 | NET | rosbridge WebSocket servers (9090/9091) without authentication or TLS |
| High | ROS-031 | NET | `ros_update` OTA service on HTTP (default 9527) with upgrade/action |
| High | ROS-032 | NET | WsServer websocket control channel and frp reverse SSH tunnel |
| High | ROS-040 | SEC | Hard-coded sudo password inside `send_depth_node` binary |
| High | ROS-041 | SEC | Hard-coded password passed on command line to `frpc_delete`/`sudo -S` |
| High | ROS-042 | SEC | Default Wi-Fi AP credentials shipped in `hostapd.conf`, AP bridged to robot network |
| High | ROS-043 | SEC | Unauthenticated ROS master / rosbridge / services control (incl. config and reboot) |
| High | ROS-044 | SEC | OTA update flow lacks integrity/authenticity controls and runs as root |
| High | SYS-002 | SEC | Exported receiver copies arbitrary files/directories on request |
| High | SYS-004 | SEC | Unauthenticated exported AIDL services: arbitrary MQTT pub/sub + voice/TTS control |
| High | SYS-005 | NET | MQTT-delivered remote shell command execution |
| High | SYS-006 | SEC | adb over TCP enabled unconditionally at init, reachable through FRP tunnel |
| High | SYS-007 | SEC | Hard-coded SMB root credentials for the robot-side host |
| High | SYS-008 | SEC | Hard-coded third-party LLM API bearer keys in code |
| High | SYS-009 | SEC | Hard-coded Azure OpenAI / Speech keys for 8 regions in a bundled asset |
| High | SYS-011 | SEC | Process-wide trust-all TLS and hostname verifier |
| High | SYS-012 | SEC | MQTT TLS trust check is a substring match on certificate subject |

Full set with evidence/impact/remediation: `FINDINGS_REGISTER.csv`, `_sec_table.md`. Secret **values** are located and named by finding but are deliberately **not reproduced** anywhere in these deliverables.

## 6. Distribution

- **By severity:** Critical 1 · High 24 · Medium 52 · Low 27 · Info 122
- **By domain:** SEC 94 · ROB 31 · NAT 23 · NET 22 · WF 19 · SDK 14 · APP 12 · DB 11

## 7. Evidence classification legend

| Label | Meaning |
|---|---|
| `CONFIRMED_STATIC` | Directly present in shipped artifacts (config literal, launch XML, decompiled constant, string table). |
| `STRONGLY_SUPPORTED` | Multiple independent static indicators agree, but one link is inferential. |
| `INFERRED` | Reasoned from convention/adjacent evidence (e.g. upstream default port). |
| `UNVERIFIED_RUNTIME` | Statically determined; requires live read-only inspection to confirm. |
| `CONFIRMED_LIVE` | **None** — no live testing was performed. |

Note: some register rows carry compound `status` strings (e.g. "CONFIRMED (strings); port INFERRED; auth NOT_ANALYZED"). This preserves per-claim nuance; the leading token is the primary classification and maps to the legend above.

## 8. Unresolved gaps

See `UNRESOLVED_GAPS.md` for the complete list: static-only method boundary, tooling blockers (no disassembler/binwalk/tshark; BKS keystore and `resources.arsc` unparsed), 16 finding-level NOT_ANALYZED/UNKNOWN items, and all `UNVERIFIED_RUNTIME` ROS items. The single highest-value read-only next step (only if authorized access is granted): `rostopic list -v` / `rosnode list` / `rosservice list` against `192.168.64.20:11311`.
