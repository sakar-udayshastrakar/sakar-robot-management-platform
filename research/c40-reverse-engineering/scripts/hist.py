import sys,collections,math
d=open(sys.argv[1],'rb').read(); step=int(sys.argv[2],0)
def ent(b):
    c=collections.Counter(b); n=len(b); return -sum(v/n*math.log2(v/n) for v in c.values())
for i in range(0,len(d),step):
    b=d[i:i+step]; c=collections.Counter(b)
    top=' '.join('%02x:%d'%(k,v) for k,v in c.most_common(6))
    print('%06x ent %.2f zero %.2f ff %.2f  %s'%(i,ent(b),b.count(0)/len(b),b.count(255)/len(b),top))
