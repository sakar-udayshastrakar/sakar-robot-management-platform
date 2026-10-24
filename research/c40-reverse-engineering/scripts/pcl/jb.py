import json,sys,glob,os
sys.stdout.reconfigure(encoding='utf-8',errors='replace')
B=sys.argv[1]
for f in sorted(glob.glob(B+'/pcl_out/jadx/'+sys.argv[2]+'*.json')):
    try:
        d=json.load(open(f,encoding='utf-8'))
        t=d['raw_result']['calls'][-1]['response']['content'][0]['text']
        j=json.loads(t)
        print('=====',j.get('full_name'),'(fell_back=%s)'%j.get('fell_back'))
        print(j.get('body','')[:int(sys.argv[3]) if len(sys.argv)>3 else 6000])
    except Exception as e:
        print('ERR',f,e)
