# Network & Protocol Analysis - Keenon C40 (release V246)

Static only. All endpoints are literals found in shipped artifacts; none was contacted. Machine-readable list: `NETWORK_ENDPOINTS.csv` (394 records) and `ROS_STATIC_REFERENCES.csv` (1,115 records). Secrets redacted. Detail: per-domain partials + `ROS_*` docs.

## 1. Network layout
Single bridged L2 segment `192.168.64.0/24` (`br0` = eth0+eth1+wlanAP on the ROS PC). ROS PC `.20`, tablet `.10` (and the PC's default gateway), DHCP `.200-.209`, LiDARs `.100/.103/.107`. Full detail and diagram: `ROS_COMMUNICATION_ARCHITECTURE.md`, `ROS_IP_IDENTIFICATION.md`.

## 2. Protocols and endpoints by layer

### 2.1 Robot-internal (tablet <-> ROS PC <-> boards)
| Protocol | Endpoint | Auth/TLS | Source | Finding |
|---|---|---|---|---|
| ROS XML-RPC / TCPROS | `192.168.64.20:11311` (Master) | none | keenonrobot_setup.sh, rosenv, interfaces | RIP-002, ROS-034 |
| rosbridge WS (data) | `ws://192.168.64.20:9090` | none/none | robot_common.launch, robotviz JS, APK relay | RIP-004, ROS-030 |
| rosbridge WS (control) | `ws://192.168.64.20:9091` ns=control | `authenticate=false, ssl=false` | rosbridge_websocket_control.launch | ROS-030/043 |
| CoAP | `coap://192.168.64.20:5683` | none | Peanut SDK, coap_bridge_server | RIP-005, ROS-033 |
| HTTP (SDK cmd) | `http://192.168.64.20:54322` (upgrade 34569) | none | SDK PeanutHttpClient | RIP-005 |
| WS push (SDK) | `ws://192.168.64.20:22345` (upgrade 12386) | none | SDK constants | RIP-005 |
| TFTP | `192.168.64.20:9527` | none | SDK TFTPService (PORT=9527) | RIP-005 |
| OTA HTTP (root) | `ros_update` `upgrade/action`,`upgrade/status` (port ~9527/9013) | UNKNOWN | ros_update strings | RIP-007, ROS-031 |
| WsServer control + frp | listening port UNKNOWN; frp reverse SSH tunnel | hard-coded pw (redacted) | WsServer, update_frpc.py | ROS-032/041 |
| Serial | `/dev/ttyS1-4`, `/dev/ttyUSBDoorCtl`, `/dev/ttyUSBSchedule` 921600/115200 | n/a | SDK constants, firmware scripts | RIP-011 |
| MCU framing | `AA AA <payload+CRC16 A5-escaped> 55 55`, CRC-16 poly 0xA001; IAP 128-byte packets, SOH, ACK0x06/NAK0x15 | none | firmware FW-016/017 | - |
| ESP-NOW | dock<->schedule gateway, shared hard-coded PMK (redacted) | PMK only | ESP firmware FW-013 | - |
| CAN | periph board <-> cleaning actuators | n/a | FW-006 | - |
| LiDAR | TCP 2111 (.103), UDP/TCP 2112 (.100) | none | lidar drivers | RIP-012 |

### 2.2 Tablet <-> cloud (MQTT / REST)
From the tablet apps (peanut-clean, installation assistant, ServiceSystem, RemoteControl). Hosts/keys redacted where credential-bearing.
- **MQTT:** Eclipse Paho clients. Mapping/robot control topics; mTLS (TLSv1.2, BKS keystores) **or** plaintext `tcp://...:1883` fallback (PCL-045/051, RIA-045). Trust checks are substring-"keenon" or trust-all (PCL-050, SYS-012, SYS-011). Client-id prefixes `mapping_`, cloud command handlers (PCL-067, SYS-005). Broker ACLs UNKNOWN.
- **REST/HTTPS:** cloud domains selected by region/flavor (`prod/pre/eng/dev`, CN/ES/JP) - `robot_iso_domain_name_link`, `*.keenonai.com`, `cloud.robotkeenon.com` (OTA). 58 Retrofit endpoints in peanut-clean, 2 HTTP SDK endpoints, Jersey/Jackson/okhttp stacks. Dev/test hosts (`172.16.8.102:8089`, `upload-test.keenonai.com`, `192.168.2.5:8083`) left in dev-only config assets (RIP-013).
- **Telemetry:** Sentry (DSN in manifest, PII + screenshots over HTTP - PCL-056/RIA-020); BI service (ServiceSystem); Umeng/Bugly (native keys).
- **Voice/LLM (ServiceSystem):** Microsoft Cognitive Services Speech, iFlytek AIUI, Moonshot/DashScope/Azure OpenAI - several with hard-coded keys (SYS-008/009/010).

### 2.3 Remote access
- **Oray/Sunlogin** remote desktop with input injection (RemoteControl, SYS-020).
- **FRP** reverse tunnels (`frpc_release_arm`, Go/fatedier frp) exposing local ports incl. the adb port 5678 (SYS-006/021, ROS-032); cloud-pushed MQTT config stands up tunnels.
- **adb over TCP** enabled unconditionally at init on port 5678 (SYS-006); dev config also references `192.168.64.20:5555`.

## 3. Serialization / message formats
- ROS: TCPROS + the `keenon_*_msgs` catalog (CONFIRMED_STATIC defs; see ROS_TOPICS_INVENTORY.md).
- CoAP: `@CoapCommond(path,type)` annotated resources (223 classes) with CoAP observe.
- JSON: fastjson (1.2.55 peanut-clean / 1.2.68 installation-assistant), Jackson 2.9.0.pr3, Gson - several on untrusted-input paths (PCL-064, RIA-016).
- Protobuf/pbstream: cartographer `.pbstream` maps.

## 4. Confidence
Endpoints and ports: CONFIRMED_STATIC (literals) except where marked INFERRED (e.g. exact rosbridge 9090 default, CoAP 5683 default, ros_update port). **No endpoint was contacted; all reachability/auth-in-practice is UNVERIFIED_RUNTIME.** Credentials: existence and location CONFIRMED_STATIC; values redacted and not extracted.
