import sys, re, json
import xml.etree.ElementTree as ET
B=sys.argv[1]
txt=open(B+'/manifests/robot-installation.xml',encoding='utf-8').read()
txt=txt.replace('android:','a_').replace('xmlns','x')
root=ET.fromstring(txt)
app=root.find('application')
comps=[]
for el in app:
    tag=el.tag
    if tag in('activity','service','receiver','provider','activity-alias'):
        at=el.attrib
        ifs=[]
        for f in el.findall('intent-filter'):
            ifs.append({'actions':[a.get('a_name') for a in f.findall('action')],'cats':[a.get('a_name') for a in f.findall('category')],'data':[dict(d.attrib) for d in f.findall('data')]})
        exp=at.get('a_exported')
        eff = (exp=='true') or (exp is None and len(ifs)>0)
        if tag=='provider' and exp is None: eff=True  # targetSdk<17? provider default true if targetSdk<17; here 22 -> default exported false... 
        comps.append({'tag':tag,'name':at.get('a_name'),'exported':exp,'effective':eff,'perm':at.get('a_permission'),'process':at.get('a_process'),'ifs':ifs,'attrs':{k:v for k,v in at.items() if k not in('a_name',)}, 'metas':[(m.get('a_name'),m.get('a_value') or m.get('a_resource')) for m in el.findall('meta-data')], 'extra':{k:v for k,v in el.attrib.items()}})
json.dump(comps,open(B+'/ria/components.json','w'),indent=1)
fp=[c for c in comps if c['name'].startswith('com.keenon') ]
print(len(comps),'components; first-party',len(fp))
from collections import Counter
print(Counter(c['tag'] for c in comps))
print('--- first party')
for c in fp:
    print(c['tag'],c['name'],'exp=',c['exported'],'eff=',c['effective'],c['perm'] or '', [i['actions'] for i in c['ifs']])
print('--- non-chromium others')
for c in comps:
    if not c['name'].startswith('com.keenon') and not c['name'].startswith('org.chromium') and not c['name'].startswith('com.google.android.apps.chrome'):
        print(c['tag'],c['name'],'exp=',c['exported'],'eff=',c['effective'],c['perm'] or '', [i['actions'] for i in c['ifs']])
