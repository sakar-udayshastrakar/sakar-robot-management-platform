# Android Application Analysis - Keenon C40 (release V246)

Consolidated across the five APKs. Full per-app detail: `partials/peanut_clean_app.md`, `partials/installation_assistant_app.md`, `partials/system_apps.md` (+ `installation_assistant_sdk.md`). Static only; JADX/REA (headless JADX 0.7.1 via `rea-agents` 6.7.0) used for targeted decompilation, custom DEX parsers for breadth.

## 1. Application set

| APK | Package | Ver | targetSdk | dex / so | sharedUserId | Signing cert | Role |
|---|---|---|---|---|---|---|---|
| peanut-clean | com.keenon.peanut.clean | 3.7.8 | 28 | 7 / 10 | - | generic `CN=Android` | Cleaning HMI (kiosk) |
| robot-installation-assistant | com.keenon.peanut.peanutservice | 4.11.0 | 22 | 6 / 12 | - | `CN=keenon` | Install/commission + mapping tool (Chromium fork) |
| ServiceSystem | com.keenon.systemservice | 2.0.11 | 22 | 3 / 30 | `android.uid.system` | generic `CN=Android` | Voice/TTS/MQTT/BI system service |
| RemoteControl | com.keenon.remote_control_oray | 3.3.1 | 22 | 3 / 12 | `android.uid.system` | generic `CN=Android` | Oray/FRP remote access |
| krlog | com.keenon.krlog | 1.2.6 | 22 | 1 / 4 | `android.uid.system` (on `<application>`, see SYS-015) | generic `CN=Android` | Log collector |

**Signing (CONFIRMED_STATIC, Python+openssl):** 4 of 5 share one generic `CN=Android ... emailAddress=android@android.com` certificate (SHA-256 `2D:37:0C:21:...:35:22`); installation-assistant uses `CN=keenon` (SHA-256 `1F:B4:F7:10:...:19:C3`). peanut-clean & installation-assistant also carry v2/v3 signing blocks; the three system apps are v1-only. Whether the generic cert is the public AOSP test key is **UNKNOWN** (not verified against the AOSP key) - SYS-014, PCL-055.

## 2. Architecture (per app, condensed)
- **peanut-clean:** kiosk app, 48 activities, 38 TheRouter routes, Vue/OpenLayers H5 UI from assets; ARouter/TheRouter/EventBus; talks to robot via embedded Peanut SDK (CoAP `192.168.64.20:5683` + serial `/dev/ttyS1,2`). Traced chains: `startClean -> cleanStartV2 -> CoAP /clean/dstV2`, recharge `/charge/auto`, reboot `/devices/reboot`. Embedded servers: REST `:8089` (wildcard CORS, no auth; reboot/ROS-restart/map/schedule/PIN) and web UI `:8090`.
- **installation-assistant:** a **Chromium 73 fork** (`SlamApplication extends ChromeApplication`, launcher `SimpleChromeActivity`) hosting the Vue/Element-Plus "Keenon Mapping Tool" SPA (AndServer `:8080`); no-auth WebSocket control `:8888` (~55 `requestType` handlers) + rosbridge relay to `192.168.64.20:9090/9091`; log WS `:8899`. Embeds peanut-sdk + Paho + Jersey/Jackson + Room.
- **ServiceSystem:** system-priv; exported broadcast receiver runs shell cmds / copies files / installs pkgs; exported AIDL voice/TTS/MQTT/BI services (no permission); enables adb-over-TCP at init.
- **RemoteControl:** system-priv; Oray/Sunlogin remote desktop + input injection; FRP tunnel (exposes adb port); MQTT remote-shell channel; embeds Rockchip OTA installer + `update.zip`.
- **krlog:** collects full device logcat+dmesg to `/sdcard/krlog`; no INTERNET permission; USB export.

## 3. Components & exported surface
Because these apps target SDK <31, components with an `<intent-filter>` and no explicit `android:exported` are **exported by default** - so the exported surface is wider than the explicit `exported=true` count. Key exported/IPC surfaces: `SysServiceBroadcastReceiver` (SYS-001/002/003), AIDL `VoiceApiService`/`TtsApiService`/`MqttApiService`/`BiService` (SYS-004), `APPReceiver` (RIA-006), peanut-clean implicit command broadcasts (PCL-060/061), exported `androidx.test` activities in release (SYS-016, RIA-021, PCL-007). Full component inventories in the per-app partials and `partials/*_artifacts.json`.

## 4. Permissions
Very broad across the privileged apps: `INSTALL_PACKAGES`, `DELETE_PACKAGES`, `WRITE_SETTINGS`, `MOUNT_UNMOUNT_FILESYSTEMS`, `READ_LOGS`, `READ_PRIVILEGED_PHONE_STATE`, `WRITE_MEDIA_STORAGE`, `SYSTEM_ALERT_WINDOW`, etc. (RIA-022: 50 unique in installation-assistant; PCL-068 over-broad/redefined in peanut-clean).

## 5. Third-party stack (selected, CONFIRMED_STATIC from `META-INF/*.version` + class prefixes)
Chromium M73, GMS, Eclipse Paho MQTT, Jersey, Jackson 2.9.0.pr3, fastjson (1.2.55/1.2.68), okhttp 3.14.9, Room 2.3.0, greenDAO, MMKV, Sentry 5.5.x, AMap, Umeng/Bugly, ARouter/TheRouter, OpenLayers/Vue; ServiceSystem: Microsoft Cognitive Speech, iFlytek AIUI; RemoteControl: Oray SDK, frp. Versions mostly EOL (PCL-064, RIA-016).

## 6. Security
See `SECURITY_AUDIT.md` (109 security-relevant findings; 1 Critical SYS-001). The Android-specific themes: unauthenticated embedded servers (:8888/:8080/:8089), exported privileged IPC, hard-coded secrets + fleet BKS keystores, trust-all/substring TLS, cleartext, MD5-only OTA, EOL Chromium.

## 7. Confidence
Manifest, components, signing, library versions, DB schemas, embedded servers, SDK link constants: CONFIRMED_STATIC. Traced command chains: STRONGLY_SUPPORTED (decompiled) to INFERRED (UI-click-to-SDK hops only partially traced). Runtime exposure/exploitability: UNVERIFIED_RUNTIME.
