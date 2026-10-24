import re,sys,json
sys.stdout.reconfigure(encoding='utf-8',errors='replace')
BS=chr(92)
B=sys.argv[1]
t=open(B+'/apkx/robot-installation/assets/ros/dist/assets/index-32d09e94.js',encoding='utf-8',errors='replace').read()
p=t.index('qrcodeTip:"Please ensure')
def up(i):
    d=0
    while i>0:
        ch=t[i]
        if ch=='}':d+=1
        elif ch=='{':
            if d==0:return i
            d-=1
        i-=1
s1=up(p);root=up(s1-1)
d=0;i=root;ins=None
while True:
    ch=t[i]
    if ins:
        if ch==BS: i+=1
        elif ch==ins: ins=None
    else:
        if ch in '"\'`': ins=ch
        elif ch=='{': d+=1
        elif ch=='}':
            d-=1
            if d==0: break
    i+=1
obj=t[root:i+1]
print(len(obj))
open(B+'/ria/spa_en_obj.txt','w',encoding='utf-8').write(obj)
d=0;ins=None;keys=[]
for j,ch in enumerate(obj):
    if ins:
        if ch==ins and obj[j-1]!=BS: ins=None
        continue
    if ch in '"\'`': ins=ch;continue
    if ch=='{':
        d+=1
        if d==2:
            m=re.search(r'([A-Za-z0-9_]+):$',obj[max(0,j-40):j])
            if m: keys.append((m.group(1),j))
    elif ch=='}': d-=1
print(len(keys),[k for k,_ in keys])
