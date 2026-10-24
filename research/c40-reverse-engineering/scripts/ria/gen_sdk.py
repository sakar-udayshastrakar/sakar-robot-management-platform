import json,sys,re,os,collections
sys.path.insert(0,os.path.dirname(os.path.abspath(__file__)))
from sig import *
sys.stdout.reconfigure(encoding='utf-8',errors='replace')
B=sys.argv[1]
D=json.load(open(B+'/ria/keenon_deep.json',encoding='utf-8'))
cmds=json.load(open(B+'/ria/sdk_commands.json',encoding='utf-8'))
cls={c['c']:c for c in D}
def pub_methods(c):
    out=[]
    for m in c['methods']:
        if (m['f']&1 or m['f']&4) and not m['m'].split('->')[1].startswith(('<clinit>','lambda$','access$')):
            n,p,r=msig(m['m'])
            if n=='<init>':
                out.append(('ctor',n,p,r,m['f']))
            else:
                out.append(('m',n,p,r,m['f']))
    return out
def is_top(n):
    base=n.split('/')[-1]
    return '$' not in base and not base.startswith('-$$') and base!='R' and base!='BuildConfig'
def kind(c):
    return 'interface' if c['f']&0x200 else ('enum' if c['f']&0x4000 else ('abstract class' if c['f']&0x400 else 'class'))
def pkgname(n): return n[1:-1].rsplit('/',1)[0].replace('/','.')
def simple(n): return n[1:-1].split('/')[-1]
out=[]
w=out.append
# ---------- package overview
pk=collections.defaultdict(list)
for c in D:
    n=c['c']
    if n.startswith('Lcom/keenon/peanut/peanutservice') or n.startswith('Lorg/chromium'): continue
    pk[pkgname(n)].append(c)
rows=[]
for p,cs in sorted(pk.items()):
    tops=[c for c in cs if is_top(c['c']) and (c['f']&1)]
    rows.append((p,len(cs),len(tops)))
w('## Appendix A. Package inventory (generated from DEX class definitions)\n')
w('Counts are total class definitions / public top-level (non-inner, non-lambda) classes. Source: `keenon_deep.json` produced by the author-written DEX parser (`scripts/ria/dexx.py`).\n')
w('| Package | Classes | Public top-level |')
w('|---|---:|---:|')
for p,a,b in rows: w(f'| `{p}` | {a} | {b} |')
w('')
# ---------- public API catalog
def catalog(prefixes,title,maxm=60,full=True):
    w(f'### {title}\n')
    for p,cs in sorted(pk.items()):
        if not any(p==x or p.startswith(x+'.') for x in prefixes): continue
        tops=sorted([c for c in cs if is_top(c['c']) and (c['f']&1)],key=lambda c:c['c'])
        if not tops: continue
        w(f'#### `{p}`\n')
        for c in tops:
            ext=c['s'] and c['s']!='Ljava/lang/Object;'
            hdr=f"**{kind(c)} `{simple(c['c'])}`**"
            if ext: hdr+=f" extends `{tname(c['s'])}`"
            if c['i']: hdr+=' implements '+', '.join('`'+tname(i)+'`' for i in c['i'])
            w('- '+hdr)
            sv=c.get('sv',[])
            consts=[]
            for i,f in enumerate(c['fields']):
                if (f[1]&1) and (f[1]&8) and (f[1]&0x10) and i<c.get('ns',0) and i<len(sv) and sv[i] is not None and not isinstance(sv[i],list):
                    nm=f[0].split('->')[1].split(':')[0]
                    consts.append(f'{nm}={sv[i]!r}')
            if kind(c)=='enum':
                en=[f[0].split('->')[1].split(':')[0] for f in c['fields'] if f[1]&0x4000]
                if en: w('  - values: '+', '.join(f'`{x}`' for x in en[:60]))
            if consts and len(consts)<=40: w('  - constants: '+'; '.join(f'`{x}`'[:90] for x in consts))
            elif consts: w(f'  - constants: {len(consts)} (first 40) '+'; '.join(f'`{x}`'[:80] for x in consts[:40]))
            ms=[x for x in pub_methods(c) if x[0]=='m' and x[1] not in('valueOf','values')]
            if ms:
                sigs=[('static ' if x[4]&8 else '')+x[3]+' '+x[1]+'('+', '.join(x[2])+')' for x in ms]
                for s in sigs[:maxm]: w('  - `'+s+'`')
                if len(sigs)>maxm: w(f'  - ... {len(sigs)-maxm} more')
        w('')
w('## Appendix B. Public API surface by package (generated)\n')
w('Signatures are decoded from DEX method_ids/proto_ids (names/types exact; parameter names are not available in release DEX unless in MethodParameters annotations). Types use simple names.\n')
catalog(['com.keenon.sdk.external','com.keenon.sdk.component','com.keenon.sdk.robot','com.keenon.sdk.config','com.keenon.sdk.auth','com.keenon.sdk.service','com.keenon.sdk.compat','com.keenon.sdk.constant','com.keenon.sdk.common','com.keenon.sdk.core','com.keenon.sdk.hedera'],'B.1 Facade, components, robot API, config, auth (com.keenon.sdk.*)',maxm=45)
catalog(['com.keenon.common','com.keenon.corelibs'],'B.2 Common layer (com.keenon.common, com.keenon.corelibs)',maxm=20)
catalog(['com.keenon.serviceSdk','com.keenon.service','com.keenon.aidl','com.keenon.bi','com.keenon.tts','com.keenon.voice'],'B.3 Service SDK / AIDL (com.keenon.serviceSdk, aidl, tts, voice)',maxm=30)
catalog(['com.keenon.peanut.core'],'B.4 peanut-core utilities (mqtt, net, security, storage, log, utils)',maxm=14)
catalog(['com.keenon.component.core','com.keenon.component.location','com.keenon.component.machineAuth','com.keenon.component.sshPwd','com.keenon.component.cybersecurity','com.keenon.component.wifi','com.keenon.component.serviceloader'],'B.5 Components (core/location/machineAuth/sshPwd/cybersecurity/wifi/serviceloader)',maxm=14)
catalog(['com.keenon.sdk.embedded','com.keenon.sdk.sensor','com.keenon.sdk.serial','com.keenon.sdk.http','com.keenon.sdk.coap','com.keenon.sdk.proxy','com.keenon.sdk.protocol','com.keenon.sdk.tftp'],'B.6 Embedded devices, sensors, transports (com.keenon.sdk.embedded/sensor/serial/http/coap/proxy)',maxm=10)
# ---------- command tables
w('## Appendix C. Robot-side command APIs declared by annotation (CoAP / Serial / HTTP)\n')
w('Each class under `com.keenon.sdk.coapapi.api.*` is annotated with `@CoapCommond(path, requestType|msgType)`, `@SerialCommand(action, body, status)` or `@HttpCommand`, plus `@LinkAdapter(board, link)`. These are STATIC REFERENCES to resources of the robot-side software (ROS/embedded boards); no live observation was made.\n')
co=[o for o in cmds if 'CoapCommond' in o['anns']]
w(f'### C.1 CoAP commands ({len(co)})\n')
w('| CoAP path | Method/msgType | API class | Signature(s) | LinkAdapter |')
w('|---|---|---|---|---|')
def en(v): return str(v).replace('enum:','') if v is not None else ''
for o in sorted(co,key=lambda o:(o['anns']['CoapCommond'].get('path',''),o['class'])):
    a=o['anns']['CoapCommond']; la=o['anns'].get('LinkAdapter',{})
    m=en(a.get('requestType')) or en(a.get('msgType')) or 'GET(default)'
    link=','.join(f'{k}={en(v)}' for k,v in la.items())
    w(f"| `{a.get('path')}` | {m} | `{o['class'].replace('com.keenon.sdk.coapapi.api.','')}` | {'; '.join(o['methods'])} | {link} |")
w('')
se=[o for o in cmds if 'SerialCommand' in o['anns']]
w(f'### C.2 Serial commands ({len(se)})\n')
w('| API class | board | link | action | body | status | Signature(s) |')
w('|---|---|---|---|---|---|---|')
for o in sorted(se,key=lambda o:o['class']):
    a=o['anns']['SerialCommand'];la=o['anns'].get('LinkAdapter',{})
    w(f"| `{o['class'].replace('com.keenon.sdk.coapapi.api.','')}` | {en(la.get('board'))} | {en(la.get('link'))} | {a.get('action')} | {a.get('body')} | {a.get('status')} | {'; '.join(o['methods'])} |")
w('')
ht=[o for o in cmds if 'HttpCommand' in o['anns']]
w(f'### C.3 HTTP commands ({len(ht)})\n')
for o in ht: w(f"- `{o['class']}` {o['anns']['HttpCommand']} {o['anns'].get('LinkAdapter',{})}")
w('')
# ---------- event keys
keys=collections.Counter()
sens=collections.Counter()
for c in D:
    if not c['c'].startswith('Lcom/keenon/sdk/'): continue
    for m in c['methods']:
        for s in m.get('str',[]):
            if re.fullmatch(r'[a-z][a-z0-9]*(\.[a-z0-9_]+){1,4}',s) and not s.startswith(('com.','org.','android.','java.','javax.','kotlin','app.','robot.','machine.')): keys[s]+=1
            if s.startswith('_sensor.') : sens[s]+=1
w('## Appendix D. Subscription / event keys referenced by the SDK (candidates)\n')
w('String literals of the form `a.b[.c]` found in `com.keenon.sdk.*` methods (used as `PeanutSDK.subscribe(key, ...)` keys or sensor/event names). INFERRED to be subscription keys; only those used by traced call sites are CONFIRMED (`navi.status`, `battery.charge.status`, `diagnosis.error` in the Installation Assistant).\n')
w(', '.join(f'`{k}`' for k in sorted(keys)))
w('')
w('### Sensor event names (`_sensor.*`)\n')
w(', '.join(f'`{k}`' for k in sorted(sens)))
w('')
# ---------- error codes
pe=cls.get('Lcom/keenon/common/error/PeanutError;')
w('## Appendix E. Error codes (`com.keenon.common.error.PeanutError`)\n')
sv=pe.get('sv',[])
rows=[]
for i,f in enumerate(pe['fields'][:pe.get('ns',0)]):
    if i<len(sv): rows.append((f[0].split('->')[1].split(':')[0],sv[i]))
w('| Constant | Value |'); w('|---|---:|')
for n,v in sorted(rows,key=lambda x:(isinstance(x[1],int) and x[1] or 0)): w(f'| `{n}` | {v} |')
w('')
open(B+'/ria/sdk_catalog_gen.md','w',encoding='utf-8').write('\n'.join(out))
print(len('\n'.join(out)),'chars',len(co),len(se),len(ht),len(keys),len(sens))
