import json,sys,re,collections,os
SP=sys.argv[1]
T=sys.argv[2]
m=json.load(open(T+'/manifest.json',encoding='utf-8'))
print('manifest keys',list(m.keys()))
files=m['files']
print(len(files))
print(collections.Counter((f.get('src'),f.get('dest'),f.get('type'),f.get('mode')) for f in files))
extra=[k for k in m.keys() if k not in ('name','files')]
for k in extra: print(k, json.dumps(m[k])[:500])
# non-standard entries
for f in files:
    if f.get('type')!='ros' or f.get('dest')!='bin/':
        print('NONSTD',f)
# keys per file
print(collections.Counter(tuple(sorted(f.keys())) for f in files))
# compare with actual deb files
names=set()
for root,d,fs in os.walk(T+'/slam-ros-v2.10.9-c2-0-g58a75a7'):
    for x in fs:
        rel=os.path.relpath(os.path.join(root,x),T+'/slam-ros-v2.10.9-c2-0-g58a75a7').replace(chr(92),'/')
        names.add(rel)
mn=set((f['src'].replace('slam-ros-v2.10.9-c2-0-g58a75a7/','')+f['name']) for f in files)
print('in manifest not on disk',sorted(mn-names))
print('on disk not in manifest',sorted(names-mn))
# dependency graph
d=json.load(open(SP+'/debs.json',encoding='utf-8'))
pk={}
for k,v in d.items():
    f={}
    for line in v['control'].splitlines():
        mm=re.match(r'^([A-Za-z-]+):\s*(.*)',line)
        if mm: f[mm.group(1)]=mm.group(2)
    pk[os.path.basename(k)]=(k,f)
# package name map (deb filename -> Package)
nm={}
for fn,(k,f) in pk.items():
    nm[f['Package']]=fn
edges=[]
ext=collections.Counter()
for fn,(k,f) in pk.items():
    deps=f.get('Depends','')
    for dep in [x.strip() for x in deps.split(',') if x.strip()]:
        dn=re.split(r'[ (|]',dep)[0]
        edges.append((f['Package'],dn,dep))
        if dn not in nm: ext[dn]+=1
json.dump({'edges':edges,'pk':{fn:{'Package':f['Package'],'Version':f['Version'],'Depends':f.get('Depends',''),'Source':f.get('Source',''),'Maintainer':f.get('Maintainer',''),'Description':f.get('Description',''),'Installed-Size':f.get('Installed-Size','')} for fn,(k,f) in pk.items()}},open(SP+'/an/deps.json','w'),indent=0)
print('edges',len(edges)); print('external deps',ext.most_common(100))
print('Version set',collections.Counter(f['Version'] for fn,(k,f) in pk.items()))
print('Source',collections.Counter(f.get('Source') for fn,(k,f) in pk.items()).most_common(8))
print('Arch',collections.Counter(f['Architecture'] for fn,(k,f) in pk.items()))
