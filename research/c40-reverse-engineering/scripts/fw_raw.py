import struct, sys, re, math, collections, hashlib, zlib
def ent(b):
    if not b: return 0
    c=collections.Counter(b); n=len(b)
    return -sum(v/n*math.log2(v/n) for v in c.values())
p=sys.argv[1]
d=open(p,'rb').read()
print(p,len(d),'md5',hashlib.md5(d).hexdigest(),'sha256',hashlib.sha256(d).hexdigest())
print('entropy total %.3f'%ent(d))
# block entropy
bs=4096
print('block entropy:',' '.join('%.1f'%ent(d[i:i+bs]) for i in range(0,len(d),bs)))
print('first 128 bytes:',d[:128].hex())
print('last 64 bytes:',d[-64:].hex())
w=struct.unpack('<%dI'%(min(64,len(d)//4)),d[:min(256,len(d)//4*4)])
for i,x in enumerate(w[:48]): print(i,hex(x))
print('zero-fill runs >=64:')
for m in re.finditer(rb'\x00{64,}|\xff{64,}',d): print(hex(m.start()),m.end()-m.start(),hex(d[m.start()]))
