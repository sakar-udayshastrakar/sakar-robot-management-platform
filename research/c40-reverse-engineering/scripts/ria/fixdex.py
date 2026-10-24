import json,sys,collections,re
B=sys.argv[1]; BS=chr(92)
a=json.load(open(B+'/ria/allclasses.json'))
cnt=collections.Counter(c['dex'].split(BS)[-1] for c in a)
P=r"D:/Sakar Robotics Projects/C40 S-LS-M014C00-RW-F00-V246/REVERSE_ENGINEERING_RESULTS/partials/installation_assistant_artifacts.json"
d=json.load(open(P,encoding='utf-8'))
for x in d:
    m=re.search(r'!/(classes[0-9]*)[.]dex$',x['relative_path'])
    if m:
        x['findings']=x['findings'].replace('0 class definitions','%d class definitions'%cnt[m.group(1)])
json.dump(d,open(P,'w',encoding='utf-8'),indent=1,ensure_ascii=False)
print([x['findings'][:30] for x in d if '.dex' in x['relative_path']])
