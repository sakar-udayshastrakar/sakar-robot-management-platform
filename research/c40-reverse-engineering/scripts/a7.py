import json,sys,collections
SP=sys.argv[1]
e=json.load(open(SP+'/an/elf.json'))
print(len(e))
print(collections.Counter((r.get('machine'),r.get('class'),r.get('stripped'),r.get('type')) for r in e))
exe=[r for r in e if r.get('type')=='EXEC' or ('/lib/' in r['path'] and not r['path'].split('/')[-1].startswith('lib')) or r['path'].startswith('./usr/bin') or r['path'].startswith('usr/bin')]
for r in e:
    if r['path'].endswith('.so') or '.so.' in r['path']: continue
    if r['path'].endswith('.a'): continue
    print(r['deb'].replace('ros-indigo-',''),r['path'].replace('opt/ros/indigo/',''),r['size'],r['type'],'stripped' if r['stripped'] else 'SYMS=%d'%r['symtab_count'],'|',','.join(n.replace('.so','') for n in r['needed'] if not n.startswith(('libc.','libm.','libgcc','libstdc','libpthread','ld-linux','libdl','librt')))[:300])
