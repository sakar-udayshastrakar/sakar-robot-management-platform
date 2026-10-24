import json,sys,re,collections
sys.stdout.reconfigure(encoding='utf-8',errors='replace')
B=sys.argv[1]
comps=json.load(open(B+'/ria/components.json'))
out=[]
w=out.append
tot=collections.Counter(c['tag'] for c in comps)
exp=[c for c in comps if c['effective']]
w(f"Total components in decoded manifest: {len(comps)} ({dict(tot)}). Components that are exported (explicit `android:exported=\"true\"` or implicit via `<intent-filter>` with targetSdk 22 < 31): **{len(exp)}**.\n")
def ifs(c):
    acts=sorted({a for i in c['ifs'] for a in i['actions']})
    data=[]
    for i in c['ifs']:
        for d in i['data']:
            data.append('/'.join(f"{k.replace('a_','')}={v}" for k,v in d.items()))
    cats=sorted({a.split('.')[-1] for i in c['ifs'] for a in i['cats']})
    return acts,sorted(set(data)),cats
w('| Kind | Component | exported attr | Effective | Permission | Intent-filter actions | Data | Note |')
w('|---|---|---|---|---|---|---|---|')
for c in comps:
    if not c['effective']: continue
    acts,data,cats=ifs(c)
    note=''
    n=c['name']
    if n.endswith('APPReceiver'): note='FIRST-PARTY exported receiver, no permission (see RIA-006)'
    elif n.endswith('SimpleChromeActivity') : note='LAUNCHER; deep link mapkeenon://mapandroid.com'
    elif 'SimpleChromeActivity' in n: note='FIRST-PARTY dev/test launcher activity exported with no filter'
    elif 'androidx.test' in n: note='androidx.test instrumentation helper activity bundled in release (MAIN filter, default-exported)'
    elif n.startswith('org.chromium') or 'chrome' in n: note='Chromium (M73) component'
    w(f"| {c['tag']} | `{n}` | {c['exported']} | yes | {c['perm'] or ''} | {', '.join(acts)[:140]} | {'; '.join(data)[:120]} | {note} |")
w('')
open(B+'/ria/gen_exported.md','w',encoding='utf-8').write('\n'.join(out))
# permissions list
txt=open(B+'/manifests/robot-installation.xml',encoding='utf-8').read()
perms=re.findall(r'<uses-permission(?:-sdk-23)? android:name="([^"]+)"',txt)
seen=[];
for p in perms:
    if p not in seen: seen.append(p)
print(len(perms),len(seen))
json.dump(seen,open(B+'/ria/perms.json','w'))
print(len(exp),'exported of',len(comps))
c2=collections.Counter(c['tag'] for c in exp); print(c2)
