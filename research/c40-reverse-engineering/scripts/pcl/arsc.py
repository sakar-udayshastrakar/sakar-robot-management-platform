import struct,sys
sys.stdout.reconfigure(encoding='utf-8',errors='replace')
d=open(sys.argv[1],'rb').read()
def pool(off):
    t,hs,sz=struct.unpack_from('<HHI',d,off)
    n,ns,flags,so,sto=struct.unpack_from('<IIIII',d,off+8)
    utf8=flags&0x100
    offs=struct.unpack_from('<%dI'%n,d,off+hs)
    out=[]
    for o in offs:
        p=off+so+o
        if utf8:
            l=d[p]; p+=1
            if l&0x80: l=((l&0x7f)<<8)|d[p]; p+=1
            bl=d[p]; p+=1
            if bl&0x80: bl=((bl&0x7f)<<8)|d[p]; p+=1
            out.append(d[p:p+bl].decode('utf-8','replace'))
        else:
            l=struct.unpack_from('<H',d,p)[0]; p+=2
            if l&0x8000: l=((l&0x7fff)<<16)|struct.unpack_from('<H',d,p)[0]; p+=2
            out.append(d[p:p+2*l].decode('utf-16le','replace'))
    return out
p=12
gp=pool(p)
p+=struct.unpack_from('<I',d,p+4)[0]
res={}  # (type,entry)->value
tnames={}
while p<len(d):
    t,hs,sz=struct.unpack_from('<HHI',d,p)
    if t==0x200: # package
        pid=struct.unpack_from('<I',d,p+8)[0]
        name=d[p+12:p+12+256].decode('utf-16le').split('\0')[0]
        tsoff,lastpt,ksoff,lastkey=struct.unpack_from('<IIII',d,p+268)
        tp=pool(p+tsoff); kp=pool(p+ksoff)
        q=p+hs
        while q<p+sz:
            t2,hs2,sz2=struct.unpack_from('<HHI',d,q)
            if t2==0x201:
                tid=d[q+8]; flags=d[q+9]; ec=struct.unpack_from('<I',d,q+12)[0]; es=struct.unpack_from('<I',d,q+16)[0]
                cfg_size=struct.unpack_from('<I',d,q+20)[0]
                cfg=d[q+20:q+20+cfg_size]
                locale=cfg[8:12]
                offs=struct.unpack_from('<%dI'%ec,d,q+hs2)
                for i,o in enumerate(offs):
                    if o==0xffffffff: continue
                    e=q+es+o
                    esz,fl,key=struct.unpack_from('<HHI',d,e)
                    if fl&1: continue
                    vs,r0,dt,dd=struct.unpack_from('<HBBI',d,e+8)
                    k=(pid,tid,i)
                    if locale==b'\0\0\0\0' or k not in res:
                        res[k]=(tp[tid-1],kp[key],dt,dd)
            q+=sz2
    p+=sz
want=sys.argv[2:]
for w in want:
    pid,tid,idx=int(w[2:4],16),int(w[4:6],16),int(w[6:10],16)
    r=res.get((pid,tid,idx))
    if not r: print(w,None); continue
    tn,kn,dt,dd=r
    print(w,tn,kn, gp[dd] if dt==3 else (dt,dd))
