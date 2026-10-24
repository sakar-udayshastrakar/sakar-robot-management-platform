import os,sys,json,re
SP=sys.argv[1]
W=SP+'/debwork'
cat={}
out=open(SP+'/an/msgs.txt','w',encoding='utf-8')
total={'msg':0,'srv':0,'action':0}
for deb in sorted(os.listdir(W)):
    pkg=deb.split('_')[0]
    items=[]
    for root,dirs,files in os.walk(W+'/'+deb):
        for fn in sorted(files):
            ext=os.path.splitext(fn)[1]
            if ext in ('.msg','.srv','.action') and '/share/' in root.replace(chr(92),'/'):
                p=os.path.join(root,fn)
                txt=open(p,encoding='utf-8',errors='replace').read()
                lines=[]
                for l in txt.splitlines():
                    l=l.split('#')[0].strip()
                    if l: lines.append(l)
                items.append((os.path.relpath(p,W+'/'+deb).replace(chr(92),'/'),fn,ext,lines))
                total[ext[1:]]+=1
    if items:
        out.write('### '+pkg+'\n')
        for rel,fn,ext,lines in items:
            out.write('%s [%s]: %s\n'%(fn,rel.split('/share/')[-1].split('/')[0],' ; '.join(lines)))
print(total)
