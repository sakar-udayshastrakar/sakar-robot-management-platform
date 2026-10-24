import json,sys,collections,os,re
d=json.load(open(sys.argv[1]+'/debs.json',encoding='utf-8'))
out=open(sys.argv[1]+'/an/filelist.txt','w',encoding='utf-8')
tot=0;ext=collections.Counter()
for k,v in d.items():
    nm=os.path.basename(k).split('_')[0]
    out.write('### '+nm+'\n')
    for f in v['files']:
        if f['type']=='dir': continue
        tot+=1
        e=os.path.splitext(f['path'])[1]
        ext[e]+=1
        out.write('%s\t%s\t%s\t%s\n'%(f['path'],f['size'],f['mode'],f['type']))
print(tot);print(ext.most_common(60))
