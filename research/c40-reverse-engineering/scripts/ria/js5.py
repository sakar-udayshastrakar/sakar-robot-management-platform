import re,sys,json,collections
B=sys.argv[1]
base=B+'/apkx/robot-installation/assets/ros/dist/assets/'
res={}
for fn in ['index-32d09e94.js','Home-0ebbc05a.js']:
    t=open(base+fn,encoding='utf-8',errors='replace').read()
    for m in re.finditer(r'([A-Z][A-Z0-9_]{2,40}):\[("(/[^"]+)")\s*,\s*"([^"]+)"(?:,([^\]]*))?\]',t):
        res[(m.group(3),m.group(4))]=(m.group(1),fn,m.group(5))
    for m in re.finditer(r'([A-Z][A-Z0-9_]{2,40}):"(/[A-Za-z0-9_/]+)"',t):
        res.setdefault((m.group(2),''),(m.group(1),fn,None))
out=[{'const':v[0],'name':k[0],'type':k[1],'file':v[1],'extra':v[2]} for k,v in sorted(res.items())]
json.dump(out,open(B+'/ria/js_ros_table.json','w'),indent=1)
print(len(out))
for o in out: print(o['const'],o['name'],o['type'],o['extra'] if o['extra'] is not None else '')
