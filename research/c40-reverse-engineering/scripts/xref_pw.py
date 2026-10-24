import re, glob, os, hashlib
os.chdir(r'C:\Users\sakar\AppData\Local\Temp\claude\D--Sakar-Robotics-Projects-support-portal\5ba6b382-e7e8-4ce5-92df-45edd177f75a\scratchpad')
fs = glob.glob('debwork/ros-indigo-*/**/send_depth_node', recursive=True)
print(fs)
d = open(fs[0], 'rb').read()
m = re.search(rb'echo\s+(\S{6,20})\s*\|\s*sudo\s+-S', d)
if not m:
    print('no echo|sudo -S pattern; showing sudo contexts (redacted lengths)')
    for mm in re.finditer(rb'[\x20-\x7e]{0,60}sudo[\x20-\x7e]{0,60}', d):
        print(mm.group()[:120])
else:
    pw = m.group(1)
    print('candidate len', len(pw), 'sha256[:12]', hashlib.sha256(pw).hexdigest()[:12])
    hits = []
    for root in ['debwork', 'apkx', 'dex', 'manifests']:
        for r, _, files in os.walk(root):
            for f in files:
                p = os.path.join(r, f)
                try:
                    if os.path.getsize(p) > 200_000_000:
                        continue
                    if pw in open(p, 'rb').read():
                        hits.append(p.replace(chr(92), '/')[:150])
                except Exception:
                    pass
    print(len(hits))
    for h in hits[:40]:
        print(' ', h)
