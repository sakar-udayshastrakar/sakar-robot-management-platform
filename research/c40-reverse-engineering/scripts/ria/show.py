import re,sys
sys.stdout.reconfigure(encoding='utf-8',errors='replace')
B=sys.argv[1]; f=sys.argv[2]; cls=sys.argv[3]; meth=sys.argv[4]; maxl=int(sys.argv[5]) if len(sys.argv)>5 else 80
SKIP=('+','C Landroid/util/Log','C Ljava/lang/String;->equals','C Ljava/lang/String;->hashCode','C Ljava/lang/Object;-><init>','C Lcom/keenon/corelibs/utils/log/KLog','C Lcom/keenon/common/log')
cur=None;curm=None;buf=[];out=[]
def flush():
    global buf
    if curm and buf:
        out.append((cur,curm,buf)); 
    buf=[]
for line in open(B+'/ria/'+f,encoding='utf-8'):
    line=line.rstrip('\n')
    if line.startswith('## '):
        flush(); cur=line[3:].split(' ')[0]; curm=None; continue
    if line.startswith('  M '):
        flush(); curm=line[4:]; continue
    if cur and re.search(cls,cur) and curm and re.search(meth,curm):
        if line.strip().startswith(SKIP): continue
        buf.append(line)
flush()
for c,m,b in out:
    print('--',c.split('/')[-1],m.split('->')[1][:110])
    for l in b[:maxl]: print(l[:200].replace('Lcom/keenon/peanut/peanutservice/','~/'))
