import re,sys,collections
B=sys.argv[1]
base=B+'/apkx/robot-installation/assets/ros/dist/assets/'
for fn in ['index-32d09e94.js','Home-0ebbc05a.js']:
    t=open(base+fn,encoding='utf-8',errors='replace').read()
    c=collections.Counter()
    for m in re.finditer(r'''["'`](/[A-Za-z0-9_\-\./\{\}\$:]{2,80})["'`]''',t):
        v=m.group(1)
        if re.search(r'\.(png|svg|jpg|gif|mp3|webm|mp4|vtt|json|js|css)$',v) : continue
        c[v]+=1
    print('=====',fn,len(c))
    print(sorted(c))
