# ROS Communication Architecture - Keenon C40 (release V246)

All relationships below are `CONFIRMED_STATIC` (derived from shipped config, launch XML, decompiled APK code and firmware strings) or `INFERRED`, as labelled. **None is `CONFIRMED_LIVE`** - no running robot was observed. Secret values (Wi-Fi PSK, SSH/SMB passwords, keystore passwords, API keys) are redacted; their locations are documented in SECURITY_AUDIT / FINDINGS registers.

## 1. Network topology (CONFIRMED_STATIC)

Single flat Layer-2 segment `192.168.64.0/24`, built by the ROS computer bridging its two Ethernet ports and the Wi-Fi AP together (`etc/network/interfaces`: `br0` = `eth0`+`eth1`; `hostapd.conf`: `bridge=br0`).

```mermaid
graph TB
  subgraph LAN["Robot internal LAN 192.168.64.0/24 (bridge br0 on ROS PC)"]
    TAB["Android tablet<br/>192.168.64.10<br/>(SDK LOCAL_ETHER_IP; default gateway for br0)"]
    ROS["ROS / SLAM industrial PC<br/>192.168.64.20<br/>(SDK REMOTE_LINK_PROXY; ROS_IP; br0 address)"]
    AP["Wi-Fi AP 'Keenon_robot_XXXXXX'<br/>wlanAP, WPA-PSK, bridged into br0<br/>DHCP .200-.209"]
    L1["LiDAR .103:2111 (free, main C40)"]
    L2["LiDAR .100:2112 (keli, alt)"]
    L3["LiDAR .107 (c260, alt)"]
  end
  ROS --- TAB
  ROS --- AP
  ROS --- L1
  ROS --- L2
  ROS --- L3
  EXT["External Wi-Fi / 4G / frp tunnel<br/>(remote access, egress)"] -. "net_gw.conf forces<br/>default route via .10" .-> TAB
```

Key facts:
- **ROS computer = `192.168.64.20`** (`br0` static address). ROS Master = `http://192.168.64.20:11311` (CONFIRMED_STATIC; see ROS_IP_IDENTIFICATION.md for the 8 corroborating sources).
- **Android tablet = `192.168.64.10`**, and it is the **default gateway** for the ROS PC (`net_gw.conf`: `ip route add default via 192.168.64.10 dev br0`). So outbound Internet/cloud traffic from the ROS computer egresses **through the Android tablet** (which holds the Wi-Fi/4G uplink). This is an unusual but clearly-evidenced design: the tablet is both an HMI and the robot's Internet gateway.
- The Wi-Fi AP, both Ethernet ports, the tablet, the ROS PC and the network LiDARs are all one broadcast domain. Anything that associates to the hotspot (PSK redacted; cross-ref ROS-042) is on the same L2 as the unauthenticated rosbridge control port.

## 2. Android tablet -> ROS computer (CONFIRMED_STATIC)

The tablet reaches the ROS computer **directly** (no separate gateway box); `REMOTE_LINK_PROXY` in the Peanut SDK literally equals the ROS PC address. Three parallel transport families terminate on `192.168.64.20`:

```mermaid
graph LR
  subgraph T["Android tablet 192.168.64.10"]
    PC["peanut-clean app<br/>(com.keenon.peanut.clean)"]
    IA["installation-assistant<br/>(peanutservice / Chromium fork)"]
    SDK["Peanut SDK 2.2.6rc3<br/>(coapapi: 223 CoAP + 88 serial + 2 HTTP cmd classes)"]
    SPA["Mapping-tool SPA + robotviz<br/>(Vue/OpenLayers)"]
  end
  subgraph R["ROS PC 192.168.64.20"]
    RB9090["rosbridge_websocket :9090 (data)"]
    RB9091["rosbridge control :9091<br/>ns=control, authenticate=false, ssl=false"]
    COAP["coap_bridge_server :5683"]
    HTTP["HTTP cmd :54322 (upgrade :34569)"]
    WS["SDK push ws :22345 (upgrade :12386)"]
    TFTP["TFTP :9527 (maps/firmware)"]
    OTA["ros_update OTA HTTP (root) ~:9527/:9013"]
    MASTER["ROS Master :11311"]
    NODES["ROS node graph (section 3)"]
  end
  PC --> SDK
  IA --> SDK
  IA --> SPA
  SDK -->|CoAP coap://.20:5683| COAP
  SDK -->|HTTP http://.20:54322| HTTP
  SDK -->|ws push :22345| WS
  SDK -->|TFTP :9527| TFTP
  SPA -->|ws://.20:9090 / :9091| RB9090
  SPA --> RB9091
  RB9090 --> MASTER
  RB9091 --> MASTER
  COAP --> NODES
  MASTER --> NODES
  SDK -.->|serial /dev/ttyS1-4, ttyUSBDoorCtl 921600/115200| MCUDIRECT["MCU boards reachable<br/>serially from tablet (bypasses ROS PC)"]
```

- **Path A - rosbridge (CONFIRMED_STATIC):** the on-robot SPA (robotviz / "Keenon Mapping Tool", also embedded in the installation-assistant APK) opens `ws://192.168.64.20:9090` (data) and `:9091` (control). The installation assistant additionally runs its **own** WebSocket relay on the tablet at `:8888` that forwards client messages to `192.168.64.20:9090/9091` (RIA-001/003). Control port 9091 is launched with `authenticate=false, ssl=false`.
- **Path B - Peanut SDK command transports (CONFIRMED_STATIC):** both APKs issue robot commands over CoAP `coap://192.168.64.20:5683` (e.g. `peanut-clean` `startClean -> /clean/dstV2`, `/charge/auto`, `/devices/reboot`), with HTTP `:54322`, push-WS `:22345`, and TFTP `:9527` as alternate channels auto-selected by `isUseHttpProtocol`/link-type logic. No auth/TLS on these transports (RIA-024).
- **Path C - direct serial (CONFIRMED_STATIC, INFERRED reach):** the SDK also drives `/dev/ttyS1-4` + `/dev/ttyUSBDoorCtl` directly from the tablet for some boards (`STM32`, `DOOR`, `SCHEDULE`, `PERIPHERAL_CONTROL_BOARD`). Whether a given board is reached via the tablet's serial port or via the ROS computer's `chassis` node depends on wiring not fully determinable statically (INFERRED).

## 3. ROS computer -> nodes -> hardware (CONFIRMED_STATIC launch graph)

```mermaid
graph TB
  MASTER["roscore / ROS Master :11311<br/>(keenonrobot.conf upstart, ROS_IP=.20)"]
  subgraph SENSE["Sensing nodes"]
    LID["free_lidar_driver + lidar_filter_1<br/>-> /scan"]
    BACK["ydlidar/GS2 back lidar"]
    TOF["sy_tof_dev_node -> /sensor/depthData"]
    FDL["fdl_ros_node (cliff) -> /fdl/obs_low, /NoFallingRiskMap"]
    XD["micro_laser_node x3 (XD edge)"]
    VS["keenon_vslam_node (mono) -> /vslam_robot_pose"]
  end
  subgraph CORE["Localization / mapping / nav"]
    LOC["peanut_localization -> /localization/robot_pose"]
    GM["gmapping -> /gmapping_map"]
    CART["cartographer_1"]
    LF["location_fusion"]
    MB["move_base (kplanner + TEB) -> /cmd_vel"]
    CP["cover_planner / complement_sweep"]
  end
  subgraph TASK["Task / UI / bridges"]
    TD["task_dispatcher"]
    TM["task_manager -> clean state"]
    INT["interactive (UI bridge)"]
    WB["web_backend -> /to_web"]
    DB["database_node (/database/config_*)"]
    COAPB["coap_bridge_server :5683"]
    RBR["rosbridge :9090/:9091"]
    LORA["lora_driver / elevator_task / gate"]
  end
  subgraph HW["Hardware via serial"]
    CH["chassis node = STM32/GD32 serial bridge"]
    MCU["GD32F3 chassis MCU (0x08004000)<br/>brushes, pump, suction, dust box, IMU, sonar, PSD, charge"]
    PER["GD32F3 periph board (RT-Thread, CAN)"]
    ESP["ESP32-S3 dock / ESP32 schedule gateway"]
  end
  MASTER --- SENSE
  MASTER --- CORE
  MASTER --- TASK
  LID --> LOC
  TOF --> FDL
  LOC --> MB
  GM --> LOC
  CART --> LOC
  MB --> CH
  CP --> TM
  TM --> CH
  TD --> TM
  INT --> TD
  CH -->|AA AA..55 55, CRC16 0xA001, 921600| MCU
  CH --> PER
  CH -. serial/ESP-NOW .-> ESP
  COAPB --> TASK
  RBR --> MASTER
```

- `chassis` is the ROS<->MCU serial bridge (ROS-006/007). It exchanges `/stm32_up`/`/stm32_down` (+schedule variants) with the GD32F3 chassis MCU and peripheral board; cross-ref firmware FW-001/002/006/016/017. Cleaning actuators (brushes `/motor_roll`,`/disc_brush`,`/roll_brush`; suction `/chassis/suction_switch`; water `/remaining_water/water_pump_value`; dust box `/BC_DustBox`) flow through this bridge.
- The ESP32-S3 dock and ESP32 schedule gateway (FW-010/011) are reached over serial/ESP-NOW, not ROS directly.

## 4. Confirmed vs inferred relationship summary

| Relationship | Status | Basis |
|---|---|---|
| ROS PC = 192.168.64.20, Master :11311 | CONFIRMED_STATIC | network config + launch + SDK, 8 sources |
| Tablet = 192.168.64.10, is ROS PC's gateway | CONFIRMED_STATIC | net_gw.conf, SDK LOCAL_ETHER_IP |
| Tablet<->ROS via rosbridge 9090/9091 (no auth on 9091) | CONFIRMED_STATIC | launch args + client URLs + APK relay |
| Tablet<->ROS via SDK CoAP/HTTP/TFTP/WS (no auth/TLS) | CONFIRMED_STATIC | decompiled SDK constants + call chains |
| No separate gateway computer between tablet and ROS PC | CONFIRMED_STATIC | REMOTE_LINK_PROXY == ROS PC addr |
| chassis node <-> GD32 MCU serial bridge | CONFIRMED_STATIC (ref) / INFERRED (framing tie-up) | chassis strings + firmware framing |
| Topic pub/sub directions | INFERRED (except CoAP bridge) | naming only; needs live/disasm |
| Any node/topic actually running | UNVERIFIED_RUNTIME | no live graph observed |
| LiDAR vendor/model per unit | INFERRED | endpoints known, model not in package |
| Which launch profile runs on a physical unit | UNKNOWN | package default is W3, not C40 |

## 5. Live verification - not performed
No read-only ROS inspector was run and no robot was network-reachable in this environment; no scanning/probing was done per constraints. All of section 1-4 is static/inferred. The single highest-value live check (read-only), if authorized access existed, is `ROS_MASTER_URI=http://192.168.64.20:11311 rostopic list -v && rosnode list` to upgrade the node/topic set to CONFIRMED_LIVE and resolve pub/sub direction via `rostopic info`.
