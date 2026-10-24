# ROS Computer IP Identification - Keenon C40 (release V246)

**Scope:** static, read-only analysis of the files shipped in this release (`slam-ros-v2.10.9-c2-0-g58a75a7`, `peanut-clean-v3.7.8`, `robot-installation-assistant-v4.11.0`, `RemoteControl-v3.3.1`, `ServiceSystem-v2.0.11`, `krlog-v1.2.6`, firmware images). **No live network access to a physical robot was available or attempted** (see "Live verification" at the end). Every IP/port below is a literal found in a shipped artifact; "current" means "as shipped in this specific release", not "observed on a running unit". Per the task instruction, `192.168.64.20:11311` is **not** assumed to be correct - it is verified below from multiple independent artifacts in this same release and is reported as `CONFIRMED_STATIC`, not `CONFIRMED_LIVE`.

## 1. Headline identification

| Role | Address | Classification | Confidence |
|---|---|---|---|
| **ROS / SLAM industrial-PC (the "ROS computer")** | **192.168.64.20** | CONFIRMED_STATIC | HIGH |
| **ROS Master URI** | **http://192.168.64.20:11311** | CONFIRMED_STATIC | HIGH |
| **Android tablet (on the robot's internal LAN)** | **192.168.64.10** | CONFIRMED_STATIC | HIGH |
| Android \<-\> ROS computer transport | rosbridge WebSocket (9090 data, 9091 control) + direct CoAP/HTTP/TFTP/serial via the embedded Peanut SDK (**not** a third gateway box) | CONFIRMED_STATIC | HIGH |

This is the strongest class of static evidence available short of live access: **four independently built artifacts agree exactly**, with no indication any one copied the value from another (different toolchains, different repos, compiled at different times):

| Evidence source | Artifact | Exact literal | File / symbol |
|---|---|---|---|
| 1 | `slam-ros` deb `robot-settings`, `etc/network/interfaces` | `address 192.168.64.20` / `gateway 192.168.64.10` on bridge `br0` (`eth0`+`eth1`) | Static network config of the ROS computer itself |
| 2 | `slam-ros` deb `robot-settings`, `etc/init/keenonrobot.conf` (upstart job that execs `roslaunch keenon_robot.launch`) | `env ROS_IP=192.168.64.20` | The job that actually starts the ROS graph on this machine |
| 3 | `slam-ros` deb `robot-settings`, `usr/bin/keenonrobot_setup.sh` (installed technician config-menu tool) and its archived twin under `.../to_abandon/keenonrobot_setup.sh` | `export ROS_MASTER_URI=http://192.168.64.20:11311` and `export ROS_IP=192.168.64.20` | Explicit, literal Master URI |
| 4 | `slam-ros` deb `remote-tools`, `usr/bin/rosenv` (`-r`/`--remote` mode) | `export ROS_MASTER_URI='http://192.168.64.20:11311'` | Engineer's "connect to the robot remotely" helper |
| 5 | `slam-ros` deb `robotviz`, `usr/share/robotviz/assets/*.js` (web UI served by `web_backend` on the ROS computer) | `ws://192.168.64.20:9090`, `ws://192.168.64.20:9091` | Browser client hard-codes its own host's rosbridge address |
| 6 | **`robot-installation-assistant` APK**, decompiled `PeanutConstants`/link classes (`com.keenon.sdk.*`) | `REMOTE_LINK_PROXY="192.168.64.20"`, `LOCAL_ETHER_IP="192.168.64.10"`, `coap://192.168.64.20:5683`, `http://192.168.64.20:54322` | Independent Android codebase, independent build | 
| 7 | **`peanut-clean` APK**, decompiled `ConsoleCleanApiRemote`/CoAP command classes | CoAP requests to **192.168.64.20:5683** for `startClean`, `/charge/auto`, `/devices/reboot`, etc. (187 SDK-method -> CoAP-path pairs) | A second, independently built Android app, same addresses |
| 8 | `slam-ros` deb `robot-settings`, `usr/bin/ros_update` (OTA HTTP service strings) | literal `192.168.64.10` next to `9527` (TFTP) | OTA tool on the ROS computer refers back to the tablet's IP |

Evidence 1-5 come from the Linux/ROS side (slam-ros package); evidence 6-8 come from two independently-built Android applications. All agree: **.20 = the ROS computer, .10 = the Android tablet**. This cross-artifact agreement is why the identification is rated HIGH confidence despite being static-only.

Cross-reference to prior findings: ROS-002, ROS-003, ROS-030, ROS-031, ROS-033, ROS-034 (ros_packages.md/json); RIA-024, RIA-034 "SDK" section, RIA-043 (installation_assistant); PCL CoAP/command findings (peanut_clean_app.md, "IPC link" section).

## 2. Important nuance on the ROS Master URI

- The upstart job that actually launches `roscore`/`roslaunch` on the ROS computer (`etc/init/keenonrobot.conf`) sets **`ROS_IP=192.168.64.20`** as an environment variable but does **not** itself hard-code `ROS_MASTER_URI`. Under standard ROS 1 / `roslaunch` behaviour, when `roslaunch` starts a master because none is running, it binds the master and advertises it using `ROS_IP`/`ROS_HOSTNAME` on the default port 11311 - i.e. the resulting URI is `http://192.168.64.20:11311`, consistent with every other literal below.
- The *literal* string `http://192.168.64.20:11311` is found in two **auxiliary, technician-facing tools** (`keenonrobot_setup.sh`, `rosenv -r`), not in the daemon start script itself. One copy of `keenonrobot_setup.sh` lives in a path literally named `to_abandon/` inside the package tree; the file *actually installed* to `/usr/bin/keenonrobot_setup.sh` (i.e. the one that runs on the device) has identical content, so the "abandoned" label applies to the duplicate location in the source tree, not to the tool's execution path on the device.
- **Conclusion:** `CONFIRMED_STATIC`, `HIGH` confidence, for this release. The task's caution ("don't assume `.20:11311` is current") is respected: the address is re-derived here from the network config and the launch mechanism, not merely repeated from an old finding, and it is explicitly *not* claimed to be `CONFIRMED_LIVE`.

## 3. Full address inventory

| Address : port | Role | Source file(s) | Status | Confidence |
|---|---|---|---|---|
| 192.168.64.20 | ROS computer, static LAN address on bridge `br0` | `robot-settings/etc/network/interfaces` | CONFIRMED_STATIC | HIGH |
| 192.168.64.20:11311 | ROS Master URI | `keenonrobot_setup.sh`, `rosenv`, upstart `ROS_IP` (derived) | CONFIRMED_STATIC | HIGH |
| 192.168.64.20:9090 | rosbridge WebSocket, data channel (`rosbridge_websocket.launch`, no `address`/port override -> binds all interfaces) | `robot_common.launch`; robotviz `Home.js`/`index.js`; installation-assistant `RosSocketClientManager.ADDRESS` | CONFIRMED_STATIC | HIGH |
| 192.168.64.20:9091 | rosbridge WebSocket, `control` namespace, `authenticate=false`, `ssl=false` | `robot_common.launch` include `rosbridge_websocket_control.launch`; same JS/APK clients as above | CONFIRMED_STATIC | HIGH |
| 192.168.64.20:5683 | CoAP server on the ROS computer (`coap_bridge_server` node + Peanut SDK `PeanutCoapClient`) | `ros_update`/`coap_bridge_server` strings (port itself not printed, inferred from libcoap default `5683`, LOW on the port only); **confirmed** independently from both APKs' `coap://192.168.64.20:5683` constant | CONFIRMED_STATIC (host); INFERRED (port, from ROS side alone) -> CONFIRMED_STATIC once APK evidence is included | HIGH |
| 192.168.64.20:54322 (upgrade 34569) | HTTP command channel (Peanut SDK `PeanutHttpClient`) | installation-assistant / peanut-clean SDK constants `HTTP_PORT=54322`, `HTTP_PORT_UPGRADE=34569` | CONFIRMED_STATIC | HIGH |
| ws://192.168.64.20:22345 (upgrade 12386) | SDK push WebSocket | same SDK constants (`WS_PORT`, `WS_PORT_UPGRADE`) | CONFIRMED_STATIC | MEDIUM (not independently seen server-side) |
| 192.168.64.20:9527 (TFTP) | Firmware/map transfer (TFTP) | SDK constant `TFTP_PORT=9527`; `ros_update` string `9527` adjacent to `upgrade/action` | CONFIRMED_STATIC | HIGH |
| 192.168.64.20:5555 | adb-over-TCP style endpoint referenced as `baseIpC` | `peanut-clean` **dev-only** asset `assets/json/dev.config.json` (`"baseIpC": "192.168.64.20:5555"`) | CONFIRMED_STATIC **for the dev/test build config only**; production `domain.json` does not carry this key (NOT_ANALYZED whether it is reachable on shipped units) | MEDIUM |
| 192.168.64.20:9013 | Unidentified additional port literal (single occurrence) | embedded string, source file not pinned down to one artifact in this pass | UNKNOWN (purpose) | LOW |
| 192.168.64.10 | Android tablet; default gateway for the ROS computer's `br0` | `robot-settings/etc/init/net_gw.conf` (`ip route add default via 192.168.64.10 dev br0`); SDK constant `LOCAL_ETHER_IP="192.168.64.10"` | CONFIRMED_STATIC | HIGH |
| 192.168.64.10 (ros_update OTA strings) | Same tablet address, referenced from the ROS-side OTA service | `usr/bin/ros_update` strings | CONFIRMED_STATIC | MEDIUM |
| 192.168.64.0/24, DHCP 192.168.64.200-209, broadcast .255, router .20 | Robot internal LAN / DHCP scope | `robot_settings/system/wifi/dhcpd.conf` | CONFIRMED_STATIC | HIGH |
| Wi-Fi AP `Keenon_robot_FFFFFF` bridged to `br0` | Robot's own hotspot, same subnet as ROS computer and tablet | `etc/hostapd/hostapd.conf` (`bridge=br0`); SSID's `FFFFFF` is a placeholder rewritten per-unit from the radio MAC by `set_ap_mode` | CONFIRMED_STATIC | HIGH |
| 192.168.64.100 | Network lidar ("keli" SDKELI LS1207DE), UDP/TCP 2112 | `sdkeli_ls_udp` package params; `lidar_manager.sh` | CONFIRMED_STATIC (sensor endpoint, not a computer) | HIGH |
| 192.168.64.103 | Network lidar ("free" / main C40 lidar), TCP 2111 | `sdkfree_params.yaml`; `lidar_manager.sh Free="192.168.64.103"` | CONFIRMED_STATIC (sensor endpoint) | HIGH |
| 192.168.64.107 | Alternate network lidar ("free_c260") | `lidar_manager.sh` | CONFIRMED_STATIC (sensor endpoint) | HIGH |
| 192.168.64.101 / .104 | Secondary-lidar example addresses in `lidar_manager.sh` | same script | CONFIRMED_STATIC (template/example, not necessarily populated on a single-lidar C40) | MEDIUM |
| 192.168.3.11 | **Commented-out** manual example for the Wi-Fi AP interface | `set_ap_mode` (`#ifconfig wlanAP 192.168.3.11 ...`) | HISTORICAL / inactive example, not used by the active code path | LOW |
| 192.168.64.199:9090 | Default `host_url` in the **calibration tool's own config template** (`calibration/config.json`) | `calibration/config/config.json` | Template default, inconsistent with the live `.20` address seen everywhere else -> treated as a **tool default placeholder**, not the robot's address | LOW |
| ws://172.16.8.13:9092 | Hard-coded **developer workstation** debug endpoint, left in the minified `robotviz`/mapping-tool JS bundle | `robotviz/assets/index.*.js`; also present in installation-assistant's bundled copy of the same SPA (`assets/ros/dist/assets/index-32d09e94.js`) | Developer artifact shipped in production bundle; not part of the robot's own network | LOW (as architecture fact) / **security note**: dev endpoint left in shipped code |
| http://172.16.8.102:8089/ | `baseIp` in peanut-clean's **dev-only** `dev.config.json` | `assets/json/dev.config.json` | Development build configuration only | LOW |
| ws://192.168.2.5:8083 | `websocket` in peanut-clean's `dev.config.json`/`test.config.json` | same files | Development/test build configuration only | LOW |
| http://192.168.2.218:8090 | Hard-coded image-server host inside a shipped fault-diagnostic HTML page (`assets/fault/dustBox.html`) | `dustBox.html` | Looks like an un-parameterised developer workstation address accidentally left in a **production** asset; cosmetic impact (broken images) unless that host is reachable | LOW (architecture) / minor quality defect |
| upload-test.keenonai.com | Test upload endpoint in `dev.config.json` | same file | Development/test only | LOW |

## 4. What this means for "Android tablet -> ROS computer"

The Android tablet does **not** reach the ROS computer through a separate gateway box. Evidence from both the ROS-side package and the two independently-analysed Android apps agrees on a single flat LAN:

```
Android tablet (192.168.64.10, "LOCAL_ETHER_IP")
        |  bridge br0 (eth0<->eth1), 192.168.64.0/24
        v
ROS / SLAM industrial PC (192.168.64.20, "REMOTE_LINK_PROXY")
```

The tablet reaches the ROS computer by **three independent, parallel paths**, all terminating on the same host:
1. **rosbridge** WebSocket on 9090 (data) / 9091 (control, unauthenticated) - used by the on-robot `robotviz`/mapping SPA (also embedded inside the installation-assistant APK) for live ROS topic/service access.
2. **Peanut SDK** CoAP (5683) / HTTP (54322, upgrade 34569) / WS push (22345) / TFTP (9527) - used by `peanut-clean` and the installation assistant for robot commands (clean, charge, navigate, reboot, map transfer).
3. **Direct serial** from the tablet to `/dev/ttyS1-4` and `/dev/ttyUSB0/2/3` plus a dedicated `/dev/ttyUSBDoorCtl`, per the SDK's `BoardType`/`Device` constants, for boards that are reached without going through the ROS computer at all (e.g. a serial link described in the SDK as going to `STM32`, `DOOR`, `SCHEDULE`, `PERIPHERAL_CONTROL_BOARD`). This path bypasses the ROS computer entirely for some hardware; see ROS_COMMUNICATION_ARCHITECTURE.md.

No evidence in any artifact in this release shows a *separate* gateway/proxy computer between the tablet and the ROS computer; `REMOTE_LINK_PROXY` in the SDK literally *is* the ROS computer's own address.

## 5. Live ROS identification - not performed

No read-only ROS inspection tool, device connection, or network path to a physical C40 unit was available in this analysis environment. No active network scanning, probing, connection attempts, or robot control were performed, per the task's constraints. Every classification above is `CONFIRMED_STATIC` or weaker; **none is `CONFIRMED_LIVE`**. If live access becomes available, the highest-value checks are (read-only): `rostopic list`/`rosnode list`/`rosservice list` against `192.168.64.20:11311`, and inspecting whether 9091 (`control` namespace) actually requires no authentication in practice.
