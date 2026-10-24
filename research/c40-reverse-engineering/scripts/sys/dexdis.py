"""Mini Dalvik disassembler (read-only). Usage: dexdis.py <apk> <class-regex> [method-regex]"""
import struct, sys, zipfile, re, os
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from dexscan import uleb

N = {}
def reg(op, name, fmt): N[op] = (name, fmt)
names = ("00 nop 10x|01 move 12x|02 move/from16 22x|03 move/16 32x|04 move-wide 12x|05 move-wide/from16 22x|06 move-wide/16 32x|"
"07 move-object 12x|08 move-object/from16 22x|09 move-object/16 32x|0a move-result 11x|0b move-result-wide 11x|"
"0c move-result-object 11x|0d move-exception 11x|0e return-void 10x|0f return 11x|10 return-wide 11x|11 return-object 11x|"
"12 const/4 11n|13 const/16 21s|14 const 31i|15 const/high16 21h|16 const-wide/16 21s|17 const-wide/32 31i|18 const-wide 51l|"
"19 const-wide/high16 21h|1a const-string 21c|1b const-string/jumbo 31c|1c const-class 21c|1d monitor-enter 11x|1e monitor-exit 11x|"
"1f check-cast 21c|20 instance-of 22c|21 array-length 12x|22 new-instance 21c|23 new-array 22c|24 filled-new-array 35c|"
"25 filled-new-array/range 3rc|26 fill-array-data 31t|27 throw 11x|28 goto 10t|29 goto/16 20t|2a goto/32 30t|2b packed-switch 31t|"
"2c sparse-switch 31t|2d cmpl-float 23x|2e cmpg-float 23x|2f cmpl-double 23x|30 cmpg-double 23x|31 cmp-long 23x|32 if-eq 22t|"
"33 if-ne 22t|34 if-lt 22t|35 if-ge 22t|36 if-gt 22t|37 if-le 22t|38 if-eqz 21t|39 if-nez 21t|3a if-ltz 21t|3b if-gez 21t|"
"3c if-gtz 21t|3d if-lez 21t|44 aget 23x|45 aget-wide 23x|46 aget-object 23x|47 aget-boolean 23x|48 aget-byte 23x|49 aget-char 23x|"
"4a aget-short 23x|4b aput 23x|4c aput-wide 23x|4d aput-object 23x|4e aput-boolean 23x|4f aput-byte 23x|50 aput-char 23x|"
"51 aput-short 23x|52 iget 22c|53 iget-wide 22c|54 iget-object 22c|55 iget-boolean 22c|56 iget-byte 22c|57 iget-char 22c|"
"58 iget-short 22c|59 iput 22c|5a iput-wide 22c|5b iput-object 22c|5c iput-boolean 22c|5d iput-byte 22c|5e iput-char 22c|"
"5f iput-short 22c|60 sget 21c|61 sget-wide 21c|62 sget-object 21c|63 sget-boolean 21c|64 sget-byte 21c|65 sget-char 21c|"
"66 sget-short 21c|67 sput 21c|68 sput-wide 21c|69 sput-object 21c|6a sput-boolean 21c|6b sput-byte 21c|6c sput-char 21c|"
"6d sput-short 21c|6e invoke-virtual 35c|6f invoke-super 35c|70 invoke-direct 35c|71 invoke-static 35c|72 invoke-interface 35c|"
"74 invoke-virtual/range 3rc|75 invoke-super/range 3rc|76 invoke-direct/range 3rc|77 invoke-static/range 3rc|78 invoke-interface/range 3rc")
for e in names.split('|'):
    a, b, c = e.split(); reg(int(a, 16), b, c)
un = ("7b neg-int 7c not-int 7d neg-long 7e not-long 7f neg-float 80 neg-double 81 int-to-long 82 int-to-float 83 int-to-double "
"84 long-to-int 85 long-to-float 86 long-to-double 87 float-to-int 88 float-to-long 89 float-to-double 8a double-to-int "
"8b double-to-long 8c double-to-float 8d int-to-byte 8e int-to-char 8f int-to-short").split()
for i in range(0, len(un), 2): reg(int(un[i], 16), un[i+1], '12x')
ops_int = "add sub mul div rem and or xor shl shr ushr".split()
for i, o in enumerate(ops_int):
    reg(0x90 + i, o + '-int', '23x'); reg(0xb0 + i, o + '-int/2addr', '12x')
    reg(0x9b + i, o + '-long', '23x'); reg(0xbb + i, o + '-long/2addr', '12x')
for i, o in enumerate("add sub mul div rem".split()):
    reg(0xa6 + i, o + '-float', '23x'); reg(0xc6 + i, o + '-float/2addr', '12x')
    reg(0xab + i, o + '-double', '23x'); reg(0xcb + i, o + '-double/2addr', '12x')
for i, o in enumerate("add rsub mul div rem and or xor".split()):
    reg(0xd0 + i, o + '-int/lit16', '22s'); reg(0xd8 + i, o + '-int/lit8', '22b')
for i, o in enumerate("shl shr ushr".split()): reg(0xe0 + i, o + '-int/lit8', '22b')

def load(apk):
    z = zipfile.ZipFile(apk); res = []
    for n in sorted(z.namelist()):
        if n.endswith('.dex') and '/' not in n:
            res.append((n, z.read(n)))
    return res

def dis(d, rxc, rxm):
    (magic, chk, sig, fsize, hsize, endian, link_sz, link_off, map_off, nstr, stroff, ntype, typeoff,
     nproto, protooff, nfield, fieldoff, nmeth, methoff, nclass, classoff, dsz, doff) = struct.unpack_from('<8sI20sIIIIIIIIIIIIIIIIIIII', d, 0)
    strs = []
    for o in struct.unpack_from('<%dI' % nstr, d, stroff):
        _, p = uleb(d, o); strs.append(d[p:d.index(b'\0', p)].decode('utf-8', 'replace'))
    types = [strs[i] for i in struct.unpack_from('<%dI' % ntype, d, typeoff)]
    protos = []
    for i in range(nproto):
        sh, rt, po = struct.unpack_from('<III', d, protooff + 12*i)
        params = []
        if po:
            n, = struct.unpack_from('<I', d, po)
            params = [types[struct.unpack_from('<H', d, po+4+2*k)[0]] for k in range(n)]
        protos.append((types[rt], params))
    fields = [(types[struct.unpack_from('<H', d, fieldoff+8*i)[0]], strs[struct.unpack_from('<I', d, fieldoff+8*i+4)[0]]) for i in range(nfield)]
    meths = []
    for i in range(nmeth):
        c, pr, n = struct.unpack_from('<HHI', d, methoff + 8*i)
        meths.append((types[c], strs[n], protos[pr]))
    def sn(t): return t.split('/')[-1].rstrip(';')
    def mname(i):
        m = meths[i]
        return '%s->%s(%s)%s' % (sn(m[0]), m[1], ','.join(sn(p) for p in m[2][1]), sn(m[2][0]))
    for ci in range(nclass):
        cidx, af, sup, ifc, src, ann, cd, sv = struct.unpack_from('<IIIIIIII', d, classoff + 32*ci)
        cname = types[cidx]
        if not rxc.search(cname) or not cd: continue
        p = cd
        sf, p = uleb(d, p); inf, p = uleb(d, p); dm, p = uleb(d, p); vm, p = uleb(d, p)
        for _ in range(sf + inf):
            _, p = uleb(d, p); _, p = uleb(d, p)
        for cnt in (dm, vm):
            idx = 0
            for _ in range(cnt):
                diff, p = uleb(d, p); acc, p = uleb(d, p); code, p = uleb(d, p); idx += diff
                m = meths[idx]
                key = m[1] + '(' + ','.join(sn(x) for x in m[2][1]) + ')'
                if rxm and not rxm.search(m[1] + '(' + ','.join(m[2][1]) + ')'): continue
                print('\n== %s.%s  acc=0x%x' % (cname, key, acc))
                if not code: continue
                regs, ins, outs, tries, dbg, isz = struct.unpack_from('<HHHHII', d, code)
                print('   regs=%d ins=%d' % (regs, ins))
                q = code + 16; base = q; end = q + isz*2
                while q < end:
                    u, = struct.unpack_from('<H', d, q); op = u & 0xff; off = (q - base)//2
                    if op == 0 and u != 0:
                        if u == 0x0100:
                            sz, = struct.unpack_from('<H', d, q+2); q += (sz*2+4)*2
                        elif u == 0x0200:
                            sz, = struct.unpack_from('<H', d, q+2); q += (sz*4+2)*2
                        else:
                            wd, sz = struct.unpack_from('<HI', d, q+2); q += (((wd*sz+1)//2)+4)*2
                        print('  %04x: <payload>' % off); continue
                    if op not in N:
                        print('  %04x: op%02x' % (off, op)); q += 2; continue
                    nm, fmt = N[op]
                    A = (u >> 8) & 0xf; Bn = (u >> 12) & 0xf; AA = u >> 8
                    def w(k): return struct.unpack_from('<H', d, q+2*k)[0]
                    def sw(k): return struct.unpack_from('<h', d, q+2*k)[0]
                    def dw(k): return struct.unpack_from('<I', d, q+2*k)[0]
                    txt = ''; ln = 1
                    if fmt == '10x': pass
                    elif fmt == '12x': txt = 'v%d, v%d' % (A, Bn)
                    elif fmt == '11n': txt = 'v%d, #%d' % (A, Bn - 16 if Bn > 7 else Bn)
                    elif fmt == '11x': txt = 'v%d' % AA
                    elif fmt == '10t': txt = '%+d' % (AA - 256 if AA > 127 else AA)
                    elif fmt == '20t': txt = '%+d' % sw(1); ln = 2
                    elif fmt == '22x': txt = 'v%d, v%d' % (AA, w(1)); ln = 2
                    elif fmt == '21t': txt = 'v%d, %+d' % (AA, sw(1)); ln = 2
                    elif fmt == '21s': txt = 'v%d, #%d' % (AA, sw(1)); ln = 2
                    elif fmt == '21h': txt = 'v%d, #0x%x0000' % (AA, w(1)); ln = 2
                    elif fmt == '21c':
                        k = w(1)
                        if op == 0x1a: txt = 'v%d, "%s"' % (AA, strs[k].replace('\n', '\\n')[:200])
                        elif 0x60 <= op <= 0x6d: txt = 'v%d, %s.%s' % (AA, sn(fields[k][0]), fields[k][1])
                        else: txt = 'v%d, %s' % (AA, types[k])
                        ln = 2
                    elif fmt == '23x': txt = 'v%d, v%d, v%d' % (AA, w(1) & 0xff, w(1) >> 8); ln = 2
                    elif fmt == '22b': txt = 'v%d, v%d, #%d' % (AA, w(1) & 0xff, struct.unpack_from('<b', d, q+3)[0]); ln = 2
                    elif fmt == '22t': txt = 'v%d, v%d, %+d' % (A, Bn, sw(1)); ln = 2
                    elif fmt == '22s': txt = 'v%d, v%d, #%d' % (A, Bn, sw(1)); ln = 2
                    elif fmt == '22c':
                        k = w(1)
                        if 0x52 <= op <= 0x5f: txt = 'v%d, v%d, %s.%s' % (A, Bn, sn(fields[k][0]), fields[k][1])
                        else: txt = 'v%d, v%d, %s' % (A, Bn, types[k])
                        ln = 2
                    elif fmt == '30t': txt = '%+d' % struct.unpack_from('<i', d, q+2)[0]; ln = 3
                    elif fmt == '32x': txt = 'v%d, v%d' % (w(1), w(2)); ln = 3
                    elif fmt == '31i': txt = 'v%d, #%d' % (AA, struct.unpack_from('<i', d, q+2)[0]); ln = 3
                    elif fmt == '31t': txt = 'v%d, %+d' % (AA, struct.unpack_from('<i', d, q+2)[0]); ln = 3
                    elif fmt == '31c': txt = 'v%d, "%s"' % (AA, strs[dw(1)][:200]); ln = 3
                    elif fmt == '35c':
                        cnt2 = Bn; k = w(1); rr = w(2)
                        regl = [rr & 0xf, (rr >> 4) & 0xf, (rr >> 8) & 0xf, (rr >> 12) & 0xf, A][:cnt2]
                        ref = mname(k) if op != 0x24 else types[k]
                        txt = '{%s}, %s' % (','.join('v%d' % r for r in regl), ref); ln = 3
                    elif fmt == '3rc':
                        cnt2 = AA; k = w(1); r0 = w(2)
                        txt = '{v%d..v%d}, %s' % (r0, r0 + cnt2 - 1, mname(k)); ln = 3
                    elif fmt == '51l': txt = 'v%d, #%d' % (AA, struct.unpack_from('<q', d, q+2)[0]); ln = 5
                    print('  %04x: %s %s' % (off, nm, txt))
                    q += ln*2

if __name__ == '__main__':
    rxc = re.compile(sys.argv[2]); rxm = re.compile(sys.argv[3]) if len(sys.argv) > 3 else None
    for n, d in load(sys.argv[1]):
        dis(d, rxc, rxm)
