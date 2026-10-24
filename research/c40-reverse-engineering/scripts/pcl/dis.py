"""Mini Dalvik disassembler (read-only). Usage: dis.py <base> <class-substring> [method-substring]
Prints smali-like listing with const/str/invoke/field detail."""
import struct, sys, os
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
NAMES = {0x0a: 'move-result', 0x0b: 'move-result-wide', 0x0c: 'move-result-object', 0x0d: 'move-exception', 0x0e: 'return-void', 0x0f: 'return', 0x10: 'return-wide', 0x11: 'return-object',
         0x12: 'const/4', 0x13: 'const/16', 0x14: 'const', 0x15: 'const/high16', 0x16: 'const-wide/16', 0x17: 'const-wide/32', 0x18: 'const-wide', 0x1a: 'const-string', 0x1b: 'const-string/jumbo',
         0x1c: 'const-class', 0x1f: 'check-cast', 0x20: 'instance-of', 0x22: 'new-instance', 0x23: 'new-array', 0x27: 'throw', 0x28: 'goto', 0x29: 'goto/16', 0x2a: 'goto/32',
         0x6e: 'invoke-virtual', 0x6f: 'invoke-super', 0x70: 'invoke-direct', 0x71: 'invoke-static', 0x72: 'invoke-interface',
         0x74: 'invoke-virtual/range', 0x75: 'invoke-super/range', 0x76: 'invoke-direct/range', 0x77: 'invoke-static/range', 0x78: 'invoke-interface/range',
         0x07: 'move-object', 0x01: 'move', 0x1d: 'monitor-enter', 0x1e: 'monitor-exit'}
for i, n in enumerate(['eq', 'ne', 'lt', 'ge', 'gt', 'le']):
    NAMES[0x32 + i] = 'if-' + n; NAMES[0x38 + i] = 'if-' + n + 'z'
for i, n in enumerate(['iget', 'iget-wide', 'iget-object', 'iget-boolean', 'iget-byte', 'iget-char', 'iget-short']):
    NAMES[0x52 + i] = n; NAMES[0x59 + i] = n.replace('iget', 'iput')
for i, n in enumerate(['sget', 'sget-wide', 'sget-object', 'sget-boolean', 'sget-byte', 'sget-char', 'sget-short']):
    NAMES[0x60 + i] = n; NAMES[0x67 + i] = n.replace('sget', 'sput')

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
    return d[p:d.index(b'\0', p)].decode('utf-8', 'replace')
def s16(x):
    return x - 0x10000 if x & 0x8000 else x
def s32(x):
    return x - (1 << 32) if x & 0x80000000 else x

base = sys.argv[1]; cpat = sys.argv[2]; mpat = sys.argv[3] if len(sys.argv) > 3 else ''
apk = base + '/apkx/peanut-clean'
for dn in ['classes.dex'] + ['classes%d.dex' % i for i in range(2, 8)]:
    d = open(os.path.join(apk, dn), 'rb').read()
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
            ps = [types[x].split('/')[-1] for x in struct.unpack_from('<%dH' % n, d, po + 4)]
        protos.append('(' + ','.join(ps) + ')' + types[rt].split('/')[-1])
    fields = []
    for i in range(nfield):
        c, t, n = struct.unpack_from('<HHI', d, fieldoff + 8 * i)
        fields.append((types[c], strs[n], types[t]))
    meths = []
    for i in range(nmeth):
        c, pr, n = struct.unpack_from('<HHI', d, methoff + 8 * i)
        meths.append((types[c], strs[n], protos[pr]))
    for ci in range(nclass):
        cidx, acc, sup, ifo, src, annoff, cdo, sv = struct.unpack_from('<IIIIIIII', d, classoff + 32 * ci)
        cname = types[cidx]
        if cpat not in cname or not cdo:
            continue
        p = cdo
        sf, p = uleb(d, p); inf, p = uleb(d, p); dm, p = uleb(d, p); vm, p = uleb(d, p)
        for cnt in (sf, inf):
            for _ in range(cnt):
                _, p = uleb(d, p)
                _, p = uleb(d, p)
        for cnt in (dm, vm):
            midx = 0
            for _ in range(cnt):
                dlt, p = uleb(d, p); macc, p = uleb(d, p); code, p = uleb(d, p)
                midx += dlt
                mc, mn, mp = meths[midx]
                if mpat not in mn or not code:
                    continue
                isz = struct.unpack_from('<I', d, code + 12)[0]
                regs = struct.unpack_from('<H', d, code)[0]
                print('\n.method %s->%s%s  regs=%d insns=%d (%s)' % (cname, mn, mp, regs, isz, dn))
                insns = struct.unpack_from('<%dH' % isz, d, code + 16)
                i = 0
                while i < isz:
                    u = insns[i]; op = u & 0xff; a = (u >> 8) & 0xf; b = u >> 12; aa = u >> 8
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
                    nm = NAMES.get(op, 'op_%02x' % op)
                    t = ''
                    if op == 0x12:
                        v = b if b < 8 else b - 16
                        t = 'v%d, %d' % (a, v)
                    elif op == 0x13:
                        t = 'v%d, %d' % (aa, s16(insns[i + 1]))
                    elif op == 0x14:
                        t = 'v%d, %d' % (aa, s32(insns[i + 1] | (insns[i + 2] << 16)))
                    elif op == 0x15:
                        t = 'v%d, 0x%x0000' % (aa, insns[i + 1])
                    elif op in (0x16, 0x17):
                        t = 'v%d, %d' % (aa, s16(insns[i + 1]) if op == 0x16 else s32(insns[i + 1] | (insns[i + 2] << 16)))
                    elif op == 0x1a:
                        t = 'v%d, "%s"' % (aa, strs[insns[i + 1]].replace('\n', '\\n')[:200])
                    elif op == 0x1b:
                        t = 'v%d, "%s"' % (aa, strs[insns[i + 1] | (insns[i + 2] << 16)][:200])
                    elif op in (0x1c, 0x1f, 0x22):
                        t = 'v%d, %s' % (aa, types[insns[i + 1]])
                    elif op in (0x20, 0x23):
                        t = 'v%d, v%d, %s' % (a, b, types[insns[i + 1]])
                    elif 0x6e <= op <= 0x72:
                        cnt_ = u >> 12 if False else (insns[i] >> 12)
                        g = (insns[i] >> 8) & 0xf
                        vv = insns[i + 2]
                        rl = [vv & 0xf, (vv >> 4) & 0xf, (vv >> 8) & 0xf, (vv >> 12) & 0xf, g][:cnt_]
                        c, n, pr = meths[insns[i + 1]]
                        t = '{%s} %s->%s%s' % (','.join('v%d' % r for r in rl), c, n, pr)
                    elif 0x74 <= op <= 0x78:
                        c, n, pr = meths[insns[i + 1]]
                        t = '{v%d..v%d} %s->%s%s' % (insns[i + 2], insns[i + 2] + aa - 1, c, n, pr)
                    elif 0x52 <= op <= 0x58 or 0x59 <= op <= 0x5f:
                        c, n, ty = fields[insns[i + 1]]
                        t = 'v%d, v%d, %s.%s:%s' % (a, b, c.split('/')[-1], n, ty.split('/')[-1])
                    elif 0x60 <= op <= 0x6d:
                        c, n, ty = fields[insns[i + 1]]
                        t = 'v%d, %s.%s:%s' % (aa, c, n, ty.split('/')[-1])
                    elif op in (0x0a, 0x0b, 0x0c, 0x0d, 0x0f, 0x10, 0x11, 0x27, 0x1d, 0x1e):
                        t = 'v%d' % aa
                    elif 0x32 <= op <= 0x37:
                        t = 'v%d, v%d, %+d' % (a, b, s16(insns[i + 1]))
                    elif 0x38 <= op <= 0x3d:
                        t = 'v%d, %+d' % (aa, s16(insns[i + 1]))
                    elif op == 0x28:
                        t = '%+d' % ((aa if aa < 128 else aa - 256))
                    elif op in (0x07, 0x01):
                        t = 'v%d, v%d' % (a, b)
                    elif op in (0xd0, 0xd1, 0xd2, 0xd3, 0xd4, 0xd5, 0xd6, 0xd7, 0xd8, 0xd9, 0xda, 0xdb, 0xdc, 0xdd, 0xde, 0xdf, 0xe0, 0xe1, 0xe2):
                        t = 'v%d, v%d, #%d' % (a if op < 0xd8 else aa, b if op < 0xd8 else insns[i + 1] & 0xff, s16(insns[i + 1]) if op < 0xd8 else (insns[i + 1] >> 8) if (insns[i + 1] >> 8) < 128 else (insns[i + 1] >> 8) - 256)
                    print('  %04x %s %s' % (i, nm, t))
                    i += W[op]
