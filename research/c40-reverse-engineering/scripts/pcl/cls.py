import sqlite3,sys
sys.stdout.reconfigure(encoding="utf-8",errors="replace")
db=sqlite3.connect("file:"+sys.argv[1]+"/pcl_out/code.db?mode=ro",uri=True)
pat=sys.argv[2]
maxc=int(sys.argv[3]) if len(sys.argv)>3 else 60
for (mid,cls,name,proto,ins) in db.execute("select id,cls,name,proto,insns from m where cls like ? order by id",(pat,)).fetchall():
    ss=[r[0] for r in db.execute("select str from s where mid=?",(mid,))]
    cs=sorted(set(r[0].split('/')[-1][:-1]+'.'+r[1] for r in db.execute("select ccls,cname from c where mid=? ",(mid,)) if not r[0].startswith(('Ljava/lang','Landroid/os','Lkotlin','Ljava/util/'))))
    print("#",cls,name,proto,ins)
    if ss: print("   S:",ss[:maxc])
    if cs: print("   C:",cs[:maxc])
