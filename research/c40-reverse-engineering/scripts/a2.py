import json,sys
d=json.load(open(sys.argv[1]+'/debs.json',encoding='utf-8'))
for k,v in d.items():
    for n,s in v['maintainer_scripts'].items():
        print('=====',k.split('/')[-1][:55],n,len(s)); print(s)
