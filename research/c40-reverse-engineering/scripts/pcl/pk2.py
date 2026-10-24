import sys; sys.path.insert(0,sys.argv[1]+'/scripts/pcl')
from lib import *
cls=load()
cnt=collections.Counter()
for c in cls:
    n=c['class']
    if n.startswith('Lcom/keenon/module/') or n.startswith('Lcom/keenon/peanut') or n.startswith('Lcom/keenon/component'):
        p=n[1:-1].split('/')[:-1]
        cnt['.'.join(p[:int(sys.argv[2])])]+=1
for k,v in sorted(cnt.items()): print(v,k)
