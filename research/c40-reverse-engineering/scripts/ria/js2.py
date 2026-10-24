import re,sys,collections
B=sys.argv[1]
base=B+'/apkx/robot-installation/assets/ros/dist/assets/'
s=open(base+'index-32d09e94.js',encoding='utf-8',errors='replace').read()
h=open(base+'Home-0ebbc05a.js',encoding='utf-8',errors='replace').read()
for nm,t in(('index',s),('Home',h)):
    print('=====',nm)
    rp=re.findall(r'path:"(/[^"]*)"[^{}]{0,200}?',t)
    print(sorted(set(rp)))
    # Window host usage
    for m in re.finditer(r'ws://\$\{window\.location\.hostname\}|ws://172\.16\.8\.13|ws://192\.168\.64\.20|8899|8888',t):
        a=max(0,m.start()-300); print('...',t[a:m.end()+300].replace('\n',' ')); print()
        break
