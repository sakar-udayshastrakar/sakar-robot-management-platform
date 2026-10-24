import sys
d=open(sys.argv[1],'rb').read()
lo=int(sys.argv[2],0); hi=int(sys.argv[3],0); end=int(sys.argv[4],0)
best=[]
for s in range(lo,hi):
    tot=0; good=0
    for i in range(s,end-3):
        if d[i]==0x12:
            t=(d[i+1]<<8)|d[i+2]
            if s+t<end and t>0x20:
                tot+=1
                if d[s+t-1] in (0x22,0x32,0x80,0x02,0x00) : good+=1
    best.append((good/ max(tot,1),good,tot,s))
best.sort(reverse=True)
for b in best[:8]: print('start %#x  ratio %.2f good %d total %d'%(b[3],b[0],b[1],b[2]))
