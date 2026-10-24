"""Read-only DEX deep parser: per-class access flags, supers, interfaces, fields, methods (sig, flags),
const-string refs, invoked methods, field refs per method. Usage: dexx.py <apk> <out.json> <prefix1,prefix2,...>"""
import struct,sys,zipfile,json
def uleb(d,p):
    r=0;s=0
    while True:
        b=d[p];p+=1
        r|=(b&0x7f)<<s
        if not b&0x80:break
        s+=7
    return r,p
LEN=[1]*256
def setl(lo,hi,n):
    for i in range(lo,hi+1): LEN[i]=n
setl(0x00,0x01,1);LEN[2]=2;LEN[3]=3;LEN[4]=1;LEN[5]=2;LEN[6]=3;LEN[7]=1;LEN[8]=2;LEN[9]=3
setl(0x0a,0x12,1);LEN[0x13]=2;LEN[0x14]=3;LEN[0x15]=2;LEN[0x16]=2;LEN[0x17]=3;LEN[0x18]=5;LEN[0x19]=2
LEN[0x1a]=2;LEN[0x1b]=3;LEN[0x1c]=2;LEN[0x1d]=1;LEN[0x1e]=1;LEN[0x1f]=2;LEN[0x20]=2;LEN[0x21]=1;LEN[0x22]=2;LEN[0x23]=2
LEN[0x24]=3;LEN[0x25]=3;LEN[0x26]=3;LEN[0x27]=1;LEN[0x28]=1;LEN[0x29]=2;LEN[0x2a]=3;LEN[0x2b]=3;LEN[0x2c]=3
setl(0x2d,0x31,2);setl(0x32,0x3d,2);setl(0x3e,0x43,1);setl(0x44,0x6d,2);setl(0x6e,0x72,3);LEN[0x73]=1;setl(0x74,0x78,3);setl(0x79,0x7a,1)
setl(0x7b,0x8f,1);setl(0x90,0xaf,2);setl(0xb0,0xcf,1);setl(0xd0,0xe2,2);setl(0xe3,0xf9,1)
LEN[0xfa]=4;LEN[0xfb]=4;LEN[0xfc]=3;LEN[0xfd]=3;LEN[0xfe]=2;LEN[0xff]=2
TRACE=False
def parse(d,prefixes):
    (magic,chk,sig,fsize,hsize,endian,link_sz,link_off,map_off,nstr,stroff,ntype,typeoff,nproto,protooff,nfield,fieldoff,nmeth,methoff,nclass,classoff,dsz,doff)=struct.unpack_from('<8sI20sIIIIIIIIIIIIIIIIIIII',d,0)
    sids=struct.unpack_from('<%dI'%nstr,d,stroff)
    _sc={}
    def S(i):
        if i in _sc:return _sc[i]
        o=sids[i];_,p=uleb(d,o);e=d.index(b'\0',p);v=d[p:e].decode('utf-8','replace');_sc[i]=v;return v
    tids=struct.unpack_from('<%dI'%ntype,d,typeoff)
    T=lambda i:S(tids[i])
    def plist(off):
        if off==0:return []
        n=struct.unpack_from('<I',d,off)[0]
        return [T(x) for x in struct.unpack_from('<%dH'%n,d,off+4)]
    protos=[]
    for i in range(nproto):
        sh,rt,po=struct.unpack_from('<III',d,protooff+12*i)
        protos.append('('+''.join(plist(po))+')'+T(rt))
    def F(i):
        c,t,n=struct.unpack_from('<HHI',d,fieldoff+8*i);return T(c)+'->'+S(n)+':'+T(t)
    def M(i):
        c,p,n=struct.unpack_from('<HHI',d,methoff+8*i);return T(c)+'->'+S(n)+protos[p]
    def evalue(p):
        h=d[p];p+=1;t=h&0x1f;a=h>>5
        if t==0x1e:return None,p
        if t==0x1f:return bool(a),p
        if t==0x1c:
            n,p=uleb(d,p);arr=[]
            for _ in range(n):
                v,p=evalue(p);arr.append(v)
            return arr,p
        if t==0x1d:
            _,p=uleb(d,p);n,p=uleb(d,p)
            for _ in range(n):
                _,p=uleb(d,p);_,p=evalue(p)
            return '<annot>',p
        n=a+1;raw=d[p:p+n];p+=n
        if t in(0x10,0x11):
            return ('float/double:'+raw.hex()),p
        v=int.from_bytes(raw,'little',signed=(t in(0x00,0x02,0x04,0x06)))
        if t==0x17:return S(v),p
        if t==0x18:return T(v),p
        if t==0x19:return F(v),p
        if t==0x1a:return M(v),p
        if t==0x1b:return 'enum:'+F(v),p
        return v,p
    out=[]
    for ci in range(nclass):
        cidx,af,sup,ifo,src,ann,cd,sv=struct.unpack_from('<IIIIIIII',d,classoff+32*ci)
        cn=T(cidx)
        if not any(cn.startswith(p) for p in prefixes):continue
        rec={'c':cn,'f':af,'s':T(sup) if sup!=0xffffffff else None,'i':plist(ifo),'src':S(src) if src!=0xffffffff else None,'fields':[],'methods':[]}
        if cd:
            p=cd
            ns,p=uleb(d,p);ni,p=uleb(d,p);nd,p=uleb(d,p);nv,p=uleb(d,p);rec['ns']=ns
            for kind,cnt in(('s',ns),('i',ni)):
                idx=0
                for _ in range(cnt):
                    df,p=uleb(d,p);fl,p=uleb(d,p);idx+=df
                    rec['fields'].append([F(idx),fl])
            for cnt in(nd,nv):
                idx=0
                for _ in range(cnt):
                    df,p=uleb(d,p);fl,p=uleb(d,p);co,p=uleb(d,p);idx+=df
                    m={'m':M(idx),'f':fl}
                    if co:
                        ins=struct.unpack_from('<I',d,co+12)[0]
                        sts=[];calls=[];frefs=[];news=[];tr=[]
                        q=co+16;end=q+ins*2
                        while q<end:
                            u=struct.unpack_from('<H',d,q)[0];op=u&0xff
                            if op==0:
                                if u==0x0100:
                                    n=struct.unpack_from('<H',d,q+2)[0];q+=(n*2+4)*2;continue
                                if u==0x0200:
                                    n=struct.unpack_from('<H',d,q+2)[0];q+=(n*4+2)*2;continue
                                if u==0x0300:
                                    w,n=struct.unpack_from('<HI',d,q+2);q+=((n*w+1)//2+4)*2;continue
                                q+=2;continue
                            if op==0x1a: sts.append(S(struct.unpack_from('<H',d,q+2)[0]));tr.append(('S',sts[-1]))
                            elif op==0x1b: sts.append(S(struct.unpack_from('<I',d,q+2)[0]));tr.append(('S',sts[-1]))
                            elif op in(0x6e,0x6f,0x70,0x71,0x72,0x74,0x75,0x76,0x77,0x78): calls.append(M(struct.unpack_from('<H',d,q+2)[0]));tr.append(('C',calls[-1]))
                            elif 0x52<=op<=0x6d: frefs.append(F(struct.unpack_from('<H',d,q+2)[0]));tr.append(('F'+('G' if (op<=0x58 or 0x60<=op<=0x66) else 'P'),frefs[-1]))
                            elif op in(0x22,0x1c): news.append(T(struct.unpack_from('<H',d,q+2)[0]))
                            q+=LEN[op]*2
                        if TRACE:m['tr']=tr
                        if sts:m['str']=sts
                        if calls:m['call']=sorted(set(calls))
                        if frefs:m['fld']=sorted(set(frefs))
                        if news:m['new']=sorted(set(news))
                    rec['methods'].append(m)
        if sv:
            try:
                n,q=uleb(d,sv);vals=[]
                for _ in range(n):
                    v,q=evalue(q);vals.append(v)
                sf=[f for f in rec['fields'][:ns_cnt[0]]] if False else None
                rec['sv']=vals
            except Exception as e: rec['sv_err']=str(e)
        out.append(rec)
    return out
if __name__=='__main__':
    z=zipfile.ZipFile(sys.argv[1]);prefixes=sys.argv[3].split(',')
    res=[]
    for n in sorted(z.namelist()):
        if n.endswith('.dex') and '/' not in n:
            r=parse(z.read(n),prefixes)
            for x in r:x['dex']=n
            res+=r;print(n,len(r),flush=True)
    json.dump(res,open(sys.argv[2],'w',encoding='utf-8'))
