"""Read-only native lib inventory for the three system APKs. Writes scratchpad/sysout/so_inventory.json"""
import sys, os, re, json, hashlib, zipfile, io, tempfile
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), '..'))
import elfinfo

ROOT = r'D:\Sakar Robotics Projects\C40 S-LS-M014C00-RW-F00-V246'
APKS = {
 'ServiceSystem': 'ServiceSystem-v2.0.11-0-g94566e5-prod-release-p-rk3288-signed.apk',
 'RemoteControl': 'RemoteControl-v3.3.1-0-gfe68b6d-prod-release-p-rk3288-signed.apk',
 'krlog': 'krlog-v1.2.6-0-g18a40d0-prod-release-p-rk3288-signed.apk',
}
INTEREST = ['socket', 'connect', 'bind', 'listen', 'accept', 'system', 'popen', 'execve', 'execl', 'execv', 'execvp', 'fork', 'dlopen', 'ptrace',
            'inotify_init', 'chmod', 'setuid', 'ioctl', 'SSL_connect', 'SSL_CTX_new', 'EVP_EncryptInit_ex', 'AES_encrypt', 'curl_easy_perform',
            'getaddrinfo', 'gethostbyname', 'mmap', 'mprotect', 'open', 'unlink', 'kill', 'prctl', 'sendto', 'recvfrom', 'daemon']
STR_RX = re.compile(rb'(https?://[\x21-\x7e]{6,}|wss?://[\x21-\x7e]{4,}|tcp://[\x21-\x7e]{4,}|/data/[\x21-\x7e]{3,}|/system/[\x21-\x7e]{3,}|/sdcard[\x21-\x7e]{0,}|/dev/[\x21-\x7e]{3,}|[A-Za-z0-9_.-]+\.(?:com|cn|net|org)\b[\x21-\x7e]{0,})')

def analyze(name, data):
    tmp = tempfile.NamedTemporaryFile(delete=False, suffix='.so'); tmp.write(data); tmp.close()
    try:
        e = elfinfo.elf(tmp.name)
    finally:
        os.unlink(tmp.name)
    rec = {'path': name, 'size': len(data), 'sha256': hashlib.sha256(data).hexdigest()}
    if not e:
        rec['elf'] = False; return rec
    jni = [s for s in e['dynsym_defined'] if s.startswith('Java_') or s == 'JNI_OnLoad']
    und = e['dynsym_undefined']
    rec.update(elf=True, cls=e['class'], machine=e['machine'], stripped=e['stripped'], needed=e['needed'],
               defined_count=len(e['dynsym_defined']), jni_exports=jni[:60], jni_count=len(jni),
               exports_sample=[s for s in e['dynsym_defined'] if s not in jni][:25],
               imports_interest=sorted({u for u in und if u in INTEREST}), imports_count=len(und),
               has_symtab=e['symtab_count'] > 0)
    strs = set()
    for m in STR_RX.finditer(data):
        s = m.group().decode('ascii', 'replace')
        if len(s) < 200 and not s.startswith(('/system/lib', '/system/bin/linker')): strs.add(s)
    keys = [s for s in strs if re.search(r'(http|ws:|tcp:|/data/|/sdcard|\.com|\.cn)', s)]
    rec['strings_notable'] = sorted(keys)[:30]
    for k in ('OpenSSL', 'mbedtls', 'libcurl', 'ffmpeg', 'FFmpeg', 'iflytek', 'IFLYTEK', 'Microsoft', 'oray', 'Oray', 'sunlogin', 'Sunlogin', 'tencent', 'Tencent', 'sentry', 'MMKV', 'lzma', 'xz', 'rokid', 'Rokid', 'frp', 'ONNX', 'onnx'):
        if k.encode() in data: rec.setdefault('vendor_markers', []).append(k)
    return rec

out = {}
for app, f in APKS.items():
    z = zipfile.ZipFile(os.path.join(ROOT, f))
    recs = []
    for n in sorted(z.namelist()):
        if (n.startswith('lib/') and n.endswith('.so')) or (n.startswith('assets/') and not n.endswith(('.json', '.txt', '.ini', '.param', '.config', '.pem', '.gif'))
                                                            and z.read(n)[:4] == b'\x7fELF'):
            recs.append(analyze(n, z.read(n)))
    out[app] = recs
dst = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..', 'sysout'); os.makedirs(dst, exist_ok=True)
json.dump(out, open(os.path.join(dst, 'so_inventory.json'), 'w', encoding='utf-8'), indent=1)
for app, recs in out.items():
    print('==', app, len(recs))
    for r in recs:
        print(' ', r['path'].split('/')[-1], r.get('machine'), r['size'], 'jni=%s' % r.get('jni_count'), 'need=%s' % ','.join(r.get('needed', [])[:6]), 'int=%s' % ','.join(r.get('imports_interest', [])[:8]), r.get('vendor_markers', ''))
