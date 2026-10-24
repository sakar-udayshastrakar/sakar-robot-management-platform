"""Read-only inspection of .deb packages: parse ar members, control, file lists. Extracts into scratch work dir."""
import os, sys, io, tarfile, json, lzma, gzip, bz2, hashlib, re

T = sys.argv[1]
WORK = sys.argv[2]
OUT = sys.argv[3]
base = os.path.join(T, 'slam-ros-v2.10.9-c2-0-g58a75a7-app-update-package')

def ar_members(path):
    data = open(path, 'rb').read()
    assert data[:8] == b'!<arch>\n', path
    i = 8
    out = {}
    while i < len(data):
        hdr = data[i:i+60]
        if len(hdr) < 60:
            break
        name = hdr[:16].decode().strip().rstrip('/')
        size = int(hdr[48:58].decode().strip())
        body = data[i+60:i+60+size]
        out[name] = body
        i += 60 + size + (size & 1)
    return out

def open_tar(name, body):
    if name.endswith('.xz'):
        body = lzma.decompress(body)
    elif name.endswith('.gz'):
        body = gzip.decompress(body)
    elif name.endswith('.bz2'):
        body = bz2.decompress(body)
    return tarfile.open(fileobj=io.BytesIO(body))

res = {}
for root, dirs, files in os.walk(base):
    for fn in sorted(files):
        if not fn.endswith('.deb'):
            continue
        p = os.path.join(root, fn)
        rel = os.path.relpath(p, T).replace('\\', '/')
        try:
            m = ar_members(p)
            ctrl_name = [n for n in m if n.startswith('control.tar')][0]
            data_name = [n for n in m if n.startswith('data.tar')][0]
            ct = open_tar(ctrl_name, m[ctrl_name])
            control = ct.extractfile('./control').read().decode('utf-8', 'replace') if './control' in ct.getnames() else ''
            scripts = {}
            for n in ct.getnames():
                if re.search(r'(preinst|postinst|prerm|postrm)$', n):
                    scripts[os.path.basename(n)] = ct.extractfile(n).read().decode('utf-8', 'replace')
            dt = open_tar(data_name, m[data_name])
            files_l = []
            for mem in dt.getmembers():
                files_l.append({'path': mem.name, 'size': mem.size, 'type': 'dir' if mem.isdir() else ('link->' + mem.linkname if mem.issym() else 'file'), 'mode': oct(mem.mode)})
            res[rel] = {'members': list(m.keys()), 'control': control, 'maintainer_scripts': scripts, 'files': files_l}
            # extract data into work dir for later inspection (scratch only)
            dest = os.path.join(WORK, fn[:-4])
            os.makedirs(dest, exist_ok=True)
            dt.extractall(dest, filter='data') if hasattr(tarfile, 'data_filter') else None
        except Exception as e:
            res[rel] = {'error': repr(e)}
json.dump(res, open(OUT, 'w', encoding='utf-8'), indent=1)
print(len(res), 'debs processed;', sum(1 for v in res.values() if 'error' in v), 'errors')
