import json,glob,os,collections,sys
B=sys.argv[1]
D=B+'/dex/peanut-clean/'
cls=[]
for i,f in enumerate(['classes']+['classes%d'%k for k in range(2,8)]):
    for c in json.load(open(D+f+'.dex.classes.json',encoding='utf-8')):
        c['dex']=f+'.dex'; cls.append(c)
print(len(cls))
byd=collections.Counter(c['dex'] for c in cls); print(byd)
def pk(n,d):
    p=n[1:-1].split('/')[:-1]
    return '.'.join(p[:d])
cnt=collections.Counter()
for c in cls:
    cnt[pk(c['class'],2)]+=1
for k,v in cnt.most_common(80): print(v,k)
print('--- com.keenon 3 deep')
cnt=collections.Counter()
for c in cls:
    if c['class'].startswith('Lcom/keenon/'): cnt[pk(c['class'],4)]+=1
for k,v in sorted(cnt.items()): print(v,k)
# defined classes only? classes include referenced ones (methods derived from method ids) 
print(sum(1 for c in cls if c.get('source')))
