import os, sys, hashlib, json, time

T = sys.argv[1]
OUT = sys.argv[2]
RES = os.path.join(T, 'REVERSE_ENGINEERING_RESULTS')

MAGICS = [
    (b'\x7fELF', 'ELF'),
    (b'PK\x03\x04', 'ZIP/APK/JAR'),
    (b'!<arch>\n', 'ar archive (deb)'),
    (b'\x1f\x8b', 'gzip'),
    (b'SQLite format 3\x00', 'SQLite3'),
    (b'dex\n', 'DEX'),
    (b'\x89PNG', 'PNG'),
    (b'{', 'JSON?'),
    (b'#!', 'script'),
]

def sniff(path):
    with open(path, 'rb') as f:
        h = f.read(64)
    for m, n in MAGICS:
        if h.startswith(m):
            return n
    ext = os.path.splitext(path)[1].lower()
    return 'unknown(' + ext + ')'

rows = []
for root, dirs, files in os.walk(T):
    if os.path.abspath(root).startswith(os.path.abspath(RES)):
        continue
    for fn in sorted(files):
        p = os.path.join(root, fn)
        h = hashlib.sha256(); m = hashlib.md5()
        with open(p, 'rb') as f:
            while True:
                b = f.read(1 << 20)
                if not b:
                    break
                h.update(b); m.update(b)
        rows.append({
            'relative_path': os.path.relpath(p, T).replace('\\', '/'),
            'file_name': fn,
            'file_type': sniff(p),
            'size_bytes': os.path.getsize(p),
            'sha256': h.hexdigest(),
            'md5': m.hexdigest(),
        })
rows.sort(key=lambda r: r['relative_path'])
json.dump({'generated': time.strftime('%Y-%m-%dT%H:%M:%S'), 'target': T, 'count': len(rows), 'artifacts': rows}, open(OUT, 'w', encoding='utf-8'), indent=1)
print(len(rows), 'artifacts hashed')
