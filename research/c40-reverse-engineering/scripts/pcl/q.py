import sqlite3,sys
sys.stdout.reconfigure(encoding="utf-8",errors="replace")
db=sqlite3.connect("file:C:/Users/sakar/AppData/Local/Temp/claude/D--Sakar-Robotics-Projects-support-portal/5ba6b382-e7e8-4ce5-92df-45edd177f75a/scratchpad/pcl_out/code.db?mode=ro",uri=True)
lim=int(sys.argv[2]) if len(sys.argv)>2 else 200
for i,r in enumerate(db.execute(sys.argv[1])):
    if i>=lim: print("..."); break
    print(" | ".join(str(x)[:300] for x in r))
