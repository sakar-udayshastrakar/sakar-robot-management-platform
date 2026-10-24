import os,sys,json,hashlib
sys.path.insert(0,sys.argv[1]+'/scripts')
import elfinfo
SP=sys.argv[1]
W=SP+'/debwork'
res=[]
for deb in sorted(os.listdir(W)):
    for root,dirs,files in os.walk(W+'/'+deb):
        for fn in files:
            p=os.path.join(root,fn)
            if os.path.islink(p): continue
            try:
                with open(p,'rb') as f: h=f.read(4)
            except Exception: continue
            if h!=b'\x7fELF': continue
            rel=os.path.relpath(p,W+'/'+deb).replace(chr(92),'/')
            try:
                e=elfinfo.elf(p)
            except Exception as ex:
                res.append({'deb':deb.split('_')[0],'path':rel,'error':repr(ex)}); continue
            res.append({'deb':deb.split('_')[0],'path':rel,'size':os.path.getsize(p),'machine':e['machine'],'class':e['class'],'type':e['type'],'stripped':e['stripped'],'needed':e['needed'],'rpath':e['rpath'],'flags':e['flags'],'symtab_count':e['symtab_count'],'ndyn_def':len(e['dynsym_defined']),'nundef':len(e['dynsym_undefined'])})
json.dump(res,open(SP+'/an/elf.json','w'),indent=0)
print(len(res))
import collections
print(collections.Counter((r.get('machine'),r.get('class'),r.get('stripped')) for r in res))
print([r for r in res if 'error' in r][:5])
