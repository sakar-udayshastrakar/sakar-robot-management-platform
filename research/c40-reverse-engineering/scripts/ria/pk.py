import json,sys,glob,collections
B=sys.argv[1]; depth=int(sys.argv[2])
allc=[]
for f in sorted(glob.glob(B+'/dex/robot-installation/classes*.dex.classes.json')):
    d=json.load(open(f,encoding='utf-8'))
    n=f.split('/')[-1].split('.dex')[0]
    for c in d: c['dex']=n
    allc+=d
json.dump(allc,open(B+'/ria/allclasses.json','w'))
cnt=collections.Counter()
for c in allc:
    nm=c['class'][1:-1].split('/')
    cnt['.'.join(nm[:depth])]+=1
print(len(allc))
for k,v in sorted(cnt.items(), key=lambda x:-x[1])[:int(sys.argv[3])]:
    print(v,k)
