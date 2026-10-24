"""Generate system_apps_artifacts.json, _endpoints.json, _ros_refs.json. Read-only on inputs."""
import json, os, zipfile, hashlib

ROOT = r'D:\Sakar Robotics Projects\C40 S-LS-M014C00-RW-F00-V246'
OUT = os.path.join(ROOT, 'REVERSE_ENGINEERING_RESULTS', 'partials')
SC = r'C:\Users\sakar\AppData\Local\Temp\claude\D--Sakar-Robotics-Projects-support-portal\5ba6b382-e7e8-4ce5-92df-45edd177f75a\scratchpad'
APKS = {
 'ServiceSystem': 'ServiceSystem-v2.0.11-0-g94566e5-prod-release-p-rk3288-signed.apk',
 'RemoteControl': 'RemoteControl-v3.3.1-0-gfe68b6d-prod-release-p-rk3288-signed.apk',
 'krlog': 'krlog-v1.2.6-0-g18a40d0-prod-release-p-rk3288-signed.apk',
}
inv = json.load(open(os.path.join(SC, 'sysout', 'so_inventory.json'), encoding='utf-8'))

SO_NOTES = {
 'libcipher_pack.so': 'JNI CipherUtil.get(Context,String): per-package native key/credential lookup table (labels only, values not extracted)',
 'libnativetool.so': 'JNI KNNativeTool.nativeCmd; imports socket/sendto/recvfrom (local command proxy, INFERRED); used by FrpClientHelper.handleNginxFile',
 'lib3Des.so': 'JNI CtrlDesUtil.encrypt (3DES)',
 'libpatchutils.so': 'JNI PatchUtil.bspatch (bsdiff patch + bzip2)',
 'libmmkv.so': 'Tencent MMKV key-value store native core',
 'libdaemon_api20.so': 'marsdaemon native process keep-alive JNI NativeDaemonAPI20.doDaemon',
 'libdaemon_api21.so': 'marsdaemon native keep-alive JNI NativeDaemonAPI21.doDaemon',
 'liborayservice_sdk.so': 'Oray Sunlogin remote-service SDK (25 JNI exports); protocol not analyzed',
 'liborayinput_agent.so': 'Oray input-injection agent, launched from /data/local/tmp',
 'liborayinput_agent5.so': 'Oray input-injection agent variant',
 'liborayscreen_agentn.so': 'Oray screen-capture agent (links libgui/libskia/libbinder/libui)',
 'liblog.so': 'stub liblog bundled as an asset',
 'libK18SttyCommInterface.so': 'Rokid K18 mic-array JNI K18SttyCommAPI',
 'libsentry.so': 'Sentry native crash SDK',
 'libsentry-android.so': 'Sentry Android NDK JNI bridge',
 'libliteavsdk.so': 'Tencent LiteAV SDK (networking, ffmpeg)',
 'libtxffmpeg.so': 'Tencent bundled FFmpeg',
 'libtxsoundtouch.so': 'SoundTouch audio processing',
 'libmsc.so': 'iFlytek MSC speech SDK (mbedtls, sockets)',
 'libaiui.so': 'iFlytek AIUI SDK',
 'libvtn.so': 'iFlytek VTN voice front-end',
 'libhlw.so': 'iFlytek wake-word engine',
 'libvadLib.so': 'VAD library', 'libydvad.so': 'VAD library',
 'libcae-jni.so': 'iFlytek CAE (AEC/beamforming) JNI', 'libalsa-jni.so': 'tinyalsa JNI mic capture',
 'libnative-lib.so': 'JNI Recorder.webRtcVadProcess (WebRTC VAD)',
 'librobotdevice.so': 'JNI RobotDeviceNative.getDeviceId (AES helper symbols)',
 'libGVoiceUtils.so': 'Tencent GVoice utils',
 'libmygvoice.so': 'Tencent GVoice wrapper',
 'liblzma.so': 'xz/lzma decompression library',
 'daemon': 'marsdaemon native daemon ELF asset',
}

def addrec(lst, rel, analyzed, method, findings, status, limits):
    lst.append({'relative_path': rel, 'analyzed': analyzed, 'method': method, 'findings': findings, 'status': status, 'limitations': limits})

arts = []
for app, f in APKS.items():
    p = os.path.join(ROOT, f)
    z = zipfile.ZipFile(p)
    names = z.namelist()
    dexes = sorted(n for n in names if n.endswith('.dex') and '/' not in n)
    addrec(arts, f, True, 'unzip + v1 cert parse (certinfo.py) + manifest decode (axml.py)',
           'Platform system app, sharedUserId android.uid.system; see system_apps.md section for this APK', 'CONFIRMED', 'No apksigner/v2-v4 block parser used')
    for d in dexes:
        addrec(arts, '%s!/%s' % (f, d), True, 'dexscan.py per-method index (strings/invokes/fields) + dexdis.py disassembly; JADX/REA decompile for selected high-value methods',
               'See system_apps.md; class/method-level citations in system_apps_findings.json', 'STRONGLY_SUPPORTED',
               'Most classes reviewed at bytecode/string level only, not fully decompiled')
    for n in sorted(names):
        base = n.rsplit('/', 1)[-1]
        if n.startswith('lib/') and n.endswith('.so'):
            addrec(arts, '%s!/%s' % (f, n), True, 'elfinfo.py (sections/dynsym/needed) + soinv.py (string/vendor-marker scan)',
                   SO_NOTES.get(base, 'native library, see system_apps_artifacts so_inventory'), 'STRONGLY_SUPPORTED',
                   'No disassembly of native code; exports/imports/strings only')
        elif n.startswith('assets/') and base.endswith(('.json', '.gson')):
            addrec(arts, '%s!/%s' % (f, n), True, 'read as JSON/text', 'config asset, see system_apps.md section 2.6/2.11', 'CONFIRMED', '-')
        elif base in ('ca.bks', 'client.bks', 'eng_ca.bks', 'eng_client.bks', 'dev_ca.bks', 'dev_client.bks'):
            addrec(arts, '%s!/%s' % (f, n), False, 'identified by path/extension only', 'BKS keystore for MQTT/TLS client cert pinning (ServiceSystem/RemoteControl)', 'NOT_ANALYZED', 'BKS format not parsed; no tool available in this environment')
        elif base.endswith('.pem'):
            addrec(arts, '%s!/%s' % (f, n), True, 'read PEM header', 'RSA key pair for VTN voice auth (ServiceSystem assets/vtn)', 'CONFIRMED', 'Key material not validated/used')
        elif base == 'resources.arsc':
            addrec(arts, '%s!/%s' % (f, n), False, 'not parsed (binary resource table)', 'referenced only via @0x7f... ids in manifest/axml output', 'NOT_ANALYZED', 'No ARSC parser available; resource string/int values for @0x7f... refs are unresolved')
        elif n.startswith('META-INF/') and base in ('CERT.RSA', 'CERT.SF', 'MANIFEST.MF'):
            addrec(arts, '%s!/%s' % (f, n), True, 'certinfo.py (CERT.RSA) / text read (SF, MF)', 'signing certificate / digest manifest, see system_apps.md 5.1', 'CONFIRMED', '-')
        elif base == 'RKUpdateService_v1.8.2.apk':
            addrec(arts, '%s!/%s' % (f, n), True, 'unzip + axml.py manifest decode', 'Rockchip OTA installer helper, package android.rockchip.update.service; exported RKUpdateService + exported Setting activity', 'CONFIRMED', 'classes.dex of this nested APK not separately scanned with dexscan')
        elif base == 'update.zip':
            addrec(arts, '%s!/%s' % (f, n), True, 'unzip + read updater-script/metadata/shell scripts', 'OTA package: target rk3288/Android 6.0.1 userdebug test-keys; replaces SystemUI.apk, patches build.prop, installs rm_slog.sh/set_video_perm.sh', 'CONFIRMED', 'update-binary (541KB) and SystemUI.apk payload not analyzed')
        elif base == 'speedTest.zip':
            addrec(arts, '%s!/%s' % (f, n), True, 'file-signature check', 'actually a RAR archive despite .zip extension, used for upload speed test', 'CONFIRMED', 'Contents not extracted (RAR, no tool)')
    arts.append  # noop keep lints quiet

json.dump(arts, open(os.path.join(OUT, 'system_apps_artifacts.json'), 'w', encoding='utf-8'), indent=1)
print('artifacts', len(arts))

endpoints = [
 {'kind': 'mqtt_broker', 'value': 'ssl://console.peanut.keenonrobot.com:8883 (prod, non-CN region)', 'source': 'ServiceSystem DynamicMqttServer.getBaseUrl + assets/domain/domain.json', 'status': 'STRONGLY_SUPPORTED', 'confidence': 'MEDIUM'},
 {'kind': 'mqtt_broker', 'value': 'tcp://console.peanut.keenonrobot.com (prod, CN region, default TLS-off path)', 'source': 'ServiceSystem DynamicMqttServer / PeanutMqttServer.getBaseUrl', 'status': 'STRONGLY_SUPPORTED', 'confidence': 'MEDIUM'},
 {'kind': 'mqtt_broker', 'value': 'ssl://<region>-cloud.ikeenon.com:18883 or :21884 (eng/pre/dev, non-CN)', 'source': 'DynamicMqttServer.getBaseUrl disassembly', 'status': 'CONFIRMED', 'confidence': 'HIGH'},
 {'kind': 'mqtt_broker', 'value': 'tcp://console.peanut.keenonrobot.com (RemoteControl SshMqttServer default, prod)', 'source': 'RemoteControl SshConstants.getMqttUrl', 'status': 'CONFIRMED', 'confidence': 'HIGH'},
 {'kind': 'mqtt_broker', 'value': 'tcp://t-cloud.ikeenon.com (RemoteControl SshMqttServer, eng)', 'source': 'RemoteControl SshConstants.getMqttUrl', 'status': 'CONFIRMED', 'confidence': 'HIGH'},
 {'kind': 'mqtt_broker', 'value': 'ssl:// mqtt-frp stats broker via ClientSSLSocketFactory (host from RemotePeanutServer.getStatsMqtt)', 'source': 'RemoteControl RemotePeanutServer.getStatsMqtt', 'status': 'STRONGLY_SUPPORTED', 'confidence': 'MEDIUM'},
 {'kind': 'mqtt_credentials', 'value': 'username/password resolved via CipherUtil.get(peanut_mqtt_u/peanut_mqtt_p[_prod]) [REDACTED]', 'source': 'ServiceSystem/RemoteControl shared peanut.core.mqtt code', 'status': 'CONFIRMED', 'confidence': 'HIGH'},
 {'kind': 'mqtt_topic', 'value': '/android/remote/cmd/frp_<robotSN>', 'source': 'RemoteControl SshConstants.getCmdTopic (remote shell command channel, see SYS-005)', 'status': 'CONFIRMED', 'confidence': 'HIGH'},
 {'kind': 'mqtt_topic', 'value': '/android/remote/cmd_resp/frp_<robotSN>', 'source': 'RemoteControl SshConstants.getCmdRespTopic', 'status': 'CONFIRMED', 'confidence': 'HIGH'},
 {'kind': 'mqtt_topic', 'value': 'robot/%s/dev_config/ros/remote-ssh/v1/cloud_msg and /dev_msg', 'source': 'RemoteControl strings table', 'status': 'CONFIRMED', 'confidence': 'HIGH'},
 {'kind': 'mqtt_topic', 'value': '/dev_config/cloud_config/hotel/v1/cloud_msg', 'source': 'RemoteControl strings table', 'status': 'CONFIRMED', 'confidence': 'MEDIUM'},
 {'kind': 'mqtt_topic', 'value': '/remote-maintenance/network_tunnel/cloud_msg and /dev_msg', 'source': 'RemoteControl strings table', 'status': 'CONFIRMED', 'confidence': 'MEDIUM'},
 {'kind': 'mqtt_topic', 'value': '/dev_data/sys_log/notify/v1/server', 'source': 'RemoteControl RemoteMsgManager.handlerMqttMsg (log notify trigger)', 'status': 'CONFIRMED', 'confidence': 'HIGH'},
 {'kind': 'mqtt_topic', 'value': '/dev_data/sys_log/notify_upload/v1/server', 'source': 'RemoteControl RemoteMsgManager.handlerMqttMsg (log upload trigger)', 'status': 'CONFIRMED', 'confidence': 'HIGH'},
 {'kind': 'mqtt_topic', 'value': '/devops_common/v1/cloud_client and /dev_client', 'source': 'RemoteControl strings table', 'status': 'CONFIRMED', 'confidence': 'MEDIUM'},
 {'kind': 'mqtt_topic', 'value': '/device/%s/ioe_mqtt_conn/v1/cloud_client', 'source': 'ServiceSystem MqttServerHelper.subscribeEmqChangeMsg / RemoteControl AppStateManager', 'status': 'CONFIRMED', 'confidence': 'HIGH'},
 {'kind': 'mqtt_topic', 'value': '/robot/ , /common/server/ , /server/push/ , /client/push/', 'source': 'RemoteControl common.mqtt.manager strings', 'status': 'CONFIRMED', 'confidence': 'MEDIUM'},
 {'kind': 'http_api_base', 'value': 'console.peanut.keenonrobot.com (prod) / p-cloud.ikeenon.com (pre) / t-cloud.ikeenon.com (eng) / d-cloud.ikeenon.com (dev)', 'source': 'ServiceSystem+RemoteControl assets/domain/domain*.json', 'status': 'CONFIRMED', 'confidence': 'HIGH'},
 {'kind': 'http_api_path', 'value': '/api/app/token/getSingleToken', 'source': 'RemoteControl strings', 'status': 'CONFIRMED', 'confidence': 'HIGH'},
 {'kind': 'http_api_path', 'value': '/api/app/encrypt/v1, /api/app/encrypt/random/v1, /api/app/encrypt/aes/safe/v1', 'source': 'RemoteControl strings', 'status': 'CONFIRMED', 'confidence': 'HIGH'},
 {'kind': 'http_api_path', 'value': '/api/app/mqtt/secret/v1', 'source': 'RemoteControl strings (MQTTConnectManager)', 'status': 'CONFIRMED', 'confidence': 'HIGH'},
 {'kind': 'http_api_path', 'value': '/api/app/robot/info/v2, /checkNewSoftware/v2, /software/package/info[/v2], /version/status/v2, /log/types/v2', 'source': 'RemoteControl strings', 'status': 'CONFIRMED', 'confidence': 'HIGH'},
 {'kind': 'http_api_path', 'value': '/api/app/robot/remote_log/v1/file_list, /upload, /upload_status', 'source': 'RemoteControl strings (log upload)', 'status': 'CONFIRMED', 'confidence': 'HIGH'},
 {'kind': 'http_api_path', 'value': '/api/remote/sunlogin/device/session/v2, /status/v2, /service/secret/v2, /device/speed/test_url', 'source': 'RemoteControl strings (Oray session mgmt)', 'status': 'CONFIRMED', 'confidence': 'HIGH'},
 {'kind': 'http_api_path', 'value': '/api/hotel_app/cloud/config/v1', 'source': 'RemoteControl strings', 'status': 'CONFIRMED', 'confidence': 'MEDIUM'},
 {'kind': 'http_endpoint', 'value': 'https://iflybot.com.cn/service/sit/manager/v1/device/getLocation', 'source': 'ServiceSystem strings', 'status': 'CONFIRMED', 'confidence': 'MEDIUM'},
 {'kind': 'http_endpoint', 'value': 'http://service.iflybot.com.cn (gatewayUrl), ws://robot.iflybot.com.cn/robot/engine/v2_2', 'source': 'ServiceSystem assets/robot.json, robot_test.json', 'status': 'CONFIRMED', 'confidence': 'HIGH'},
 {'kind': 'http_endpoint', 'value': 'http://robotnlp.keenonrobot.com:80, http://corrector.keenonrobot.com:80 (and t- test variants)', 'source': 'ServiceSystem strings', 'status': 'CONFIRMED', 'confidence': 'HIGH'},
 {'kind': 'http_endpoint', 'value': 'http://iss.openspeech.cn/v, http://openapi.openspeech.cn/webapi/wfr.do, http://wke.openspeech.cn/wakeup/, https://open.xf-yun.com/msp.do, https://log.iflytek.com/log, https://logconf.iflytek.com/hotupdate', 'source': 'ServiceSystem strings (iFlytek cloud SDK)', 'status': 'CONFIRMED', 'confidence': 'HIGH'},
 {'kind': 'http_endpoint', 'value': 'https://api.moonshot.cn/v1/chat/completions (Kimi LLM)', 'source': 'ServiceSystem KimiChatApi', 'status': 'CONFIRMED', 'confidence': 'HIGH'},
 {'kind': 'http_endpoint', 'value': 'https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions (DeepSeek via DashScope)', 'source': 'ServiceSystem DeepseekChatApi', 'status': 'CONFIRMED', 'confidence': 'HIGH'},
 {'kind': 'http_endpoint', 'value': 'https://<resource>.openai.azure.com/openai/deployments/%s/chat/completions, https://<region>.api.cognitive.microsoft.com (8 regions)', 'source': 'ServiceSystem assets/voiceConfig/micro_resource.json', 'status': 'CONFIRMED', 'confidence': 'HIGH'},
 {'kind': 'http_endpoint', 'value': 'https://applog.iotcloud.tencentiotcloud.com/api/xp2p_ops/applog ; room://cloud.tencent.com/rtc ; room://rtc.tencent.com', 'source': 'ServiceSystem Tencent LiteAV/GVoice strings', 'status': 'CONFIRMED', 'confidence': 'MEDIUM'},
 {'kind': 'telemetry_endpoint', 'value': 'http://<REDACTED_KEY>@sentry.keenonrobot.com/12 (Sentry DSN, cleartext)', 'source': 'ServiceSystem AndroidManifest.xml meta-data io.sentry.dsn', 'status': 'CONFIRMED', 'confidence': 'HIGH'},
 {'kind': 'ftp_frp_endpoint', 'value': 'frp.keenonrobots.com (latency ping target; actual FRP server/port/token delivered via MQTT frpConfig)', 'source': 'RemoteControl AppStateManager.getFrpDelayTime', 'status': 'CONFIRMED', 'confidence': 'HIGH'},
 {'kind': 'local_socket_or_host', 'value': '192.168.64.20 (SMB share root + rosbridge-style ws://192.168.64.20:9013)', 'source': 'RemoteControl SMBManager, WebClientManager', 'status': 'CONFIRMED', 'confidence': 'HIGH'},
 {'kind': 'smb_credentials', 'value': 'user root, password from SMBInterface.PASS_LIST[0..1] [REDACTED: 2 hard-coded strings]', 'source': 'RemoteControl SMBManager.lambda$init$0', 'status': 'CONFIRMED', 'confidence': 'HIGH'},
 {'kind': 'adb_tcp_port', 'value': '5678 (service.adb.tcp.port, set at ServiceSystem init; also frpc local_port)', 'source': 'ServiceSystem SystemInitializer.init; RemoteControl FrpClientHelper.buildFrpClientConfig', 'status': 'STRONGLY_SUPPORTED', 'confidence': 'MEDIUM'},
 {'kind': 'websocket', 'value': 'ws://10.40.53.203/robot/engine (string constant, likely stale/dev default)', 'source': 'ServiceSystem strings', 'status': 'HISTORICAL', 'confidence': 'LOW'},
 {'kind': 'api_key_location', 'value': 'ServiceSystem assets/voiceConfig/micro_resource.json: 8x openaiKey + 8x speechKey [REDACTED]', 'source': 'asset file', 'status': 'CONFIRMED', 'confidence': 'HIGH'},
 {'kind': 'api_key_location', 'value': 'ServiceSystem KimiChatApi/DeepseekChatApi: hard-coded Bearer sk-... keys [REDACTED]', 'source': 'DEX string table', 'status': 'CONFIRMED', 'confidence': 'HIGH'},
 {'kind': 'api_key_location', 'value': 'ServiceSystem assets/robot.json / robot_test.json: appId+secret for iFlytek robot gateway [REDACTED]', 'source': 'asset file', 'status': 'CONFIRMED', 'confidence': 'HIGH'},
 {'kind': 'private_key_location', 'value': 'ServiceSystem assets/vtn/privateKey.pem (RSA private key, 1700 bytes) [REDACTED]', 'source': 'asset file', 'status': 'CONFIRMED', 'confidence': 'HIGH'},
]
json.dump(endpoints, open(os.path.join(OUT, 'system_apps_endpoints.json'), 'w', encoding='utf-8'), indent=1)
print('endpoints', len(endpoints))

ros_refs = [
 {'kind': 'host', 'value': '192.168.64.20 (SMB share presumed to be the ROS/robot compute side; WebSocket port 9013 also here)', 'source': 'RemoteControl SMBManager / WebClientManager strings', 'status': 'STATIC_REFERENCE', 'confidence': 'MEDIUM'},
 {'kind': 'mqtt_topic', 'value': 'robot/%s/dev_config/ros/remote-ssh/v1/{cloud_msg,dev_msg}', 'source': 'RemoteControl strings table', 'status': 'STATIC_REFERENCE', 'confidence': 'HIGH'},
 {'kind': 'filesystem_path', 'value': '/oem/etc/ros/runtime/remotemap (nginx map config deploy target)', 'source': 'RemoteControl FrpClientHelper.handleNginxFile', 'status': 'STATIC_REFERENCE', 'confidence': 'HIGH'},
 {'kind': 'filesystem_path', 'value': '/oem/etc/nginx/conf.d, /system/bin/ubuntu1604 (nginx reload inside an Ubuntu 16.04 userland)', 'source': 'RemoteControl FrpClientHelper.handleNginxFile', 'status': 'STATIC_REFERENCE', 'confidence': 'HIGH'},
 {'kind': 'filesystem_path', 'value': 'robot/work/robot/Bin/Arm-Release/, robot/var, ros/home, ros/config (algorithm/ROS log roots)', 'source': 'RemoteControl AlgorithmLogPath.<clinit>', 'status': 'STATIC_REFERENCE', 'confidence': 'HIGH'},
 {'kind': 'filesystem_path', 'value': '/keenon/{bag,idlebag,log,console,schedule,fdl_data}/, krlog, krlog/android, krlog/robot, krlog/database (log-collection roots, some ROS-produced)', 'source': 'RemoteControl AlgorithmLogPath.<clinit>', 'status': 'STATIC_REFERENCE', 'confidence': 'HIGH'},
 {'kind': 'property', 'value': 'getprop robot.keenbot.ver (== "2" selects "fusion board" log/path behavior)', 'source': 'krlog SystemInitHelper.initFusionBoard; ServiceSystem init path', 'status': 'STATIC_REFERENCE', 'confidence': 'HIGH'},
 {'kind': 'data_file', 'value': 'peanut.db (referenced log/data artifact name, likely the robot-side business data store)', 'source': 'RemoteControl AlgorithmLogManager strings', 'status': 'STATIC_REFERENCE', 'confidence': 'LOW'},
 {'kind': 'note', 'value': 'No ROS topic names, serial device nodes, or motion/navigation command strings were found in ServiceSystem, RemoteControl or krlog. The link to the robot is via file/SMB/MQTT bridging to a separate ROS/Ubuntu compute unit, not a direct ROS client library in these APKs.', 'source': 'absence of rclcpp/rclpy/roscpp/ros::NodeHandle style strings or classes in all three dex sets', 'status': 'NOT_ANALYZED', 'confidence': 'LOW'},
]
json.dump(ros_refs, open(os.path.join(OUT, 'system_apps_ros_refs.json'), 'w', encoding='utf-8'), indent=1)
print('ros_refs', len(ros_refs))
