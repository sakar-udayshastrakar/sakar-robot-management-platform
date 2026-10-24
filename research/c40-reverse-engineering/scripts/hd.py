import sys
d=open(sys.argv[1],'rb').read(); a=int(sys.argv[2],0); n=int(sys.argv[3],0)
for i in range(a,min(a+n,len(d)),16):
    c=d[i:i+16]; print('%06x %-47s %s'%(i,' '.join('%02x'%x for x in c),''.join(chr(x) if 32<=x<127 else '.' for x in c)))
