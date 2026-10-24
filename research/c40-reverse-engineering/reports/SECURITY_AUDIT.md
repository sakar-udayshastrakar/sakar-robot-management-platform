# Security Audit - Keenon C40 software release V246

**Defensive review of locally-held artifacts only.** All findings are from static analysis of the shipped release (APKs, ROS debs, firmware, configs). **No exploitation, no live robot, cloud, or network testing was performed**; no active scanning or probing. Secret values are **redacted everywhere** - only the type and location are recorded (per the evidence rules). "Reproduction" for every finding means inspecting the named local artifact, not attacking a live system. Live reachability/exploitability of most issues is therefore `UNVERIFIED_RUNTIME`.

This file is the consolidated register. Per-domain detail and full evidence/remediation text live in the partial reports:
`partials/system_apps.md`, `partials/peanut_clean_app.md`, `partials/installation_assistant_app.md`, `partials/ros_packages.md`, `partials/firmware_and_scripts.md`, and the per-finding JSON (`partials/*_findings.json`, consolidated in `FINDINGS_REGISTER.csv`).

## 1. Severity summary (security-relevant findings)

| Severity | Count |
|---|---|
| Critical | 1 |
| High | 24 |
| Medium | 53 |
| Low | 26 |
| Info | 5 |
| **Total (SEC + sev-rated)** | **109** |

Across all domains there are **226** findings total (SEC 94, ROB 31, NAT 23, NET 22, WF 19, SDK 14, APP 12, DB 11).

## 2. Cross-cutting themes (the important story)

These recur across every component and are the substance of the audit:

1. **Unauthenticated control surfaces on a flat LAN.** The tablet and the ROS computer sit on one bridged `192.168.64.0/24` segment that also carries the robot's own Wi-Fi AP (ROS-034). On that segment: rosbridge control `:9091` runs `authenticate=false, ssl=false` (ROS-030/043); the installation assistant exposes a no-auth WebSocket control server on `:8888` (RIA-001) and an AndServer HTTP API on `:8080` (RIA-004); peanut-clean exposes a wildcard-CORS REST API on `:8089` with reboot/ROS-restart/map/PIN setters (PCL-048); the Peanut SDK's CoAP/HTTP/TFTP/WS robot transports carry no auth or TLS (RIA-024, PCL-066). Anything that can join the hotspot can reach robot-affecting commands.
2. **Arbitrary command / file / install primitives reachable from IPC.** `ServiceSystem` exports an unauthenticated broadcast receiver that runs arbitrary shell commands (SYS-001, the single Critical), copies arbitrary files (SYS-002) and triggers package installs (SYS-003); it exports AIDL services with no permission check (SYS-004); `RemoteControl` takes shell commands over MQTT (SYS-005). Because these apps are `sharedUserId=android.uid.system`, those primitives run with system privilege.
3. **Hard-coded secrets throughout (all redacted here).** A sudo password compiled into a ROS binary that is also the Wi-Fi PSK (ROS-040/041/042); SMB root credentials (SYS-007); third-party LLM / Azure keys (SYS-008/009); an iFlytek RSA private key in assets (SYS-010); a VTN private key PEM (ServiceSystem assets); fleet-wide mTLS client keystores shipped in every APK with passwords served by `libcipher_pack.so` (PCL-052, RIA-008/009, SYS-017); a compiled-in backup admin PIN (PCL-049).
4. **Update/OTA integrity is MD5-or-nothing.** The vendor manifest uses MD5+size and no signature (FW-023, ROS-050, ROS-044); MCU firmware has MD5 only and no secure boot (FW-003/012); ESP images carry a hard-coded ESP-NOW key and no signature (FW-013); APK OTA trusts a server-supplied MD5 (PCL-054, RIA-014). The `ros_update` OTA HTTP service runs as root (ROS-031/044).
5. **TLS that does not actually validate.** Substring "keenon" certificate checks (PCL-050, SYS-012), process-wide trust-all managers (SYS-011, RIA-011, PCL-065), cleartext permitted globally and user-CA trust enabled (PCL-051, RIA-010, SYS-013).
6. **End-of-life stack + debug/test residue in production.** ROS Indigo / Ubuntu 14.04 / GCC 4.8.4 (ROS-049, FW-019); embedded Chromium M73 (RIA-012); `androidx.test` activities exported in release builds (SYS-016, RIA-021, PCL-007); developer endpoints and full DWARF/dev paths left in shipped binaries (FW-018, ROS-055, RIP-013).

## 3. Full security register (sorted by severity)

Component column is truncated for width; full component, evidence, impact, remediation and reproduction are in `FINDINGS_REGISTER.csv` and the per-domain partials. Status values combine the worker's static-evidence qualifier; confidence is HIGH/MEDIUM/LOW.
| ID | Sev | Status | Conf | Component | Title |
|---|---|---|---|---|---|
| SYS-001 | Critical | CONFIRMED | HIGH | ServiceSystem: com.keenon.systemservice.SysServiceBroad | Exported receiver executes arbitrary shell commands from an unauthenticated broadcast |
| PCL-048 | High | STRONGLY_SUPPORTED | HIGH | ApiServerManager, RequestUtils, ApiProvider, intercepto | Unauthenticated, wildcard-CORS REST API listening on all interfaces (port 8089) with reboot/ROS-restart/map/clean/passwo |
| PCL-049 | High | CONFIRMED | HIGH | PassWordActivity.isPasswordCorrect, PsConstants (DEFAUL | PIN gate: compiled-in backup admin PIN, native default PINs, PIN logged in clear |
| PCL-050 | High | CONFIRMED | HIGH | com.keenon.component.core.mqtt.DynamicMqttServer$2 (che | MQTT TLS trust manager only checks that the certificate subject contains "keenon"/"keenoon" |
| RIA-001 | High | CONFIRMED | HIGH | com.keenon.peanut.peanutservice.server.webSocket.server | Unauthenticated WebSocket control server bound to all interfaces (TCP 8888) |
| RIA-002 | High | STRONGLY_SUPPORTED | MEDIUM | InformationSupport.onWriteFile -> MapFileUtils.writeFil | Arbitrary file write with path traversal via "postWriteInFile" request |
| RIA-003 | High | STRONGLY_SUPPORTED | MEDIUM | WebServerManager.handleRosInfo / RosSocketClientManager | rosbridge relay: unauthenticated LAN clients can reach ROS (192.168.64.20:9090/9091) through the app |
| RIA-012 | High | CONFIRMED | HIGH | lib/armeabi-v7a/libchrome.so; org.chromium.chrome.* (12 | Outdated embedded Chromium 73.0.3683.93 with many default-exported browser components |
| ROS-030 | High | CONFIRMED (launch args/cli | MEDIUM | robot-settings (rosbridge_websocket_control.launch), ro | rosbridge WebSocket servers (ports 9090 and 9091) without authentication or TLS |
| ROS-031 | High | CONFIRMED (strings); port  | MEDIUM | ros-indigo-libcpprest usr/bin/ros_update | ros_update OTA service listens on HTTP (default port 9527) with upgrade/action and upgrade/status resources |
| ROS-032 | High | CONFIRMED (strings/scripts | MEDIUM | ros-indigo-remote-tools (package name "build") | WsServer websocket control channel and frp reverse tunnel (remote SSH) |
| ROS-040 | High | CONFIRMED | HIGH | ros-indigo-show-points opt/ros/indigo/lib/show_points/s | Hard-coded sudo password inside send_depth_node binary |
| ROS-041 | High | CONFIRMED | HIGH | ros-indigo-remote-tools usr/bin/WsServer, usr/bin/frpc_ | Hard-coded password passed on command line to frpc_delete / sudo -S (WsServer and script) |
| ROS-042 | High | CONFIRMED (file); on-devic | MEDIUM | ros-indigo-robot-settings etc/hostapd/hostapd.conf | Default Wi-Fi AP credentials shipped in hostapd.conf (WPA-PSK), AP bridged to robot network |
| ROS-043 | High | CONFIRMED (interfaces exis | MEDIUM | ros-indigo-robot-settings, robotviz, ROS master | Unauthenticated ROS master / rosbridge / services control (including config and reboot) |
| ROS-044 | High | CONFIRMED (package structu | MEDIUM | manifest.json, ros-indigo-libcpprest ros_update, debs | OTA update flow lacks integrity/authenticity controls and runs as root |
| SYS-002 | High | CONFIRMED | HIGH | ServiceSystem: SysServiceBroadcastReceiver (action com. | Exported receiver copies arbitrary files/directories on request |
| SYS-004 | High | CONFIRMED | HIGH | ServiceSystem: VoiceApiService (IVoiceAidlApi), TtsApiS | Unauthenticated exported AIDL services allow arbitrary MQTT publish/subscribe and voice/TTS control |
| SYS-005 | High | CONFIRMED | HIGH | RemoteControl: com.keenon.ssh.mqtt.APPCmdHelper.inputCm | MQTT-delivered remote shell command execution |
| SYS-006 | High | STRONGLY_SUPPORTED | MEDIUM | ServiceSystem: com.keenon.systemservice.init.SystemInit | adb over TCP enabled unconditionally at init and reachable through the FRP tunnel |
| SYS-007 | High | CONFIRMED | HIGH | RemoteControl: com.keenon.peanut.uploadlog.smb.SMBInter | Hard-coded SMB root credentials for the robot-side host |
| SYS-008 | High | CONFIRMED | HIGH | ServiceSystem: com.keenon.systemservice.voice.ctrl.nlp. | Hard-coded third-party LLM API bearer keys in code |
| SYS-009 | High | CONFIRMED | HIGH | ServiceSystem: assets/voiceConfig/micro_resource.json | Hard-coded Azure OpenAI / Speech keys for 8 regions in a bundled asset |
| SYS-011 | High | CONFIRMED | HIGH | RemoteControl: com.keenon.common.http.security.AllowX50 | Process-wide trust-all TLS and hostname verifier |
| SYS-012 | High | CONFIRMED | HIGH | ServiceSystem: com.keenon.component.core.mqtt.DynamicMq | MQTT TLS trust check is a substring match on certificate subject, not real validation |
| FW-003 | Medium | CONFIRMED | HIGH | GD32 chassis/periph .bin, iap_main, upgradeConfig.json | MCU firmware integrity is MD5-only: no signature or authenticity check on GD32 images |
| FW-012 | Medium | CONFIRMED | HIGH | base_station-app .bin (ends at 0xBE730 right after SHA- | No secure-boot signature block in either ESP image file |
| FW-013 | Medium | CONFIRMED | HIGH | Schedule .bin @0x67B8; base_station .bin @0x4070 | Hard-coded ESP-NOW PMK literal shared by gateway and dock firmware (value redacted) |
| FW-014 | Medium | INFERRED | LOW | base_station .bin strings 0x3E29-0x4D4D | Dock accepts management and OTA frames over ESP-NOW with MAC-based filtering and an open pairing state |
| FW-020 | Medium | CONFIRMED | HIGH | base_station-app-v1.1.0-0-gf0ff236\script\upgrade.sh (8 | base_station upgrade.sh: chmod -R 777 *, no shebang, no checks |
| FW-021 | Medium | CONFIRMED | HIGH | base_station-app-v1.1.0-0-gf0ff236\update_Stm32.sh | update_Stm32.sh flashes the first matching .bin with no integrity, version or type check |
| FW-023 | Medium | CONFIRMED | HIGH | upgradeConfig.json (4,682 B) | upgradeConfig.json uses MD5 + size only; no signature; https on cloud.robotkeenon.com |
| PCL-051 | Medium | CONFIRMED | HIGH | AndroidManifest (usesCleartextTraffic=true), res/xml/ne | Cleartext transports allowed and used (HTTP, MQTT tcp/1883, ws, CoAP/UDP, TFTP) |
| PCL-052 | Medium | CONFIRMED | HIGH | res/raw/{client,prod_client,dev_client,eng_client,*_cn_ | Fleet-wide mTLS client identity (BKS with sealed private key) shipped in every APK; passwords from the native secret sto |
| PCL-053 | Medium | CONFIRMED | HIGH | datasync.utils.AesUtil, peanut.core.security.{AesUtil,D | Weak/misused cryptography in registration, domain cache and utilities |
| PCL-054 | Medium | STRONGLY_SUPPORTED | MEDIUM | component.upgrade.*, AppInstaller, AppInstallerUtil, Ke | Software update integrity relies on MD5 supplied by the same server; silent/root install paths |
| PCL-055 | Medium | CONFIRMED | MEDIUM | META-INF/CERT.RSA, CERT.SF, MANIFEST.MF, APK Signing Bl | APK signing certificate is a generic "CN=Android" certificate; v1+v2/v3 signatures |
| PCL-056 | Medium | CONFIRMED | HIGH | AndroidManifest meta-data io.sentry.*, SentryInitProvid | Sentry configuration sends PII, screenshots and view hierarchy over cleartext HTTP |
| PCL-058 | Medium | STRONGLY_SUPPORTED | MEDIUM | AndroidManifest (allowBackup=true, requestLegacyExterna | Local data at rest: unencrypted SQLite on shared external storage, allowBackup, broad storage permissions |
| PCL-061 | Medium | CONFIRMED | MEDIUM | KeenonServiceUtil.exeCmd/silentInstallApp/copyDataToUDr | Implicit broadcasts carry command strings and file paths to the system-service app |
| PCL-063 | Medium | CONFIRMED | HIGH | KLog/LogFile/logback, PassWordActivity, DynamicMqttServ | Sensitive data written to logs |
| PCL-064 | Medium | STRONGLY_SUPPORTED | MEDIUM | com.alibaba.fastjson 1.2.55, okhttp 3.14.9, Sentry 5.5. | Outdated components with known weaknesses on untrusted-input paths |
| PCL-066 | Medium | STRONGLY_SUPPORTED | MEDIUM | PeanutCoapClient, PeanutHttpClient, TFTPServerManager | Unauthenticated plaintext CoAP/HTTP/WebSocket/TFTP between tablet and IPC |
| PCL-067 | Medium | STRONGLY_SUPPORTED | MEDIUM | CleanServerServiceImpl.cleanRobotCommandHandle, RobotCm | Cloud remote commands accepted over MQTT without message-level authentication |
| RIA-004 | Medium | STRONGLY_SUPPORTED | MEDIUM | server.http.controller.ApiController / ApiControllerAda | Local HTTP server (AndServer, port 8080): unsanitised file download / picture / video endpoints, CORS "*", no real authe |
| RIA-005 | Medium | STRONGLY_SUPPORTED | MEDIUM | ApiController.login(HttpRequest,HttpResponse,String,Str | "login" endpoint performs no credential verification and logs the submitted account and password |
| RIA-006 | Medium | CONFIRMED | HIGH | com.keenon.peanut.peanutservice.receiver.APPReceiver (m | Exported broadcast receiver APPReceiver without permission accepts spoofable control broadcasts |
| RIA-007 | Medium | CONFIRMED | HIGH | PsvConstants.HOTPOT_SSID / HOTPOT_PWD; service.WifiHotp | Hard-coded Wi-Fi hotspot SSID and WPA password in code |
| RIA-008 | Medium | STRONGLY_SUPPORTED | MEDIUM | lib/armeabi-v7a/libcipher_pack.so; com.keenon.peanut.co | Credential/secret store in native library libcipher_pack.so (ROS SSH/SMB passwords, PINs, MQTT and TLS passwords, AES ke |
| RIA-009 | Medium | STRONGLY_SUPPORTED | MEDIUM | res/raw/{prod,eng}_{client,ca}.bks and *_cn_*.bks; Dyna | Shipped TLS client keystores (BKS) for MQTT in res/raw |
| RIA-010 | Medium | CONFIRMED | MEDIUM | AndroidManifest application @networkSecurityConfig, res | Cleartext traffic permitted and user-installed CAs trusted (networkSecurityConfig); allowBackup=true; targetSdk 22 |
| RIA-013 | Medium | CONFIRMED | HIGH | com.keenon.peanut.core.utils.device.ShellUtil; android. | Root/shell command execution and silent package install helpers |
| RIA-014 | Medium | STRONGLY_SUPPORTED | MEDIUM | httprequest.manager.UpgradeHelper (checkUpdate, toDownl | OTA APK update integrity relies on cloud-supplied MD5 only; install via ACTION_VIEW / pm install |
| RIA-015 | Medium | CONFIRMED | MEDIUM | com.keenon.serviceSdk.utils.KeenonServiceUtil.exeCmd /  | Unprotected implicit broadcasts used as command channel to a privileged service (EXECUTE_CMD, SILENT_INSTALL_APP) |
| RIA-016 | Medium | STRONGLY_SUPPORTED | MEDIUM | com.alibaba.fastjson (JSON.VERSION=1.2.68); com.fasterx | fastjson 1.2.68 and Jackson 2.9.0.pr3 parse untrusted WebSocket JSON |
| RIA-017 | Medium | STRONGLY_SUPPORTED | MEDIUM | CommUtils.macTo6DigitCRC; InformationSupport.onHandleGe | Offline mapping verification code is a CRC32-derived 6-digit value of the MAC with a hard-coded suffix |
| RIA-018 | Medium | STRONGLY_SUPPORTED | MEDIUM | InformationSupport.onHandleUpdateEnvironmentConfigurati | Cloud environment/domain can be switched through the unauthenticated WebSocket (operationEnvironmentConfiguration) |
| RIA-023 | Medium | STRONGLY_SUPPORTED | MEDIUM | smb.SMBManager.smbPut/onSmbPut; server.webSocket.help.U | SMB1 (jcifs-smb1) file transfer to the ROS board with fleet-shared credentials |
| RIA-024 | Medium | STRONGLY_SUPPORTED | MEDIUM | com.keenon.sdk.http.OkHttpFactory/PeanutHttpClient; com | Robot-link transports use plaintext HTTP/CoAP/WS/TFTP to 192.168.64.20 without authentication in the SDK |
| ROS-033 | Medium | CONFIRMED (classes/strings | MEDIUM | ros-indigo-libcoap (coap_bridge_server, libcoap_bridge. | CoAP bridge server and download client (libcoap) |
| ROS-034 | Medium | CONFIRMED (config); effect | HIGH | ros-indigo-robot-settings | Robot internal network design (192.168.64.0/24, bridge br0) and Wi-Fi AP bridged into it |
| ROS-045 | Medium | CONFIRMED (code); whether  | HIGH | ros-indigo-robotviz assets/Login.396f4042.js | robotviz login is client-side only with hard-coded key/ciphertext and pre-filled default credentials |
| ROS-046 | Medium | CONFIRMED (strings); injec | MEDIUM | ros-indigo-libcoap coap_bridge_server | CoAP bridge builds sudo shell commands and downloads archives by URL without signature |
| ROS-047 | Medium | CONFIRMED (config); INFERR | MEDIUM | ros-indigo-robot-settings system/conf/smb.conf, postins | Samba shares exposing /home/peanut and /root/.ros (read-write) and /etc/ros/runtime |
| ROS-049 | Medium | CONFIRMED (names/versions) | HIGH | Whole package | End-of-life software stack |
| ROS-050 | Medium | CONFIRMED | HIGH | all 106 debs, manifest.json | Debian packages unsigned; control files carry no md5sums; manifest has no checksums |
| ROS-057 | Medium | CONFIRMED (code) | HIGH | remote-tools update_frpc.py, update_map_frpc.py | Remote-tools tunnel client and command stack executes strings built from message fields |
| SYS-003 | Medium | STRONGLY_SUPPORTED | MEDIUM | ServiceSystem: SysServiceBroadcastReceiver (action com. | Exported receiver triggers the package-install flow for a caller-supplied path |
| SYS-010 | Medium | CONFIRMED | HIGH | ServiceSystem: assets/robot.json, assets/robot_test.jso | iFlytek robot-platform credentials and an RSA private key shipped in assets |
| SYS-013 | Medium | CONFIRMED | HIGH | ServiceSystem and RemoteControl manifests (android:uses | Cleartext transports and cleartext permitted at the application level |
| SYS-014 | Medium | CONFIRMED | HIGH | ServiceSystem, RemoteControl, krlog META-INF/CERT.RSA | Generic/shared platform signing identity across system-privileged apps |
| SYS-015 | Medium | CONFIRMED | MEDIUM | krlog AndroidManifest.xml | krlog's sharedUserId is declared on <application>, not <manifest> |
| SYS-016 | Medium | CONFIRMED | HIGH | RemoteControl: androidx.test.core.app.InstrumentationAc | androidx.test instrumentation activities exported in a release build |
| SYS-017 | Medium | STRONGLY_SUPPORTED | MEDIUM | ServiceSystem/RemoteControl: lib*/libcipher_pack.so (Ci | Hard-coded AES/keystore material and a native key map, secrets cached in unencrypted MMKV/SharedPreferences |
| SYS-019 | Medium | CONFIRMED | HIGH | ServiceSystem: com.keenon.component.core.mqtt.DynamicMq | Secrets written to logcat, which krlog then stores persistently on shared storage |
| SYS-020 | Medium | STRONGLY_SUPPORTED | MEDIUM | RemoteControl: com.keenon.remotectrl.oray.RemoteControl | Remote desktop with input injection launched from a world-writable-adjacent temp path |
| SYS-021 | Medium | STRONGLY_SUPPORTED | MEDIUM | RemoteControl: com.keenon.ssh.frpc.FrpClientHelper.hand | Cloud-controlled nginx/map config deployment with native shell helper and no observed filename validation |
| FW-004 | Low | CONFIRMED | HIGH | all four .bin images | MCU images are plaintext (no encryption or obfuscation) |
| FW-015 | Low | STRONGLY_SUPPORTED | MEDIUM | base_station .bin 0x3678-0x39D4 (UART_CMD) | Dock UART console accepts unauthenticated actuator and channel commands |
| FW-018 | Low | CONFIRMED | HIGH | iap_main (256 symtab entries, no DWARF); ota_set_* and  | Release flashing tools ship with symbols; ota_set/serial_cmd also ship full DWARF and developer paths |
| FW-019 | Low | CONFIRMED | HIGH | .comment of iap_main, ota_set_arm, serial_cmd_arm: GCC  | Outdated toolchains used for flashing tools (GCC 4.8.4 / Ubuntu 14.04, no PIE) |
| FW-022 | Low | CONFIRMED | HIGH | schedule-v3.1.1-0-g11c2ad2\script\upgrade.sh | Schedule upgrade.sh: fragile control flow and weak success test |
| FW-026 | Low | CONFIRMED | HIGH | ota_set_arm/x86 DWARF variable tmparr (unsigned char[20 | ota_set has a fixed 2,048,000-byte image buffer |
| FW-027 | Low | INFERRED | MEDIUM | Schedule .bin: uart_param_config(UART_NUM_0...) @0x70C8 | Schedule firmware shares UART0 between console logs and the robot command protocol |
| FW-028 | Low | CONFIRMED | HIGH | Schedule .bin (e.g. 0x2F01, 0x3189, 0x3A6B); base_stati | ESP images contain verbose debug logging and panic handlers with ANSI colour logs left in release builds |
| PCL-007 | Low | CONFIRMED | HIGH | APK root: androidx.test, junit, *.ftl, freemarker, org/ | Release APK packaging contains test, build-tool and desktop artifacts |
| PCL-057 | Low | CONFIRMED | HIGH | AndroidManifest com.amap.api.v2.apikey, PeanutLocationM | Hardcoded third-party credentials (AMap key, Google API key, Sentry DSN key, Umeng/Bugly keys in native) |
| PCL-059 | Low | CONFIRMED | HIGH | RootUtils, AppInstallerUtil, SambaUtils, ShellUtil, Ser | Shell, su and system-property use |
| PCL-060 | Low | STRONGLY_SUPPORTED | MEDIUM | Manifest + BusinessInitializer.initSystemReceiver, Syst | Exported components and unprotected dynamic receivers |
| PCL-062 | Low | CONFIRMED | HIGH | JsBridgeUtils.initWebSettings/initBridgeWebSettings, Br | WebView hardening gaps for the embedded H5 UI |
| PCL-068 | Low | CONFIRMED | HIGH | AndroidManifest.xml uses-permission/permission | Over-broad and redefined permissions in the manifest |
| RIA-011 | Low | STRONGLY_SUPPORTED | MEDIUM | com.keenon.peanut.peanutservice.httprequest.http.Retrof | Trust-all X509TrustManager and always-true HostnameVerifier implemented in RetrofitFactory.getRetrofitSsl (no caller fou |
| RIA-019 | Low | STRONGLY_SUPPORTED | MEDIUM | database.dao.AppDatabase (SD_PATH/peanutservice/DBfile/ | Mapping database stored on external storage (world-readable by other apps with storage permission) |
| RIA-020 | Low | CONFIRMED | MEDIUM | AndroidManifest meta-data io.sentry.dsn, io.sentry.trac | Sentry DSN, AMap key and Sentry full tracing configured in manifest; sensitive data in logs |
| RIA-021 | Low | CONFIRMED | HIGH | androidx.test...InstrumentationActivityInvoker$* (expor | Release APK ships developer/test artifacts (androidx.test activities, dev hosts, dev launcher activities, generator temp |
| ROS-048 | Low | CONFIRMED | HIGH | ros-indigo-robot-settings, ydlidar-ros-driver | Device nodes created world read/write (MODE 0666) by udev rules and scripts |
| ROS-051 | Low | CONFIRMED | HIGH | ros-indigo-robot-settings system/conf/wvdial.conf, syst | Wvdial guest/guest credentials and legacy SSH public key shipped |
| ROS-052 | Low | CONFIRMED | HIGH | ros-indigo-robot-settings DEBIAN/postinst | Postinst overwrites system libraries and binaries with vendor copies |
| ROS-053 | Low | CONFIRMED (script); whethe | MEDIUM | ros-indigo-robot-settings usr/bin/user_monitor.sh, user | Thermal trip points raised and ADB re-enabled by user_monitor script |
| ROS-054 | Low | CONFIRMED | HIGH | ros-indigo-robot-settings etc/init/keenonrobot.conf | All robot services run as root; unlimited core dumps written to /home/peanut/keenon/core |
| SYS-018 | Low | CONFIRMED | HIGH | All three manifests | allowBackup=true on all three system apps |
| SYS-022 | Low | CONFIRMED | MEDIUM | ServiceSystem: voice.config.VoiceConfigActivity, voice. | Exported voice deep-link activities and boot receivers (lower-severity exported surface) |
| SYS-023 | Low | CONFIRMED | HIGH | krlog: LogcatHelper, MainActivity (USB export) | Full device logcat persisted to shared external storage with no file-level access control |
| SYS-024 | Low | STRONGLY_SUPPORTED | LOW | ServiceSystem: com.keenon.systemservice.voice.ctrl.nlp. | LLM system prompt can be overridden from a file on writable external storage |
| PCL-065 | Info | STRONGLY_SUPPORTED | MEDIUM | com.keenon.sdk.http.OkHttpFactory$TrustAllManager/Trust | Trust-all TLS code present in SDK and OkGo library (usage not confirmed) |
| RIA-022 | Info | CONFIRMED | HIGH | AndroidManifest uses-permission (50 unique) | Very broad dangerous / system-level permission set (INSTALL_PACKAGES, DELETE_PACKAGES, WRITE_SETTINGS, SYSTEM_ALERT_WIND |
| ROS-055 | Info | CONFIRMED | HIGH | ros-indigo-libcpprest, libcoap, busy-map, fdl-ros, micr | Build-environment path leakage and bundled debug symbols |
| ROS-056 | Info | CONFIRMED | HIGH | ros-indigo-remote-tools_2.3.1c1-4573-g1406f3b-trusty_ar | Packaging anomaly: ros-indigo-remote-tools deb declares Package: build |
| ROS-058 | Info | CONFIRMED | HIGH | ros-indigo-robot-settings cfg/yaml/*/elevator_params.ya | Fixed cloud broker IP and test topic names shipped in production configuration |

## 4. Orchestrator cross-validation

Two installation-assistant findings were independently re-decompiled at the orchestrator level with REA/JADX (evidence in `work/rea_val_*.json`, record in `partials/orchestrator_validation.json`):

- **RIA-001** confirmed: `WebServerSocket.onOpen` classifies connections only by URL path, no credential/token/origin check.
- **RIA-002** confirmed (static): `InformationSupport.onWriteFile` -> `MapFileUtils.writeFile` concatenates a client-supplied filename into a path and writes, with no `..`/canonicalisation check. Runtime reachability and process privilege remain UNVERIFIED_RUNTIME.

## 5. Not performed / out of scope

No exploitation, no live/dynamic testing, no network scanning, no robot control, no secret extraction. BKS keystore contents and `resources.arsc` were not parsed (no tool); native libraries were inspected by symbols/strings/imports, not disassembled (no Ghidra/IDA/Hopper installed). Server-side controls (MQTT broker ACLs, actual adb security state, cloud auth) are UNKNOWN. See UNRESOLVED_GAPS.md.
