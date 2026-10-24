"""Read-only Dalvik bytecode scanner. Builds per-method reference index (const-string, invoke, field, new-instance, const-class).
Usage: dexscan.py build <apk> <outpickle>   |  dexq.py ... for queries"""
import struct, sys, zipfile, pickle, os

W = [1]*256
def setw(rng, w):
    for i in rng: W[i] = w
setw([0x02,0x05,0x08,0x13,0x15,0x16,0x19,0x1a,0x1c,0x1f,0x20,0x22,0x23,0x29],2)
setw([0x03,0x06,0x09,0x14,0x17,0x1b,0x24,0x25,0x26,0x2a,0x2b,0x2c],3)
W[0x18]=5
setw(range(0x2d,0x32),2); setw(range(0x32,0x3e),2); setw(range(0x44,0x52),2); setw(range(0x52,0x60),2); setw(range(0x60,0x6e),2)
setw(range(0x6e,0x73),3); setw(range(0x74,0x79),3); setw(range(0x90,0xb0),2); setw(range(0xd0,0xe3),2)
W[0xfa]=4; W[0xfb]=4; W[0xfc]=3; W[0xfd]=3; W[0xfe]=2; W[0xff]=2

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
        protos.append(strs[sh])
    fields = []
    for i in range(nfield):
        c, t, n = struct.unpack_from('<HHI', d, fieldoff + 8 * i)
        fields.append((types[c], strs[n], types[t]))
    meths = []
    for i in range(nmeth):
        c, pr, n = struct.unpack_from('<HHI', d, methoff + 8 * i)
        meths.append((types[c], strs[n], protos[pr]))
    out = {}
    for i in range(nclass):
        ci, af, sup, ifc, src, ann, cd, sv = struct.unpack_from('<IIIIIIII', d, classoff + 32 * i)
        cname = types[ci]
        ent = {'super': types[sup] if sup != 0xffffffff else None, 'access': af, 'methods': {}}
        if ifc:
            n, = struct.unpack_from('<I', d, ifc)
            ent['ifaces'] = [types[struct.unpack_from('<H', d, ifc+4+2*k)[0]] for k in range(n)]
        out[cname] = ent
        if not cd: continue
        p = cd
        sf, p = uleb(d, p); inf, p = uleb(d, p); dm, p = uleb(d, p); vm, p = uleb(d, p)
        for _ in range(sf + inf):
            _, p = uleb(d, p); _, p = uleb(d, p)
        for cnt in (dm, vm):
            idx = 0
            for _ in range(cnt):
                diff, p = uleb(d, p); acc, p = uleb(d, p); code, p = uleb(d, p)
                idx += diff
                m = meths[idx]
                key = m[1] + m[2]
                info = {'acc': acc, 'str': [], 'inv': [], 'fld': [], 'new': [], 'cls': []}
                if code:
                    regs, ins, outs, tries, dbg, isz = struct.unpack_from('<HHHHII', d, code)
                    q = code + 16; end = q + isz * 2
                    if isz <= 6: info['insn'] = d[q:end].hex()
                    while q < end:
                        u, = struct.unpack_from('<H', d, q)
                        op = u & 0xff
                        if op == 0x00 and u != 0:
                            if u == 0x0100:
                                sz, = struct.unpack_from('<H', d, q+2); q += (sz*2+4)*2
                            elif u == 0x0200:
                                sz, = struct.unpack_from('<H', d, q+2); q += (sz*4+2)*2
                            elif u == 0x0300:
                                wd, sz = struct.unpack_from('<HI', d, q+2); q += (((wd*sz+1)//2)+4)*2
                            else: q += 2
                            continue
                        w = W[op]
                        if op == 0x1a:
                            info['str'].append(strs[struct.unpack_from('<H', d, q+2)[0]])
                        elif op == 0x1b:
                            info['str'].append(strs[struct.unpack_from('<I', d, q+2)[0]])
                        elif 0x6e <= op <= 0x72 or 0x74 <= op <= 0x78 or op in (0xfa,):
                            mi, = struct.unpack_from('<H', d, q+2)
                            mm = meths[mi]
                            info['inv'].append(mm[0] + '->' + mm[1])
                        elif 0x52 <= op <= 0x6d:
                            fi, = struct.unpack_from('<H', d, q+2)
                            ff = fields[fi]
                            info['fld'].append(('put:' if (op in range(0x59,0x60) or op in range(0x67,0x6e)) else 'get:') + ff[0] + '.' + ff[1])
                        elif op == 0x22:
                            info['new'].append(types[struct.unpack_from('<H', d, q+2)[0]])
                        elif op == 0x1c:
                            info['cls'].append(types[struct.unpack_from('<H', d, q+2)[0]])
                        q += w * 2
                ent['methods'][key] = info
    return out

def build(apk):
    z = zipfile.ZipFile(apk)
    res = {}
    for n in sorted(z.namelist()):
        if n.endswith('.dex') and '/' not in n:
            r = parse(z.read(n))
            for c, e in r.items():
                e['dex'] = n
                res[c] = e
    return res

if __name__ == '__main__':
    if sys.argv[1] == 'build':
        r = build(sys.argv[2])
        pickle.dump(r, open(sys.argv[3], 'wb'))
        print(len(r), 'classes', sum(len(e['methods']) for e in r.values()), 'methods')
