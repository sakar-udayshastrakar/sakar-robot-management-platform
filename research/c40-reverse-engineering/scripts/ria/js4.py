import re,sys,collections,json
B=sys.argv[1]
base=B+'/apkx/robot-installation/assets/ros/dist/assets/'
out=[]
for fn in ['index-32d09e94.js','Home-0ebbc05a.js']:
    t=open(base+fn,encoding='utf-8',errors='replace').read()
    # topic/service defs
    pat=re.compile(r'name:\s*"(/[A-Za-z0-9_/\-\{\}\$\.]+)"\s*,\s*(messageType|serviceType|actionName)\s*:\s*"([A-Za-z0-9_/]+)"')
    for m in pat.finditer(t): out.append((fn,m.group(1),m.group(2),m.group(3)))
    pat2=re.compile(r'(messageType|serviceType)\s*:\s*"([A-Za-z0-9_/]+)"')
    types=collections.Counter(m.group(2) for m in pat2.finditer(t))
    print(fn,'types',len(types)); print(sorted(types))
print(len(out))
for o in sorted(set(out)): print(o)
json.dump(out,open(B+'/ria/js_ros_pairs.json','w'))
