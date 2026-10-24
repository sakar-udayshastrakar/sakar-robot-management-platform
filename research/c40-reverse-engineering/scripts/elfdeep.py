import struct,sys,re,json,hashlib
p=sys.argv[1]; d=open(p,'rb').read()
cls=d[4]; E='<'
if cls==1:
    (et,mach,ver,entry,phoff,shoff,flags,ehs,phes,phn,shes,shn,shstr)=struct.unpack('<HHIIIIIHHHHHH',d[16:52])
else:
    (et,mach,ver,entry,phoff,shoff,flags,ehs,phes,phn,shes,shn,shstr)=struct.unpack('<HHIQQQIHHHHHH',d[16:64])
secs=[]
for i in range(shn):
    o=shoff+i*shes
    if cls==1: nm,ty,fl,ad,of,sz,lk,inf,al,es=struct.unpack('<IIIIIIIIII',d[o:o+40])
    else: nm,ty,fl,ad,of,sz,lk,inf,al,es=struct.unpack('<IIQQQQIIQQ',d[o:o+64])
    secs.append(dict(nm=nm,ty=ty,fl=fl,ad=ad,of=of,sz=sz,lk=lk,es=es))
sh=d[secs[shstr]['of']:secs[shstr]['of']+secs[shstr]['sz']]
for s in secs: s['name']=sh[s['nm']:sh.find(b'\0',s['nm'])].decode()
S={s['name']:s for s in secs}
def sec(n): s=S.get(n); return d[s['of']:s['of']+s['sz']] if s else b''
print('== sections'); 
for s in secs: print('  %-20s addr %#010x off %#08x size %#08x'%(s['name'],s['ad'],s['of'],s['sz']))
print('== comment:',[x.decode(errors='replace') for x in sec('.comment').split(b'\0') if x])
bi=sec('.note.gnu.build-id'); 
if bi: print('== build-id:',bi[16:].hex())
print('== phdrs')
for i in range(phn):
    o=phoff+i*phes
    if cls==1: t,of,va,pa,fs,ms,fl,al=struct.unpack('<IIIIIIII',d[o:o+32])
    else: t,fl,of,va,pa,fs,ms,al=struct.unpack('<IIQQQQQQ',d[o:o+56])
    print('  type %#x off %#x va %#x filesz %#x memsz %#x flags %#x'%(t,of,va,fs,ms,fl))
# symtab
st=sec('.symtab'); strt=sec('.strtab')
es=16 if cls==1 else 24
syms=[]
for k in range(len(st)//es):
    o=k*es
    if cls==1: nm,val,sz,info,oth,shn_=struct.unpack('<IIIBBH',st[o:o+16])
    else: nm,info,oth,shn_,val,sz=struct.unpack('<IBBHQQ',st[o:o+24])
    n=strt[nm:strt.find(b'\0',nm)].decode(errors='replace')
    syms.append((n,val,sz,info&15,info>>4,shn_))
print('== symtab count',len(syms))
types={0:'NOTYPE',1:'OBJECT',2:'FUNC',3:'SECTION',4:'FILE',6:'TLS'}
print('== FILE symbols:',[s[0] for s in syms if s[3]==4])
print('== defined FUNC (non-libc):')
for s in syms:
    if s[3]==2 and s[5]!=0: print('  %-70s %#x size %d'%(s[0][:70],s[1],s[2]))
print('== defined OBJECT:')
for s in syms:
    if s[3]==1 and s[5]!=0: print('  %-50s %#x size %d'%(s[0][:50],s[1],s[2]))
# rodata strings
ro=sec('.rodata')
print('== .rodata strings')
for m in re.finditer(rb'[\x20-\x7e\t\n]{3,}',ro): print('  ',m.start(), repr(m.group().decode()))
# debug strings
ds=sec('.debug_str')
if ds:
    print('== .debug_str (paths/producers)')
    for m in re.finditer(rb'[\x20-\x7e]{4,}',ds):
        t=m.group().decode()
        if '/' in t or 'GNU' in t or t.endswith(('.c','.cpp','.h','.hpp','.cc')): print('  ',t)
    print('== .debug_str total strings',len(re.findall(rb'[\x20-\x7e]{4,}',ds)))
# .data strings
dt=sec('.data')
print('== .data strings',[m.group().decode() for m in re.finditer(rb'[\x20-\x7e]{4,}',dt)][:50])
