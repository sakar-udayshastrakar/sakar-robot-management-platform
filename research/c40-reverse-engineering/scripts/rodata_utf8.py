import sys,struct,io
sys.path.insert(0,'.')
p=sys.argv[1]; out=sys.argv[2]
d=open(p,'rb').read()
cls=d[4]
if cls==1:
    shoff,=struct.unpack('<I',d[32:36]); shes,shn,shstr=struct.unpack('<HHH',d[46:52])
else:
    shoff,=struct.unpack('<Q',d[40:48]); shes,shn,shstr=struct.unpack('<HHH',d[58:64])
secs=[]
for i in range(shn):
    o=shoff+i*shes
    if cls==1: nm,ty,fl,ad,of,sz=struct.unpack('<IIIIII',d[o:o+24])
    else: nm,ty,fl,ad,of,sz=struct.unpack('<IIQQQQ',d[o:o+40])
    secs.append([nm,of,sz,ad])
sh=d[secs[shstr][1]:secs[shstr][1]+secs[shstr][2]]
res=[]
with io.open(out,'w',encoding='utf-8') as f:
    for nm,of,sz,ad in secs:
        n=sh[nm:sh.find(b'\0',nm)].decode()
        if n in ('.rodata','.data'):
            data=d[of:of+sz]
            f.write('## %s addr %#x\n'%(n,ad))
            for part in data.split(b'\0'):
                if len(part)>=3:
                    try: t=part.decode('utf-8')
                    except: t=part.decode('latin1')
                    if any(c.isprintable() for c in t): f.write(repr(t)[1:-1].replace('\n','⏎')+'\n') if False else f.write(t.replace('\n','\n')+'\n')
