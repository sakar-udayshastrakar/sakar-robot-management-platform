import re,sys
p=sys.argv[1]; key=sys.argv[2].encode(); win=int(sys.argv[3]) if len(sys.argv)>3 else 600
d=open(p,'rb').read()
for m in re.finditer(re.escape(key),d):
    a=max(0,m.start()-win); b=min(len(d),m.end()+win)
    seg=d[a:b]
    print('--- offset',m.start())
    for s in re.finditer(rb'[\x20-\x7e]{4,}',seg):
        print('  ',s.group().decode()[:200])
    break
