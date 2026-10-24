"""Read-only DEX parser: dumps strings, class->methods/fields, to JSON lines.
Usage: dexinfo.py <apk> <outdir>   (writes <outdir>/<dexname>.classes.json and .strings.txt)"""
import struct, sys, zipfile, json, os

def uleb(d, p):
    r = 0; s = 0
    while True:
        b = d[p]; p += 1
        r |= (b & 0x7f) << s
        if not b & 0x80: break
        s += 7
    return r, p

def parse(d):
    (magic, chk, sig, fsize, hsize, endian, link_sz, link_off, map_off, nstr, stroff, ntype, typeoff,
     nproto, protooff, nfield, fieldoff, nmeth, methoff, nclass, classoff, dsz, doff) = struct.unpack_from('<8sI20sIIIIIIIIIIIIIIIIIIII', d, 0)
    sids = struct.unpack_from('<%dI' % nstr, d, stroff)
    strs = []
    for o in sids:
        _, p = uleb(d, o)
        e = d.index(b'\0', p)
        strs.append(d[p:e].decode('utf-8', 'replace'))
    types = [strs[i] for i in struct.unpack_from('<%dI' % ntype, d, typeoff)]
    protos = []
    for i in range(nproto):
        sh, rt, po = struct.unpack_from('<III', d, protooff + 12 * i)
        protos.append((strs[sh], types[rt]))
    fields = []
    for i in range(nfield):
        c, t, n = struct.unpack_from('<HHI', d, fieldoff + 8 * i)
        fields.append((types[c], types[t], strs[n]))
    meths = []
    for i in range(nmeth):
        c, pr, n = struct.unpack_from('<HHI', d, methoff + 8 * i)
        meths.append((types[c], strs[n], protos[pr][0]))
    classes = {}
    for m in meths:
        classes.setdefault(m[0], {'methods': [], 'fields': []})['methods'].append(m[1] + m[2])
    for f in fields:
        classes.setdefault(f[0], {'methods': [], 'fields': []})['fields'].append(f[2] + ':' + f[1])
    defined = []
    for i in range(nclass):
        ci, af, sup, ifc, src, ann, cd, sv = struct.unpack_from('<IIIIIIII', d, classoff + 32 * i)
        defined.append({'class': types[ci], 'super': types[sup] if sup != 0xffffffff else None, 'source': strs[src] if src != 0xffffffff else None})
    return strs, defined, classes

if __name__ == '__main__':
    z = zipfile.ZipFile(sys.argv[1]); out = sys.argv[2]; os.makedirs(out, exist_ok=True)
    for n in z.namelist():
        if n.endswith('.dex') and '/' not in n:
            strs, defined, classes = parse(z.read(n))
            for c in defined:
                c.update(classes.get(c['class'], {}))
            json.dump(defined, open(os.path.join(out, n + '.classes.json'), 'w', encoding='utf-8'))
            open(os.path.join(out, n + '.strings.txt'), 'w', encoding='utf-8').write('\n'.join(strs))
            print(n, 'strings', len(strs), 'classes', len(defined))
