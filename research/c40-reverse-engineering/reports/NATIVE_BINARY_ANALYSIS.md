# Native Binary & Firmware Analysis - Keenon C40 (release V246)

Static only. **No disassembler (Ghidra/IDA/Hopper/objdump/rizin) was installed**, so this is header/section/symbol/string/DWARF evidence plus hand-decoded Thumb where explicitly noted - not full decompilation. Detail: `partials/firmware_and_scripts.md` (MCU/ESP firmware, 31 findings FW-001..031) and `partials/ros_packages.md` section 5.3/ROS-070..074 (283 ROS ELFs). Secrets redacted.

## 1. MCU / board firmware

| Image | Target | Build system | Load/base | Integrity | Notes |
|---|---|---|---|---|---|
| `gd32f3-c40-chassis-app` v1.1.5 | GD32F3 Cortex-M | bare-metal | `0x08004000` (scatter-load proven) | 148-B Keenon IAP tail (len, MD5, version[64], name[64]); MD5 matches payload | plaintext; BMS ASCII battery frames (FW-008); embeds ~26 KB 8051 blob (INFERRED); IMU/encoder/motor-fault/wheel-lock logic (INFERRED) |
| `gd32f3-c40-periph-app` v1.1.4 | GD32F3 | **RT-Thread** | `0x08004000` | same IAP tail | drives brushes/vacuum/pump/sensors over **CAN** (FW-006) |
| `base_station-app` v1.1.0 | **ESP32-S3** | **ESP-IDF 5.1.2** | ESP image | valid checksum + appended SHA-256, **no secure-boot sig** (FW-012) | water + charging FSM + error codes (FW-030); **it is the dock, NOT an STM32** despite `update_Stm32.sh` naming (FW-010/024) |
| `Schedule` v3.1.1 | **ESP32** | **ESP-IDF 4.4.2** | ESP image | SHA-256, no sig | `espnow_doorbell` call-bell gateway (UART<->ESP-NOW + OTA relay) (FW-011) |

- **Hard-coded ESP-NOW PMK** shared by both ESP images (FW-013, value redacted).
- **Serial/IAP protocol (FW-016/017, hand-decoded Thumb + strings):** frames `AA AA <payload+CRC16, 0xA5-escaped> 55 55`, CRC-16 poly `0xA001`; IAP 128-byte packets, SOH header, ACK `0x06`/NAK `0x15`; modes direct / `-e` (encoder boards via chassis) / `-s` (dock). Common device-info JSON schema across chassis/periph/dock (FW-009). Version strings match the manifest for all four (FW-029).

## 2. Flashing / OTA tools (ELF, unstripped)

| Tool | Arch | Build | Note |
|---|---|---|---|
| `iap_main` | ARM32 (armhf, EXEC) | GCC 4.8.4 | 256 symtab entries, no DWARF; IAP serial flasher |
| `ota_set_arm`/`_x86`, `serial_cmd_arm`/`_x86` | ARM32 + x86-64 | GCC 4.8.4 | **full DWARF with developer paths** (`/home/peanut/ota`, `/root/cmd`, `/home/litreily/...`) (FW-018); fixed 2,048,000-B image buffer (FW-026) |

Scripts: `base_station upgrade.sh` no shebang + `chmod -R 777 *` (FW-020); `update_Stm32.sh` flashes first matching `.bin`, no checks (FW-021); `schedule upgrade.sh` weak success grep (FW-022); `upgradeConfig.json` MD5+size only, https, no signature, `isForced=false` (FW-023).

## 3. ROS-side native libraries & binaries (283 ELFs, ARM32 armhf)
- **Node executables:** ~76 ARM32 ELF nodes (mostly stripped) - ROS-070. Topic/service names harvested from their string tables (ROS-003, see ROS_TOPICS_INVENTORY.md).
- **Bundled 3rd-party (ROS-071, versions from sonames/strings):** PCL 1.8.1, OpenCV, Boost 1.54, cpprest (ros_update), websocketpp 0.8.2 (WsServer/ros_update), libcoap, jsoncpp, yaml-cpp, ZeroMQ+libsodium (MX module), frp (Go 1.16.2).
- **Sensor firmware/SDK blobs (ROS-072/073):** `micro_laser` line-laser firmware (RISC-V32 **and** ARM32 Linux userland ELFs); vendor depth-camera SDKs in `fdl-ros` (Orbbec OpenNI2, Berxel); `params100Q.bin` 6 MB camera params; lidar SDKs (`libc200_lidar.so`, `libydlidar_sdk.so`).
- **Security-relevant natives:** `send_depth_node` embeds a hard-coded sudo password (ROS-040, redacted); WsServer/frpc pass a password on the command line (ROS-041).

## 4. Android native libraries (178 `.so`, armeabi-v7a)
Inspected by ELF header/symbols/strings/imports only (no disassembly). Notable:
- **`libcipher_pack.so`** (all 4 robot apps): the fleet secret store - serves ROS SSH/SMB passwords, PINs, MQTT/TLS/keystore passwords, AES keys (RIA-008, SYS-017, PCL-052). Values **not extracted**.
- **`lib3Des.so`, `libcipher_pack.so`:** crypto.
- **RemoteControl:** `liborayservice_sdk.so` (4.9 MB Oray remote-desktop), `liborayinput_agent*.so` (input injection), `libnativetool.so`, daemon libs, `libpatchutils.so` (bsdiff).
- **ServiceSystem:** `libMicrosoft.CognitiveServices.Speech.*` (Azure Speech), `libaiui.so`/`libcae-jni.so` (iFlytek), `libGVoiceUtils.so`, alsa.
- **installation-assistant:** `libchrome.so` (Chromium M73), ARCore/GVR, Sentry, MMKV, `libkeenon_serial.so` (raw serial to MCU, su-assisted - RIA-041).

## 5. Confidence & limits
Architecture, build system, load address, integrity scheme, symbols, linked libraries, versions, embedded strings: CONFIRMED_STATIC. Firmware control logic beyond strings/vector-table/hand-decoded fragments: INFERRED or NOT_ANALYZED. Xtensa (ESP), 8051, and most Thumb/ARM32 logic were **not disassembled**. GD32 bootloader and eFuse/secure-boot state are **BLOCKED/UNKNOWN** (not in the package). The 3 zip packages' MD5s could not be re-verified (shipped only extracted). No native code was executed.
