import sqlite3,sys
sys.stdout.reconfigure(encoding="utf-8",errors="replace")
db=sqlite3.connect("file:"+sys.argv[1]+"/pcl_out/code.db?mode=ro",uri=True)
pat=sys.argv[2]
flt=sys.argv[3] if len(sys.argv)>3 else '%'
seen={}
for cls,name,s in db.execute("select m.cls,m.name,s.str from s join m on m.id=s.mid where m.cls like ? and s.str like ? order by m.id",(pat,flt)):
    seen.setdefault(cls,[])
    if s not in seen[cls]: seen[cls].append(s)
for k,v in seen.items():
    print("#",k)
    for s in v: print("   ",repr(s)[:260])
