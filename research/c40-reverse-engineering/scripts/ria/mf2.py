import json,sys
B=sys.argv[1]
c=json.load(open(B+'/ria/components.json'))
for x in c:
    if x['name'].startswith('org.chromium') or x['name'].startswith('com.google.android.apps.chrome'):
        print(x['tag'][:3],x['name'].replace('org.chromium.chrome.browser.',''),'E' if x['effective'] else '-',x['exported'],x['perm'] or '',(x['process'] or ''), ','.join(sorted({a for i in x['ifs'] for a in i['actions']}))[:100])
