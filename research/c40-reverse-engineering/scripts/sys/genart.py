"""Build system_apps_artifacts.json (inventory of the three APKs and embedded items). Read-only on inputs."""
import json, os, zipfile, hashlib, re

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
    'libcipher_pack.so': 'JNI CipherUtil.get(Context,String): per-package native key/credential lookup (labels only recorded; values not extracted)',
    'libnativetool.so': 'JNI KNNativeTool.nativeCmd -> KeenonShell::proxyCmd; socket/sendto/recvfrom imports (local command proxy, INFERRED)',
    'lib3Des.so': 'JNI CtrlDesUtil.encrypt (3DES)',
    'libpatchutils.so': 'JNI PatchUtil.bspatch (bsdiff + bzip2); no caller found in app code',
    'libmmkv.so': 'Tencent MMKV native core',
    'libdaemon_api20.so': 'marsdaemon keep-alive native (fork/kill)',
    'libdaemon_api21.so': 'marsdaemon keep-alive native (fork/inotify)',
    'liborayservice_sdk.so': 'Oray Sunlogin service SDK (25 JNI exports)',
    'liborayinput_agent.so': 'Oray input agent launched from /data/local/tmp (-o 0)',
    'liborayinput_agent5.so': 'Oray input agent variant',
    'liborayscreen_agentn.so': 'Oray screen agent (links libgui/libskia/libbinder)',
    'liblog.so': 'stub liblog bundled in APK',
    'libK18SttyCommInterface.so': 'Rokid K18 mic-array JNI K18SttyCommAPI',
    'libsentry.so': 'Sentry native crash SDK',
    'libsentry-android.so': 'Sentry NDK JNI bridge',
    'libliteavsdk.so': 'Tencent LiteAV SDK (302 JNI exports)',
    'libtxffmpeg.so': 'Tencent FFmpeg bundle',
    'libtxsoundtouch.so': 'SoundTouch audio',
    'libmsc.so': 'iFlytek MSC speech SDK',
    'libaiui.so': 'iFlytek AIUI SDK',
    'libvtn.so': 'iFlytek VTN voice front-end',
    'libhlw.so': 'iFlytek wake-word engine',
    'libvadLib.so': 'VAD library', 'libydvad.so': 'VAD library',
    'libcae-jni.so': 'iFlytek CAE JNI', 'libalsa-jni.so': 'tinyalsa JNI',
    'libnative-lib.so': 'WebRTC VAD JNI (Recorder.webRtcVadProcess)',
    'librobotdevice.so': 'JNI RobotDeviceNative.getDeviceId',
    'libGVoiceUtils.so': 'Tencent GVoice utils', 'libmygvoice.so': 'Tencent GVoice wrapper',
    'liblzma.so': 'xz/lzma library',
    'daemon': 'marsdaemon daemon executable (asset)',
}


def sha(b):
    return hashlib.sha256(b).hexdigest()


arts = []
for app, f in APKS.items():
    p = os.path.join(ROOT, f)
    data = open(p, 'rb').read()
    z = zipfile.ZipFile(p)
    names = z.namelist()
    emb = lambda n: f + '!/' + n
    arts.append({'relative_path': f, 'analyzed': True,
                 'method': 'manifest decode (axml.py), DEX scan/disassembly (own scripts), JADX via REA on selected methods, certificate parse, ELF inventory',
                 'findings': 'sha256=%s size=%d; v1 JAR signature only; shared generic "Android" platform cert; see report section for app' % (sha(data), len(data)),
                 'status': 'CONFIRMED',
                 'limitations': 'Not run; most classes reviewed at bytecode/string level only; MD5 verified upstream by coordinator'})
    arts.append({'relative_path': emb('AndroidManifest.xml'), 'analyzed': True, 'method': 'axml.py binary XML decode',
                 'findings': 'components, permissions, exported surface documented', 'status': 'CONFIRMED',
                 'limitations': '@0x7f resource references unresolved'})
    for n in sorted(n for n in names if n.endswith('.dex') and '/' not in n):
        arts.append({'relative_path': emb(n), 'analyzed': True, 'method': 'dexinfo/dexscan index, dexdis disassembly, JADX on selected classes',
                     'findings': 'sha256=%s size=%d' % (sha(z.read(n)), z.getinfo(n).file_size), 'status': 'STRONGLY_SUPPORTED',
                     'limitations': 'not fully decompiled; obfuscated vendor SDK packages (e.g. x102x, z895z) not analyzed'})
    for n in ('resources.arsc',):
        if n in names:
            arts.append({'relative_path': emb(n), 'analyzed': False, 'method': 'size/listing only',
                         'findings': 'size=%d; resource names not resolved' % z.getinfo(n).file_size, 'status': 'NOT_ANALYZED',
                         'limitations': 'no arsc decoder run'})
    for n in sorted(names):
        if re.match(r'META-INF/(CERT\.(RSA|SF)|MANIFEST\.MF)$', n):
            arts.append({'relative_path': emb(n), 'analyzed': True, 'method': 'certinfo.py PKCS#7/X.509 parse (RSA) / manifest digest scan',
                         'findings': 'SHA-256 digests, sha256WithRSA 2048-bit; cert subject CN=Android; serial 00ff0641323cf95512; cert sha256 2d370c21f5dfd553d2a796314b70925fb38adeef90864c920bbbbb12887d3522' if n.endswith('RSA') else 'v1 signature file',
                         'status': 'CONFIRMED', 'limitations': 'chain/trust anchors not evaluated'})
    # native libs
    for r in inv[app]:
        base = r['path'].split('/')[-1]
        note = SO_NOTES.get(base, 'native library')
        arts.append({'relative_path': emb(r['path']), 'analyzed': True, 'method': 'ELF header/dynsym/needed/strings via elfinfo (no disassembly)',
                     'findings': '%s %d-bit stripped=%s size=%d jni_exports=%s needed=%s; %s; sha256=%s' % (r.get('machine'), r.get('cls', 0), r.get('stripped'), r['size'], r.get('jni_count'), ','.join(r.get('needed', [])[:6]), note, r['sha256'][:16]),
                     'status': 'STRONGLY_SUPPORTED', 'limitations': 'native code not disassembled; behavior in