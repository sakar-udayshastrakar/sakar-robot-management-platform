import json,sys,re
sys.stdout.reconfigure(encoding='utf-8',errors='replace')
B=sys.argv[1]; pat=sys.argv[2]; mode=sys.argv[3] if len(sys.argv)>3 else 'full'
D=json.load(open(B+'/ria/'+(sys.argv[4] if len(sys.argv)>4 else 'keenon_deep.json'),encoding='utf-8'))
FL={1:'public',2:'private',4:'protected',8:'static',0x10:'final',0x200:'interface',0x400:'abstract',0x4000:'enum'}
def fl(f): return ' '.join(v for k,v in FL.items() if f&k)
for c in D:
    if re.search(pat,c['c']):
        print('##',c['c'],fl(c['f']),'extends',c['s'],'implements',c['i'],c['dex'])
        if mode=='list': continue
        sv=c.get('sv',[])
        for i,f in enumerate(c['fields']):
            val=''
            if i<c.get('ns',0) and i<len(sv): val=' = '+repr(sv[i])[:200]
            print('   F',fl(f[1]),f[0].split('->')[1],val)
        for m in c['methods']:
            print('   M',fl(m['f']),m['m'].split('->')[1])
            if mode in('full','str'):
                for s in m.get('str',[]): print('        S',repr(s)[:200])
            if mode=='full':
                cs=[x for x in m.get('call',[]) if not x.startswith(('Ljava/lang','Landroid/os','Landroid/util/Log'))]
                for x in cs[:25]: print('        C',x)
