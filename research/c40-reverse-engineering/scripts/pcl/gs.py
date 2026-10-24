import sys,re; sys.path.insert(0,sys.argv[1]+'/scripts/pcl')
from lib import *
S=strings()
pat=re.compile(sys.argv[2],re.I)
lim=int(sys.argv[3]) if len(sys.argv)>3 else 400
n=0
seen=set()
for d,l in S.items():
    for s in l:
        if pat.search(s) and s not in seen:
            seen.add(s)
            print(d[:-4]+'\t'+s[:int(__import__("os").environ.get("W","300"))].replace('\n','\n')); n+=1
            if n>=lim: sys.exit()
