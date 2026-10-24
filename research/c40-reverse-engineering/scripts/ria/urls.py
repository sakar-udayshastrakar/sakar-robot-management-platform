import re,glob,sys,collections
sys.stdout.reconfigure(encoding='utf-8',errors='replace')
B=sys.argv[1]
c=collections.defaultdict(set)
pat=re.compile(r'((?:https?|wss?|tcp|ssl|ftp|tftp|coap|rtsp|mqtt)://[^\s"\'<>]{3,150})|(\b(?:\d{1,3}\.){3}\d{1,3}(?::\d+)?\b)|(\b[a-z0-9\-]+(?:\.[a-z0-9\-]+)*\.(?:com|cn|net|org|io|jp)\b)')
for f in sorted(glob.glob(B+'/dex/robot-installation/*.strings.txt')):
    for s in open(f,encoding='utf-8',errors='replace').read().split('\n'):
        if len(s)>400: continue
        for m in pat.finditer(s):
            v=m.group(0)
            c[f.split('/')[-1].split('.')[0]].add(v)
allv=collections.Counter()
for k,v in c.items():
    for x in v: allv[x]+=1
skip=re.compile(r'(w3\.org|apache\.org|xmlpull|schemas\.android|java\.sun|javax\.|json\.org|googleapis|android\.com|google\.com|gstatic|mozilla|eclipse\.org|jcifs|chromium|slf4j|qos\.ch|sentry\.io|github|freemarker|bouncycastle|oracle|ietf|sun\.com|xml\.org|jboss|jackson|fasterxml|apache|junit|hamcrest|kotlin|jetbrains|reactivex|squareup|bumptech|greenrobot|alibaba\.com|aliyun|amap|autonavi|tencent|umeng|bugly|example\.com|localhost|0\.0\.0\.0|127\.0\.0\.1|255\.255|1\.0\.0\.0|\d+\.\d+\.\d+\.\d+$)')
out=[]
for v,n in sorted(allv.items()):
    if re.match(r'^\d+\.\d+\.\d+\.\d+',v) or not skip.search(v): out.append(v)
print(len(out))
for v in out: print(v)
