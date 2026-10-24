"""Read-only DEX bytecode scanner: builds sqlite db of methods, const-strings, invokes, field accesses, annotations.
Usage: dexcode.py <dexdir> <out.db>"""
import struct, sys, sqlite3, os
sys.stdout.reconfigure(encoding='utf-8', errors='replace')
W = [1] * 256
def setw(a, b, w):
    for i in range(a, b + 1):
        W[i] = w
setw(0x02, 0x02, 2); setw(0x03, 0x03, 3); setw(0x05, 0x05, 2); setw(0x06, 0x06, 3); setw(0x08, 0x08, 2); setw(0x09, 0x09, 3)
setw(0x13, 0x13, 2); setw(0x14, 0x14, 3); setw(0x15, 0x16, 2); setw(0x17, 0x17, 3); setw(0x18, 0x18, 5); setw(0x19, 0x19, 2)
setw(0x1a, 0x1a, 2); setw(0x1b, 0x1b, 3); setw(0x1c, 0x1c, 2); setw(0x1f, 0x20, 2); setw(0x22, 0x23, 2); setw(0x24, 0x26, 3)
setw(0x29, 0x29, 2); setw(0x2a, 0x2c, 3); setw(0x2d, 0x3d, 2); setw(0x44, 0x6d, 2); setw(0x6e, 0x72, 3); setw(0x74, 0x78, 3)
setw(0x90, 0xaf, 2); setw(0xd0, 0xe2, 2); setw(0xfa, 0xfb, 4); setw(0xfc, 0xfd, 3); setw(0xfe, 0xff, 2)

def uleb(d, p):
    r = 0; s = 0
    while True:
        b = d[p]; p += 1
        r |= (b & 0x7f) << s
        if not b & 0x80:
            break
        s += 7
    return r, p

def mutf(d, o):
    _, p = uleb(d, o)
    e = d.index(b'\0', p)
    return d[p:e].decode('utf-8', 'replace')

def enc_val(d, p, strs, types):
    h = d[p]; p += 1
    t = h & 0x1f; a = h >> 5
    if t == 0x1c:
        n, p = uleb(d, p); out = []
        for _ in range(n):
            v, p = enc_val(d, p, strs, types)
            out.append(v)
        return out, p
    if t == 0x1d:
        ty, p = uleb(d, p); n, p = uleb(d, p)
        for _ in range(n):
            _, p = uleb(d, p)
            _, p = enc_val(d, p, strs, types)
        return '@', p
    if t == 0x1e:
        return None, p
    if t == 0x1f:
        return bool(a), p
    sz = a + 1
    v = int.from_bytes(d[p:p + sz], 'little')
    p += sz
    if t == 0x17:
        return strs[v], p
    if t == 0x18:
        return types[v], p
    return v, p

def run(dexdir, out):
    if os.path.exists(out):
        os.remove(out)
    db = sqlite3.connect(out)
    db.executescript('''create table m(id integer primary key,dex text,cls text,name text,proto text,insns integer);
    create table s(mid int,str text);create table c(mid int,ccls text,cname text,cproto text,op int);
    create table f(mid int,fcls text,fname text,ftype text,op int);create table a(cls text,mname text,atype text,vals text,kind text);
    create table k(cls text,sup text,ifaces text,dex text,flags int);create table sv(cls text,fname text,ftype text,val text);''')
    names = ['classes.dex'] + ['classes%d.dex' % i for i in range(2, 8)]
    mid = 0
    for dn in names:
        d = open(os.path.join(dexdir, dn), 'rb').read()
        hdr = struct.unpack_from('<8sI20sIIIIIIIIIIIIIIIIIIII', d, 0)
        (_, _, _, fsize, _, _, _, _, map_off, nstr, stroff, ntype, typeoff, nproto, protooff, nfield, fieldoff, nmeth, methoff, nclass, classoff, dsz, doff) = hdr
        sids = struct.unpack_from('<%dI' % nstr, d, stroff)
        strs = [mutf(d, o) for o in sids]
        types = [strs[i] for i in struct.unpack_from('<%dI' % ntype, d, typeoff)]
        protos = []
        for i in range(nproto):
            sh, rt, po = struct.unpack_from('<III', d, protooff + 12 * i)
            ps = []
            if po:
                n = struct.unpack_from('<I', d, po)[0]
                ps = [types[x] for x in struct.unpack_from('<%dH' % n, d, po + 4)]
            protos.append('(' + ''.join(ps) + ')' + types[rt])
        fields = []
        for i in range(nfield):
            c, t, n = struct.unpack_from('<HHI', d, fieldoff + 8 * i)
            fields.append((types[c], strs[n], types[t]))
        meths = []
        for i in range(nmeth):
            c, pr, n = struct.unpack_from('<HHI', d, methoff + 8 * i)
            meths.append((types[c], strs[n], protos[pr]))
        mrows = []; srows = []; crows = []; frows = []; arows = []; krows = []; svrows = []
        for ci in range(nclass):
            cidx, acc, sup, ifo, src, annoff, cdo, sv = struct.unpack_from('<IIIIIIII', d, classoff + 32 * ci)
            cname = types[cidx]
            ifs = []
            if ifo:
                n = struct.unpack_from('<I', d, ifo)[0]
                ifs = [types[x] for x in struct.unpack_from('<%dH' % n, d, ifo + 4)]
            krows.append((cname, types[sup] if sup != 0xffffffff else None, ','.join(ifs), dn, acc))
            if annoff:
                co, fs, ms, ps = struct.unpack_from('<IIII', d, annoff)
                def aset(off):
                    n = struct.unpack_from('<I', d, off)[0]; res = []
                    for eo in struct.unpack_from('<%dI' % n, d, off + 4):
                        vis = d[eo]; p = eo + 1
                        ty, p = uleb(d, p); cnt, p = uleb(d, p); els = {}
                        for _ in range(cnt):
                            ni, p = uleb(d, p)
                            v, p = enc_val(d, p, strs, types)
                            els[strs[ni]] = v
                        res.append((types[ty], els, vis))
                    return res
                if co:
                    for ty, els, vis in aset(co):
                        arows.append((cname, None, ty, repr(els)[:600], 'class'))
                q = annoff + 16
                for _ in range(fs):
                    fi, ao = struct.unpack_from('<II', d, q); q += 8
                    for ty, els, vis in aset(ao):
                        arows.append((cname, fields[fi][1], ty, repr(els)[:600], 'field'))
                for _ in range(ms):
                    mi, ao = struct.unpack_from('<II', d, q); q += 8
                    for ty, els, vis in aset(ao):
                        arows.append((cname, meths[mi][1] + meths[mi][2], ty, repr(els)[:600], 'method'))
            if not cdo:
                continue
            p = cdo
            sf, p = uleb(d, p); inf, p = uleb(d, p); dm, p = uleb(d, p); vm, p = uleb(d, p)
            sfl = []
            fidx = 0
            for _ in range(sf):
                dl, p = uleb(d, p); _, p = uleb(d, p)
                fidx += dl; sfl.append(fidx)
            for _ in range(inf):
                _, p = uleb(d, p)
                _, p = uleb(d, p)
            if sv and sfl:
                try:
                    n, q2 = uleb(d, sv)
                    for i in range(n):
                        v, q2 = enc_val(d, q2, strs, types)
                        if i < len(sfl) and v is not None and not isinstance(v, (bool,)) :
                            fc, fn, ft = fields[sfl[i]]
                            svrows.append((cname, fn, ft, repr(v)[:300]))
                except Exception as ex:
                    pass
            for cnt in (dm, vm):
                midx = 0
                for _ in range(cnt):
                    dlt, p = uleb(d, p); macc, p = uleb(d, p); code, p = uleb(d, p)
                    midx += dlt
                    mc, mn, mp = meths[midx]
                    mid += 1
                    if not code:
                        mrows.append((mid, dn, mc, mn, mp, 0))
                        continue
                    isz = struct.unpack_from('<I', d, code + 12)[0]
                    mrows.append((mid, dn, mc, mn, mp, isz))
                    base = code + 16
                    insns = struct.unpack_from('<%dH' % isz, d, base)
                    i = 0
                    ss = set(); cc = set(); ff = set(); tt = set()
                    while i < isz:
                        u = insns[i]; op = u & 0xff
                        if op == 0 and u != 0:
                            if u == 0x100:
                                i += insns[i + 1] * 2 + 4
                            elif u == 0x200:
                                i += insns[i + 1] * 4 + 2
                            elif u == 0x300:
                                i += (insns[i + 1] * (insns[i + 2] | (insns[i + 3] << 16)) + 1) // 2 + 4
                            else:
                                i += 1
                            continue
                        if op == 0x1a:
                            ss.add(strs[insns[i + 1]])
                        elif op == 0x1b:
                            ss.add(strs[insns[i + 1] | (insns[i + 2] << 16)])
                        elif 0x6e <= op <= 0x72 or 0x74 <= op <= 0x78:
                            cc.add((insns[i + 1], op))
                        elif 0x52 <= op <= 0x6d:
                            ff.add((insns[i + 1], op))
                        elif op == 0x1c or op == 0x22:
                            tt.add((insns[i + 1], op))
                        i += W[op]
                    for x in ss:
                        srows.append((mid, x))
                    for x, op in cc:
                        cl, nm, pr = meths[x]
                        crows.append((mid, cl, nm, pr, op))
                    for x, op in ff:
                        fc, fn, ft = fields[x]
                        frows.append((mid, fc, fn, ft, op))
                    for x, op in tt:
                        frows.append((mid, types[x], '', '', op))
        db.executemany('insert into m values(?,?,?,?,?,?)', mrows)
        db.executemany('insert into s values(?,?)', srows)
        db.executemany('insert into c values(?,?,?,?,?)', crows)
        db.executemany('insert into f values(?,?,?,?,?)', frows)
        db.executemany('insert into a values(?,?,?,?,?)', arows)
        db.executemany('insert into k values(?,?,?,?,?)', krows)
        db.executemany('insert into sv values(?,?,?,?)', svrows)
        db.commit()
        print(dn, nclass, len(mrows), len(srows), len(crows), flush=True)
    db.executescript('create index i1 on s(mid);create index i2 on c(mid);create index i3 on m(cls);create index i4 on c(ccls,cname);create index i5 on s(str);create index i6 on f(mid);create index i7 on a(cls);create index i8 on a(atype)')
    db.commit()

run(sys.argv[1], sys.argv[2])
