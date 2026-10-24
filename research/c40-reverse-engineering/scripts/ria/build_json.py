import json,sys,re,zipfile,hashlib,os,collections
sys.stdout.reconfigure(encoding='utf-8',errors='replace')
B=sys.argv[1]
OUT=r"D:\Sakar Robotics Projects\C40 S-LS-M014C00-RW-F00-V246\REVERSE_ENGINEERING_RESULTS\partials"
APKN='robot-installation-assistant-v4.11.0-0-gb7c7c7b-prod-release-p.apk'
APK=r"D:\Sakar Robotics Projects\C40 S-LS-M014C00-RW-F00-V246"+'\\'+APKN
z=zipfile.ZipFile(APK)
raw=open(APK,'rb').read()
nat=json.load(open(B+'/ria/native_info.json'))
cmds=json.load(open(B+'/ria/sdk_commands.json'))
tbl=json.load(open(B+'/ria/js_ros_table.json'))
D=json.load(open(B+'/ria/keenon_deep.json',encoding='utf-8'))
allc=json.load(open(B+'/ria/allclasses.json'))
dexcount=collections.Counter(c['dex'] for c in allc)
def sha(b): return hashlib.sha256(b).hexdigest()
# ------------------------------------------------ artifacts
art=[]
def A(rel,analyzed,method,findings,status,lim,embedded=True):
    art.append({'relative_path':(APKN+'!/'+rel) if embedded else rel,'analyzed':analyzed,'method':method,'findings':findings,'status':status,'limitations':lim})
A(APKN,True,'Python stdlib ZIP/AXML/DEX/ELF parsers (own code), manifest decode, string and bytecode-trace analysis; MD5 previously verified vs vendor upgradeConfig.json (by coordinator); sha256='+sha(raw),
  'Chromium-M73-based Keenon Mapping Tool / robot installation assistant v4.11.0 (versionCode 350, prod/release, targetSdk 22, minSdk 21), embeds peanut-sdk 2.2.6rc3-beta24; self-signed cert CN=keenon valid 2017-2042; v1+v2 signature present.','CONFIRMED','No JADX/REA decompilation (JADX engine hash check failed: JADX_BAD marker); no dynamic analysis; signing block not fully verified cryptographically.',embedded=False)
for n in sorted(z.namelist()):
    if re.fullmatch(r'classes\d*\.dex',n):
        A(n,True,'Own DEX parser: class/method/field tables, const-string, invoke and field references, annotations, encoded static values','%d class definitions; %s'%(dexcount[n.replace('.dex','')] if False else sum(1 for c in allc if c['dex']==n.replace('.dex','')) ,'contains first-party com.keenon.* and third-party (Chromium, GMS, BouncyCastle, RxJava, Paho, fastjson, Jackson, Room, okhttp, ...)'),'CONFIRMED','No decompiled source; register-level data flow not modelled (call/string traces only); obfuscated names (a/b/c) in some peanut-core classes.')
for f,r in nat.items():
    notes={'libchrome.so':'Chromium 73.0.3683.93 browser engine (50.5 MB, ARM32, stripped, only JNI_OnLoad exported). Outdated.',
      'libcipher_pack.so':'Native secret/cipher-map store. JNI export Java_com_keenon_peanut_core_cipher_CipherUtil_get(Context,String). Contains key-id names (ros_ssh_pass, ros_pass1/2, system_pin_code, app_pin_code, peanut_mqtt_u/p, ssl_key/trust pwd, aes_register_key/iv, mmkv_encrypt_key, token_secret, md5_secret_key, offline_work_code_key, umeng/bugly/amap/google keys) and cipherMap{Common,Disinfect,FoodDelivery,Hotel,MedicalDelivery}. Values NOT extracted.',
      'libkeenon_serial.so':'Serial port JNI: SerialPort.open/close (used by com.keenon.sdk.serial.internal.SerialPort; /dev/ttyS*, /dev/ttyUSB*).',
      'libpatchutils.so':'bsdiff patch JNI: PatchUtil.bspatch (com.keenon.peanut.core.utils.patch.PatchUtil).',
      'libmmkv.so':'Tencent MMKV key-value store native library (encrypted storage with mmkv_encrypt_key from cipher pack).',
      'libsentry.so':'Sentry native (sentry-native/breakpad) library.','libsentry-android.so':'Sentry Android NDK JNI bridge.',
      'libcrashpad_handler.so':'Chromium crashpad handler executable packaged as library.','libchromium_android_linker.so':'Chromium Android relro linker.',
      'libarcore_sdk_c.so':'Google ARCore SDK (bundled by Chromium VR/AR support).','libarcore_sdk_jni.so':'ARCore JNI bridge (139 JNI exports).','libgvr.so':'Google VR (Cardboard/Daydream) library bundled by Chromium.'}
    A('lib/armeabi-v7a/'+f,True,'Own ELF parser: header, needed libs, dynsym exports/imports, strings (names only for secret-store); sha256='+r['sha256'],
      '%s ELF32 ARM, %d bytes, stripped=%s, NEEDED=%s, JNI exports=%d. %s'%(r['machine'],r['size'],r['stripped'],','.join(r['needed']),len(r['jni']),notes.get(f,'')),'CONFIRMED','No disassembly; behaviour inferred from exports/imports/strings only; arm32 only.')
A('resources.arsc',True,'Size/presence check; resource names inferred from res/ file names and manifest ids (arsc not fully decoded)','Resource table 2.7 MB; manifest resource refs (@0x7f...) unresolved (theme, label, networkSecurityConfig=@0x7f170019 presumed res/xml/network_security_config.xml).','STRONGLY_SUPPORTED','arsc string/value tables not decoded.')
A('AndroidManifest.xml',True,'axml.py binary XML decode','210 components (87 activity, 83 service, 18 receiver, 15 activity-alias, 7 provider); 29 effectively exported; 62 uses-permission entries (50 unique); metadata keys (amap api key, sentry dsn) redacted in reports.','CONFIRMED','Resource references unresolved.')
for sf,desc in [('META-INF/CERT.RSA','PKCS#7 signature block: self-signed cert CN=keenon, sha256WithRSA, validity 2017-08-03..2042-07-28.'),('META-INF/CERT.SF','JAR signature file, SHA-256 digests; X-Android-APK-Signed: 2 (v2 present).'),('META-INF/MANIFEST.MF','JAR manifest (Created-By Android Gradle 4.1.2; Built-By Signflinger).')]:
    A(sf,True,'Text/DER inspection',desc,'CONFIRMED','Cryptographic verification of signature not performed.')
A('(APK Signing Block)',True,'Byte search for "APK Sig Block 42"','APK Signing Block magic present (v2 and possibly v3 scheme).','STRONGLY_SUPPORTED','Block contents not parsed.')
for n in sorted(z.namelist()):
    if n.startswith('META-INF/services/'):
        c=z.read(n).decode('utf-8','replace').strip().replace('\n',' | ')
        A(n,True,'Read','Service provider registration: '+c[:200],'CONFIRMED','')
for n in ['META-INF/native-image/io.sentry/sentry/native-image.properties','META-INF/native-image/io.sentry/sentry/reflect-config.json']:
    if n in z.namelist(): A(n,True,'Listed','GraalVM native-image metadata of Sentry (no runtime relevance).','CONFIRMED','')
# kotlin modules & version files summary
km=[n for n in z.namelist() if n.endswith('.kotlin_module')]
A('META-INF/*.kotlin_module ('+str(len(km))+' files)',True,'Names inspected','Module names reveal first-party Gradle modules: '+', '.join(sorted(os.path.basename(n).replace('.kotlin_module','') for n in km if 'release' in n)),'CONFIRMED','')
vf=[n for n in z.namelist() if n.startswith('META-INF/') and n.endswith('.version')]
A('META-INF/*.version ('+str(len(vf))+' files)',True,'Read','AndroidX/support library version stamps (room-runtime 2.3.0, core 1.3.2, appcompat 1.1.0, lifecycle-runtime 2.2.0, material 1.0.0-rc01 ...).','CONFIRMED','')
# assets
A('assets/ros/dist/index.html',True,'Read','Vite SPA entry (title Keenon, module script /assets/index-32d09e94.js); served by AndServer on :8080.','CONFIRMED','')
A('assets/ros/dist/assets/index-32d09e94.js',True,'Regex/string extraction on minified bundle (4.0 MB): routes, i18n, ROS topic table, REST paths, WS URLs','Vue3+Element-Plus SPA core: routes /, /install; rosbridge (roslib/EventEmitter2), cloud REST /api/tool_support/*, /api/app/*, logger WS :8899, app WS :8888; ROS topic/service table (140 entries); SheetJS, JSZip, lodash.','CONFIRMED','Minified; no sourcemap; component logic only sampled; Home chunk loaded lazily.')
A('assets/ros/dist/assets/Home-0ebbc05a.js',True,'Regex/string extraction (2.9 MB)','Main UI chunk: rosbridge client (ws://192.168.64.20:9090/9091 or ws://<host>:8888?port=PC9090/PC9091), three.js/zrender/QR code, manual /cmd_vel drive, map editing UI, QR for remote control (http://<localHost>/#/install?remote=true), vConsole.','CONFIRMED','Minified.')
A('assets/ros/dist/assets/worker-6b29a5b0.js',True,'Strings','Web worker (lodash/underscore license) for map processing.','STRONGLY_SUPPORTED','Not analysed further.')
A('assets/ros/dist/assets (CSS, locale de/fr, PNG/SVG/GIF media, 53 files)',True,'Listed','Element-Plus CSS, robot model images (C30, C40, C55, M104, S100, T1-T11, W3, X101), charger images, tutorial SVG art.','CONFIRMED','Images not viewed.')
A('assets/ros/dist/audio/{en,ja,ko,th,zh}',True,'Listed','139 prompt MP3 files (area, charge, scan_map_*, revise_map_*, over_speed, ...) for the mapping guide.','CONFIRMED','Audio not played.')
A('assets/ros/dist/json/{en,ja,ko,th,zh}',True,'Listed','35 Lottie-style JSON animations (charge_position, eraser, lost, reverse, set_table, speed, speed1).','STRONGLY_SUPPORTED','Not parsed.')
A('assets/ros/dist/subtitle/{en,ja,ko,th,zh}',True,'Listed','35 WebVTT subtitle files for tutorial videos.','CONFIRMED','')
A('assets/ros/dist/video (9 files)',True,'Listed','Tutorial videos: charge, glass, hollow, normal, origin, reflect, scan, stair, virtual-record.','CONFIRMED','Not played.')
try:
    zz=zipfile.ZipFile(__import__('io').BytesIO(z.read('assets/ros/dist/empty-database.zip')))
    A('assets/ros/dist/empty-database.zip',True,'Unzipped in memory; sqlite3 inspect','Contains peanut.db (1.31 MB, SQLite3, page_size 1024, 1282 pages of which 1280 free): schema only sqlite_sequence; no user data. Template for fresh DB.','CONFIRMED','Free pages not carved for residual data (3 printable strings only).')
except Exception as e: pass
A('assets/domain/domain.json',True,'Read','Prod cloud domains per region: console.peanut.keenonrobot.com (CN), es-cloud.robotkeenon.com (ES/EU), cloud.robotkeenon.com (JP/other).','CONFIRMED','')
A('assets/domain/domain-pre.json',True,'Read','Pre-production domain p-cloud.ikeenon.com (all regions).','CONFIRMED','')
A('assets/domain/domain-eng.json',True,'Read','Engineering domains t-cloud / t-overseas-cloud / t-overseas2-cloud .ikeenon.com.','CONFIRMED','')
A('assets/domain/domain-dev.json',True,'Read','Dev domains d-cloud / d-overseas-cloud / d-overseas2-cloud .ikeenon.com. Shipped in production APK.','CONFIRMED','')
A('assets/LightConfig.json',True,'JSON parse','Per-robot-model light device configuration (T8, T5 PRO, T3, W3, S100, T10, T5, T9): plateNumber, devs[lightName, lightType, devId, connectType coap/com_usb, transmissionProtocol com_v1_multi/com_v2_multi, ledNumber]. No C40 entry.','CONFIRMED','Used by PeanutLightHalManage (INFERRED).')
A('assets/sensors.json',True,'Read','`{"factories":[]}` (empty sensor-factory config; PeanutConstants.SENSOR_CONFIG).','CONFIRMED','')
A('assets/com.keenon.peanut.peanutservice.andserver',True,'Read','41-byte marker file (AndServer processor module name).','CONFIRMED','')
A('assets/icudtl.dat, resources.pak, chrome_100_percent.pak, natives_blob.bin, snapshot_blob_32.bin, unwind_cfi_32, locales/*.pak',True,'Listed (sizes)','Chromium runtime data: ICU 6.4 MB, V8 natives/snapshot, resources.pak, locales en-US/zh-CN/zh-TW.','CONFIRMED','Not unpacked.')
for n in ['eng_ca','eng_client','eng_cn_ca','eng_cn_client','prod_ca','prod_client','prod_cn_ca','prod_cn_client']:
    b=z.read('res/raw/%s.bks'%n)
    A('res/raw/%s.bks'%n,True,'Size, sha256, BKS header check (magic 0x00000002); contents not opened (password protected, from native store)','BouncyCastle BKS keystore v2, %d bytes, sha256=%s...; %s'%(len(b),sha(b)[:16],'client key/cert (mutual TLS credential) for MQTT' if 'client' in n else 'CA/trust store for MQTT TLS'),'STRONGLY_SUPPORTED','Entries/aliases not enumerated (needs password).')
A('res/raw/empty.wav',True,'Listed','58-byte silent WAV.','CONFIRMED','')
A('res/xml/network_security_config.xml',True,'axml.py','base-config cleartextTrafficPermitted=true; trust-anchors system + user.','CONFIRMED','')
A('res/xml/file_paths.xml, util_code_provider_paths.xml',True,'axml.py','FileProvider path maps; util_code maps whole files/cache/external dirs ("." paths).','CONFIRMED','')
A('res/ (3713 files: layouts, drawables, Chromium prefs xml)',True,'Listed','Mostly Chromium UI resources; first-party app UI is web based.','CONFIRMED','Layouts not analysed one by one.')
A('Push.proto, RobotRestriction.proto',True,'Read','Protobuf definitions: Push{action,data(Any),msgId,timestamp,version}; RobotRestriction{restriction,type,liftStatus} (com.keenon.component.location.proto).','CONFIRMED','')
A('*.ftl (entity, dao, dao-deep, dao-master, dao-session, dao-unit-test, content-provider), freemarker/**',True,'Listed','greenDAO generator FreeMarker templates bundled into release APK (build tooling leak).','CONFIRMED','Not read in detail.')
A('bundle.properties, about.html, LICENSE-junit.txt, junit/**, org/**, google/protobuf/**, kotlin/**, okhttp3/**, com/**, images/ant_logo_large.gif, DebugProbesKt.bin, *.properties (play-services-*)',True,'Listed','Third-party library resource leftovers (Paho bundle.properties, JUnit, protobuf, kotlin builtins, okhttp publicsuffix, play-services version properties).','CONFIRMED','')
A('androidsupportmultidexversion.txt',True,'Listed','MultiDex marker (6 dex files).','CONFIRMED','')
json.dump(art,open(OUT+r'\installation_assistant_artifacts.json','w',encoding='utf-8'),indent=1,ensure_ascii=False)
print('artifacts',len(art))
# ------------------------------------------------ endpoints
ep=[]
def E(kind,value,source,status='STATIC_REFERENCE',conf='HIGH'):
    ep.append({'kind':kind,'value':value,'source':source,'status':status,'confidence':conf})
E('HOST','192.168.64.20','HubConstant.LINUX_IP; PeanutConstants.REMOTE_LINK_PROXY; RosSocketClientManager.ADDRESS; PeanutSDK.initAuth ping target (robot Linux/ROS board)')
E('HOST','192.168.64.10','PeanutConstants.LOCAL_ETHER_IP; SimpleChromeActivity.showWebView default SERVER_IP (Android side of internal Ethernet)')
E('HOST','127.0.0.1','PeanutConstants.LOCAL_LINK_PROXY')
E('URL','ws://192.168.64.20:9090','RosSocketClientManager.init (rosbridge #1 "PC9090"); Home chunk rosIp','STATIC_REFERENCE')
E('URL','ws://192.168.64.20:9091','RosSocketClientManager.init (rosbridge #2 "PC9091"); Home chunk rosCtrlIp')
E('URL','ws://<hostname>:8888 (?port=PC9090|PC9091|PC8888|APP8888|ALL8888)','WebServerSocket.PORT=8888 (java-websocket server, wildcard bind); SPA useRosConnect/ws client; WebServerSocket.onOpen resource-descriptor tags')
E('URL','ws://<hostname>:8899','SocketServerManager.LOG_PORT; WebLogServerManager; SPA remote logger (title "mapping-app")')
E('URL','http://<serverIp>:8080/ (AndServer; routes /, /login POST, /file/upload POST, /file/download GET, /map/pic GET, /video/play GET, /v3/test GET)','AndService.PORT=8080; ApiControllerAdapter; PageControllerAdapter; AppConfig AssetsWebsite(/ros/dist)')
E('URL','http://192.168.64.20:8080/install','SimpleChromeActivity1$InternalIntentDelegate.processUrlViewIntent (dev/test activity)')
E('URL','http://172.16.25.170:8080/#/install','SimpleChromeActivity2$InternalIntentDelegate (hard-coded developer LAN IP)','STATIC_REFERENCE','HIGH')
E('URL','http://localhost:8080/video/play?fileName=<name>','SPA index chunk (tutorial video playback)')
E('URL','http://192.168.64.20:54322','PeanutConstants.HTTP_PORT; SDK OkHttp transport to robot (HTTP link type); upgrade port 34569')
E('URL','coap://192.168.64.20:5683','PeanutConstants.COAP_PORT/COAP_PROTOCOL; SDK PeanutCoapClient')
E('URL','ws://192.168.64.20:22345 (upgrade 12386)','PeanutConstants.WS_PORT / WS_PORT_UPGRADE (SDK http.ws push channel)','STATIC_REFERENCE','MEDIUM')
E('URL','tftp://192.168.64.20:9527','PeanutConstants.TFTP_PORT/TFTP_PROTOCOL (SDK TFTP map/firmware transfer)','STATIC_REFERENCE','MEDIUM')
E('URL','smb://192.168.64.20/home','UDriveHelp.copyToRos -> SMBManager.onSmbPut (jcifs smb1); creds from native cipher pack (ros_name/ros_pass1/ros_pass2) [REDACTED]')
E('SERIAL','/dev/ttyS1,/dev/ttyS2,/dev/ttyS3(921600 baud),/dev/ttyS4,/dev/ttyUSB0,/dev/ttyUSB2,/dev/ttyUSB3,/dev/ttyUSBDoorCtl (default 115200)','PeanutConstants COM*, COM3RATE, DEFAULT_RATE; libkeenon_serial.so')
for d,r in [('console.peanut.keenonrobot.com','PROD CN'),('es-cloud.robotkeenon.com','PROD ES/EU'),('cloud.robotkeenon.com','PROD JP/other'),('p-cloud.ikeenon.com','PRE'),('t-cloud.ikeenon.com','ENG CN'),('t-overseas-cloud.ikeenon.com','ENG JP'),('t-overseas2-cloud.ikeenon.com','ENG ES'),('d-cloud.ikeenon.com','DEV CN (also d-overseas-cloud / d-overseas2-cloud in assets)'),('t-us-cloud.ikeenon.com','ENG US (SPA)'),('us-cloud.robotkeenon.com','PROD US (SPA)'),('cloud.keenonrobot.com','SPA domain list')]:
    E('HOST',d,'PsvConstants / ServerManager / assets/domain/*.json / SPA Home chunk: '+r)
for d in ['t-portal.ikeenon.com','t-overseas-portal.ikeenon.com','t-overseas2-portal.ikeenon.com','d-portal.ikeenon.com','p-portal.ikeenon.com','t-us-portal.ikeenon.com','www.robotkeenon.com','es.robotkeenon.com','us.robotkeenon.com']:
    E('HOST',d,'SPA Home chunk (web portal URLs, e.g. for QR/help links)','STATIC_REFERENCE','MEDIUM')
E('HOST','mqtt.keenonrobot.com','DynamicMqttServer (default MQTT host; ssl://%s:%d TLSv1.2 with BKS; fallback tcp://%s:%d)')
E('URL_TEMPLATE','https://<domain>:443 or :80 via ServerManager "%s%s:%d"','ServerManager.changeUrl/setServerUrl (HTTP_PORT=80, HTTP_PORT_SSL=443)')
E('HOST','sentry.keenonrobot.com','AndroidManifest meta-data io.sentry.dsn = https://<public-key REDACTED>@sentry.keenonrobot.com/14; traces-sample-rate 1.0')
E('KEY','AMap (Gaode) API key present in manifest meta-data com.amap.api.v2.apikey = [REDACTED 32 hex]','AndroidManifest.xml')
E('HOST','223.5.5.5','com.blankj.utilcode NetworkUtils (connectivity ping target; third-party library)','STATIC_REFERENCE','MEDIUM')
E('HOST','dns.quad9.net, cloudflare-dns.com, 1dot1dot1dot1.cloudflare-dns.com','Chromium M73 built-in DNS-over-HTTPS provider list','STATIC_REFERENCE','MEDIUM')
for p in sorted(set(re.findall(r"'(/api/[A-Za-z0-9_/\.\{\}\$]+)'",open(B+'/ria/js_paths.txt',encoding='utf-8').read()))):
    E('REST_PATH',p+' (SPA, host = selected cloud domain)','assets/ros/dist/assets/index-32d09e94.js',conf='HIGH')
for p,s in [('/api/app/robot/checkNewSoftware (GET)','ClientUpdateApi'),('/api/app/robot/getSoftware (GET)','ClientUpdateApi'),('/api/app/token/getSingleToken (POST)','GetTokenApi / ITokenApi'),('/api/app/mqtt/secret/v1 (GET)','MqttGetPasswordApi'),('/api/app/mqtt/secret/v2 (GET)','IMqttPwdApi (component.core)'),('/api/app/robot/appResource/status (POST)','UploadDeviceInfoApi'),('/api/app/robot/info/update (POST)','UploadDeviceInfoApi'),('/api/app/robot/version/status (POST)','UploadDeviceInfoApi'),('/api/app/v1/robot_iso_domain_name_link (GET)','IDomainApi'),('/api/app/v2/healthz (GET)','IDomainApi ping'),('/api/app/robot/v1/net/sec (GET)','IHttpCybersecurityApi'),('/api/app/robot/v1/geolocation (POST)','LocationApi'),('/api/app/robot/v2/restriction (POST)','LocationApi'),('geolocation/v1/geolocate (POST, Google geolocation API via proxy host)','Request'),('/api/app/device/report/identity (POST)','IRobotBaseApi (machineAuth)'),('/api/app/device/report/identity/check (GET)','IRobotBaseApi'),('/api/app/v1/device/ssh/login-secret (GET)','IPlatformHttpApi (sshPwd) - cloud-provided robot SSH login secret')]:
    E('REST_PATH',p,'Retrofit annotation in '+s,conf='HIGH')
for t,s in [('/mapping/v1/cloud_msg (sub, prefixed /tool_support/mapping_<id>?)','HubConstant.TOPIC_MAP_AND_DISPATCH_ID; MapMqttManager'),('/mapping/v1/dev_msg (pub ack)','HubConstant.TOPIC_MAP_AND_DISPATCH_ID_ACK'),('/tool_support/mapping_ (topic prefix)','MapMqttManager'),('/tool_support/','HubConstant.TOPIC_SUPPORT'),('/device/%s/ioe_mqtt_conn/v1/cloud_client','HubConstant.TOPIC_SUB_IOE_MQTT_CONN_CLOUD_MSG; MqttServerHelper (broker host change notices)'),('/robot... ssh pwd credential publish/subscribe topics (MqttNetHelper.TOPIC_SSH_PWD_CREDENTIAL_PUBLISH/SUBSCRIBE; actions RobotSshCredential / ...Reply)','component.sshPwd.net.MqttNetHelper'),('/keenon... client data publish (MqttHelper.TOPIC_CLIENT_DATA_PUBLISH), /identity firmware-change observer','component.machineAuth.net.MqttHelper')]:
    E('MQTT_TOPIC',t,s,conf='MEDIUM' if 'robot...' in t or 'keenon...' in t else 'HIGH')
E('MQTT_CLIENT_ID_PREFIX','mapping_','SlamApplication.initMqtt -> MqttManager.setMqttClientIdPrefix')
E('BROADCAST_ACTION','com.keenon.action.MAPPING_START / MAPPING_STOP / MAPPING_HEART_BRAT / RESTORE_FACTORY_SETTINGS / DISCONNECT_ROBOT / IMPORT_U_DRIVE_DATA / com.keenon.IMPORT_U_DRIVE.STATE / EXECUTE_CMD / EXECUTE_CMD_RESULT / SILENT_INSTALL_APP / DOMAIN_ISO_CHANGED / android.peanut.action.MAPPING_RESTART_ROS(_SUC) / MAP_DOWNLOAD_START / keenon.intent.action.REFRESH_PROPERTIES','HubConstant, APPReceiver, KeenonServiceUtil, CommUtils, DomainManager, PropertiesStorage')
E('INTENT_URI','mapkeenon://mapandroid.com?Language=<lang>&cloud3d=<bool>&modifyPoint=<bool>','AndroidManifest SimpleChromeActivity intent-filter; handlerIntentData')
E('AIDL_SERVICE','com.keenon.systemservice/{bi.BiService, mqtt.MqttApiService, tts.TtsApiService, voice.VoiceApiService}','com.keenon.serviceSdk.common.BaseAidlClient.getServicePackage; per-client getServiceClass')
E('APP_PACKAGE','com.keenon.launcher; com.keenon.peanut.solutionfooddelivery / solutionhotel / solutiondisinfect / com.keenon.solutionindustry / com.keenon.peanut.clean; com.keenon.remote_control_oray (+ .daemon.HeadService)','HubConstant/CommUtils.packageNameArray/checkRemoteAPP/InformationSupport.onHandleFactoryReset')
json.dump(ep,open(OUT+r'\installation_assistant_endpoints.json','w',encoding='utf-8'),indent=1,ensure_ascii=False)
print('endpoints',len(ep))
# ------------------------------------------------ ros refs
rr=[]
def R(kind,name,typ,source,note=''):
    rr.append({'kind':kind,'name':name,'type':typ,'source':source,'status':'STATIC_REFERENCE','confidence':'HIGH' if 'SPA' in source or 'Android' in source else 'MEDIUM','note':note})
for t in tbl:
    ty=t['type'] or ''
    if t['name'].startswith('/rosapi/'): k='service'
    elif '/topo_action/' in t['name']: k='action'
    elif re.search(r'\.srv$|_srv$|Srv$|SetBool|SetFilterMode|launchConfig|webGetParam|manager$|saveCombineMap|Service',ty) or ty.endswith('.srv'): k='service_or_topic(srv type)'
    else: k='topic_or_service(unclassified)'
    R(k,t['name'],ty,'SPA ros table '+t['const']+' ('+t['file']+')')
for n,ty in [('/rosapi/topics','rosapi/Topics'),('/rosapi/nodes','rosapi/Nodes'),('/rosapi/get_param','rosapi/GetParam'),('/rosapi/set_param','rosapi/SetParam'),('/rosapi/delete_param','rosapi/DeleteParam'),('/rosapi/services','rosapi/Services'),('/rosapi/topic_type','rosapi/TopicType'),('/rosapi/message_details','rosapi/MessageDetails'),('/rosapi/node_details','rosapi/NodeDetails'),('/rosapi/get_param_names','rosapi/GetParamNames'),('/rosapi/action_servers','rosapi/GetActionServers'),('/rosapi/service_type','rosapi/ServiceType'),('/rosapi/services_for_type','rosapi/ServicesForType'),('/rosapi/topics_for_type','rosapi/TopicsForType'),('/rosapi/service_request_details','rosapi/ServiceRequestDetails'),('/rosapi/service_response_details','rosapi/ServiceResponseDetails'),('/tf2_web_republisher','tf2_web_republisher/RepublishTFs (+TFArray)'),('/cmd_vel','(geometry_msgs/Twist presumed)'),('/cmd_vel_smart','(unknown)'),('/persistence_params/move_base/side_drive_status','(unknown)'),('/record_position','(unknown)'),('/manual_loop_closure','(unknown)'),('/base_link','(tf frame id)'),('/move_base/local_costmap/footprint','(footprint)'),('/robot_current_state/floor','(unknown)'),('/robot_current_state/map','(unknown)'),('/robot_current_state/virtual_wall','(unknown)'),('/robot/init_origin_floor','(unknown)'),('/cancel','actionlib_msgs/GoalID'),('/status','actionlib_msgs/GoalStatusArray'),('/goal','(action goal)'),('/result','(action result)'),('/version','keenon_database_msgs/version'),('/home/peanut/rosShare/','(filesystem path on ROS board, shared via smb://192.168.64.20/home)')]:
    if not any(x['name']==n for x in rr): R('rosbridge_reference',n,ty,'SPA bundle (index/Home chunks)',)
for n in ['/switch_dest_floor_map','/republish_tfs','/scan_base_map','/motor_lock','/tf2_web_republisher']:
    R('rosbridge_proxy_filter',n,'','Android RosSocketClientManager.topic_91/service_91 arrays (routed to port 9091 client)','Messages with these names are sent via the 9091 connection (name matching from clinit strings; exact array assignment INFERRED)')
for o in cmds:
    if 'CoapCommond' in o['anns']:
        a=o['anns']['CoapCommond']; R('sdk_coap_resource',a.get('path'),(a.get('requestType') or a.get('msgType') or 'GET').replace('enum:',''),'SDK '+o['class']+' @CoapCommond (robot-side resource on coap://192.168.64.20:5683 or HTTP 54322)')
    if 'SerialCommand' in o['anns']:
        a=o['anns']['SerialCommand']; la=o['anns'].get('LinkAdapter',{})
        R('sdk_serial_command','action=%s body=%s status=%s'%(a.get('action'),a.get('body'),a.get('status')),str(la.get('board','')).replace('enum:','')+'/'+str(la.get('link','')).replace('enum:',''),'SDK '+o['class']+' @SerialCommand')
for k,t in [('navi.status','NavigationApi observe via PeanutSDK.subscribe'),('battery.charge.status','BatteryComponent/ChargeStatusApi'),('diagnosis.error','Diagnosis error report (sub key in WebSocketHelp.onHandleMessage strings)')]:
    R('sdk_subscription_key',k,'',t+'; used by RobotManager.onSubscribe (Installation Assistant)')
json.dump(rr,open(OUT+r'\installation_assistant_ros_refs.json','w',encoding='utf-8'),indent=1,ensure_ascii=False)
print('ros refs',len(rr))
