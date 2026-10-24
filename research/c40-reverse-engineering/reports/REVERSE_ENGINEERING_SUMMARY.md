# Reverse-Engineering Summary - Keenon C40 (V246)

Decision-oriented summary. Full detail in `MASTER_REVERSE_ENGINEERING_REPORT.md` and the per-topic docs. Static-only; nothing live-verified; secrets redacted.

## What this release is
The complete software stack of a Keenon "Peanut" C40 commercial floor-scrubbing robot: 5 Android APKs (tablet), a 106-package ROS Indigo SLAM/nav stack (industrial PC), and firmware for 4 MCUs (2x GD32F3, 2x ESP32). 127 files / 378 MB, all inventoried and hashed; 7/7 single-file packages match the vendor MD5 manifest.

## The answer to the primary objective (ROS IP + topics)
- **ROS computer IP: `192.168.64.20`** - CONFIRMED_STATIC (network config + launch + both Android apps; 8 independent sources).
- **ROS Master URI: `http://192.168.64.20:11311`** - CONFIRMED_STATIC.
- **Android tablet: `192.168.64.10`**, also the ROS PC's default gateway.
- **Architecture:** one flat bridged LAN `192.168.64.0/24` (eth+eth+Wi-Fi AP). Tablet talks to the ROS PC **directly** - no separate gateway - via rosbridge WS 9090/9091 **and** the Peanut SDK (CoAP 5683 / HTTP 54322 / TFTP 9527 / serial). None authenticated/TLS inside the robot net.
- **Topics/nodes:** ~35 C40 nodes and hundreds of topic names catalogued by function (lidar, ToF/camera, IMU, odom, TF, localization, mapping, nav, cleaning, STM32-serial, status) in `ROS_TOPICS_INVENTORY.md`. All are **static references**; pub/sub direction mostly unknown; **none CONFIRMED_LIVE** (no runtime access).

## Confirmed robot architecture
Tablet (RK3288) -> flat LAN -> ROS PC (Rockchip, ROS Indigo) -> ROS nodes -> `chassis` serial bridge -> GD32F3 chassis/periph MCUs (brushes, suction, water pump, dust box, IMU, charge) + ESP32-S3 dock + ESP32 schedule gateway. Sensors: "free" network LiDAR (.103), GS2 back lidar, Sunny ToF, 4x XD edge lasers, FDL cliff, mono VSLAM. (C40 does **not** use the astra/berxel RGB-D cameras.)

## Most important findings (226 total; 109 security-relevant)
Severity: **1 Critical, 24 High, 53 Medium, 26 Low, 5 Info.** Top themes:
1. **Unauthenticated control plane** on a LAN shared with the robot's own Wi-Fi AP (rosbridge 9091 no-auth; app servers :8888/:8080/:8089; SDK CoAP/HTTP/TFTP).
2. **Critical (SYS-001):** system-signed `ServiceSystem` exports an unauthenticated broadcast receiver that runs **arbitrary shell commands**; related receivers copy files / install packages; AIDL services have no permission check.
3. **Hard-coded secrets everywhere** (redacted): a sudo password == the Wi-Fi PSK; SMB root creds; LLM/Azure/iFlytek keys + an RSA private key; fleet-wide mTLS keystores; a backup admin PIN.
4. **MD5-only, unsigned updates** at every layer (APK OTA, ROS debs, GD32 MCU, ESP images); `ros_update` OTA runs as root.
5. **TLS that doesn't validate** (substring "keenon" checks, trust-all managers, cleartext allowed).
6. **EOL stack + debug/test residue** (ROS Indigo/Ubuntu 14.04, Chromium M73, exported `androidx.test` activities, full DWARF/dev paths shipped).

## What is NOT established
- No live/runtime verification of anything (no robot reachable; none attempted).
- Native ROS/firmware/`.so` not disassembled (no Ghidra/IDA/Hopper installed).
- Keystore/secret values not extracted; BKS/`resources.arsc` not parsed; 3 zip packages' MD5s unverifiable.
- Which launch profile runs on a real unit; live topic graph & pub/sub direction; runtime auth state. See `UNRESOLVED_GAPS.md`.

## Static vs live (one line)
**Everything in this investigation is static or inferred evidence. Nothing is confirmed from a running robot.** The two next steps that would most raise confidence: authorized read-only live ROS inspection against `192.168.64.20:11311`, and disassembly with a configured REA native provider.
