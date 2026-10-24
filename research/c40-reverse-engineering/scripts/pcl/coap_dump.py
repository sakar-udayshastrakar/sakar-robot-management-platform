import sqlite3,sys,json,ast,re
sys.stdout.reconfigure(encoding="utf-8",errors="replace")
B=sys.argv[1]
db=sqlite3.connect("file:"+B+"/pcl_out/code.db?mode=ro",uri=True)
out=[]
for cls,atype,vals in db.execute("select cls,atype,vals from a where kind='class' and atype in ('Lcom/keenon/sdk/coap/adapter/CoapCommond;','Lcom/keenon/sdk/http/adapter/HttpCommand;','Lcom/keenon/sdk/serial/adapter/SerialCommand;')"):
    try: v=ast.literal_eval(vals)
    except Exception: v={'raw':vals}
    out.append({'api_class':cls[1:-1].replace('/','.'),'transport':atype.split('/')[-1][:-1],**v})
json.dump(out,open(B+'/pcl_out/coap_cmds.json','w',encoding='utf-8'),indent=1,ensure_ascii=False)
print(len(out))
import collections
c=collections.Counter(o['transport'] for o in out);print(c)
byp=collections.Counter(o['api_class'].split('.')[-2] for o in out);print(byp)
for o in out:
    if o['transport']!='CoapCommond': print(o)
