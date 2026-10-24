import json
OUT = r'D:\Sakar Robotics Projects\C40 S-LS-M014C00-RW-F00-V246\REVERSE_ENGINEERING_RESULTS\partials'

F = []
def add(domain, title, severity, component, evidence, status, confidence, impact, remediation, description):
    F.append(dict(id='FW-%03d' % (len(F) + 1), domain=domain, title=title, severity=severity, component=component,
                  evidence=evidence, status=status, confidence=confidence, impact=impact,
                  remediation=remediation, description=description))

add('NAT', 'Chassis and periph MCU images are raw Cortex-M apps linked at 0x08004000 (bootloader not in package)', 'Info',
    'gd32f3-c40-chassis-app-v1.1.5-0-g5bec8c6.bin; gd32f3-c40-periph-app-v1.1.4-0-g3df9e74.bin',
    'Chassis: vector table 0x0-0x12F, SP word0=0x200095F8, Reset word1=0x08004149; Region$$Table at file 0xC54C with literals 0x0801054C/0x0801056C at 0x7B8/0x7BC => base 0x08004000. Periph: SP 0x2000F6F0, Reset 0x08004149, Region$$Table at 0xB120 (load 0x0800F140).',
    'CONFIRMED', 'HIGH', 'Defines flash map: app at +16 KB, bootloader region 0x08000000-0x08003FFF is flashed separately (not shipped) so app-only updates keep the bootloader.',
    'Keep bootloader versions documented; include bootloader image in recovery kit.',
    'Both images have no ELF/header, begin with the vector table, 60-IRQ table (GD32F30x high-density layout, INFERRED), RW data stored with ARM-Compiler scatter compression (load size 0x29C vs exec 0xA54 chassis; 0x230 vs 0x79C periph). Plaintext, entropy 7.07 and 6.81.')

add('NAT', 'Keenon 148-byte IAP tail (length, MD5, version, name) appended to GD32 images', 'Info',
    'chassis tail @0x12E50; periph tail @0xB370; iap_main symbol app_tail (148 B)',
    'u32 len==tail offset (0x12E50 / 0xB370); MD5 fields 7692ca461c339c7e6fa74113f1e0a202 and 5ad13df946287a8b527104824eb99843 equal recomputed MD5 of payload; version at +20 (v1.1.5-0-g5bec8c6 / v1.1.4-0-g3df9e74); name at +84 ("gd32f3-c40-chassis-app\\0bin\\0", "gd32f3-c40-periph-app\\0bin\\0"). iap_main help: file must be app WITHOUT bootloader but WITH tail.',
    'CONFIRMED', 'HIGH', 'Tail is the only metadata the flasher/bootloader can use for integrity and board-name matching.',
    'Document the tail format; add an authenticated signature field if tooling is rebuilt.',
    'Structure: u32 payload_len, MD5[16], version[64], name+"bin"[64], FF padded. The version/name strings also appear as plain strings inside the payload (0x7514, 0x72D4 chassis).')

add('SEC', 'MCU firmware integrity is MD5-only: no signature or authenticity check on GD32 images', 'Medium',
    'GD32 chassis/periph .bin, iap_main, upgradeConfig.json',
    'Tail MD5 is recomputed from payload (verified); manifest subPkgMd5 for 06aced7e... and f2bd47fc... equals file MD5; no signature/public-key fields in tail or manifest; no crypto constants (CRC32/MD5/SHA/AES tables) in either image; iap_main contains only mbedtls_md5.',
    'CONFIRMED', 'HIGH', 'Anyone able to write the package or intercept the transfer can craft a modified image with a valid MD5 tail; MD5 also has known collision weaknesses. Bootloader checks (MSP range, name match) are anti-misflash, not authentication.',
    'Add asymmetric signature verification in the bootloader (e.g. Ed25519/ECDSA over the payload+version), SHA-256 instead of MD5, and anti-rollback counter.',
    'Absence of verification inside the (unavailable) bootloader cannot be proven; however all evidence available (tail layout, host tool) shows MD5 as the only check.')

add('SEC', 'MCU images are plaintext (no encryption or obfuscation)', 'Low',
    'all four .bin images',
    'Readable strings and JSON templates (chassis 0x72B4-0x7648, 0xC464; periph 0x2040; ESP images full IDF log strings); vector tables parse cleanly; entropy 6.8-7.15 consistent with code, not ciphertext.',
    'CONFIRMED', 'HIGH', 'Firmware is trivially reverse-engineerable and clonable; enables targeted attacks on protocol/state machines.',
    'Consider flash read-out protection (GD32 FMC security protection, ESP flash encryption) if IP protection matters.',
    'Not encrypted or compressed apart from ARM scatter-load RW compression.')

add('NAT', 'Chassis image embeds an ~26 KB MCS-51 (8051) firmware blob', 'Info',
    'gd32f3-c40-chassis-app 0xC57C-0x12BB3',
    'mcs51_probe.py: at base 0xC57C 76% of LCALL (0x12) targets land after RET/LJMP vs 35% elsewhere; LJMP vectors at +0/+3/+6 (0x5A4B,0x64CC,0x4B59); byte histogram 0x90/0xE0/0xF0/0xA3; strings BP-M07S001-APP-2420 (0x125E2), BP01 (0x125F6), HuaCLi (0x125FB), BP001 (0x12602). Blob ends at 0x12BB4 = compressed RW load address.',
    'INFERRED', 'LOW', 'Chassis firmware can carry and probably re-program a secondary 8051-class controller; its update path is unreviewed and unsigned.',
    'Identify the target MCU (likely battery/BMS, UNKNOWN) and include it in the firmware inventory; disassemble with an 8051 tool.',
    'No direct 32-bit pointer to the blob was found (accessed some other way, UNKNOWN). The "BP" prefix meaning is speculative.')

add('NAT', 'Periph board runs RT-Thread and drives cleaning actuators over CAN', 'Info',
    'gd32f3-c40-periph-app-v1.1.4-0-g3df9e74.bin',
    'tidle @0x72B8; can_recv_sem/can_send_sem @0x2E0C/0x2E1F; i2c_mutex @0x3CC8; water_pump_task_tid @0x35A4; scrb_tid/srb_tid; side_brush(_self_detect) @0x8AA0/0x8AB0; vacuum_motor @0xAD3C; carpet_detection_sensor @0x307C; press_mon @0x5BE8; watch_dog_tid @0xAE78; PendSV 0x0800419A and HardFault 0x0800424C custom handlers.',
    'STRONGLY_SUPPORTED', 'MEDIUM', 'Defines the scope of the periph controller (brushes, vacuum, pump, sensors) - useful for fault diagnosis and for scoping what an IAP update can break.',
    'None (informational).',
    'RT-Thread inferred from idle-thread name and object naming; peripheral mapping from names (not disassembled). CAN link to chassis is INFERRED.')

add('NAT', 'Chassis image: bare-metal control firmware with IMU, magnetic encoders, motor-fault and wheel-lock logic', 'Info',
    'gd32f3-c40-chassis-app-v1.1.5-0-g5bec8c6.bin',
    'IMU nodata/IMU freq @0x1BD7/0x1BE8; magnetic sensor error / encode not cali @0x8E24-0x8E78; Unlocked by key|cmd|bump @0x8EB0-0x8ED0; overcurrent/hardware fault/overheat/body voltage risk/block/Motor overload @0x8EE4-0x8F38; Left/Right @0xC454; system exception stubs at 0x08005BF8-0x08005C00.',
    'INFERRED', 'MEDIUM', 'Describes the drive-train safety logic; matches Keenon wheel-motor diagnostics.',
    'None.',
    'IRQ set (HD map, INFERRED): DMA0 ch0/2/5/6, ADC0_1, ADC2, CAN0_RX0, TIMER0/1/5/7, USART0-2, UART3-4. RTOS not detected (no thread names).')

add('NET', 'Chassis speaks YD/T 1363.3-style ASCII battery (BMS) frames with valid length and checksum', 'Info',
    'chassis @0x3DC4 "~22004A45A00601070FFC49\\r"; @0x12E33 "~22004A42E00201FD29\\r"',
    'LCHKSUM nibbles A (LENID 006) and E (LENID 002) are correct; CHKSUM FC49 and FD29 equal two-complement of the ASCII sum (0x3B7, 0x2D7) - recomputed.',
    'CONFIRMED', 'HIGH', 'Chassis polls the battery pack using a plain-text, unauthenticated protocol (standard for BMS).',
    'None; keep link physically protected.',
    'Frame structure is confirmed; the attribution to a YD/T 1363.3-family BMS protocol (CID1 0x4A) is INFERRED, MEDIUM.')

add('NET', 'Common device-info JSON schema across chassis, periph and dock firmware', 'Info',
    'chassis 0xC464-0xC548, 0x6630, 0x66BC, 0x7580, 0x765C; periph 0x2040; base_station 0x3AC4-0x3B44',
    'status/code/msg/data/property/property-name-en/property-name-cn/devices, devices ids 2 and 21, "hw":{"uuid":"%s"}, "FN%d" function nodes.',
    'CONFIRMED', 'HIGH', 'Host queries a uniform JSON "devices" list; hardware UUID (MCU UID) is exposed to the host.',
    'Document schema for the diagnostic tooling; treat the UUID as an identifier.',
    'The periph entry is appended to the chassis list (trailing %s). sn is empty in one template.')

add('NAT', 'base_station-app .bin is an ESP32-S3 ESP-IDF v5.1.2 image (dock controller), not an STM32/GD32 image', 'Info',
    'base_station-app-v1.1.0-0-gf0ff236.bin',
    'Header e9 05 02 2f entry 0x40375030, chip_id 9 (ESP32-S3); app_desc @0x20 magic 0xABCD5432, project base_station, v1.1.0-0-gf0ff236, 19:19:01 Aug 12 2026, IDF v5.1.2-dirty; XOR checksum 0x2D and appended SHA-256 da7583...5012 verified; strings: valves, pumps, flow meters, charging FSM, error codes 18/26/27/28.',
    'CONFIRMED', 'HIGH', 'The update script name (update_Stm32.sh) and manifest column work_station_ctl_board_version are misleading; the dock is an ESP32-S3 water/charging station reached via the Schedule gateway.',
    'Rename scripts/manifest columns; document the real MCU.',
    'IDF built from a "dirty" (modified) tree. Dock functions are STRONGLY_SUPPORTED from log strings.')

add('NAT', 'Schedule .bin is an ESP32 espnow_doorbell gateway (UART to ESP-NOW call-bell bridge with OTA relay)', 'Info',
    'Schedule-v3.1.1-0-g11c2ad2.bin',
    'chip_id 0 (ESP32), project espnow_doorbell, v3.1.1, Aug 24 2023 13:39:11, IDF v4.4.2-dirty; commands get version/get channel/get heart/ota start/wifi ota/reboot/toggle forward/clear maclist (0x6D24-0x7000); doorbell_wifi_ota_task; UART_NUM_0; SHA-256 0117...de64 verified.',
    'CONFIRMED', 'HIGH', 'The device behind /dev/ttyUSBSchedule is the radio gateway; flashing the dock and flashing the gateway both go through it.',
    'None.',
    'Project name "espnow_doorbell" shows the firmware is derived from an ESP-NOW doorbell/example codebase.')

add('SEC', 'No secure-boot signature block in either ESP image file', 'Medium',
    'base_station-app .bin (ends at 0xBE730 right after SHA-256); Schedule .bin (ends 0xAB600)',
    'esp_img.py: nothing follows the appended 32-byte SHA-256 in both files; no 0xE7 signature-block sector.',
    'CONFIRMED', 'HIGH', 'If secure boot is not enabled in eFuses, any image with a valid checksum/SHA-256 boots; the SHA-256 is self-computed and gives no authenticity.',
    'Enable Secure Boot v2 and flash encryption on production units (note OTA signature flow).',
    'Device eFuse state is UNKNOWN (BLOCKED from static analysis); this finding is about the shipped file only.')

add('SEC', 'Hard-coded ESP-NOW PMK literal shared by gateway and dock firmware (value redacted)', 'Medium',
    'Schedule .bin @0x67B8; base_station .bin @0x4070',
    'identical 22-character literal beginning pmk_door_ followed by a predictable digit sequence; Schedule: esp_now_set_pmk((uint8_t *)CONFIG_ESPNOW_PMK).',
    'CONFIRMED', 'HIGH', 'Same predictable key in every unit and in two products; anyone with the binary (public download) can derive the PMK. ESP-NOW unicast encryption additionally needs per-peer LMKs (not evidenced).',
    'Provision per-site/per-unit keys; use per-peer LMK encryption; do not ship keys in firmware.',
    'Only the first 16 bytes of a PMK are used by ESP-NOW. Value deliberately not reproduced here.')

add('SEC', 'Dock accepts management and OTA frames over ESP-NOW with MAC-based filtering and an open pairing state', 'Medium',
    'base_station .bin strings 0x3E29-0x4D4D',
    '"Allow management cmd(0x%02X) from <MAC>", "Frame MAC mismatch, ignored", "Received MAC pairing command", "Unpair success, back to broadcast mode", "Search response: currently unpaired", "Recv OTA Frame", "OTA SOH: fw_name=%s", "ota_process_eot: size mismatch" (only a size check string; no MD5/signature string found).',
    'INFERRED', 'LOW', 'If confirmed, a device in radio range could pair with an unpaired dock (or spoof the paired MAC) and push unsigned firmware or valve/pump commands. Raise to High if confirmed.',
    'Authenticate frames (per-peer LMK or signed commands), require physical pairing confirmation, verify firmware signature before esp_ota_set_boot_partition.',
    'Based solely on strings; behaviour of the checks and any encryption flag in esp_now_peer_info were not decoded (Xtensa code not disassembled).')

add('SEC', 'Dock UART console accepts unauthenticated actuator and channel commands', 'Low',
    'base_station .bin 0x3678-0x39D4 (UART_CMD)',
    '"channel set%hhu", "channel get", "self clean %hhu %hhu", clean_water_valve1/2_open|close, deter_water_pump_open|close, dirty_water_pump_open|close, water_level_print_open|close.',
    'STRONGLY_SUPPORTED', 'MEDIUM', 'Physical-access attacker can run pumps/valves (flood/dry-run) or change the radio channel.',
    'Disable or password-protect the console in production builds.',
    'No authentication strings were found; verified only by string inventory.')

add('NET', 'Keenon serial framing: AA AA payload+CRC16 (A5-escaped) 55 55, CRC-16 poly 0xA001', 'Info',
    'iap_main generateFrame @0xCA70, crc16_ibm_step @0xC9E1, initTransportCRC @0xD364; ESP strings "UART_TRANS_INIT", "initTransportCRC for uart"',
    'Hand-decoded Thumb: movs #0xAA twice at start, compares against 0xAA/0x55/0xA5 with 0xA5 prefix writes, two 0x55 trailer bytes, call to crc16_ibm; crc step uses movw 0xA001 and 8 iterations; initTransportCRC stores sendFrame 0xCCB9, generateFrame 0xCA71, receiveByte 0xCDBD. 8192-byte frame buffers.',
    'INFERRED', 'MEDIUM', 'Common transport lets one tool talk to chassis bootloader, dock and gateway; framing offers integrity only (CRC) with no authentication.',
    'None.',
    'Hand decoding only (no disassembler); escape byte semantics (original byte stored unchanged after 0xA5) taken from the stores seen. Same function names in ESP firmware (CONFIRMED) suggest the same library.')

add('NAT', 'iap_main serial IAP protocol: 128-byte packets, SOH header, ACK 0x06/NAK 0x15, bootloader MD5/MSP/name checks', 'Info',
    'iap_main .rodata 0xD70C-0xE430 strings; PACK_LEN=0x0080 (.data 0x170A0); cmd_* functions',
    'Strings: "Downloading file size = %d, pack len = %d", SOH:%03d, "Wait SOH ACK timeout", seq/reqseq retransmit messages, MD5 verify fail, MSP-not-in-SRAM, flash MD5 fail, name mismatch, APP successfully boot; hand-decoded cmp #6/#0x15 and range 0x20-0x23; payloads 00 20 21 00 00 00 (reset, 0xD9A8) and 00 62 51 00 01 00 03 (encoder IAP, 0xD9A0).',
    'STRONGLY_SUPPORTED', 'MEDIUM', 'Describes how GD32 chassis/periph/encoder boards are programmed through the resident bootloader; bootloader checks are MD5, MSP range and board-name only.',
    'Document protocol; if re-implementing, add signature checks and slot rollback.',
    'Message strings (Chinese, translated in the report) describe bootloader errors; the bootloader itself is not in the package (INFERRED behaviour).')

add('SEC', 'Release flashing tools ship with symbols; ota_set/serial_cmd also ship full DWARF and developer paths', 'Low',
    'iap_main (256 symtab entries, no DWARF); ota_set_* and serial_cmd_* (.debug_info/.debug_str)',
    'DWARF comp_dir /home/peanut/ota, /root/cmd, /home/litreily/workspace/esp/esp32_serial_tools/ota (x86 serial.c GCC 7.5.0 Ubuntu 18.04); iap_info_t members status/ack_nack/reqpackseq/packseq/packlen/write_size/fw_size/fd_s/md5/filebuf; global tmparr[2048000].',
    'CONFIRMED', 'HIGH', 'Leaks internal usernames, build hosts, toolchain and data structures; eases reverse engineering.',
    'Strip release binaries (strip -s, no -g); build in clean CI.',
    'The x86 ota_set links a serial helper from a third-party-looking esp32_serial_tools tree (licence provenance UNKNOWN).')

add('SEC', 'Outdated toolchains used for flashing tools (GCC 4.8.4 / Ubuntu 14.04, no PIE)', 'Low',
    '.comment of iap_main, ota_set_arm, serial_cmd_arm: GCC (Ubuntu/Linaro 4.8.4-2ubuntu1~14.04.4) 4.8.4',
    'ELF EXEC non-PIE at 0x8000; stack-protector present (__stack_chk_fail imported); GNU_STACK RW; iap_main imports sprintf/strncpy; fixed 2048/8192-byte buffers.',
    'CONFIRMED', 'HIGH', 'Weaker exploit mitigations and unsupported compiler; tools parse serial data from boards (low exposure).',
    'Rebuild with a supported toolchain, -fPIE -pie, -D_FORTIFY_SOURCE, bounded string functions.',
    'Exploitability not assessed (no disassembly).')

add('SEC', 'base_station upgrade.sh: chmod -R 777 *, no shebang, no checks', 'Medium',
    'base_station-app-v1.1.0-0-gf0ff236\\script\\upgrade.sh (85 B)',
    'Content: script_dir=`dirname "$0"`; cd "$script_dir"/..; chmod -R 777 *; bash update_Stm32.sh. No "#!" line.',
    'CONFIRMED', 'HIGH', 'Makes iap_main, the .bin and scripts world-writable; if the package dir is shared or the script runs as root, a local user can swap the binary/firmware before execution (privilege escalation / malicious firmware). Run-as user is UNKNOWN.',
    'Use chmod 750 on specific files; add shebang, set -eu, quote paths; extract packages into root-owned dir.',
    'The `*` is unquoted and breaks on odd filenames.')

add('SEC', 'update_Stm32.sh flashes the first matching .bin with no integrity, version or type check', 'Medium',
    'base_station-app-v1.1.0-0-gf0ff236\\update_Stm32.sh',
    '`selected_files=($(find . -maxdepth 1 -name "*.bin" | grep -E "$keyword"))`; `"$path/./iap_main" -s -p /dev/ttyUSBSchedule -b 921600 -f "$stm32"`; sudo service keenonrobot stop/start commented out; no md5/sha/sig, no version compare, no status output.',
    'CONFIRMED', 'HIGH', 'Any file whose name contains base_station is flashed to the dock via the gateway; failure detection depends on iap_main exit code (UNKNOWN); ROS node may still hold the port.',
    'Verify signature/hash before flashing, compare versions, quote variables, check exit codes, re-enable service stop/start or lock the port.',
    'Chinese comments translated in the report. Name "Stm32" is legacy (target is ESP32-S3).')

add('ROB', 'Schedule upgrade.sh: fragile control flow and weak success test', 'Low',
    'schedule-v3.1.1-0-g11c2ad2\\script\\upgrade.sh',
    '`cd $(dirname $0)/../script` unquoted; unknown arch only echoes; `PACKAGE_NAME=$(basename ../bin/*.bin)`; `sleep 2`; `NEW_V=$(... awk -F- "{print $2}")`; success iff `grep ${NEW_V}` on version string (unanchored substring).',
    'CONFIRMED', 'HIGH', 'False success if old version equals new or v3.1.1 matches v3.1.10; unknown architecture continues; aarch64 uses 32-bit armhf tools that need multiarch libs; no pre-flash validation.',
    'Fail fast on unknown arch, anchor the version compare, verify file hash/signature, quote variables, set -euo pipefail.',
    'The updateSuccess/updateFail strings are probably parsed by the calling service (other workers).')

add('SEC', 'upgradeConfig.json uses MD5 + size only; no signature; https on cloud.robotkeenon.com', 'Medium',
    'upgradeConfig.json (4,682 B)',
    'Fields subPkgMd5/subPkgSize/subPkgUrl only; all 10 URLs https://cloud.robotkeenon.com/filesResource/software/<column>/<version>/<file>; isForced=false; seven present files md5/size verified.',
    'CONFIRMED', 'HIGH', 'Integrity depends on TLS validation in the client and trust in the server; a compromised server/CDN or a TLS-validation bug allows malicious packages with matching MD5. MD5 is collision-prone.',
    'Add detached signatures (and a signed manifest) verified on the robot; use SHA-256; add min-version/anti-rollback.',
    'URLs were not contacted. TLS validation by the client is not assessed here.')

add('ROB', 'Misleading naming: stm32_* manifest column and update_Stm32.sh refer to GD32F3 / ESP32-S3 targets', 'Info',
    'upgradeConfig.json subPkgVerColumnName stm32_main_c40_bottom_ctrl; update_Stm32.sh',
    'Chassis column "stm32_main_c40_bottom_ctrl" -> gd32f3-c40-chassis-app; script update_Stm32.sh -> ESP32-S3 dock.',
    'CONFIRMED', 'HIGH', 'Operators may assume STM32 tooling/bootloaders; legacy naming complicates audits.',
    'Rename or add mapping documentation.',
    'Manifest field typo subPkgVerColumnDetialId is also vendor-defined.')

add('NAT', 'ota_set text-command OTA to the ESP32 gateway, optional relay to a call bell by MAC', 'Info',
    'ota_set_arm/x86 .rodata and DWARF',
    'Usage "%s <dev> <bin> [-w [mac_addr]]"; strings "ota start", "esp_ota_begin end", "ack_id", "hold on", "wifi ota %s" with sample MAC 98:cd:ac:45:14:04; DWARF globals flag_ota_begin/ack/fail/wait, flag_wifi_ota, tmparr[2048000].',
    'STRONGLY_SUPPORTED', 'MEDIUM', 'Gateway firmware and bell firmware are updated with an unauthenticated text/serial command channel.',
    'Authenticate OTA start and verify image signature on the ESP.',
    'Baud for ota_set is not a decoded immediate (UNKNOWN; scripts use 921600 for serial_cmd).')

add('ROB', 'ota_set has a fixed 2,048,000-byte image buffer', 'Low',
    'ota_set_arm/x86 DWARF variable tmparr (unsigned char[2048000])',
    'DWARF .debug_info/.symtab: tmparr size 2048000 in .bss.',
    'CONFIRMED', 'HIGH', 'Images larger than ~2 MB cannot be sent; current schedule image 701,952 B fits.',
    'Size-check input and report a clear error; allocate dynamically.',
    'Check for explicit bounds checking not done (no disassembly).')

add('ROB', 'Schedule firmware shares UART0 between console logs and the robot command protocol', 'Low',
    'Schedule .bin: uart_param_config(UART_NUM_0...) @0x70C8, log strings "Send command: %s" @0x700D and "recv(%d): %s" @0x7035',
    'Both IDF console (/dev/uart, uart_write) and the app use UART0.',
    'INFERRED', 'MEDIUM', 'Log noise can corrupt replies of "get version" and OTA acks (script parsing by grep).',
    'Move logs to another UART or disable log output at runtime during OTA.',
    'Inferred from the shared UART number and log strings; not tested.')

add('SEC', 'ESP images contain verbose debug logging and panic handlers with ANSI colour logs left in release builds', 'Low',
    'Schedule .bin (e.g. 0x2F01, 0x3189, 0x3A6B); base_station .bin (0x12D, 0x307)',
    'Strings: "I (%u) %s: Application information", "ELF file SHA256: ", "Guru Meditation Error: Core", backtrace/register dump labels, "Rebooting...".',
    'CONFIRMED', 'HIGH', 'Information disclosure over the UART (versions, task names, addresses) and unauthenticated reboot-on-fault behaviour.',
    'Reduce log level and disable panic dumps in production.',
    'Standard ESP-IDF defaults; impact is limited to local serial access.')

add('NAT', 'Embedded version strings match the manifest for all four MCU packages', 'Info',
    'upgradeConfig.json vs images',
    'chassis v1.1.5-0-g5bec8c6 (tail + 0x7514), periph v1.1.4-0-g3df9e74 (tail + 0x202B), dock app_desc v1.1.0-0-gf0ff236, Schedule v3.1.1 (string v3.1.1-0-g11c2ad2 @0x6D30); md5 of chassis/periph equals manifest; zips absent so their md5 not re-verified here.',
    'CONFIRMED', 'HIGH', 'Release V246 is internally consistent for MCU packages.',
    'None.',
    'zip md5 values (a5fe3462..., 8c939e42...) could not be recomputed because only extracted trees exist.')

add('NAT', 'Dock firmware: water system, charging FSM and error codes', 'Info',
    'base_station .bin 0x1BDD-0x4D4D',
    'charging_task, CHARGING_FSM (standby/trickle/charging), exceptions codes 26 (battery connector over heat), 27 (charge MOS over heat), 28 (charge voltage over limit), drain pump error 18, self_clean_task, flow_meter_task, detergent ratio 20~40/40~200, ROBOT_DET, machine model.',
    'STRONGLY_SUPPORTED', 'MEDIUM', 'Gives fault-code meanings for support diagnostics.',
    'None.',
    'Derived from log strings only.')

add('NAT', 'No standard crypto/CRC tables in GD32 images; ESP images carry only IDF crypto', 'Info',
    'constant scan of chassis, periph, base_station, Schedule',
    'No 0x77073096, 0x1021/0xC0C1 tables, MD5 K0 d76aa478, SHA-256 K0 428a2f98 in GD32 images; ESP images: AES S-box (dock 0xBB18) and SHA-256 K (gateway 0x11EB4) from IDF/mbedtls.',
    'INFERRED', 'MEDIUM', 'Verification is performed elsewhere (bootloader/host) or by computed CRCs; app does not authenticate anything cryptographically.',
    'None.',
    'Absence of constants is not proof of absence of crypto (bitwise implementations).')

with open(OUT + r'\firmware_and_scripts_findings.json', 'w', encoding='utf-8') as f:
    json.dump(F, f, ensure_ascii=False, indent=2)

A = []
def art(path, analyzed, method, findings, status, limits):
    A.append(dict(relative_path=path, analyzed=analyzed, method=method, findings=findings, status=status, limitations=limits))

art('gd32f3-c40-chassis-app-v1.1.5-0-g5bec8c6.bin', True,
    'Raw Cortex-M parsing: vector table, Region$$Table, entropy, strings, constant scan, 8051 probe, tail MD5 recompute, hand decode of BMS frame checksums',
    'Base 0x08004000, SP 0x200095F8, Reset 0x08004149; 148-byte tail with verified MD5; plaintext; embedded ~26 KB 8051 blob; BMS ASCII frames; JSON device info; no signature.',
    'CONFIRMED (layout, tail); INFERRED (8051 blob, peripherals)',
    'No disassembly; bootloader absent; 8051 target unknown; part number inferred.')
art('gd32f3-c40-periph-app-v1.1.4-0-g3df9e74.bin', True,
    'Raw Cortex-M parsing, Region$$Table, strings, tail MD5, RT-Thread name inventory',
    'Base 0x08004000, SP 0x2000F6F0; tail MD5 verified; RT-Thread; brushes/vacuum/pump/carpet sensor/CAN names; JSON sub-device entry.',
    'CONFIRMED (layout); STRONGLY_SUPPORTED (RT-Thread)',
    'No disassembly; peripheral roles inferred from names.')
art('base_station-app-v1.1.0-0-gf0ff236/base_station-app-v1.1.0-0-gf0ff236.bin', True,
    'ESP image parser (header, 5 segments, checksum, SHA-256), app descriptor, string analysis',
    'ESP32-S3 IDF v5.1.2 image; checksum+SHA-256 valid; no signature block; dock water/charging logic; ESP-NOW pairing and OTA module; UART console; hard-coded PMK literal (redacted).',
    'CONFIRMED (format); STRONGLY_SUPPORTED (function); INFERRED (OTA auth weakness)',
    'Xtensa code not disassembled; eFuse security state unknown.')
art('base_station-app-v1.1.0-0-gf0ff236/iap_main', True,
    'ELF parse (sections, symtab, dynsym, PLT), UTF-8 rodata, hand-decoded Thumb (BL targets, movw/movt, immediates), .data read',
    'ARM32 hf ELF GCC 4.8.4 unstripped no DWARF; CLI -p -b -f -e -s; frame AA AA..55 55, A5 escape, CRC16 0xA001; PACK_LEN 128; ACK 0x06/NAK 0x15; bootloader checks (MSP, MD5, name) per messages.',
    'CONFIRMED (symbols/strings); INFERRED (protocol semantics)',
    'No disassembler; command payload meaning and exit-code behaviour not decoded; baud mapping partly decoded.')
art('base_station-app-v1.1.0-0-gf0ff236/update_Stm32.sh', True,
    'Read as UTF-8; Chinese comments translated; flow review',
    'Selects single *base_station*.bin, runs iap_main -s -p /dev/ttyUSBSchedule -b 921600; no integrity/version check; ROS stop/start commented out.',
    'CONFIRMED', 'Run-as user and caller unknown.')
art('base_station-app-v1.1.0-0-gf0ff236/script/upgrade.sh', True,
    'Read; flow review', 'No shebang; cd package root; chmod -R 777 *; bash update_Stm32.sh.',
    'CONFIRMED', 'Caller unknown.')
art('schedule-v3.1.1-0-g11c2ad2/bin/Schedule-v3.1.1-0-g11c2ad2.bin', True,
    'ESP image parser (6 segments, checksum, SHA-256), app descriptor, string analysis',
    'ESP32 IDF v4.4.2 espnow_doorbell v3.1.1; UART<->ESP-NOW gateway with text commands and OTA relay; PMK literal (redacted); no signature block.',
    'CONFIRMED (format); STRONGLY_SUPPORTED (function)',
    'Xtensa code not disassembled; LMK/encryption use unknown.')
art('schedule-v3.1.1-0-g11c2ad2/script/ota_set_arm', True,
    'ELF + DWARF parse (custom), rodata, hand-decoded movw/movt', 'ARM32 hf, unstripped with DWARF (/home/peanut/ota, GCC 4.8.4); text OTA to ESP32; -w MAC relay; tmparr 2,048,000 B.',
    'CONFIRMED (metadata); STRONGLY_SUPPORTED (behaviour)', 'Baud and OTA chunking not decoded.')
art('schedule-v3.1.1-0-g11c2ad2/script/ota_set_x86', True,
    'ELF + DWARF parse, rodata', 'x86-64 twin of ota_set_arm; serial.c from /home/litreily/workspace/esp/esp32_serial_tools/ota built with GCC 7.5.0; main.c GCC 4.8.4 in /home/peanut/ota.',
    'CONFIRMED (metadata)', 'No disassembly.')
art('schedule-v3.1.1-0-g11c2ad2/script/serial_cmd_arm', True,
    'ELF + DWARF parse, rodata', 'ARM32 hf with DWARF (/root/cmd); sends "get version" at given baud.',
    'CONFIRMED', 'No disassembly.')
art('schedule-v3.1.1-0-g11c2ad2/script/serial_cmd_x86', True,
    'ELF + DWARF parse, rodata', 'x86-64 twin of serial_cmd_arm.', 'CONFIRMED', 'No disassembly.')
art('schedule-v3.1.1-0-g11c2ad2/script/upgrade.sh', True,
    'Read; flow review', 'Arch-based tool select; get version, ota_set, sleep 2, substring grep success test; updateSuccess/updateFail; unquoted vars; unknown arch continues.',
    'CONFIRMED', 'Caller and run-as user unknown.')
art('upgradeConfig.json', True,
    'JSON parse (UTF-8), md5/size comparison with present files, URL pattern check', 'V246 manifest, 10 packages, MD5+size only, https, isForced false; 7 present files verified; 3 zips absent.',
    'CONFIRMED', 'URLs not contacted; zip md5 not re-verifiable here.')

with open(OUT + r'\firmware_and_scripts_artifacts.json', 'w', encoding='utf-8') as f:
    json.dump(A, f, ensure_ascii=False, indent=2)
print(len(F), len(A))
