import sqlite3, json, sys, ast, re, collections
B = sys.argv[1]
OUTD = B + '/pcl_out/final/'
db = sqlite3.connect('file:' + B + '/pcl_out/code.db?mode=ro', uri=True)
def short(c): return c[1:-1].replace('/', '.')
E = []
def ep(kind, value, source, status='CONFIRMED', conf='HIGH', note=None):
    d = dict(kind=kind, value=value, source=source, status=status, confidence=conf)
    if note: d['note'] = note
    E.append(d)

# 1. Retrofit
verbs = {'GET': 'GET', 'POST': 'POST', 'PUT': 'PUT', 'DELETE': 'DELETE'}
rows = db.execute("select atype,cls,mname,vals from a where atype like 'Lretrofit2/http/%' and kind='method'").fetchall()
for atype, cls, mname, vals in rows:
    t = atype.split('/')[-1][:-1]
    if t not in ('GET', 'POST', 'PUT', 'DELETE', 'HTTP'): continue
    try: v = ast.literal_eval(vals)
    except Exception: continue
    path = v.get('value') or v.get('path')
    if not path: continue
    verb = t if t != 'HTTP' else v.get('method', 'HTTP')
    ep('http', '%s %s' % (verb, path), '%s#%s' % (short(cls), mname.split('(')[0]),
       'STATIC_REFERENCE', 'HIGH', 'Retrofit relative path; scheme/host chosen at runtime from DomainManager region (PROD https, DEV/TEST/PRE http) - STATIC_REFERENCE, not observed live')
# 2. Local API
cl = {}
for cls, vals in db.execute("select cls,vals from a where atype='Lcom/keenon/module/api/scan/Request;' and kind='class'"):
    cl[cls] = ast.literal_eval(vals).get('value', '')
for cls, mname, vals in db.execute("select cls,mname,vals from a where atype='Lcom/keenon/module/api/scan/Request;' and kind='method'"):
    if '/apiImpl/' not in cls: continue
    v = ast.literal_eval(vals)
    ep('http', 'http://<tablet-ip>:8089%s%s' % (cl.get(cls, ''), v.get('value', '')), '%s#%s' % (short(cls), mname.split('(')[0]),
       'STRONGLY_SUPPORTED', 'HIGH', 'In-app AsyncHttpServer API (listen(8089), wildcard bind); POST-style method code %s' % v.get('method', 'default'))
ep('http', 'http://<tablet-ip>:8090/ (assets/index.html SPA + static files)', 'com.keenon.module.android.sync.FileServerManager#start', 'CONFIRMED', 'HIGH', 'AsyncHttpServer.listen(8090), serves APK assets')
ep('http', 'http://localhost:8090/#/{checkArea,nav,record/report?cleanLogId=%s&robotWidth=%s}', 'MainActivity#initWebView; SelectAreaActivity$5#run; NaviDebugContainerActivity#initBridgeWebView', 'CONFIRMED', 'HIGH')
ep('http', 'http://localhost:8089/test/post', 'assets/js/app.5eca38b4.js handleHttp', 'CONFIRMED', 'HIGH')
# 3. assets
for env, f in (('PROD', 'domainConfig_PROD.json'), ('PRE', 'domainConfig_PRE.json'), ('TEST', 'domainConfig_TEST.json'), ('DEVELOP', 'domainConfig_DEVELOP.json')):
    for r in json.load(open(B + '/apkx/peanut-clean/assets/slamConfig/' + f, encoding='utf-8')):
        src = 'assets/slamConfig/' + f
        ep('https' if r['httpUrl'].startswith('https') else 'http', r['httpUrl'], src + ' (' + env + ' iso2=' + r['iso2'] + ')')
        ep('https' if r['cleanBaseApiUrl'].startswith('https') else 'http', r['cleanBaseApiUrl'], src + ' cleanBaseApiUrl')
        ep('https' if r['cleanUploadApiUrl'].startswith('https') else 'http', r['cleanUploadApiUrl'], src + ' cleanUploadApiUrl')
        ep('mqtt', r['mqttUrl'], src + ' mqttUrl isMqttTSL=%s' % r['isMqttTSL'])
for f in ('dev', 'prod', 'test', 'test-dev'):
    c = json.load(open(B + '/apkx/peanut-clean/assets/json/%s.config.json' % f, encoding='utf-8'))
    for k, v in c.items():
        if v and k in ('baseIp', 'uploadIp', 'baseIpC', 'websocket'):
            kind = 'ws' if k == 'websocket' else ('http' if str(v).startswith('http') or k == 'baseIpC' else 'other')
            if str(v).startswith('wss'): kind = 'ws'
            ep(kind, v, 'assets/json/%s.config.json key %s' % (f, k))
for f in ('domain.json', 'domain-pre.json', 'domain-eng.json', 'domain-dev.json'):
    for r in json.load(open(B + '/apkx/peanut-clean/assets/domain/' + f, encoding='utf-8'))['data']:
        ep('https', r['domainName'], 'assets/domain/%s (%s, iso2=%s)' % (f, r['englishName'], r['iso2']), 'CONFIRMED', 'HIGH', 'region selector domain list')
# 4. Hand-curated
H = [
 ('mqtt', 'tcp://<host>:1883 (MQTT_TCP_DEF_PORT)', 'CoreConfig static; DynamicMqttServer.getBaseUrl', 'CONFIRMED', 'HIGH', None),
 ('mqtt', 'ssl://<host>:8883 (prod), :18883 (test), :20001 (test CN)', 'CoreConfig.MQTT_SSL_PROD_PORT/MQTT_SSL_TEST_PORT/MQTT_SSL_TEST_PORT_CN; DynamicMqttServer.getBaseUrl (JADX)', 'CONFIRMED', 'HIGH', None),
 ('mqtt', 'mqtt.keenonrobot.com (CN_MQTT_HOST)', 'DynamicMqttServer static field', 'CONFIRMED', 'HIGH', None),
 ('coap', 'coap://192.168.64.20:5683', 'LinkTypeAutoDetectHelper.coapUrl; PeanutConstants.COAP_PORT', 'CONFIRMED', 'HIGH', 'IPC CoAP server (UDP)'),
 ('http', 'http://192.168.64.20:54322', 'LinkTypeAutoDetectHelper.httpUrl; PeanutConstants.HTTP_PORT', 'CONFIRMED', 'HIGH', 'IPC HTTP protocol path (/upgrade/action,/upgrade/status, /system/protocolVersion)'),
 ('http', 'http://192.168.64.20:34569 (HTTP_PORT_UPGRADE)', 'PeanutConstants', 'STRONGLY_SUPPORTED', 'MEDIUM', None),
 ('ws', 'ws://192.168.64.20:22345 (WS_PORT) / :12386 (WS_PORT_UPGRADE)', 'PeanutConstants WS_PORT/WS_PORT_UPGRADE (usage in com.keenon.sdk.http.ws not traced)', 'STRONGLY_SUPPORTED', 'LOW', None),
 ('ws', 'ws://192.168.64.20:9090 (rosbridge)', 'RosManager.getRosManager', 'CONFIRMED', 'HIGH', 'client present, topic registration stubbed (dormant)'),
 ('http', 'http://192.168.64.20:5555', 'assets/json/*.config.json baseIpC; DEX string http://192.168.64.20:5555 (classes3)', 'CONFIRMED', 'MEDIUM', 'legacy IPC console API (ConsoleApiEnums)'),
 ('udp', 'tftp://192.168.64.10:9527 (tablet TFTP server, root /peanut/server/tftp/)', 'TFTPServerManager.init / TFTPService.createServer', 'CONFIRMED', 'HIGH', None),
 ('other', '/dev/ttyS1 (KC01), /dev/ttyS2 (emotion), /dev/ttyS3 (921600), /dev/ttyS4, /dev/ttyUSB0/2/3, /dev/ttyUSBDoorCtl, baud 115200', 'PeanutConstants / BusinessInitializer.initSDK', 'CONFIRMED', 'HIGH', 'serial endpoints'),
 ('adb', 'none found (no adb command string or 5555 adb usage; 5555 appears only as IPC console base)', 'DEX string search', 'UNKNOWN', 'LOW', 'Do not read as negative proof'),
 ('other', 'keenonrobot://mobile.com/page_fail_list?code=%s&robotName=%s', 'DEX string (classes3)', 'CONFIRMED', 'MEDIUM', 'custom URI scheme used for fault-help page link'),
 ('other', 'supsetkeenon://supsetandroid.com (VIEW/BROWSABLE deep link to SetupActivity)', 'AndroidManifest SetupActivity intent-filter', 'CONFIRMED', 'HIGH', None),
 ('http', 'http://<key>@sentry.keenonrobot.com/7 (Sentry DSN, credential REDACTED)', 'AndroidManifest meta-data io.sentry.dsn', 'CONFIRMED', 'HIGH', 'cleartext'),
 ('https', 'https://aaid.umeng.com, ulogs.umeng.com, alogus.umeng.com, ouplog.umeng.com, pslog.umeng.com, plbslog.umeng.com', 'Umeng SDK DEX strings', 'CONFIRMED', 'HIGH', 'logging disabled by app config but code present'),
 ('http', 'http://apilocate.amap.com/mobile/binary, restsdk.amap.com/v3/*, cgicol.amap.com/collection/collectData, https://adiu.amap.com/ws/device/adius', 'AMap SDK DEX strings', 'CONFIRMED', 'HIGH', 'AMap API key REDACTED (manifest)'),
 ('https', 'geolocation/v1/geolocate (Google Geolocation API; key REDACTED)', 'com.keenon.component.location.http.Request#getGoogleMapLocation; PeanutLocationManager', 'CONFIRMED', 'MEDIUM', 'base URL not found as a string (UNKNOWN)'),
 ('https', 'https://www.baidu.com ; ping -c 3 www.baidu.com ; 8.8.8.8 ; 223.5.5.5', 'NetworkEventManager / network checks', 'CONFIRMED', 'HIGH', 'connectivity probes'),
 ('http', 'http://upload-test.keenonai.com ; http://clean-test.keeonai.com', 'DEX strings classes3/classes4', 'CONFIRMED', 'HIGH', 'test hosts embedded in prod build (second host spelled keeonai)'),
 ('ws', 'ws://162.243.238.80:9090', 'com.jilk.ros.Example#main (library sample)', 'CONFIRMED', 'HIGH', 'third-party sample, not app logic'),
 ('http', 'http://172.16.8.102:8089/ ; ws://192.168.2.5:8083 ; tcp://172.16.13.135:1883', 'assets/json/dev.config.json; test.config.json; domainConfig_PROD/TEST ES mqttUrl', 'CONFIRMED', 'HIGH', 'private/dev addresses embedded'),
 ('http', 'http://upload.keenonai.com/uploads/<id>/<id>.png', 'assets/js/chunk-38c9b0c6/f077fb7a/fba32598 (H5 default image src)', 'CONFIRMED', 'HIGH', None),
 ('http', 'http://weixin.qq.com/r/ejrO1u7E_bOGraq1929o ; http://weixin.qq.com/r/uSk-J6fEKfpWreNE93yr', 'DEX strings classes4 (QR/help links)', 'CONFIRMED', 'MEDIUM', None),
]
for k, v, s, st, c, n in H: ep(k, v, s, st, c, n)
# MQTT topics
for fname, val in db.execute("select fname,val from sv where cls like '%MqttPeanutStatsConfig%'"):
    ep('mqtt', 'topic ' + ast.literal_eval(val), 'MqttPeanutStatsConfig.' + fname, 'STATIC_REFERENCE', 'HIGH', 'topic template; %s = robot SN, many suffixed with SN at runtime')
for t in ('/%s/%s/user/elevator/sub', '/%s/%s/user/elevator/pub', '/%s/%s/user/ping/sub', '/device/%s/ioe_mqtt_conn/v1/cloud_client', 'clean/%s/robot_open/v1/dev_msg'):
    ep('mqtt', 'topic ' + t, 'AliMqttTopicEnums / MqttServerHelper / MqttUtil', 'STATIC_REFERENCE', 'HIGH')
json.dump(E, open(OUTD + 'peanut_clean_app_endpoints.json', 'w', encoding='utf-8'), ensure_ascii=False, indent=1)
print('endpoints', len(E))

# ---------------- ROS refs ----------------
R = []
def rr(kind, name, source, **kw):
    d = dict(kind=kind, name=name, label='STATIC_REFERENCE', source=source)
    d.update(kw); R.append(d)
cmds = json.load(open(B + '/pcl_out/coap_cmds.json', encoding='utf-8'))
for c in cmds:
    s = short('L' + c['api_class'].replace('.', '/') + ';')
    if c['transport'] == 'CoapCommond':
        rr('coap_resource', c['path'], s + ' (@CoapCommond)', requestType=c.get('requestType'), msgType=c.get('msgType'), group=s.split('.')[-2],
           note='msgType 53646/53647 resources are observe/subscribe style (INFERRED from naming/Observe usage); numeric codes are opaque SDK constants')
    elif c['transport'] == 'HttpCommand':
        rr('http_resource', c['path'], s + ' (@HttpCommand)', interval=c.get('interval'), requestType=c.get('requestType'))
    else:
        rr('serial_command', 'action=%s body=%s%s' % (c['action'], c['body'], (' status=' + c['status']) if c.get('status') else ''), s + ' (@SerialCommand)', group=s.split('.')[-2])
impl = json.load(open(B + '/pcl_out/sdk_impl_method_paths.json', encoding='utf-8'))
# app-called method names
app_called = set(r[0] for r in db.execute("select distinct c.cname from c join m on m.id=c.mid where (c.ccls like 'Lcom/keenon/sdk/robot/api/%' or c.ccls like 'Lcom/keenon/sdk/component/%Component;' or c.ccls like 'Lcom/keenon/sdk/external/PeanutSDK;' or c.ccls like 'Lcom/keenon/sdk/component/kc01/%') and m.cls not like 'Lcom/keenon/sdk/%' and m.cls not like 'Lcom/keenon/peanut/%'"))
comp_called = set(r[0] for r in db.execute("select distinct c.cname from c join m on m.id=c.mid where m.cls like 'Lcom/keenon/sdk/component/%Component;' and c.ccls like 'Lcom/keenon/sdk/robot/api/%'"))
for k, v in impl.items():
    for m, paths in v.items():
        for p in paths:
            rr('sdk_method_to_coap', '%s.%s -> %s' % (k, m, p), 'com.keenon.sdk.coapapi.impl.%s#%s' % (k, m), path=p,
               app_invokes_interface_method_by_name=(m in app_called), status='STRONGLY_SUPPORTED' if m in app_called else 'STATIC_REFERENCE',
               note='name match against non-SDK caller method names; chain via Component -> RobotApiCompat -> robot.api interface')
# ROS topics (rosbridge)
topics = [('/error_report', 'keenon_ui_msgs/error_report'), ('/elevator_task_status', 'keenon_elevator_msgs/elev_task_status'), ('/task_manager_report_slow', 'keenon_clean_msgs/task_manager_report'),
          ('/robot_location_point', 'keenon_clean_msgs/web_location_point'), ('/charger_feedback', 'std_msgs/UInt8'), ('/keenon_app_monitor', 'keenon_clean_msgs/keenon_app_monitor'),
          ('/urgency_button_status', 'std_msgs/Bool'), ('/elevator_4g_request', 'keenon_elevator_msgs/elev_request'), ('/elevator_response', 'keenon_elevator_msgs/elev_response'),
          ('/soundPlay', 'std_msgs/UInt16'), ('/android_monitor', 'keenon_clean_msgs/android_monitor'), ('/android_msg_upload', 'std_msgs/String'),
          ('/camera_calibration/feedback', 'keenon_clean_msgs/camera_calibrationActionFeedback'), ('/camera_calibration/result', 'keenon_clean_msgs/camera_calibrationActionResult'),
          ('/android_console_initialization_complete', 'std_msgs/String'), ('/map/info/origin', 'nav_msgs/OccupancyGrid/info/origin')]
for t, ty in topics:
    rr('ros_topic', t, 'com.keenon.module.api.enums.RosTopicEnums', msg_type=ty, status='STATIC_REFERENCE',
       note='enum constant only; no caller of RosAPI.topicSubscribe/publishMsg found; rosbridge client dormant (STRONGLY_SUPPORTED)')
# ROS params / config style names
params = [r[0] for r in db.execute("select distinct s.str from s join m on m.id=s.mid where m.cls like 'Lcom/keenon/module/main/repo/CleanWaterCollectSettingsRepo%' and s.str like '/%'")]
for p in sorted(params):
    rr('ros_param_name', p, 'com.keenon.module.main.repo.CleanWaterCollectSettingsRepo', note='water-collect tuning key shown in WaterCollectSettingsActivity; transport to IPC not traced (UNKNOWN)')
# Console API enum (legacy IPC console HTTP)
cons = [r[0] for r in db.execute("select distinct s.str from s join m on m.id=s.mid where m.cls like 'Lcom/keenon/module/api/enums/ConsoleApiEnums%' and s.str like '/%'")]
for p in sorted(cons):
    rr('console_http_path', p, 'com.keenon.module.api.enums.ConsoleApiEnums$ConsoleApi', note='legacy IPC console API path; only referenced from HttpLogInterceptor')
# IPC action broadcasts / ROS-related actions
for a in ('android.peanut.action.MAPPING_RESTART_ROS', 'android.peanut.action.MAPPING_RESTART_ROS_SUC', 'android.peanut.action.MAP_DOWNLOAD_START', 'com.keenon.action.MAPPING_START', 'com.keenon.action.MAPPING_STOP', 'com.keenon.action.MAPPING_HEART_BRAT'):
    rr('android_broadcast_action_ros_related', a, 'RosMapDownLoadManager / SystemReceiver / BusinessInitializer')
for n, s in (('keenon_clean_msgs', 'RosTopicEnums message types'), ('keenon_ui_msgs', 'RosTopicEnums'), ('keenon_elevator_msgs', 'RosTopicEnums'), ('std_msgs', 'RosTopicEnums'), ('nav_msgs', 'RosTopicEnums')):
    rr('ros_message_package', n, s)
json.dump(R, open(OUTD + 'peanut_clean_app_ros_refs.json', 'w', encoding='utf-8'), ensure_ascii=False, indent=1)
print('ros_refs', len(R), collections.Counter(r['kind'] for r in R))
