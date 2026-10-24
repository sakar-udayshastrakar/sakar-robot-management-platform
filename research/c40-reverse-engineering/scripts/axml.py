"""Minimal Android binary XML (AXML) decoder -> text XML. Read-only. Usage: axml.py file.apk [member] """
import struct, sys, zipfile

RES_STRING_POOL = 0x0001
RES_XML_START_NS = 0x0100; RES_XML_END_NS = 0x0101
RES_XML_START_EL = 0x0102; RES_XML_END_EL = 0x0103; RES_XML_CDATA = 0x0104
RES_XML_RESMAP = 0x0180

def read_pool(d, off):
    typ, hsz, size, nstr, nsty, flags, sstart, ststart = struct.unpack_from('<HHIIIIII', d, off)
    utf8 = bool(flags & 0x100)
    offs = struct.unpack_from('<%dI' % nstr, d, off + hsz)
    base = off + sstart
    out = []
    for o in offs:
        p = base + o
        if utf8:
            n = d[p]; p += 1
            if n & 0x80: n = ((n & 0x7f) << 8) | d[p]; p += 1
            n2 = d[p]; p += 1
            if n2 & 0x80: n2 = ((n2 & 0x7f) << 8) | d[p]; p += 1
            out.append(d[p:p+n2].decode('utf-8', 'replace'))
        else:
            n = struct.unpack_from('<H', d, p)[0]; p += 2
            if n & 0x8000:
                n = ((n & 0x7fff) << 16) | struct.unpack_from('<H', d, p)[0]; p += 2
            out.append(d[p:p+n*2].decode('utf-16le', 'replace'))
    return out

def typed(strs, vtype, data, raw):
    if vtype == 3: return strs[raw] if raw != 0xffffffff else ''
    if vtype == 18: return 'true' if data else 'false'
    if vtype == 16: return str(struct.unpack('<i', struct.pack('<I', data))[0])
    if vtype == 17: return hex(data)
    if vtype == 1: return '@0x%08x' % data
    if vtype == 2: return '?0x%08x' % data
    if vtype == 4: return str(struct.unpack('<f', struct.pack('<I', data))[0])
    return '(type 0x%x) 0x%x' % (vtype, data)

def decode(d):
    magic, hsz, size = struct.unpack_from('<HHI', d, 0)
    off = hsz
    strs = []
    out = []
    ind = 0
    ns = {}
    while off < len(d):
        typ, hsz, size = struct.unpack_from('<HHI', d, off)
        if typ == RES_STRING_POOL:
            strs = read_pool(d, off)
        elif typ == RES_XML_START_NS:
            line, cm, prefix, uri = struct.unpack_from('<IIII', d, off + 8)
            ns[strs[uri]] = strs[prefix]
        elif typ == RES_XML_START_EL:
            line, cm, nsi, name, astart, asize, acount, idx, cidx, sidx = struct.unpack_from('<IIIIHHHHHH', d, off + 8)
            tag = strs[name]
            atts = []
            ab = off + 16 + astart
            for i in range(acount):
                ans, an, araw, vsz, res0, vtype, vdata = struct.unpack_from('<IIIHBBI', d, ab + i * asize)
                nm = strs[an]
                if ans != 0xffffffff and strs[ans] in ns:
                    nm = ns[strs[ans]] + ':' + nm
                atts.append('%s="%s"' % (nm, typed(strs, vtype, vdata, araw)))
            out.append('  ' * ind + '<' + tag + (' ' + ' '.join(atts) if atts else '') + '>')
            ind += 1
        elif typ == RES_XML_END_EL:
            ind -= 1
            nm = struct.unpack_from('<IIII', d, off + 8)[3]
            out.append('  ' * ind + '</' + strs[nm] + '>')
        off += size
    return '\n'.join(out)

if __name__ == '__main__':
    z = zipfile.ZipFile(sys.argv[1])
    m = sys.argv[2] if len(sys.argv) > 2 else 'AndroidManifest.xml'
    sys.stdout.reconfigure(encoding='utf-8')
    print(decode(z.read(m)))
