import re,sys,collections
B=sys.argv[1]
base=B+'/apkx/robot-installation/assets/ros/dist/assets/'
for fn in ['index-32d09e94.js','Home-0ebbc05a.js','worker-6b29a5b0.js','de-8247df91.js']:
    s=open(base+fn,encoding='utf-8',errors='replace').read()
    print('=====',fn,len(s))
    urls=collections.Counter(re.findall(r'(?:https?|wss?|ws|ftp)://[A-Za-z0-9_\-\.:/\?=&%#@\{\}\$]{4,120}',s))
    for u,c in urls.most_common(80): print(c,u)
