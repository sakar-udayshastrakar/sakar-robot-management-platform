"""Annotation extractor. Usage: annot.py <prefix-regex> -> prints class/method/field annotations with values"""
import struct,sys,zipfile,re,os
sys.stdout.reconfigure(encoding='utf-8',errors='replace')
apk=r"D:\Sakar Robotics Projects\C40 S-LS-M014C00-RW-F00-V246\robot-installation-assistant-v4.11.0-0-gb7c7c7b-prod-release-p.apk"
def uleb(d,p):
    r=0;s=0
    while True:
        b=d[p];p+=1;r|=(b&0x7f)<<s
        if not b&0x80:break
        s+=7
    return r,p
def run(pat,only_ann=None):
  z=zipfile.ZipFile(apk)
  for n in sorted(z.namelist()):
    if not(n.endswith('.dex') and '/' not in n):continue
    d=z.read(n)
    (magic,chk,sig,fsize,hsize,endian,link_sz,link_off,map_off,nstr,stroff,ntype,typeoff,nproto,protooff,nfield,fieldoff,nmeth,methoff,nclass,classoff,dsz,doff)=struct.unpack_from('<8sI20sIIIIIIIIIIIIIIIIIIII',d,0)
    sids=struct.unpack_from('<%dI'%nstr,d,stroff);_sc={}
    def S(i):
        if i in _sc:return _sc[i]
        _,p=uleb(d,sids[i]);e=d.index(b'\0',p);_sc[i]=d[p:e].decode('utf-8','replace');return _sc[i]
    tids=struct.unpack_from('<%dI'%ntype,d,typeoff);T=lambda i:S(tids[i])
    def plist(off):
        if off==0:return []
        k=struct.unpack_from('<I',d,off)[0];return [T(x) for x in struct.unpack_from('<%dH'%k,d,off+4)]
    protos=[]
    for i in range(nproto):
        sh,rt,po=struct.unpack_from('<III',d,protooff+12*i);protos.append('('+''.join(plist(po))+')'+T(rt))
    def M(i):
        c,p,nn=struct.unpack_from('<HHI',d,methoff+8*i);return S(nn)+protos[p]
    def F(i):
        c,t,nn=struct.unpack_from('<HHI',d,fieldoff+8*i);return S(nn)
    def ev(p):
        h=d[p];p+=1;t=h&0x1f;a=h>>5
        if t==0x1e:return None,p
        if t==0x1f:return bool(a),p
        if t==0x1c:
            k,p=uleb(d,p);arr=[]
            for _ in range(k):
                v,p=ev(p);arr.append(v)
            return arr,p
        if t==0x1d: return ann(p)
        k=a+1;raw=d[p:p+k];p+=k
        v=int.from_bytes(raw,'little',signed=(t in(0,2,4,6)))
        if t==0x17:return S(v),p
        if t==0x18:return T(v),p
        if t in(0x10,0x11):return raw.hex(),p
        if t==0x19:return F(v),p
        if t==0x1a:return M(v),p
        if t==0x1b:return 'enum:'+F(v),p
        return v,p
    def ann(p):
        ti,p=uleb(d,p);k,p=uleb(d,p);el={}
        for _ in range(k):
            ni,p=uleb(d,p);v,p=ev(p);el[S(ni)]=v
        return (T(ti),el),p
    def aset(off):
        if off==0:return []
        k=struct.unpack_from('<I',d,off)[0];res=[]
        for i in range(k):
            ao=struct.unpack_from('<I',d,off+4+4*i)[0]
            vis=d[ao];(t,el),_=ann(ao+1);res.append((t,el))
        return res
    for ci in range(nclass):
        cidx,af,sup,ifo,src,an,cd,sv=struct.unpack_from('<IIIIIIII',d,classoff+32*ci)
        cn=T(cidx)
        if not re.search(pat,cn) or an==0:continue
        co,fs,ms,ps=struct.unpack_from('<IIII',d,an)
        print('##',cn,n)
        for t,el in aset(co):
            if only_ann and not re.search(only_ann,t):continue
            print('   @C',t,el)
        q=an+16
        for i in range(fs):
            fi,ao=struct.unpack_from('<II',d,q+8*i)
            for t,el in aset(ao): print('   @F',F(fi),t,el)
        q+=8*fs
        for i in range(ms):
            mi,ao=struct.unpack_from('<II',d,q+8*i)
            for t,el in aset(ao):
                if only_ann and not re.search(only_ann,t):continue
                print('   @M',M(mi)[:90],t,el)
if __name__=='__main__':
    run(sys.argv[1],sys.argv[2] if len(sys.argv)>2 else None)
