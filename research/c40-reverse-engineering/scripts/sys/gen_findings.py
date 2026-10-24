"""Generates system_apps_findings.json (read-only analysis output). Values of secrets are never included."""
import json, os
OUT = r'D:\Sakar Robotics Projects\C40 S-LS-M014C00-RW-F00-V246\REVERSE_ENGINEERING_RESULTS\partials'
SS = 'ServiceSystem-v2.0.11-0-g94566e5-prod-release-p-rk3288-signed.apk'
RC = 'RemoteControl-v3.3.1-0-gfe68b6d-prod-release-p-rk3288-signed.apk'
KL = 'krlog-v1.2.6-0-g18a40d0-prod-release-p-rk3288-signed.apk'
F = []
def add(domain, title, sev, component, evidence, status, conf, impact, remediation, description, reproduction):
    F.append(dict(id='SYS-%03d' % (len(F) + 1), domain=domain, title=title, severity=sev, component=component, evidence=evidence,
                  status=status, confidence=conf, impact=impact, remediation=remediation, description=description, reproduction=reproduction))

STATIC = 'Static verification only (no execution): '
# ---------------- security ----------------
add('SEC', 'Exported receiver executes arbitrary shell commands (EXECUTE_CMD)', 'Critical',
    'com.keenon.systemservice.SysServiceBroadcastReceiver (ServiceSystem)',
    f'{SS}!/AndroidManifest.xml receiver SysServiceBroadcastReceiver has intent-filter incl. action com.keenon.action.EXECUTE_CMD, no android:permission, no exported attribute (targetSdk 22 => exported). JADX (REA inspect-android-method) SysServiceBroadcastReceiver.onReceive: ACTION_EXE_CMD -> exeCmd(intent.getStringExtra("keenon_data")); exeCmd$2.doRun -> ShellUtil.exeCmdSync(cmd) -> exeCmdSync(cmd,false) -> ProcessBuilder("sh"); BaseBroadcastReceiver.onReceive(Context,Intent) has no sender/permission check; result broadcast com.keenon.action.EXECUTE_CMD_RESULT extra keenon_data.',
    'CONFIRMED', 'HIGH',
    'Any installed app can ask the app to run a shell command as the app UID (system UID if the shared UID is honoured) and read the output from a broadcast. Full command execution on the tablet.',
    'Protect the receiver with a signature-level custom permission (android:permission) or make it non-exported and use explicit, caller-verified Binder IPC; add a command allow-list; drop the feature in release builds.',
    'The receiver is declared with intent filters and without a permission. On BOOT_COMPLETED it starts BiService/MqttApiService/VoiceApiService; for EXECUTE_CMD it passes a caller-supplied string to a shell on a pool thread and broadcasts the combined stdout. The same action string is used by KeenonServiceUtil.exeCmd in peanut-clean and robot-installation-assistant (strings), i.e. it is a designed IPC, but nothing restricts the sender.',
    STATIC + 'decode the manifest, locate the receiver and its action list; decompile onReceive and ShellUtil.exeCmdSync with JADX and confirm no calling-permission checks. Do not send the broadcast to a robot.')
add('SEC', 'Exported receiver copies arbitrary files/directories (IMPORT_U_DRIVE_DATA)', 'High',
    'SysServiceBroadcastReceiver.copyFile (ServiceSystem)',
    f'{SS}: onReceive branch for com.keenon.action.IMPORT_U_DRIVE_DATA reads extras "formPath" [sic] and "toPath" and calls copyFile -> PeanutPoolExecutor -> FileUtil.copyFile / FileUtil.copyDirFiles (dexdis of SysServiceBroadcastReceiver$1.doRun: File.isFile -> copyFile else copyDirFiles). No path validation constants found.',
    'CONFIRMED', 'HIGH',
    'Any app can make the system-privileged app copy any readable file or directory to any writable location (data theft from other apps if UID permits, overwriting configuration or binaries).',
    'Require a signature permission; restrict source/destination to allow-listed directories (canonicalise paths, reject ".."); run copies under the caller identity.',
    'The action name suggests importing data from a USB drive but the implementation accepts any two paths. Same action string is used by KeenonServiceUtil.copyDataToUDriveVLLL (peanut-clean, robot-installation).',
    STATIC + 'disassemble SysServiceBroadcastReceiver$1.doRun and FileUtil.copyFile/copyDirFiles; confirm there is no path filter.')
add('SEC', 'Exported receiver starts the package installer for any file path (SILENT_INSTALL_APP)', 'Medium',
    'SysServiceBroadcastReceiver -> AppInstallerUtil.installApp (ServiceSystem)',
    f'{SS}: onReceive ACTION_SILENT_INSTALL_APP reads extra "formPath", checks File.exists, calls AppInstallerUtil.installApp(context, path). JADX/dexdis of installApp: Intent ACTION_VIEW with application/vnd.android.package-archive, file:// URI below API 24 (FileProvider authority "<pkg>.fileprovider" otherwise; no such provider declared in the manifest). The silent variant (installAppSilence, "pm install -r") is NOT called from this receiver.',
    'CONFIRMED', 'HIGH',
    'Despite the name the call opens the system installer UI (user interaction expected on Android 6); any app can trigger it for an on-device APK path, enabling UI confusion/social engineering and nuisance prompts. If the platform installer auto-approves for system UID callers this becomes silent install (UNKNOWN).',
    'Remove from release or protect with signature permission; validate package signature and path; do not expose installer triggers to arbitrary callers.',
    'The action name says silent install but the implementation is the interactive installer. INSTALL_PACKAGES is held by the app, so behaviour on the real image should be confirmed.',
    STATIC + 'decompile onReceive and AppInstallerUtil.installApp.')
add('SEC', 'Unauthenticated exported AIDL services (Voice, TTS, MQTT gateway, BI)', 'High',
    'VoiceApiService, TtsApiService, MqttApiService, BiService (ServiceSystem)',
    f'{SS}!/AndroidManifest.xml: four <service> with android:exported="true" and no permission. onBind returns the AIDL Stub (raw bytecode returns iget-object; BiService.onBind builds BiImpManager). dexq invoke search for getCallingUid/getCallingPid/checkCallingPermission/enforceCallingPermission over all non-library classes: no hits. MqttAidlApiImpl.publish/subscribe forward caller-supplied topic strings to MqttManager.mqtt() with no allow-list (strings "publish : %s, %s, %s", "subscribe : %s, %s, %d, %s").',
    'STRONGLY_SUPPORTED', 'MEDIUM',
    'Any app can bind and (a) publish MQTT messages under the robot identity to arbitrary topics and observe topics the device credentials may subscribe, (b) issue voice commands (wake/sleep, TTS, mode, hot words, LLM function lists), (c) inject BI events. Confidentiality and integrity of cloud messaging depend on broker ACLs only.',
    'Enforce caller identity (Binder.getCallingUid -> package/signature check) or a signature permission on the services; restrict topics per caller; rate-limit.',
    'The services are the intended IPC for peanut-clean and robot-installation-assistant (matching serviceSdk Client classes). The absence of checks is a negative search result over bytecode, hence MEDIUM confidence.',
    STATIC + 'search the DEX for Binder caller checks and read MqttAidlApiImpl.')
add('SEC', 'Cloud-delivered remote shell: MQTT payload executed with sh', 'High',
    'com.keenon.ssh.mqtt.SshMqttProxy$2 / APPCmdHelper.inputCmd (RemoteControl)',
    f'{RC}: SshMqttServer.onConnected subscribes to SshConstants.getSubTopic() and getCmdTopic() = "/android/remote/cmd/frp_%s" (format arg PeanutProfile.getRobotSn()). SshMqttProxy$2.onMessageArrived: topic==getCmdTopic -> APPCmdHelper.inputCmd(new String(payload)); inputCmd: new JSONObject(payload).getString("msg") -> ShellUtil.exeCmd(msg, callback) (-> exeCmd(cmd,false,cb) -> sh) ; reply on /android/remote/cmd_resp/frp_%s. SshMqttServer URL: preference local_mqtt_url or default tcp://console.peanut.keenonrobot.com (prod) / tcp://t-cloud.ikeenon.com (eng), no port; credentials from CipherUtil.get("peanut_mqtt_u"/"peanut_mqtt_p").',
    'CONFIRMED', 'HIGH',
    'Whoever can publish to the per-serial command topic can run arbitrary shell commands on the tablet. Protection is solely the broker authentication/ACL; the same native-stored credentials are embedded in every copy of the app (recoverable offline, INFERRED), the topic suffix is the robot serial number, and the transport is plain tcp:// (cleartext) in the default URL. A shared-credential or broker misconfiguration would affect the whole fleet.',
    'Use per-device credentials and broker ACLs; TLS with pinned CA; sign and expire commands (server signature verified on device); restrict to an allow-listed command set; enable only on explicit maintenance sessions.',
    'This is a vendor remote-maintenance design (feature gated by region CN via SshMqttProxy.isShowFrpSsh). The finding is about the trust model, not an observed compromise. Broker-side controls were not assessed.',
    STATIC + 'decompile SshMqttProxy$2.onMessageArrived and APPCmdHelper.inputCmd; read SshConstants topic builders.')
add('SEC', 'adb over TCP 5678 enabled at every initialisation; cloud config toggles USB adb and tunnels port 5678', 'High',
    'com.keenon.systemservice.init.SystemInitializer.init (ServiceSystem); FrpClientHelper (RemoteControl)',
    f'{SS} JADX SystemInitializer.init: on release builds with fusion board: setprop sys.secure.re disable; setprop sys.usb.config none; start remote_control_oray CommonService; then unconditionally ShellUtil.exeCmd("setprop service.adb.tcp.port 5678\\nstop adbd\\nstart adbd"). {RC} JADX FrpClientHelper.startFrpClient: exeCmdSync("setprop sys.secure.re enable\\nsetprop sys.usb.config adb"), writes frpc.ini with local_ip=127.0.0.1, local_port=5678, token and server from the MQTT message, starts "frpc -c". OTA metadata in update.zip: build fingerprint ends userdebug/test-keys.',
    'CONFIRMED', 'HIGH',
    'adbd listens on TCP 5678 on every boot (reachable from the robot LAN if adbd accepts connections) and the vendor tunnel forwards that port to a cloud FRP server on demand. Impact depends on adbd authentication (ro.adb.secure) and whether adbd runs as root on the userdebug image: UNKNOWN. Potentially full device control for anyone on the LAN or holding the FRP/tunnel parameters.',
    'Do not enable adb TCP in release; if needed, require adb key authentication and a time-boxed maintenance toggle; bind adbd to loopback and tunnel only with mutual auth.',
    'Local port 5678 appears in both apps, linking init (adbd TCP) with the tunnel (INFERRED link). The vendor intent is remote maintenance.',
    STATIC + 'decompile SystemInitializer.init and FrpClientHelper.startFrpClient; compare the port constants.')
add('SEC', 'Hard-coded SMB credentials (root, two-password list) for the ROS host', 'High',
    'com.keenon.peanut.uploadlog.smb.SMBInterface.PASS_LIST / SMBManager (RemoteControl)',
    f'{RC}: SMBInterface.<clinit> stores a 2-element String[] PASS_LIST (lengths 11 and 6; values withheld); SMBManager.lambda$init$0 connects SMBClient to "192.168.64.20" and authenticates with user "root", domain "DOMAIN", password PASS_LIST[passIndex] with retry.',
    'CONFIRMED', 'HIGH',
    'Root-level SMB access credentials to the robot-side host are recoverable from the APK of any robot of this firmware family and likely identical fleet-wide. Gives read access to ROS logs/bags/maps/config (and possibly write, depending on share permissions: UNKNOWN).',
    'Remove embedded credentials; provision per-device secrets; avoid root SMB users; restrict the share to read-only log directories and to the tablet interface.',
    'Used by the log-upload workflow to download robot logs from the ROS side before uploading to the cloud.',
    STATIC + 'disassemble SMBInterface.<clinit> and SMBManager.lambda$init$0; do not attempt to authenticate.')
add('SEC', 'Hard-coded LLM provider API keys in code (Moonshot Kimi, DashScope/DeepSeek)', 'High',
    'DeepseekChatApi.initUrlConnectionAuth, KimiChatApi.initUrlConnectionAuth (ServiceSystem)',
    f'{SS}: secretscan.py (type: bearer-token / sk-style key; lengths 42 and 58; values withheld) located in com/keenon/systemservice/voice/ctrl/nlp/deepseek/DeepseekChatApi#initUrlConnectionAuth and .../kimi/KimiChatApi#initUrlConnectionAuth; sibling methods set URLs https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions (model deepseek-v3) and https://api.moonshot.cn/v1/chat/completions (model moonshot-v1-8k).',
    'CONFIRMED', 'HIGH',
    'Keys are extractable by anyone with the APK: quota theft, cost abuse, possible access to provider-side data; one key set