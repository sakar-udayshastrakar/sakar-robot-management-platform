import json,sys,re
sys.path.insert(0,'.')
from sig import *
sys.stdout.reconfigure(encoding='utf-8',errors='replace')
B=sys.argv[1]; pat=sys.argv[2]; stronly=len(sys.argv)>3 and sys.argv[3]=='s'
D=json.load(open(B+'/ria/keenon_deep.json',encoding='utf-8'))
FL={1:'public',2:'private',4:'protected',8:'static',0x10:'final',0x400:'abstract'}
for c in D:
    if re.search(pat,c['c']):
        kind='interface' if c['f']&0x200 else ('enum' if c['f']&0x4000 else 'class')
        print('##',kind,tname(c['c'],False),'extends',tname(c['s'],False) if c['s'] else '','impl',[tname(i,False) for i in c['i']])
        sv=c.get('sv',[])
        for i,f in enumerate(c['fields']):
            if f[1]&1 or f[1]&4:
                nm,ty=f[0].split('->')[1].split(':')
                v=''
                if i<c.get('ns',0) and i<len(sv) and sv[i] is not None: v=' = '+repr(sv[i])[:80]
                print('   f','static' if f[1]&8 else '',tname(ty),nm+v)
        for m in c['methods']:
            if m['f']&3==1 or m['f']&4:
                n,p,r=msig(m['m'])
                print('   m',('static ' if m['f']&8 else '')+r,n+'('+', '.join(p)+')')
                if stronly:
                    for s in m.get('str',[])[:6]: print('        S',repr(s)[:100])
