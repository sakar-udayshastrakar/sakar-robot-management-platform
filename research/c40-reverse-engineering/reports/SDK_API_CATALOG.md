# Peanut SDK API Catalog - Keenon C40 (release V246)

This is the top-level summary. **The exhaustive, generated catalog (~392 KB) is `partials/installation_assistant_sdk.md`** (full class/method/interface/annotation listing + appendices A-D), with a second independent extraction in `partials/peanut_clean_app.md`. Static only (decompiled from the two APKs that embed the SDK); nothing runtime-verified.

## 1. Identity
- **`peanut-sdk 2.2.6rc3-beta24`** (installation-assistant) + companion modules; peanut-clean embeds the same SDK family (313 command classes seen there).
- Package roots: `com.keenon.sdk.*` (coapapi, coap, http, tftp, proxy, protocol, component, transport), `com.keenon.serviceSdk.*`.

## 2. Facade & components (CONFIRMED_STATIC by class relationships)
`PeanutSDK` is a singleton registering `Component`s in `ComponentManager`, with accessors: **battery, clean, device, deviceNode, disinfect, door, map, navigation (navi), charge, transport, update, voice, ...**. Each component's methods instantiate API classes under `coapapi.api.*`.

## 3. Transports & link selection
- **Link types** (`PeanutConstants$LinkType`): `COAP`, `HTTP`, `COM` (serial), `COM_COAP`, `DEFAULT`. Proxy layer (`com.keenon.sdk.proxy`, `protocol.proxy`) picks transport at runtime; HTTP auto-selected when a ping to `192.168.64.20` succeeds (`isUseHttpProtocol`/`isNetPortConn`).
- **Endpoints (constants):** `COAP_PORT=5683` (`coap://192.168.64.20:5683`), `HTTP_PORT=54322` (upgrade `34569`), `WS_PORT=22345` (upgrade `12386`), `TFTP_PORT=9527`, `REMOTE_LINK_PROXY=192.168.64.20`, `LOCAL_ETHER_IP=192.168.64.10`, `LOCAL_LINK_PROXY=127.0.0.1`.
- **Serial:** `COM1-4=/dev/ttyS1-4`, `COM3RATE=921600`, `DEFAULT_RATE=115200`, `COM_USB0/2/3`, `COM_USB4=/dev/ttyUSBDoorCtl`, timeouts 10-60 s.
- **No auth/TLS** on any transport (`HttpRequestBody` has no credential field; schemes `coap://`/`http://`/`ws://`/`tftp://`) - RIA-024, PCL-066.

## 4. Command surface (CONFIRMED_STATIC by annotation)
Declared via annotations `@LinkAdapter(board, link)`, `@CoapCommond(path, type)`, `@SerialCommand(action, body, status)`, `@HttpCommand`:
- **223 CoAP resource APIs**, **88 serial command APIs**, **2 HTTP command APIs** (installation-assistant count; peanut-clean maps 187 SDK-method->CoAP-path pairs). Full tables: SDK partial Appendix C.
- `BoardType`: BATTERY, DOOR, EMOTION, KC01, LIGHT, ROBOT, ROLA, DEFAULT. `Device` enum: 30 identities incl. ANDROID, ROS, STM32, MOTOR, POWER, PERIPHERAL_CONTROL_BOARD, SCHEDULE, VSLAM, LIDAR_FRONT/BACK, WORK_STATION_CTL_BOARD, plus string forms like `gd32f3-c40-chassis`.
- Representative resources: clean (`/clean/dstV2`, `/clean/*`), charge (`/charge/auto`), devices (`/devices/reboot`), map (`/update/*`, TFTP map transfer), navigation.

## 5. Transport/service hooks & AIDL bridge
- `META-INF/services`: `ThroughUpDataTransfer`, `IRobotApiCompat`, `ISubscribeCallback`.
- The service SDK binds **AIDL services in `com.keenon.systemservice`** (BI, MQTT, TTS, Voice) and exposes `KeenonServiceUtil` (exeCmd, silentInstallApp, copyDataToUDrive) - this is the privilege bridge between normal apps and the system-signed ServiceSystem (PCL-061, RIA-037, SYS-004).

## 6. Auth / identity model (STRONGLY_SUPPORTED)
`AuthInfo`/`LicenseInfo`, `OfflineAuth.start`, `PeanutError.AUTH_*` (codes ~203021-2030xx); robot identity/SSH (`sshPwd`, `machineAuth`), `location` restriction, `cybersecurity` component. `AUTH_ID`/`AUTH_SECRET` constants ship empty. Actual enforcement is UNVERIFIED_RUNTIME.

## 7. Data models & error codes
`keenon_database_msgs`-aligned models (map/pose/zone/label/elevator/gate); error-code families per component (CONFIRMED_STATIC from constants). Full enumeration in the SDK partial.

## 8. Confidence
Class/method/interface/annotation inventory, link constants, transport selection: CONFIRMED_STATIC. Method->CoAP-path mapping beyond annotated ones: INFERRED. Physical effect of a command and runtime auth: UNVERIFIED_RUNTIME.
