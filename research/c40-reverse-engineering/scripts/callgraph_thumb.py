# Hand-decoder: lists BL targets (named via symtab / PLT) and MOVW/MOVT string references inside a Thumb-2 function. Not a disassembler.
import sys,struct
p=sys.argv[1]; fns=sys.argv[2:]  # names
d=open(p,'rb').read()
shoff,=struct.unpack('<I',d[32:36]); shes,shn,shstr=struct.unpack('<HHH',d[46:52])
secs=[]
for i in range(shn):
    o=shoff+i*shes; nm,ty,fl,ad,of,sz,lk,inf,al,es=struct.unpack('<IIIIIIIIII',d[o:o+40]); secs.append(dict(nm=nm,ty=ty,ad=ad,of=of,sz=sz,lk=lk,es=es))
sh=d[secs[shstr]['of']:secs[shstr]['of']+secs[shstr]['sz']]
for s in secs: s['name']=sh[s['nm']:sh.find(b'\0',s['nm'])].decode()
S={s['name']:s for s in secs}
def blob(n): s=S[n]; return d[s['of']:s['of']+s['sz']]
def cstr(t,o): return t[o:t.find(b'\0',o)].decode('utf-8','replace')
def syms(sn,stn):
    st=blob(sn); t=blob(stn); out=[]
    for k in range(len(st)//16):
        nm,val,sz,info,oth,shn_=struct.unpack('<IIIBBH',st[k*16:k*16+16]); out.append((cstr(t,nm),val,sz,info&15))
    return out
sym=syms('.symtab','.strtab'); dyn=syms('.dynsym','.dynstr')
addr2name={}
for n,v,sz,t in sym:
    if t==2 and n: addr2name[v&~1]=n
# PLT
rel=blob('.rel.plt'); plt=S['.plt']
for k in range(len(rel)//8):
    off,info=struct.unpack('<II',rel[k*8:k*8+8]); idx=info>>8
    addr2name[plt['ad']+20+12*k]=dyn[idx][0]+'@plt'
ro=S['.rodata']
def rostr(a):
    if ro['ad']<=a<ro['ad']+ro['sz']: return cstr(d[ro['of']:ro['of']+ro['sz']],a-ro['ad'])
fn={n:(v&~1,sz) for n,v,sz,t in sym if t==2}
base=0x8000
for name in fns:
    va,size=fn[name]; off=va-base; print('=== %s @%#x size %d'%(name,va,size))
    i=0; regs={}
    while i<size-1:
        h1,=struct.unpack_from('<H',d,off+i); a=va+i
        if (h1>>11) in (0x1d,0x1e,0x1f):
            h2,=struct.unpack_from('<H',d,off+i+2)
            if (h1&0xf800)==0xf000 and (h2&0xd000)==0xd000:  # BL
                s=(h1>>10)&1; j1=(h2>>13)&1; j2=(h2>>11)&1; i1=~(j1^s)&1; i2=~(j2^s)&1
                imm=(s<<24)|(i1<<23)|(i2<<22)|((h1&0x3ff)<<12)|((h2&0x7ff)<<1)
                if s: imm-=1<<25
                tgt=(a+4+imm)
                print('  %#x BL %#x %s'%(a,tgt,addr2name.get(tgt&~1,'?')))
            elif (h1&0xfbf0)==0xf240 or (h1&0xfbf0)==0xf2c0:
                imm4=h1&0xf;i_=(h1>>10)&1;imm3=(h2>>12)&7;imm8=h2&0xff;rd=(h2>>8)&0xf
                imm=(imm4<<12)|(i_<<11)|(imm3<<8)|imm8
                if (h1&0xfbf0)==0xf240: regs[rd]=imm
                else:
                    v=(imm<<16)|regs.get(rd,0); s=rostr(v)
                    if s is not None: print('  %#x str-ref %#x %r'%(a,v,s[:90]))
            i+=4
        else:
            i+=2
