# Master Reverse-Engineering Report - Keenon C40 cleaning robot

**Target:** `C40 S-LS-M014C00-RW-F00-V246` software release (Keenon "Peanut" C40 commercial floor-scrubbing robot).
**Date:** 2026-10-24. **Method:** static, evidence-driven, local-only. **No live robot, cloud, or network testing.**
**Toolkit:** REA (`rea-agents` 6.7.0) with headless JADX 0.7.1 for Android decompilation; custom Python stdlib parsers (ELF, AR/deb, AXML, DEX, APK-signature) for breadth; `openssl` for certificates. Ghidra/IDA/Hopper not installed (no native decompilation).
**Evidence classes:** CONFIRMED_STATIC / CONFIRMED_LIVE / STRONGLY_SUPPORTED / INFERRED / HISTORICAL / UNKNOWN / NOT_ANALYZED / BLOCKED. **Nothing is CONFIRMED_LIVE** (no runtime access). Secrets redacted throughout (type + location only). No original files modified; all output under `REVERSE_ENGINEERING_RESULTS/`.

---

## 1. Executive summary

The V246 release is the **complete multi-board software stack** of the Keenon C40: 5 Android APKs (tablet HMI + system services), a 106-package ROS Indigo SLAM/navigation stack, and firmware for four microcontrollers (two GD32F3 + two ESP32). From it we reconstructed the **full system architecture, the robot's internal network, the ROS Master and topic surface, the Android<->robot command path (Peanut SDK), the database schemas, and a 226-item finding register** including 109 security-relevant issues (1 Critical, 24 High).

Headline technical facts (all CONFIRMED_STATIC):
- **ROS computer = `192.168.64.20`, ROS Master = `http://192.168.64.20:11311`**; Android tablet = `192.168.64.10` (and the ROS PC's Internet gateway). One flat bridged LAN `192.168.64.0/24` also carries the robot's own Wi-Fi AP.
- The tablet drives the robot **directly** (no gateway box) via (a) rosbridge WS 9090/9091 and (b) the Peanut SDK over CoAP 5683 / HTTP 54322 / TFTP 9527 / serial - **none authenticated or TLS-protected** inside the robot network.
- Cleaning hardware (brushes, suction, water pump, dust box) is driven through the ROS `chassis` node over a serial link to a **GD32F3** MCU; the dock is an **ESP32-S3** (not an STM32, despite the script names).

Headline security story: a flat, unauthenticated control plane shared with the robot's own Wi-Fi hotspot; system-privileged Android apps exposing shell/file/install primitives over unauthenticated IPC (one Critical); hard-coded secrets throughout (incl. a sudo password reused as the Wi-Fi PSK); MD5-only, unsigned update/OTA at every layer; and TLS that does not actually validate. Details in `SECURITY_AUDIT.md`.

## 2. Investigation scope
All artifacts of release V246 (127 top-level files, 378 MB; ~18,000 embedded files): 5 APKs, 106 ROS `.deb` packages + install manifest, 4 MCU firmware images + flashing tools + upgrade scripts, and `upgradeConfig.json`. In scope: manifest/component/permission analysis, DEX decompilation, SDK reconstruction, native/firmware static analysis, network/protocol mapping, database schema recovery, ROS architecture, workflows, and a defensive security review. Out of scope (by rule): any live/dynamic testing, exploitation, robot control, flashing, scanning, or secret extraction.

## 3. Target inventory overview
- 127 files hashed (SHA-256 + MD5) -> `ARTIFACT_INVENTORY.csv` (with per-file analysis status; 100% covered by a worker record). Embedded files -> `ARTIFACT_INVENTORY_EMBEDDED.csv` (18,047 rows).
- **Integrity check vs `upgradeConfig.json`:** all 7 single-file packages' MD5s **match**; the 3 zip packages are present only extracted, so their MD5s could not be re-verified.
- Signing: 4/5 APKs use a generic `CN=Android` cert; installation-assistant uses `CN=keenon` (see §6).

## 4. Methodology & tooling
REA-first for Android (JADX via `rea-agents` CLI; verified by re-decompiling selected methods at the orchestrator level, `work/rea_val_*.json`). Because REA's native providers (Ghidra/IDA/Hopper) were not installed, ROS/firmware/native `.so` were analysed by header/section/symbol/string/DWARF evidence and small hand-decoded fragments. Five domain workers ran in parallel (ROS, firmware, peanut-clean, installation-assistant+SDK, system-apps); results consolidated here. The run survived two network outages and a rate-limit, resuming workers rather than restarting (see `INVESTIGATION_STATE.md`).

## 5. System architecture
Three compute tiers on one bridged LAN (full diagram: `ROBOTICS_SYSTEM_ARCHITECTURE.md`):
1. **Android tablet (RK3288, .10):** HMI (`peanut-clean`), commissioning + mapping (`installation-assistant`, a Chromium-73 fork), and 3 system-signed services (`ServiceSystem` voice/MQTT/BI, `RemoteControl` Oray/FRP, `krlog`). Also the robot's Internet gateway.
2. **ROS/SLAM PC (Rockchip aarch64 kernel, armhf ROS Indigo userland, .20):** ~35-node ROS graph (localization, gmapping+cartographer, move_base+TEB, cover/clean planners, task manager, database, rosbridge, CoAP bridge, web backend) + the `chassis` serial bridge.
3. **MCUs:** GD32F3 chassis (bare-metal) + GD32F3 peripheral (RT-Thread, CAN) + ESP32-S3 dock + ESP32 schedule gateway.

## 6. Android application architecture
See `ANDROID_APPLICATION_ANALYSIS.md`. Five apps; three are `sharedUserId=android.uid.system`. Wide default-exported surface (targetSdk 22-28). Signing (CONFIRMED_STATIC via openssl): 4/5 share generic `CN=Android` cert (SHA-256 `2D:37:0C:21:...:35:22`); installation-assistant uses `CN=keenon` (SHA-256 `1F:B4:F7:10:...:19:C3`). Whether the generic cert is the public AOSP test key is UNKNOWN.

## 7. SDK architecture
See `SDK_API_CATALOG.md` (+ 392 KB `partials/installation_assistant_sdk.md`). `peanut-sdk 2.2.6rc3-beta24`: a `PeanutSDK` facade over components (clean, charge, navigation, map, device, battery, door, ...) that issue **223 CoAP + 88 serial + 2 HTTP** annotated commands to `192.168.64.20`, selecting CoAP/HTTP/serial at runtime. It also binds AIDL services in the system-signed `ServiceSystem`, forming the privilege bridge for shell/install primitives.

## 8. Native binary & firmware findings
See `NATIVE_BINARY_ANALYSIS.md` (+ `partials/firmware_and_scripts.md`, FW-001..031). GD32F3 chassis/periph images are plaintext Cortex-M apps at `0x08004000` with a 148-byte Keenon IAP tail (MD5 matches); dock is ESP32-S3/ESP-IDF 5.1.2, schedule is ESP32/ESP-IDF 4.4.2, both unsigned with a shared hard-coded ESP-NOW key. Serial/IAP protocol recovered (framing, CRC-16 0xA001, 128-byte packets). 283 ROS ELFs + 178 Android `.so` inventoried by symbol/string only.

## 9. Network communication architecture
See `NETWORK_PROTOCOL_ANALYSIS.md`, `ROS_COMMUNICATION_ARCHITECTURE.md`, `NETWORK_ENDPOINTS.csv` (394). Flat `192.168.64.0/24`; tablet<->ROS via rosbridge + SDK CoAP/HTTP/TFTP/WS (no auth/TLS); cloud via MQTT (Paho; mTLS-or-cleartext) + REST to `*.keenonai.com` / `cloud.robotkeenon.com`; remote access via Oray + FRP tunnels (exposing adb). 

## 10. Database architecture
See `DATABASE_SCHEMA_ANALYSIS.md`. ROS `peanut.db` (SQLite/WAL at `/etc/ros/runtime/database/`) is the on-robot map/pose/zone/label/elevator/gate store, served via `keenon_database_msgs/*Config` services; the tablet mirrors subsets into Room DBs (`kc01.db` 13 tables; `Mapping.db` 7 tables) on external storage (unencrypted). The only openable shipped DB was an empty seed.

## 11. Robotics architecture
See `ROBOTICS_SYSTEM_ARCHITECTURE.md`. C40 profile (from `keenon_robot_C40.launch`): main "free" network LiDAR (.103:2111) + GS2 back lidar, Sunny ToF, 4x XD edge lasers, FDL cliff, mono VSLAM, Cartographer + peanut_localization, move_base (kplanner+TEB), cover_planner cleaning. **C40 does NOT use the astra/berxel RGB-D cameras** (present but disabled). Which profile runs on a physical unit is UNKNOWN (package default is W3).

## 12. ROS Master, topics & IP (primary objective of the latest tasking)
Dedicated deliverables: **`ROS_IP_IDENTIFICATION.md`**, **`ROS_TOPICS_INVENTORY.md`**, **`ROS_COMMUNICATION_ARCHITECTURE.md`**, **`ROS_IP_TOPICS_FINDINGS.json`** (RIP-001..015), plus `ROS_STATIC_REFERENCES.csv` (1,115). ROS PC `192.168.64.20`, Master `:11311`, corroborated by 8 independent artifacts. Hundreds of topic names catalogued by functional category (lidar, camera/ToF, IMU, odom, TF, localization, mapping, nav, cleaning, STM32-serial, status) - all CONFIRMED_STATIC references, pub/sub direction mostly UNKNOWN, **none CONFIRMED_LIVE**.

## 13. Application workflows
See `APPLICATION_WORKFLOWS.md` (PCL-034..047, RIA-027..031): boot/PIN, start-clean (`-> CoAP /clean/dstV2`), scheduled clean, auto-recharge (`/charge/auto`), self-clean/dock, map sync, cloud MQTT commands, OTA, registration, teach path, re-localisation.

## 14-16. Confirmed / inferred / unknown capabilities
- **Confirmed (static):** board & firmware identities; network layout & ROS Master; C40 sensor/actuator set; message/service/action catalog; SDK command surface; DB schemas; serial/IAP protocol; signing; 226 findings with evidence.
- **Inferred:** topic pub/sub direction & topic<->type pairing; which physical lidar vendor; UI-click-to-actuator chains; some firmware logic.
- **Unknown/unverified:** live ROS graph; which launch profile runs on a unit; runtime reachability/auth of services; SoC models; keystore/secret contents; GD32 bootloader/eFuse state. See `UNRESOLVED_GAPS.md`.

## 17. Limitations
Static-only (nothing live); no native decompiler (ROS/firmware/`.so` not disassembled); BKS/`resources.arsc` not parsed; 3 zip packages' MD5s unverifiable; no secret values read. Full list: `UNRESOLVED_GAPS.md`.

## 18. Major discoveries
1. Exact ROS computer IP + Master URI + topic surface, cross-confirmed across ROS and two Android codebases.
2. The dock/base-station is an **ESP32-S3**, not an STM32 - the vendor's own `update_Stm32.sh` naming is misleading.
3. A single flat LAN unifies tablet, ROS PC, Wi-Fi AP and LiDARs, with unauthenticated control on it.
4. The Peanut SDK is the authoritative Android->robot command channel (223 CoAP commands), not rosbridge alone.
5. Systemic hard-coded secrets, incl. one string reused as both a sudo password and the Wi-Fi PSK.
6. Update integrity is MD5-only and unsigned at every layer (APK OTA, ROS debs, MCU, ESP).

## 19. Recommendations (defensive)
Authenticate/segment the robot control plane (rosbridge auth+TLS, per-role topic allow-list, bind SDK transports to loopback/internal, take the Wi-Fi AP off the control bridge); remove hard-coded secrets and rotate; sign + verify all updates (APK, deb, MCU, ESP secure-boot); implement real TLS validation; lock down exported privileged IPC (permissions/signature-level); strip test/dev artifacts and debug symbols from release; upgrade the EOL stack (ROS Indigo/Ubuntu 14.04, Chromium M73). Prioritise by `SECURITY_AUDIT.md` severity (Critical SYS-001 first).

## 20. Final completion assessment
The investigation reached its **evidence-based completion point** for a static engagement: every accessible artifact is inventoried and assigned an analysis record; all five domains are analysed and cross-referenced; the primary objective (ROS IP + Master + topics + comms architecture) is answered with cross-confirmed static evidence; 226 findings carry evidence references; and all remaining gaps are documented with the specific missing access/tooling. **This is not 100% reverse engineering:** no runtime/live ROS verification was possible, and native/firmware binaries were not disassembled (no Ghidra/IDA). Those are the two gaps that bound completeness; both are closable with authorized live access and a configured native decompiler.

## Deliverables index (all under `REVERSE_ENGINEERING_RESULTS/`)
- `MASTER_REVERSE_ENGINEERING_REPORT.md` (this file), `REVERSE_ENGINEERING_SUMMARY.md`
- `ROS_IP_IDENTIFICATION.md`, `ROS_TOPICS_INVENTORY.md`, `ROS_COMMUNICATION_ARCHITECTURE.md`, `ROS_IP_TOPICS_FINDINGS.json`
- `ANDROID_APPLICATION_ANALYSIS.md`, `SDK_API_CATALOG.md`, `NATIVE_BINARY_ANALYSIS.md`, `NETWORK_PROTOCOL_ANALYSIS.md`, `DATABASE_SCHEMA_ANALYSIS.md`, `ROBOTICS_SYSTEM_ARCHITECTURE.md`, `APPLICATION_WORKFLOWS.md`, `SECURITY_AUDIT.md`, `UNRESOLVED_GAPS.md`, `INVESTIGATION_STATE.md`
- `ARTIFACT_INVENTORY.csv`, `ARTIFACT_INVENTORY_EMBEDDED.csv`, `FINDINGS_REGISTER.csv` (226), `NETWORK_ENDPOINTS.csv` (394), `ROS_STATIC_REFERENCES.csv` (1,115)
- `partials/` - the full per-domain reports (ROS, firmware, peanut-clean, installation-assistant + SDK, system-apps) with their own findings/artifacts/endpoints/ros_refs JSON, and `orchestrator_validation.json`
- `work/` - raw REA evidence JSON and signature analysis
