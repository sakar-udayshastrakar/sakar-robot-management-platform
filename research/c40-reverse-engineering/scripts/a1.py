import json,re,collections,os,sys
SP=sys.argv[1]
d=json.load(open(SP+'/debs.json',encoding='utf-8'))
print(len(d))
# control summary
rows=[]
for k,v in d.items():
    c=v['control']
    f={}
    for line in c.splitlines():
        m=re.match(r'^([A-Za-z-]+):\s*(.*)',line)
        if m: f[m.group(1)]=m.group(2)
    rows.append((k,f,v))
fields=collections.Counter()
for k,f,v in rows: fields.update(f.keys())
print(fields)
for k,f,v in rows:
    print(os.path.basename(k)[:60],'|',f.get('Package'),f.get('Version'),f.get('Installed-Size'),'|',f.get('Maintainer'),'|',list(v['maintainer_scripts'].keys()),'| nfiles',len(v['files']))
