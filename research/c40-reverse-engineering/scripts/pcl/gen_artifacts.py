import json, sys, sqlite3
B = sys.argv[1]
H = json.load(open(B + '/pcl_out/member_hashes.json'))
db = sqlite3.connect('file:' + B + '/pcl_out/code.db?mode=ro', uri=True)
APK = 'peanut-clean-v3.7.8-0-g153214a-prod-release-p.apk'
A = []
def rec(path, analyzed, method, findings, status, limits, **extra):
    d = dict(relative_path=path, analyzed=analyzed, method=method, findings=findings, status=status, limitations=limits)
    d.update(extra); A.append(d)

rec(APK, True, 'ZIP inventory, manifest decode (axml), DEX bytecode scan + mini-disassembly, REA/JADX 0.7.1 decompilation of ~60 selected methods, ELF header/string parsing, openssl cert parse, asset/JSON/SQL reading',
    'Prod release of Keenon clean app v3.7.8 (versionCode 24); 3,484 members, 7 DEX, 10 armeabi-v7a .so, 122 MB; v1+v2/v3 signed with generic "CN=Android" certificate; embedded REST API on 8089/8090; CoAP/serial Peanut SDK; Room DB; MQTT/HTTP cloud sync. 68 findings.',
    'CONFIRMED', 'Not executed; no runtime observation; JADX decompiled only selected classes (CoAP/MQTT/UI hops partly INFERRED); native code not disassembled; 33.6k-class DEX only partially reviewed.',
    size_bytes=121990249, sha256='9b5eb4f20e3696d1bd467a270b0db0798246ac5dbf9a59d6b968b56cb3b7fdd5')
dex_desc = {
 'classes.dex': 'com.keenon.component.*, peanut.core, ARouter/TheRouter, Paho, okhttp, okgo, androidx; MQTT/DynamicMqttServer, DomainManager, upgrade, fault, resource managers',
 'classes2.dex': 'sdk robot models/beans, component classes, Retrofit/RxJava, jilk rosbridge',
 'classes3.dex': 'AMap/Umeng SDK, datasync, Keenon module.common/android (embedded servers), upgrade installers',
 'classes4.dex': 'com.keenon.module.* app code (main UI, slam services, datebase Room, task controllers), 2.5k UI classes',
 'classes5.dex': 'Peanut SDK (coapapi, embedded, sensor, tftp, http), Californium, AndroidAsync, commons-net',
 'classes6.dex': 'Sentry, ACRA, Glide, google libs, kotlin, logback, org.* libs',
 'classes7.dex': 'JTS, bouncy/crypto providers, snakeyaml, freemarker, remaining third-party',
}
for n in ['classes.dex'] + ['classes%d.dex' % i for i in range(2, 8)]:
    ncls = db.execute("select count(*) from k where dex=?", (n,)).fetchone()[0]
    nm = db.execute("select count(*) from m where dex=?", (n,)).fetchone()[0]
    rec('%s!/%s' % (APK, n), True, 'DEX parse (header/strings/types/classes/code items) + bytecode scan (const-string, invoke, field, annotation, static values) into SQLite',
        '%d class definitions, %d methods; content: %s' % (ncls, nm, dex_desc[n]), 'CONFIRMED',
        'Class descriptions are heuristic; only selected classes decompiled with JADX.', size_bytes=H[n]['size'], sha256=H[n]['sha256'])
so = {
 'libcipher_pack.so': ('Secret store; JNI CipherUtil.get; holds fleet-wide AES/MQTT/keystore/PIN/MMKV/Umeng/Bugly secrets (values not extracted)', True),
 'libkeenon_serial.so': ('JNI SerialPort open/close (termios) for com.keenon.sdk.serial', True),
 'libandroid_serial_port.so': ('JNI com.aill.androidserialport.SerialPort open/close', True),
 'libmmkv.so': ('Tencent MMKV (AES crypt key support)', True),
 'libpatchutils.so': ('bsdiff/bspatch (PatchUtil.bspatch) with bundled bzip2', True),
 'libpl_droidsonroids_gif.so': ('android-gif-drawable decoder', True),
 'libsentry-android.so': ('Sentry NDK JNI bridge', True),
 'libsentry.so': ('Sentry native SDK (sentry_init, transport)', True),
 'libsqlcipher.so': ('SQLCipher (OpenSSL EVP); not wired to Room DB (greenDAO lib refs only)', True),
 'libumeng-spy.so': ('Umeng device-id/anti-tamper helper (fork/pipe/syscall imports)', True),
}
for n, (d, a) in so.items():
    k = 'lib/armeabi-v7a/' + n
    rec('%s!/%s' % (APK, k), a, 'ELF32 ARM header/dynsym/dynstr parse (scripts/elfinfo.py + elfall.py), JNI export names, import list, readable strings',
        d, 'CONFIRMED', 'Stripped; no disassembly/decompilation (no Ghidra/objdump installed); internal logic UNKNOWN.', size_bytes=H[k]['size'], sha256=H[k]['sha256'])
rec('%s!/resources.arsc' % APK, True, 'Custom arsc string-pool parser (scripts/pcl/arsc.py)', 'Resolved app_name (brand name), xml resource ids (network_security_config=0x7f140002, provider_paths=0x7f140004, device_filter, file_paths, provider_path); theme/style ids not resolved', 'STRONGLY_SUPPORTED', 'Only selected resource ids resolved; styles/themes not decoded.', size_bytes=H['resources.arsc']['size'], sha256=H['resources.arsc']['sha256'])
rec('%s!/AndroidManifest.xml' % APK, True, 'Binary XML decode (scripts/axml.py), default-export evaluation', '48 activities, 7 services, 4 receivers, 8 providers; effective exported: SetupActivity, ApkInstallReceiver, 3 androidx.test activities; 40+ permissions; usesCleartextTraffic=true; allowBackup=true; Sentry/AMap meta-data', 'CONFIRMED', 'Some @0x7f.. resource refs unresolved.', size_bytes=H['AndroidManifest.xml']['size'], sha256=H['AndroidManifest.xml']['sha256'])
for n, d in (('META-INF/CERT.RSA', 'PKCS#7 signature block: generic CN=Android RSA-2048 sha256 certificate, valid 2014-12-23..2042-05-10'), ('META-INF/CERT.SF', 'v1 signature file (SHA-256 digest manifest; X-Android-APK-Signed: 2)'), ('META-INF/MANIFEST.MF', 'v1 manifest, Built-By Signflinger, Created-By Android Gradle 4.2.0, 3,481 entries')):
    rec('%s!/%s' % (APK, n), True, 'openssl pkcs7/x509, text read', d, 'CONFIRMED', 'Signature verification (cryptographic validity) not re-computed; APK Sig Block 42 present at offset 121686153 (v2/v3 contents not parsed).', size_bytes=H[n]['size'], sha256=H[n]['sha256'])
rec('%s!/res/raw/*.bks (13 files)' % APK, True, 'BKS v2 container parser (scripts/pcl/bks.py) + openssl x509 for public certs', 'ca/client/prod_*/dev_*/eng_* keystores: sealed client key entries + CA certs for MQTT/HTTPS mTLS (CN=127.0.0.1 client issued by *.robotkeenon.com/*.ikeenon.com/Mqtt_Root); robot.bks expired 2019; private keys NOT decrypted', 'CONFIRMED', 'Key passwords come from libcipher_pack and were not obtained; certificate fingerprints available in pcl_out/certs.',
    size_bytes=sum(H[k]['size'] for k in H if k.startswith('res/raw/') and k.endswith('.bks')))
for k in ('event.json', 'fault.json', 'level.json'):
    rec('%s!/res/raw/%s' % (APK, k), True, 'JSON read', {'event.json': '11 app event codes (61001 ... )', 'fault.json': '144 fault codes with level 3/5 and Chinese resolution hints', 'level.json': '6 fault levels with save/show/upload flags'}[k], 'CONFIRMED', 'Chinese text not translated in full.', size_bytes=H.get('res/raw/' + k, {}).get('size'), sha256=H.get('res/raw/' + k, {}).get('sha256'))
rec('%s!/res/raw-en/fault.json' % APK, True, 'size/listing only', 'English fault-description variant', 'STRONGLY_SUPPORTED', 'Not parsed in detail.')
groups = [
 ('assets/domain/*.json (4)', 'JSON read', 'Region/domain lists for PROD/PRE/ENG/DEV (CN/ES/JP/US), keenonrobot/robotkeenon/ikeenon domains', 'CONFIRMED', ''),
 ('assets/json/{dev,prod,test,test-dev}.config.json + readme.txt', 'JSON read', 'baseIp/uploadIp/baseIpC(192.168.64.20:5555)/websocket for H5 SPA per environment', 'CONFIRMED', ''),
 ('assets/slamConfig/* (11 files)', 'JSON read', 'cleanConfigMode/Params/Setting, consumptive statistics, power-save config, domainConfig_{DEVELOP,PRE,PROD,TEST}.json (cleanBaseApiUrl, upload, mqttUrl, isMqttTSL), softTypeConfig (ROS software codes: chassis, cover_planner, robot_location, keenon_clean_msgs ...)', 'CONFIRMED', 'Readme text file name is mojibake in the archive.'),
 ('assets/sql/24.sql..41.sql + demo.sql (19)', 'SQL read', 'Room migration scripts (tables, columns; v29/30 purges data)', 'CONFIRMED', ''),
 ('assets/therouter/routeMap.json', 'JSON read', '38 TheRouter routes /solution_clean/*', 'CONFIRMED', ''),
 ('assets/index.html, assets/js/* (59), assets/css/* (46), assets/img/* (96), assets/image/*, assets/error/*', 'file listing, grep of JS for URLs/bridge handlers', 'Vue SPA map/record UI (OpenLayers/Konva/proj4); calls localhost:8089, JS bridge handlers', 'STRONGLY_SUPPORTED', 'Minified JS not deobfuscated; business logic of SPA not analysed.'),
 ('assets/fault/*.html (23) + assets/jsbridge + WebViewJavascriptBridge.js', 'listing/read', 'Localised fault help pages (500/503/601/1102/dustBox/model) and JS bridge shim', 'STRONGLY_SUPPORTED', 'HTML not audited for XSS.'),
 ('assets/video/* (25 mp4, 66 MB), assets/voice/{en,zh} (79 mp3), assets/bg (4 mp3), assets/expression (3 mp4), assets/character/2_UNICODE.bin (4 MB)', 'listing only', 'Maintenance videos, voice prompts, background music, robot-face expression videos, character bitmap font for the emotion screen', 'STRONGLY_SUPPORTED', 'Media not inspected.'),
 ('assets/LightConfig.json, assets/sensors.json, assets/viewSize/layout_rule.yaml, assets/font/digital.TTF, assets/favicon.ico', 'read', 'Light-strip config per model (T8 etc.; delivery models), empty sensor list, UI font-size rules', 'CONFIRMED', 'LightConfig references other Keenon models (T8) - shared with other products.'),
 ('res/layout (446), res/xml (5), res/drawable*, res/mipmap*, res/anim*', 'listing, axml decode of res/xml', 'UI resources; xml: device_filter (CH340), file_paths, provider_path(s), network_security_config', 'CONFIRMED', 'Layouts not individually decoded.'),
 ('EventRecord.proto, ResourceDownload.proto (APK root)', 'read', 'Protobuf schemas for fault/event upload and resource download status', 'CONFIRMED', ''),
 ('com/keenon/{aidl,bi,tts,voice}/**/*.aidl (15)', 'read', 'AIDL interfaces to system-service app (MQTT, TTS, voice, BI)', 'CONFIRMED', ''),
 ('google/api/*.proto, google/protobuf/*.proto, google/rpc, google/logging, google/longrunning, google/cloud, google/geo (58)', 'listing', 'Bundled googleapis/protobuf proto sources (library resources)', 'STRONGLY_SUPPORTED', ''),
 ('*.ftl (7), freemarker/*, org/apache/tools/ant/* (7), org/sqlite/native/* (6), org/eclipse/paho/*.properties, okhttp3/internal/*', 'listing', 'greenDAO-generator templates, FreeMarker and Ant resources, desktop sqlite-jdbc natives, Paho NLS bundles, okhttp public-suffix data (build/test leftovers)', 'CONFIRMED', ''),
 ('libs/xpp3-1.1.3_8.jar', 'listing only', 'XmlPull parser jar embedded as a resource (not loaded as code by Android)', 'STRONGLY_SUPPORTED', 'Not opened.'),
 ('bundle.properties, sqlite-jdbc.properties, androidsupportmultidexversion.txt, LICENSE-junit.txt, META-INF/*.version, META-INF/services/*, META-INF/*.kotlin_module, META-INF/native-image/*', 'read', 'Library metadata: sqlite-jdbc 3.44.1.0, Paho bundle, androidx versions, service-loader registrations (IApplicationLifecycle: Slam/Ros/Android/Datasync/SoundPlayer lifecycle; OtaCommand implementors), module names peanut-sdk/peanut-core/protocol-*', 'CONFIRMED', ''),
 ('Databases embedded as files', 'search of ZIP members for *.db/*.sqlite', 'No .db/.sqlite/.keystore/.jks/.pem/.p12 member present in the APK (only .bks keystores and SQL migration scripts)', 'CONFIRMED', 'Files created at runtime (e.g. /keenon/db/*.db) not available offline.'),
 ('Maps / ML models / jar / properties assets', 'ZIP member search', 'No map images or ML model files (.tflite/.pb/.onnx) found; only UI assets; one .jar (xpp3) and properties files listed above', 'CONFIRMED', 'Absence from ZIP listing only.'),
]
for p, m, f, s, l in groups:
    rec('%s!/%s' % (APK, p), s != 'NOT_ANALYZED', m, f, s, l or 'None significant.')
json.dump(A, open(B + '/pcl_out/final/peanut_clean_app_artifacts.json', 'w', encoding='utf-8'), ensure_ascii=False, indent=1)
print(len(A))
