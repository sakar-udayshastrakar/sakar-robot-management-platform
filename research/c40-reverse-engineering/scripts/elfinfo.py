"""Minimal read-only ELF + raw-binary inspector (no external deps)."""
import struct, sys, re, json, os

def strings(data, minlen=6):
    return [m.group().decode('ascii') for m in re.finditer(rb'[\x20-\x7e]{%d,}' % minlen, data)]

def elf(path):
    d = open(path, 'rb').read()
    if d[:4] != b'\x7fELF':
        return None
    cls = d[4]; end = '<' if d[5] == 1 else '>'
    r = {'class': 64 if cls == 2 else 32, 'endian': 'little' if end == '<' else 'big'}
    if cls == 1:
        (etype, mach, ver, entry, phoff, shoff, flags, ehsize, phentsize, phnum, shentsize, shnum, shstrndx) = struct.unpack(end + 'HHIIIIIHHHHHH', d[16:52])
    else:
        (etype, mach, ver, entry, phoff, shoff, flags, ehsize, phentsize, phnum, shentsize, shnum, shstrndx) = struct.unpack(end + 'HHIQQQIHHHHHH', d[16:64])
    r.update(type={1: 'REL', 2: 'EXEC', 3: 'DYN'}.get(etype, etype), machine={3: 'x86', 40: 'ARM', 62: 'x86-64', 183: 'AArch64', 8: 'MIPS'}.get(mach, mach), entry=hex(entry), flags=hex(flags))
    secs = []
    for i in range(shnum):
        o = shoff + i * shentsize
        if cls == 1:
            nm, ty, fl, ad, of, sz, lk, inf, al, es = struct.unpack(end + 'IIIIIIIIII', d[o:o+40])
        else:
            nm, ty, fl, ad, of, sz, lk, inf, al, es = struct.unpack(end + 'IIQQQQIIQQ', d[o:o+64])
        secs.append(dict(nm=nm, ty=ty, fl=fl, ad=ad, of=of, sz=sz, lk=lk, es=es))
    if secs:
        stro = secs[shstrndx]
        shs = d[stro['of']:stro['of']+stro['sz']]
        for s in secs:
            e = shs.find(b'\0', s['nm']); s['name'] = shs[s['nm']:e].decode()
    r['sections'] = [s['name'] for s in secs]
    r['stripped'] = '.symtab' not in r['sections']
    def readstr(tab, off):
        e = tab.find(b'\0', off); return tab[off:e].decode('utf-8', 'replace')
    # dynamic
    needed = []; rpath = []
    for s in secs:
        if s['name'] == '.dynamic':
            dynstr = [x for x in secs if x['name'] == '.dynstr'][0]
            tab = d[dynstr['of']:dynstr['of']+dynstr['sz']]
            es = 8 if cls == 1 else 16
            for k in range(s['sz'] // es):
                if cls == 1: tag, val = struct.unpack(end + 'II', d[s['of']+k*es:s['of']+k*es+8])
                else: tag, val = struct.unpack(end + 'QQ', d[s['of']+k*es:s['of']+k*es+16])
                if tag == 1: needed.append(readstr(tab, val))
                if tag in (15, 29): rpath.append(readstr(tab, val))
    r['needed'] = needed; r['rpath'] = rpath
    # interp
    for s in secs:
        if s['name'] == '.interp':
            r['interp'] = d[s['of']:s['of']+s['sz']].rstrip(b'\0').decode()
    # symbols
    def syms(secname, strname):
        out = []
        ss = [x for x in secs if x['name'] == secname]
        st = [x for x in secs if x['name'] == strname]
        if not ss or not st: return out
        s = ss[0]; tab = d[st[0]['of']:st[0]['of']+st[0]['sz']]
        es = 16 if cls == 1 else 24
        for k in range(s['sz'] // es):
            o = s['of'] + k * es
            if cls == 1: nm, val, sz, info, oth, shn = struct.unpack(end + 'IIIBBH', d[o:o+16])
            else: nm, info, oth, shn, val, sz = struct.unpack(end + 'IBBHQQ', d[o:o+24])
            n = readstr(tab, nm)
            if n: out.append((n, val, sz, info & 0xf, info >> 4, shn))
        return out
    dyn = syms('.dynsym', '.dynstr')
    r['dynsym_undefined'] = sorted({n for n, v, s, t, b, sh in dyn if sh == 0})
    r['dynsym_defined'] = sorted({n for n, v, s, t, b, sh in dyn if sh != 0})
    sym = syms('.symtab', '.strtab')
    r['symtab_functions'] = [n for n, v, s, t, b, sh in sym if t == 2][:3000]
    r['symtab_count'] = len(sym)
    return r

if __name__ == '__main__':
    p = sys.argv[1]
    e = elf(p)
    print(json.dumps(e, indent=1)[:6000] if e else 'not ELF')
