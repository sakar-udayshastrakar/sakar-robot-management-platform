"""Read-only minimal PKCS#7 / X.509 summary of an APK's META-INF/*.RSA|DSA|EC (v1 JAR signature). Usage: certinfo.py <apk>..."""
import sys, zipfile, hashlib, re

def tlv(d, p):
    t = d[p]; l = d[p+1]; p += 2
    if l & 0x80:
        n = l & 0x7f; l = int.from_bytes(d[p:p+n], 'big'); p += n
    return t, p, l  # tag, content start, length

def children(d, p, l):
    out = []; e = p + l
    while p < e:
        t, cs, cl = tlv(d, p)
        out.append((t, cs, cl, p)); p = cs + cl
    return out

OIDN = {'2.5.4.3': 'CN', '2.5.4.6': 'C', '2.5.4.7': 'L', '2.5.4.8': 'ST', '2.5.4.10': 'O', '2.5.4.11': 'OU', '1.2.840.113549.1.9.1': 'E',
        '1.2.840.113549.1.1.11': 'sha256WithRSA', '1.2.840.113549.1.1.5': 'sha1WithRSA', '1.2.840.113549.1.1.4': 'md5WithRSA',
        '1.2.840.113549.1.1.1': 'rsaEncryption', '1.2.840.113549.1.1.13': 'sha512WithRSA'}

def oid(d, cs, cl):
    b = d[cs:cs+cl]; a = [b[0] // 40, b[0] % 40]; v = 0
    for x in b[1:]:
        v = (v << 7) | (x & 0x7f)
        if not x & 0x80: a.append(v); v = 0
    return '.'.join(map(str, a))

def name(d, cs, cl):
    parts = []
    for rdn in children(d, cs, cl):
        for atv in children(d, rdn[1], rdn[2]):
            k, v = children(d, atv[1], atv[2])[:2]
            parts.append('%s=%s' % (OIDN.get(oid(d, k[1], k[2]), oid(d, k[1], k[2])), d[v[1]:v[1]+v[2]].decode('utf-8', 'replace')))
    return ', '.join(parts)

def find_cert(d):
    # locate X.509 certificate: SEQUENCE { SEQUENCE(tbs) {...version [0]...}, ...}. Search for 0x30 0x82 candidates
    for m in re.finditer(b'\x30\x82', d):
        p = m.start()
        try:
            t, cs, cl = tlv(d, p)
            ch = children(d, cs, cl)
            if len(ch) == 3 and ch[0][0] == 0x30 and ch[1][0] == 0x30 and ch[2][0] == 0x03:
                tb = children(d, ch[0][1], ch[0][2])
                if tb[0][0] == 0xa0:
                    return d[p:cs+cl], tb, ch
        except Exception:
            continue
    return None

def summarize(apk):
    z = zipfile.ZipFile(apk)
    res = {'apk': apk}
    for n in z.namelist():
        if re.match(r'META-INF/[^/]+\.(RSA|DSA|EC)$', n):
            d = z.read(n)
            r = find_cert(d)
            if not r: res[n] = 'cert not found'; continue
            cert, tb, ch = r
            i = 1
            ser = int.from_bytes(d[0:0], 'big')
            # tb: [0]version, serial, sigalg, issuer, validity, subject, spki
            base = cert
            # re-parse relative to cert bytes
            t, cs, cl = tlv(cert, 0)
            ch = children(cert, cs, cl); tb = children(cert, ch[0][1], ch[0][2])
            off = 1 if tb[0][0] == 0xa0 else 0
            serial = cert[tb[off][1]:tb[off][1]+tb[off][2]].hex()
            sigalg = OIDN.get(oid(cert, children(cert, tb[off+1][1], tb[off+1][2])[0][1], children(cert, tb[off+1][1], tb[off+1][2])[0][2]), '?')
            issuer = name(cert, tb[off+2][1], tb[off+2][2])
            val = children(cert, tb[off+3][1], tb[off+3][2])
            nb = cert[val[0][1]:val[0][1]+val[0][2]].decode(); na = cert[val[1][1]:val[1][1]+val[1][2]].decode()
            subject = name(cert, tb[off+4][1], tb[off+4][2])
            spki = children(cert, tb[off+5][1], tb[off+5][2])
            pk = cert[spki[1][1]+1:spki[1][1]+spki[1][2]]
            try:
                t2, c2, l2 = tlv(pk, 0); modl = children(pk, c2, l2)[0][2]; bits = (modl - 1) * 8
            except Exception:
                bits = None
            res[n] = {'subject': subject, 'issuer': issuer, 'serial_hex': serial, 'sigalg': sigalg, 'notBefore': nb, 'notAfter': na,
                      'rsa_bits': bits, 'cert_sha256': hashlib.sha256(cert).hexdigest(), 'cert_sha1': hashlib.sha1(cert).hexdigest()}
    # manifest digest algs
    try:
        mf = z.read('META-INF/MANIFEST.MF').decode('utf-8', 'replace')
        res['manifest_digest_algs'] = sorted(set(re.findall(r'^(SHA[-0-9]*|MD5)-Digest', mf, re.M)))
    except KeyError:
        pass
    res['has_v2_block'] = None
    return res

if __name__ == '__main__':
    import json
    for a in sys.argv[1:]:
        print(json.dumps(summarize(a), indent=1))
